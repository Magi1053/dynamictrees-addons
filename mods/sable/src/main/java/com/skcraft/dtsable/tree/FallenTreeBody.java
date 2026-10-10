package com.skcraft.dtsable.tree;

import com.dtteam.dynamictrees.tree.species.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative ownership metadata for one managed Sable tree body.
 *
 * {@code unpaidVolume} is the scaled DT branch volume still owed per branch-block index.
 * It starts as the grown volume of the wood placed in the body and shrinks as logs and
 * sticks are paid out.
 */
public record FallenTreeBody(
        UUID id,
        ResourceKey<Level> dimension,
        Species species,
        int baseRadius,
        boolean establishedTree,
        Set<BlockPos> wood,
        Set<BlockPos> leaves,
        Map<BlockPos, BlockPos> shellOwners,
        Map<Integer, Integer> unpaidVolume) {
    public FallenTreeBody {
        baseRadius = Math.max(0, baseRadius);
        wood = Set.copyOf(wood);
        leaves = Set.copyOf(leaves);
        shellOwners = Map.copyOf(shellOwners);
        unpaidVolume = Map.copyOf(unpaidVolume);
    }

    public boolean owns(BlockPos pos) {
        return wood.contains(pos) || leaves.contains(pos) || shellOwners.containsKey(pos);
    }

    public FallenTreeBody withBlocks(Set<BlockPos> wood, Set<BlockPos> leaves, Map<BlockPos, BlockPos> shellOwners,
                                     Map<Integer, Integer> unpaidVolume) {
        return new FallenTreeBody(id, dimension, species, baseRadius, establishedTree, wood, leaves, shellOwners,
                unpaidVolume);
    }

    public FallenTreeBody withLeaves(Set<BlockPos> leaves) {
        return withBlocks(wood, leaves, shellOwners, unpaidVolume);
    }

    /** A new body that inherits this body's standing-tree metadata. */
    public FallenTreeBody child(UUID childId, Set<BlockPos> wood, Set<BlockPos> leaves,
                                Map<BlockPos, BlockPos> shellOwners, Map<Integer, Integer> unpaidVolume) {
        return new FallenTreeBody(childId, dimension, species, baseRadius, establishedTree, wood, leaves,
                shellOwners, unpaidVolume);
    }
}
