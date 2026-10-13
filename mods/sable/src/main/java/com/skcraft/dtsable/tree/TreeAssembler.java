package com.skcraft.dtsable.tree;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.ThickBranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.DtSable;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Transactional conversion from DT destruction data to one managed Sable body. */
public final class TreeAssembler {
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    /** Neighbor updates, so the stump below a harvested tip disconnects. Network destroy stays suppressed. */
    private static final int HARVEST_FLAGS = Block.UPDATE_ALL;
    /** Not a body. The cut was harvested in place, so dropTree must not spawn a falling tree. */
    private static final UUID HARVESTED_CUT = new UUID(0L, 1L);
    private TreeAssembler() {}

    public static UUID tryAssemble(ServerLevel level, Player player, BranchDestructionData data) {
        return tryAssemble(level, player, data, null);
    }

    public static UUID tryAssemble(ServerLevel level, Player player, BranchDestructionData data,
                                    BiConsumer<BlockPos, ItemStack> drops) {
        if (!DtSableConfig.isEnabled() || DtIntegration.isMutatingBody()
                || SubLevelContainer.getContainer(level) == null) return null;
        FelledTreePlan plan = FelledTreePlan.create(level, data);
        // A DT FutureBreak can be emitted by blocks inside a Sable plot. Those coordinates
        // are plot-local storage, not standing-world coordinates, and must never recurse.
        if (Sable.HELPER.getContaining(level, plan.cutPos()) != null) return null;
        // The chopped block is excluded from the body. A tip with nothing above it is still a fell:
        // harvest that log in place instead of letting DT spawn a falling tree.
        if (plan.branches().isEmpty()) {
            return DtIntegration.mutateWithoutNetworkDestroy(() -> harvestCut(level, player, plan, drops));
        }
        if (!plan.validate(DtSableConfig.maxBlocks())) return null;
        Map<BlockPos, BlockState> source = captureSource(level, plan);
        try {
            return DtIntegration.mutateWithoutNetworkDestroy(() -> convert(level, player, plan, source, drops));
        } catch (RuntimeException | StackOverflowError failure) {
            DtSable.LOGGER.error("Failed to convert Dynamic Tree at {}; restoring it for native DT felling",
                    plan.cutPos(), failure);
            DtIntegration.mutateWithoutNetworkDestroy(() -> restoreSource(level, source));
            return null;
        }
    }

