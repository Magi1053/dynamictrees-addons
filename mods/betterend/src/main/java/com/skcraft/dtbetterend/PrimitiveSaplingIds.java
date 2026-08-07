package com.skcraft.dtbetterend;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Primitive BetterEnd saplings replaced by DT seeds. */
public final class PrimitiveSaplingIds {
    public static final Set<ResourceLocation> BLOCK_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("betterend", "dragon_tree_sapling"),
            ResourceLocation.fromNamespaceAndPath("betterend", "lacugrove_sapling"),
            ResourceLocation.fromNamespaceAndPath("betterend", "lucernia_sapling"),
            ResourceLocation.fromNamespaceAndPath("betterend", "pythadendron_sapling"),
            ResourceLocation.fromNamespaceAndPath("betterend", "tenanea_sapling"));

    private PrimitiveSaplingIds() {}

    public static boolean isBlocked(ResourceLocation id) {
        return id != null && BLOCK_IDS.contains(id);
    }
}
