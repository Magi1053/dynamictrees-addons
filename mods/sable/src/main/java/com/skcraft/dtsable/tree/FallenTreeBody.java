package com.skcraft.dtsable.tree;

import com.dtteam.dynamictrees.tree.species.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Authoritative ownership metadata for one managed Sable tree body. */
public record FallenTreeBody(
        UUID id,
        ResourceKey<Level> dimension,
        Species species,
        Set<BlockPos> wood,
        Set<BlockPos> leaves,
        Map<BlockPos, BlockPos> shellOwners) {
    public FallenTreeBody {
        wood = Set.copyOf(wood);
        leaves = Set.copyOf(leaves);
        shellOwners = Map.copyOf(shellOwners);
    }

    public boolean owns(BlockPos pos) {
        return wood.contains(pos) || leaves.contains(pos) || shellOwners.containsKey(pos);
    }
}
