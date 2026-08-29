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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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

/** Transactional conversion from DT destruction data to one managed Sable body. */
public final class TreeAssembler {
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private TreeAssembler() {}

    public static UUID tryAssemble(ServerLevel level, Player player, BranchDestructionData data,
                                   List<ItemStack> woodDrops) {
        if (!DtSableConfig.isEnabled() || DtIntegration.isMutatingBody()
                || SubLevelContainer.getContainer(level) == null) return null;
        FelledTreePlan plan = FelledTreePlan.create(level, data, woodDrops);
        // A DT FutureBreak can be emitted by blocks inside a Sable plot. Those coordinates
        // are plot-local storage, not standing-world coordinates, and must never recurse.
        if (Sable.HELPER.getContaining(level, plan.cutPos()) != null) return null;
        if (!plan.validate(DtSableConfig.maxBlocks())) return null;
        Map<BlockPos, BlockState> source = captureSource(level, plan);
        try {
            return DtIntegration.mutateWithoutNetworkDestroy(() -> convert(level, player, plan, source));
        } catch (RuntimeException | StackOverflowError failure) {
            DtSable.LOGGER.error("Failed to convert Dynamic Tree at {}; restoring it for native DT felling",
                    plan.cutPos(), failure);
            DtIntegration.mutateWithoutNetworkDestroy(() -> restoreSource(level, source));
            return null;
        }
    }

    private static UUID convert(ServerLevel level, Player player, FelledTreePlan plan,
                                Map<BlockPos, BlockState> source) {
        clearSource(level, source.keySet());
        List<BlockPos> placed = new ArrayList<>();
        Set<BlockPos> worldWood = new HashSet<>();
        Set<BlockPos> worldLeaves = new HashSet<>();
        Map<BlockPos, BlockPos> worldShellOwners = new HashMap<>();
        for (FelledTreePlan.Node node : plan.branches()) {
            if (!level.getBlockState(node.pos()).canBeReplaced()) throw new IllegalStateException("fell target obstructed");
            level.setBlock(node.pos(), node.state(), FLAGS);
            placed.add(node.pos());
            worldWood.add(node.pos());
            if (node.radius() > WoodThickness.PRIMARY && node.state().getBlock() instanceof ThickBranchBlock branch) {
                branch.setRadius(level, node.pos(), node.radius(), Direction.DOWN, FLAGS);
                collectShells(level, node.pos(), placed, worldShellOwners);
            }
        }
        for (FelledTreePlan.Node node : plan.leaves()) {
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
        BlockPos plotAnchor = subLevel.getPlot().getCenterBlock();
        FallenTreeBody body = new FallenTreeBody(subLevel.getUniqueId(), level.dimension(), plan.species(),
                translate(worldWood, anchor, plotAnchor), translate(worldLeaves, anchor, plotAnchor),
                translateOwners(worldShellOwners, anchor, plotAnchor));
        FallenTreeRegistry.register(level, body);
        FallenTreeRegistry.scheduleTopple(body, plan.cutPos(), player);
        spawnDrops(level, plan.cutPos(), plan.cutDrops());
        DtSable.LOGGER.info("Converted Dynamic Tree at {} to Sable body {} ({} branches, {} leaves)",
                plan.cutPos(), body.id(), body.wood().size(), body.leaves().size());
        return body.id();
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
                    || state.is(BlockTags.LEAVES)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
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

    private static void spawnDrops(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        for (ItemStack stack : drops) {
            ItemEntity item = new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, stack.copy());
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }
}
