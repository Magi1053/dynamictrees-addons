package com.skcraft.dtaether;

import com.skcraft.dtaether.worldgen.AetherDtTreeConfig;
import com.skcraft.dtaether.worldgen.AetherDtTreeFeature;
import com.skcraft.dtaether.worldgen.CrystalIslandFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DtAetherRegistries {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, DtAether.MOD_ID);

    public static final DeferredHolder<Feature<?>, AetherDtTreeFeature> AETHER_DT_TREE = FEATURES.register(
            "aether_dt_tree", () -> new AetherDtTreeFeature(AetherDtTreeConfig.CODEC));

    public static final DeferredHolder<Feature<?>, CrystalIslandFeature> CRYSTAL_ISLAND = FEATURES.register(
            "crystal_island", CrystalIslandFeature::new);

    private DtAetherRegistries() {}
}
