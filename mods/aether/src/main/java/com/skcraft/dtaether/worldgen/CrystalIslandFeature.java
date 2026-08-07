package com.skcraft.dtaether.worldgen;

import com.skcraft.dtaether.DtAether;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Dynamic Trees replacement for {@code aether:crystal_island}: builds the grass / holystone
 * platform first, then places a crystal-skyroot DT tree on the surface (vanilla order).
 */
public class CrystalIslandFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceLocation CRYSTAL_SKYROOT =
            ResourceLocation.fromNamespaceAndPath(DtAether.MOD_ID, "crystal_skyroot");

    private static final BlockState AETHER_GRASS =
            BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("aether", "aether_grass_block"))
                    .defaultBlockState();
    private static final BlockState HOLYSTONE =
            BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("aether", "holystone"))
                    .defaultBlockState();

    public CrystalIslandFeature() {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();

        buildIsland(level, origin);

        BlockPos treePos = origin.above();
        if (!AetherTreeWorldGen.tryPlaceSpecies(level, treePos, random, CRYSTAL_SKYROOT)) {
            return false;
        }
        return true;
    }

    private static void buildIsland(WorldGenLevel level, BlockPos origin) {
        for (int layer = 0; layer < 3; layer++) {
            BlockState islandBlock = layer == 0 ? AETHER_GRASS : HOLYSTONE;
            BlockPos center = origin.below(layer);
            setIslandBlock(level, center, islandBlock);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                setIslandBlock(level, center.relative(direction), islandBlock);
                if (layer < 2) {
                    setIslandBlock(level, center.relative(direction, 2), islandBlock);
                    setIslandBlock(
                            level,
                            center.relative(direction).relative(direction.getClockWise()),
                            islandBlock);
                }
            }
        }
    }

    private static void setIslandBlock(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (!level.isStateAtPosition(pos, existing -> existing.canBeReplaced() || existing.isAir())) {
            return;
        }
        level.setBlock(pos, state, 2);
    }
}
