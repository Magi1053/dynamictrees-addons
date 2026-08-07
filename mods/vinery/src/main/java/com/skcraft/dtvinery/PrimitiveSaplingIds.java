package com.skcraft.dtvinery;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Primitive Vinery saplings replaced by DT seeds. */
public final class PrimitiveSaplingIds {
    public static final Set<ResourceLocation> BLOCK_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("vinery", "apple_tree_sapling"),
            ResourceLocation.fromNamespaceAndPath("vinery", "dark_cherry_sapling"),
            ResourceLocation.fromNamespaceAndPath("vinery", "potted_apple_tree_sapling"),
            ResourceLocation.fromNamespaceAndPath("vinery", "potted_dark_cherry_tree_sapling"));

    private PrimitiveSaplingIds() {}

    public static boolean isBlocked(ResourceLocation id) {
        return id != null && BLOCK_IDS.contains(id);
    }
}
