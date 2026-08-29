package com.skcraft.dtsable.tree;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.systems.nodemapper.NetVolumeNode;
import com.skcraft.dtsable.dt.DtIntegration;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The sole removal path for DT blocks owned by a managed fallen body. */
public final class TreeHarvest {
    private static final int FLAGS = Block.UPDATE_ALL;
    private TreeHarvest() {}

    public static boolean breakManually(ServerLevel level, BlockPos pos, Player player) {
        FallenTreeBody body = FallenTreeRegistry.find(level, pos).orElse(null);
        if (body == null) return false;
        if (body.leaves().contains(pos) && level.getBlockState(pos).is(BlockTags.LEAVES)) {
            return snapFrom(level, pos);
        }
        return breakAt(level, pos, player, false);
    }

    public static boolean breakAt(ServerLevel level, BlockPos requested, Player player, boolean collision) {
        FallenTreeBody body = FallenTreeRegistry.find(level, requested).orElse(null);
        if (body == null) return false;
        BlockPos pos = body.shellOwners().getOrDefault(requested, requested);
        BlockState selected = level.getBlockState(pos);
        if (!body.wood().contains(pos)) return false;
        boolean rigid = WoodThickness.isRigid(selected);
        boolean thin = WoodThickness.isThinWood(selected);
        if (!rigid && !thin) return false;

        Set<BlockPos> thinWood = new HashSet<>();
        for (BlockPos wood : body.wood()) if (WoodThickness.isThinWood(level.getBlockState(wood))) thinWood.add(wood);
        Set<BlockPos> removedWood = new HashSet<>();
        if (collision) {
            // Collision callbacks identify the exact collider that was hit. Do not turn that
            // into a harvest-style flood through every connected thin branch.
            removedWood.add(pos);
        } else if (rigid) {
            removedWood.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                removedWood.addAll(TreeTopology.flood(neighbor, thinWood, TreeHarvest::neighbors));
            }
        } else {
            removedWood.addAll(TreeTopology.flood(pos, thinWood, TreeHarvest::neighbors));
        }
        if (removedWood.isEmpty()) return false;

        Set<BlockPos> remainingWood = new HashSet<>(body.wood());
        remainingWood.removeAll(removedWood);
        if (collision) {
            Set<BlockPos> unsupportedThin = unsupportedThinWood(level, remainingWood);
            removedWood.addAll(unsupportedThin);
            remainingWood.removeAll(unsupportedThin);
        }
        Set<BlockPos> removedLeaves = unsupportedLeaves(body, remainingWood);
        List<Set<BlockPos>> components = TreeTopology.connectedComponents(remainingWood, TreeHarvest::neighbors);

