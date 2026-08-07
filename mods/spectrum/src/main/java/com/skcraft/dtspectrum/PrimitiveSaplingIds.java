package com.skcraft.dtspectrum;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Primitive Spectrum saplings replaced by DT seeds. */
public final class PrimitiveSaplingIds {
    private static final String[] COLORS = {
            "black", "blue", "brown", "cyan", "gray", "green", "light_blue", "light_gray",
            "lime", "magenta", "orange", "pink", "purple", "red", "white", "yellow",
    };

    public static final Set<ResourceLocation> BLOCK_IDS = Arrays.stream(COLORS)
            .flatMap(color -> Stream.of(
                    ResourceLocation.fromNamespaceAndPath("spectrum", color + "_sapling"),
                    ResourceLocation.fromNamespaceAndPath("spectrum", "potted_" + color + "_sapling")))
            .collect(Collectors.toUnmodifiableSet());

    private PrimitiveSaplingIds() {}

    public static boolean isBlocked(ResourceLocation id) {
        return id != null && BLOCK_IDS.contains(id);
    }
}
