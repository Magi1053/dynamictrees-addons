package com.skcraft.dtaether;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Primitive Aether saplings replaced by DT seeds. */
public final class PrimitiveSaplingIds {
    public static final Set<ResourceLocation> BLOCK_IDS = Set.of(
            ResourceLocation.fromNamespaceAndPath("dtaether", "crystal_skyroot_seed"),
            ResourceLocation.fromNamespaceAndPath("dtaether", "crystal_skyroot_sapling"),
            ResourceLocation.fromNamespaceAndPath("aether", "skyroot_sapling"),
            ResourceLocation.fromNamespaceAndPath("aether", "golden_oak_sapling"),
            ResourceLocation.fromNamespaceAndPath("deep_aether", "roseroot_sapling"),
            ResourceLocation.fromNamespaceAndPath("deep_aether", "blue_roseroot_sapling"),
            ResourceLocation.fromNamespaceAndPath("deep_aether", "conberry_sapling"),
            ResourceLocation.fromNamespaceAndPath("deep_aether", "cruderoot_sapling"),
            ResourceLocation.fromNamespaceAndPath("deep_aether", "sunroot_sapling"),
            ResourceLocation.fromNamespaceAndPath("deep_aether", "yagroot_sapling"));

    private PrimitiveSaplingIds() {}

    public static boolean isBlocked(ResourceLocation id) {
        return id != null && BLOCK_IDS.contains(id);
    }
}