        return DtIntegration.mutateWithoutNetworkDestroy(() -> {
            splitSecondaryComponents(level, body, components, removedLeaves);
            dropHarvest(level, body, pos, selected, removedWood, player, collision);
            playBreakSound(level, pos, selected);
            removeOwned(level, body, removedWood, removedLeaves);
            updateSourceBody(level, body, components, removedWood, removedLeaves);
            return true;
        });
    }

    public static boolean snapFrom(ServerLevel level, BlockPos pos) {
        FallenTreeBody body = FallenTreeRegistry.find(level, pos).orElse(null);
        if (body == null) return false;
        BlockState state = level.getBlockState(pos);
        if (!WoodThickness.isAutoBreakable(state)) return false;
        if (state.is(BlockTags.LEAVES)) {
            DtIntegration.mutateWithoutNetworkDestroy(() -> {
                playBreakSound(level, pos, state);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            });
            Set<BlockPos> leaves = new HashSet<>(body.leaves());
            leaves.remove(pos);
            FallenTreeRegistry.register(new FallenTreeBody(body.id(), body.dimension(), body.species(),
                    body.wood(), leaves, body.shellOwners()));
            return true;
        }
        return breakAt(level, pos, null, true);
    }

    public static boolean breakStandingLeaf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.LEAVES) || FallenTreeRegistry.find(level, pos).isPresent()) return false;
        playBreakSound(level, pos, state);
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    private static void playBreakSound(ServerLevel level, BlockPos pos, BlockState state) {
        var sound = state.getSoundType();
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS,
                (sound.getVolume() + 1.0F) / 2.0F, sound.getPitch() * 0.8F);
    }

    private static void splitSecondaryComponents(ServerLevel level, FallenTreeBody source,
                                                 List<Set<BlockPos>> components, Set<BlockPos> removedLeaves) {
        if (components.size() <= 1) return;
        Set<BlockPos> availableLeaves = new HashSet<>(source.leaves());
        availableLeaves.removeAll(removedLeaves);
        for (int i = 1; i < components.size(); i++) {
            Set<BlockPos> wood = components.get(i);
            Set<BlockPos> leaves = attachedLeaves(level, availableLeaves, wood);
            availableLeaves.removeAll(leaves);
            Map<BlockPos, BlockPos> shells = new HashMap<>();
            source.shellOwners().forEach((shell, core) -> { if (wood.contains(core)) shells.put(shell, core); });
            Set<BlockPos> moving = new HashSet<>(wood);
            moving.addAll(leaves);
            moving.addAll(shells.keySet());
            if (moving.isEmpty()) continue;
            BlockPos anchor = wood.iterator().next();
            BoundingBox3i bounds = BoundingBox3i.from(moving);
            if (bounds == null) continue;
            ServerSubLevel split = SubLevelAssemblyHelper.assembleBlocks(level, anchor, moving, bounds.expand(1, 1, 1));
            if (split == null || split.isRemoved()) throw new IllegalStateException("Sable split failed");
            BlockPos destination = split.getPlot().getCenterBlock();
            FallenTreeBody splitBody = new FallenTreeBody(split.getUniqueId(), level.dimension(), source.species(),
                    translate(wood, anchor, destination), translate(leaves, anchor, destination),
                    translateOwners(shells, anchor, destination));
            FallenTreeRegistry.register(level, splitBody);
        }
    }

    private static void removeOwned(ServerLevel level, FallenTreeBody body, Set<BlockPos> wood, Set<BlockPos> leaves) {
        Set<BlockPos> positions = new HashSet<>(wood);
        positions.addAll(leaves);
        body.shellOwners().forEach((shell, core) -> { if (wood.contains(core)) positions.add(shell); });
        for (BlockPos pos : positions) level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
    }

    private static void updateSourceBody(ServerLevel level, FallenTreeBody old, List<Set<BlockPos>> components,
                                         Set<BlockPos> removedWood, Set<BlockPos> removedLeaves) {
        if (components.isEmpty()) {
            ServerSubLevel source = FallenTreeRegistry.subLevel(level, old.id());
            if (source != null) {
                SubLevelContainer.getContainer(level).removeSubLevel(source, SubLevelRemovalReason.REMOVED);
            }
            FallenTreeRegistry.forget(old.id());
            return;
        }
        Set<BlockPos> largest = components.getFirst();
        Set<BlockPos> leaves = attachedLeaves(level, difference(old.leaves(), removedLeaves), largest);
        Map<BlockPos, BlockPos> shells = new HashMap<>();
        old.shellOwners().forEach((shell, core) -> { if (largest.contains(core)) shells.put(shell, core); });
        FallenTreeRegistry.register(new FallenTreeBody(old.id(), old.dimension(), old.species(), largest, leaves, shells));
        ServerSubLevel source = FallenTreeRegistry.subLevel(level, old.id());
    }

    private static Set<BlockPos> unsupportedThinWood(ServerLevel level, Set<BlockPos> remainingWood) {
        Set<BlockPos> rigid = new HashSet<>();
        Set<BlockPos> thin = new HashSet<>();
        for (BlockPos pos : remainingWood) {
            if (WoodThickness.isRigid(level.getBlockState(pos))) rigid.add(pos);
            else if (WoodThickness.isThinWood(level.getBlockState(pos))) thin.add(pos);
        }
        Set<BlockPos> unsupported = new HashSet<>();
        for (Set<BlockPos> component : TreeTopology.connectedComponents(thin, TreeHarvest::neighbors)) {
            if (component.stream().noneMatch(pos -> touches(pos, rigid))) unsupported.addAll(component);
        }
        return unsupported;
    }

    private static Set<BlockPos> unsupportedLeaves(FallenTreeBody body, Set<BlockPos> remainingWood) {
        Set<BlockPos> candidates = new HashSet<>(body.leaves());
        Set<BlockPos> unsupported = new HashSet<>();
        for (Set<BlockPos> leafGroup : TreeTopology.connectedComponents(candidates, TreeHarvest::neighbors)) {
            boolean supported = leafGroup.stream().anyMatch(leaf -> touches(leaf, remainingWood));
            if (!supported) unsupported.addAll(leafGroup);
        }
        return unsupported;
    }

    private static Set<BlockPos> attachedLeaves(ServerLevel level, Set<BlockPos> available, Set<BlockPos> wood) {
        Set<BlockPos> result = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos leaf : available) if (touches(leaf, wood)) { result.add(leaf); queue.add(leaf); }
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (available.contains(next) && result.add(next)) queue.add(next);
            }
        }
        return result;
    }

    private static boolean touches(BlockPos pos, Set<BlockPos> target) {
        for (Direction direction : Direction.values()) if (target.contains(pos.relative(direction))) return true;
        return false;
    }

    private static Iterable<BlockPos> neighbors(BlockPos pos) {
        return java.util.Arrays.stream(Direction.values()).map(pos::relative).toList();
    }

    private static void dropHarvest(ServerLevel level, FallenTreeBody body, BlockPos at, BlockState selected,
                                    Set<BlockPos> removed, Player player, boolean collision) {
        if (!collision && player != null && !player.isCreative() && WoodThickness.isRigid(selected)
                && selected.getBlock() instanceof BranchBlock branch) {
            Block.popResource(level, at, new ItemStack(DtIntegration.primitiveLog(branch)));
        }
        if (player != null && player.isCreative()) return;
        NetVolumeNode.Volume volume = new NetVolumeNode.Volume();
        for (BlockPos pos : removed) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof BranchBlock branch) || !WoodThickness.isThinWood(state)) continue;
            int radius = WoodThickness.radius(state);
            volume.addVolume(radius * radius * 64, branch.getFamily().getBranchBlockIndex(branch));
        }
        List<ItemStack> drops = body.species().getBranchesDrops(level, volume,
                player == null ? ItemStack.EMPTY : player.getMainHandItem());
        for (ItemStack drop : drops) Block.popResource(level, at, drop);
    }

    private static Set<BlockPos> difference(Set<BlockPos> all, Set<BlockPos> removed) {
        Set<BlockPos> result = new HashSet<>(all);
        result.removeAll(removed);
        return result;
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
}