    /** One log, the cut's leaves, and its trunk shells. No body and no topple. */
    private static UUID harvestCut(ServerLevel level, Player player, FelledTreePlan plan,
                                   BiConsumer<BlockPos, ItemStack> drops) {
        BlockPos cut = plan.cutPos();
        BlockState cutState = level.getBlockState(cut);
        if (player == null || !player.isCreative()) spawnDrops(level, cut, plan.cutDrops(), drops);
        for (FelledTreePlan.Node leaf : plan.leaves()) {
            if (drops == null) TreeHarvest.breakStandingLeaf(level, leaf.pos());
            else TreeHarvest.breakStandingLeaf(level, leaf.pos(), drops);
        }
        if (DtIntegration.radius(cutState) > WoodThickness.PRIMARY) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos shell = cut.offset(dx, 0, dz);
                if (level.getBlockState(shell).getBlock() instanceof TrunkShellBlock) {
                    level.setBlock(shell, Blocks.AIR.defaultBlockState(), HARVEST_FLAGS);
                }
            }
        }
        level.setBlock(cut, Blocks.AIR.defaultBlockState(), HARVEST_FLAGS);
        return HARVESTED_CUT;
    }

    private static UUID convert(ServerLevel level, Player player, FelledTreePlan plan,
                                Map<BlockPos, BlockState> source, BiConsumer<BlockPos, ItemStack> drops) {
        clearSource(level, source.keySet());
        Map<BlockPos, FelledTreePlan.Node> branches = new HashMap<>();
        Map<BlockPos, FelledTreePlan.Node> leaves = new HashMap<>();
        plan.branches().forEach(node -> branches.put(node.pos(), node));
        plan.leaves().forEach(node -> leaves.put(node.pos(), node));
        List<TreeAssemblyPartition.Component<BlockPos>> components = TreeAssemblyPartition.partition(
                branches.keySet(), leaves.keySet(), TreeAssembler::neighbors);
        if (components.isEmpty()) throw new IllegalStateException("empty fell body");

        List<ServerSubLevel> created = new ArrayList<>();
        List<FallenTreeBody> bodies = new ArrayList<>();
        try {
            for (TreeAssemblyPartition.Component<BlockPos> component : components) {
                FallenTreeBody body = assembleComponent(level, plan, component, branches, leaves, created);
                bodies.add(body);
                FallenTreeRegistry.register(level, body);
            }
        } catch (RuntimeException | StackOverflowError failure) {
            for (ServerSubLevel subLevel : created) {
                FallenTreeRegistry.forget(subLevel.getUniqueId());
                if (!subLevel.isRemoved()) {
                    SubLevelContainer.getContainer(level).removeSubLevel(subLevel, SubLevelRemovalReason.REMOVED);
                }
            }
            throw failure;
        }

        for (FallenTreeBody body : bodies) FallenTreeRegistry.scheduleTopple(body, plan.cutPos(), player);
        spawnDrops(level, plan.cutPos(), plan.cutDrops(), drops);
        DtSable.LOGGER.info("Converted Dynamic Tree at {} to {} Sable bodies ({} branches, {} leaves)",
                plan.cutPos(), bodies.size(), plan.branches().size(), plan.leaves().size());
        return bodies.getFirst().id();
    }

    private static FallenTreeBody assembleComponent(ServerLevel level, FelledTreePlan plan,
                                                     TreeAssemblyPartition.Component<BlockPos> component,
                                                     Map<BlockPos, FelledTreePlan.Node> branches,
                                                     Map<BlockPos, FelledTreePlan.Node> leaves,
                                                     List<ServerSubLevel> created) {
        List<BlockPos> placed = new ArrayList<>();
        Set<BlockPos> worldWood = new HashSet<>();
        Set<BlockPos> worldLeaves = new HashSet<>();
        Map<BlockPos, BlockPos> worldShellOwners = new HashMap<>();
        Map<Integer, Integer> unpaidVolume = new HashMap<>();
        double lootScale = DtIntegration.lootVolumeScale(plan.species());
        boolean unstrippedCore = false;
        for (BlockPos pos : component.wood()) {
            FelledTreePlan.Node node = branches.get(pos);
            if (node == null) throw new IllegalStateException("missing planned branch");
            if (!level.getBlockState(node.pos()).canBeReplaced()) throw new IllegalStateException("fell target obstructed");
            level.setBlock(node.pos(), node.state(), FLAGS);
            placed.add(node.pos());
            worldWood.add(node.pos());
            WoodBudget.add(unpaidVolume, node.state(), node.radius(), plan.baseRadius(), lootScale);
            if (DtIntegration.yieldRadius(node.state(), node.radius()) >= WoodThickness.THRESHOLD
                    && !DtIntegration.isStrippedBranch(node.state())) {
                unstrippedCore = true;
            }
            if (node.radius() > WoodThickness.PRIMARY && node.state().getBlock() instanceof ThickBranchBlock branch) {
                branch.setRadius(level, node.pos(), node.radius(), Direction.DOWN, FLAGS);
                collectShells(level, node.pos(), placed, worldShellOwners);
            }
        }
        if (!unstrippedCore) {
            WoodBudget.foldUnstrippedIfAllCoresStripped(unpaidVolume,
                    DtIntegration.strippedBranchIndex(plan.species()));
        }
        for (BlockPos pos : component.leaves()) {
            FelledTreePlan.Node node = leaves.get(pos);
            if (node == null) throw new IllegalStateException("missing planned leaves");
            if (!level.getBlockState(node.pos()).canBeReplaced()) continue;
            level.setBlock(node.pos(), node.state(), FLAGS);
            placed.add(node.pos());
            worldLeaves.add(node.pos());
        }
        if (placed.isEmpty()) throw new IllegalStateException("empty fell body");
        BlockPos anchor = placed.getFirst();
        BoundingBox3i bounds = BoundingBox3i.from(placed);
        if (bounds == null) throw new IllegalStateException("missing body bounds");
        ServerSubLevel subLevel = SubLevelAssemblyHelper.assembleBlocks(level, anchor, placed, bounds.expand(1, 1, 1));
        if (subLevel == null || subLevel.isRemoved()) throw new IllegalStateException("Sable assembly failed");
        created.add(subLevel);
        BlockPos plotAnchor = subLevel.getPlot().getCenterBlock();
        int pendingSeeds = SeedYield.roll(worldLeaves.size(), () -> level.getRandom().nextFloat());
        return new FallenTreeBody(subLevel.getUniqueId(), level.dimension(), plan.species(),
                plan.baseRadius(), plan.establishedTree(),
                translate(worldWood, anchor, plotAnchor), translate(worldLeaves, anchor, plotAnchor),
                translateOwners(worldShellOwners, anchor, plotAnchor), unpaidVolume, pendingSeeds);
    }

    private static Iterable<BlockPos> neighbors(BlockPos pos) {
        return java.util.Arrays.stream(Direction.values()).map(pos::relative).toList();
    }

    public static Map<BlockPos, BlockState> captureSource(ServerLevel level, FelledTreePlan plan) {
        Map<BlockPos, BlockState> result = new LinkedHashMap<>();
        for (BlockPos pos : plan.sourcePositions()) result.put(pos, level.getBlockState(pos));
        for (FelledTreePlan.Node node : plan.branches()) {
            if (node.radius() <= WoodThickness.PRIMARY) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) {
                BlockPos shell = node.pos().offset(dx, 0, dz);
                result.putIfAbsent(shell, level.getBlockState(shell));
            }
        }
        return result;
    }

    public static void restoreSource(ServerLevel level, Map<BlockPos, BlockState> snapshot) {
        snapshot.forEach((pos, state) -> level.setBlock(pos, state, FLAGS));
    }

    private static void clearSource(ServerLevel level, Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BranchBlock || state.getBlock() instanceof TrunkShellBlock
                    || DtIntegration.isLeaves(state)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    private static void collectShells(ServerLevel level, BlockPos core, List<BlockPos> placed,
                                      Map<BlockPos, BlockPos> owners) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            BlockPos pos = core.offset(dx, 0, dz);
            if (level.getBlockState(pos).getBlock() instanceof TrunkShellBlock) {
                placed.add(pos);
                owners.put(pos, core);
            }
        }
    }

    private static Set<BlockPos> translate(Set<BlockPos> input, BlockPos from, BlockPos to) {
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : input) result.add(translate(pos, from, to));
        return result;
    }

    private static Map<BlockPos, BlockPos> translateOwners(Map<BlockPos, BlockPos> input, BlockPos from, BlockPos to) {
        Map<BlockPos, BlockPos> result = new HashMap<>();
        input.forEach((shell, core) -> result.put(translate(shell, from, to), translate(core, from, to)));
        return result;
    }

    private static BlockPos translate(BlockPos pos, BlockPos from, BlockPos to) {
        return to.offset(pos.getX() - from.getX(), pos.getY() - from.getY(), pos.getZ() - from.getZ());
    }

    private static void spawnDrops(ServerLevel level, BlockPos pos, List<ItemStack> stacks,
                                   BiConsumer<BlockPos, ItemStack> drops) {
        for (ItemStack stack : stacks) {
            if (drops != null) {
                drops.accept(pos, stack.copy());
                continue;
            }
            ItemEntity item = new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack.copy());
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }
}
