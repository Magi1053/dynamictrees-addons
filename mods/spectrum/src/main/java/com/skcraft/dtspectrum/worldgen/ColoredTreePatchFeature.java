package com.skcraft.dtspectrum.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Replaces Spectrum's {@code colored_tree_patch} configured feature: same random-patch
 * attempt count and spread, CMY color weights, and DT {@link com.dtteam.dynamictrees.tree.species.Species}
 * worldgen at each valid ground position.
 */
public class ColoredTreePatchFeature extends Feature<NoneFeatureConfiguration> {
    public ColoredTreePatchFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        return SpectrumTreeWorldGen.tryPlacePatch(level, origin, context.random());
    }
}
