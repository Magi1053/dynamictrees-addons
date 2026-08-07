package com.skcraft.dtaether.worldgen;

import com.dtteam.dynamictrees.api.worldgen.LevelContext;
import com.dtteam.dynamictrees.api.worldgen.RandomXOR;
import com.dtteam.dynamictrees.tree.species.Species;
import com.dtteam.dynamictrees.utility.CoordUtils;
import com.dtteam.dynamictrees.worldgen.DynamicTreeGenerationContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/** Places a single Dynamic Trees species at a worldgen origin (after Aether placement modifiers). */
public final class AetherTreeWorldGen {
    public static final int WORLD_GEN_RADIUS = 1;
    public static final int GROUND_SEARCH_DEPTH = 4;

    private AetherTreeWorldGen() {}

    public static boolean tryPlaceSpecies(
            WorldGenLevel level, BlockPos origin, RandomSource random, ResourceLocation speciesId) {
        return tryPlaceTree(
                level,
                origin,
                random,
                new AetherDtTreeConfig(
                        java.util.List.of(new AetherDtTreeConfig.WeightedSpecies(speciesId, 1))));
    }

    public static boolean tryPlaceTree(
            WorldGenLevel level, BlockPos origin, RandomSource random, AetherDtTreeConfig config) {
        ResourceLocation speciesId = config.pickSpecies(random);
        Species species = Species.REGISTRY.get(speciesId);
        if (!species.isValid()) {
            return false;
        }

        BlockPos groundPos = resolveGround(level, origin, species);
        if (groundPos == null) {
            return false;
        }
        BlockPos plantPos = groundPos.above();

        if (!passesSurfaceWaterDepth(level, groundPos)) {
            return false;
        }
        if (!level.getBlockState(plantPos).isAir() && !level.getBlockState(plantPos).canBeReplaced()) {
            return false;
        }

        BlockState soil = level.getBlockState(groundPos);
        if (!species.isAcceptableSoilForWorldgen(level, groundPos, soil)) {
            return false;
        }

        LevelContext levelContext = LevelContext.create(level);
        Holder<Biome> biome = getNoiseBiome(level, groundPos);
        RandomXOR xorRandom = new RandomXOR();
        xorRandom.setXOR(groundPos);

        BlockPos.MutableBlockPos rootPos = groundPos.mutable();
        DynamicTreeGenerationContext context = new DynamicTreeGenerationContext(
                levelContext,
                species,
                origin,
                rootPos,
                biome,
                CoordUtils.getRandomDir(xorRandom),
                WORLD_GEN_RADIUS,
                true);
        return species.generate(context);
    }

    private static BlockPos resolveGround(WorldGenLevel level, BlockPos origin, Species species) {
        BlockPos.MutableBlockPos cursor = origin.mutable();
        for (int i = 0; i <= GROUND_SEARCH_DEPTH; i++) {
            BlockState state = level.getBlockState(cursor);
            if (species.isAcceptableSoilForWorldgen(level, cursor, state)) {
                return cursor.immutable();
            }
            cursor.move(0, -1, 0);
        }

        int surfaceY = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());
        BlockPos surface = new BlockPos(origin.getX(), surfaceY, origin.getZ());
        if (species.isAcceptableSoilForWorldgen(level, surface, level.getBlockState(surface))) {
            return surface;
        }
        return null;
    }

    private static boolean passesSurfaceWaterDepth(WorldGenLevel level, BlockPos groundPos) {
        FluidState fluid = level.getFluidState(groundPos);
        if (!fluid.isEmpty()) {
            return false;
        }
        FluidState above = level.getFluidState(groundPos.above());
        return above.isEmpty();
    }

    private static Holder<Biome> getNoiseBiome(WorldGenLevel level, BlockPos pos) {
        Level world = level.getLevel();
        return world.getUncachedNoiseBiome(pos.getX() >> 2, pos.getY() >> 2, pos.getZ() >> 2);
    }
}
