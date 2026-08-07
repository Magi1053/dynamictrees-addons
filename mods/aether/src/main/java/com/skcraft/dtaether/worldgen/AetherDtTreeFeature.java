package com.skcraft.dtaether.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

public class AetherDtTreeFeature extends Feature<AetherDtTreeConfig> {
    public AetherDtTreeFeature(Codec<AetherDtTreeConfig> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<AetherDtTreeConfig> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        return AetherTreeWorldGen.tryPlaceTree(level, origin, context.random(), context.config());
    }
}
