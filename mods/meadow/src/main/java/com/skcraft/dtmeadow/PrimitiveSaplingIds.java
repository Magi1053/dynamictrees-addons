package com.skcraft.dtmeadow;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Primitive Meadow saplings replaced by DT seeds. */
public final class PrimitiveSaplingIds {
    public static final Set<ResourceLocation> BLOCK_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("meadow", "pine_sapling"),
            ResourceLocation.fromNamespaceAndPath("meadow", "alpine_birch_sapling"),
            ResourceLocation.fromNamespaceAndPath("meadow", "yellow_pine_sapling"));

    private PrimitiveSaplingIds() {}

    public static boolean isBlocked(ResourceLocation id) {
        return id != null && BLOCK_IDS.contains(id);
    }
}
