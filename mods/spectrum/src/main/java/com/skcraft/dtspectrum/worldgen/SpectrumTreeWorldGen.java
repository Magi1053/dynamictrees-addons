package com.skcraft.dtspectrum.worldgen;

import com.dtteam.dynamictrees.api.worldgen.LevelContext;
import com.dtteam.dynamictrees.api.worldgen.RandomXOR;
import com.dtteam.dynamictrees.tree.species.Species;
import com.dtteam.dynamictrees.utility.CoordUtils;
import com.dtteam.dynamictrees.worldgen.DynamicTreeGenerationContext;
import com.skcraft.dtspectrum.DtSpectrum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

/** Spectrum {@code colored_tree_patch} CMY weights and DT species placement. */
public final class SpectrumTreeWorldGen {
    public static final int PATCH_TRIES = 4;
    public static final int PATCH_XZ_SPREAD = 6;
    public static final int PATCH_Y_SPREAD = 3;
    public static final int WORLD_GEN_RADIUS = 1;
    public static final int GROUND_SEARCH_DEPTH = 4;

    private static final BlockState SURVIVE_PROBE = saplingSurvivalProbe();

    private static final SimpleWeightedRandomList<String> WEIGHTED_COLORS = buildWeightedColors();

    private static boolean loggedFirstSuccess;

    private SpectrumTreeWorldGen() {}

    private static BlockState saplingSurvivalProbe() {
        Block block = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("spectrum", "red_sapling"));
        if (block == Blocks.AIR) {
            return Blocks.GRASS_BLOCK.defaultBlockState();
        }
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.STAGE)) {
            state = state.setValue(BlockStateProperties.STAGE, 0);
        }
        return state;
    }

    private static SimpleWeightedRandomList<String> buildWeightedColors() {
        SimpleWeightedRandomList.Builder<String> builder = SimpleWeightedRandomList.builder();
        builder.add("blue", 1);
        builder.add("cyan", 3);
        builder.add("green", 1);
        builder.add("light_blue", 1);
        builder.add("lime", 1);
        builder.add("magenta", 3);
        builder.add("orange", 1);
        builder.add("pink", 1);
        builder.add("purple", 1);
        builder.add("red", 1);
        builder.add("yellow", 3);
        return builder.build();
    }

    public static boolean tryPlacePatch(WorldGenLevel level, BlockPos origin, RandomSource random) {
        boolean placed = false;
        for (int attempt = 0; attempt < PATCH_TRIES; attempt++) {
            BlockPos candidate = offsetPatch(random, origin);
            if (tryPlaceTree(level, candidate, random)) {
                placed = true;
            }
        }
        return placed;
    }

    private static BlockPos offsetPatch(RandomSource random, BlockPos origin) {
        return origin.offset(
                random.nextInt(PATCH_XZ_SPREAD + 1) - random.nextInt(PATCH_XZ_SPREAD + 1),
                random.nextInt(PATCH_Y_SPREAD + 1) - random.nextInt(PATCH_Y_SPREAD + 1),
                random.nextInt(PATCH_XZ_SPREAD + 1) - random.nextInt(PATCH_XZ_SPREAD + 1));
    }

    private static boolean tryPlaceTree(WorldGenLevel level, BlockPos origin, RandomSource random) {
        String color = WEIGHTED_COLORS.getRandomValue(random).orElse("red");
        Species species = Species.REGISTRY.get(
                ResourceLocation.fromNamespaceAndPath(DtSpectrum.MOD_ID, color));
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
        if (!SURVIVE_PROBE.canSurvive(level, plantPos)) {
            return false;
        }

        BlockState soil = level.getBlockState(groundPos);
        if (!species.isAcceptableSoilForWorldgen(level, groundPos, soil)) {
            return false;
        }

        species.setAllowedWaterHeightForWorldgen(0);

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

        if (!species.generate(context)) {
            return false;
        }

        if (!loggedFirstSuccess) {
            loggedFirstSuccess = true;
            DtSpectrum.LOGGER.info("Placed first Spectrum DT colored tree ({}) at {}", color, groundPos);
        }
        return true;
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
