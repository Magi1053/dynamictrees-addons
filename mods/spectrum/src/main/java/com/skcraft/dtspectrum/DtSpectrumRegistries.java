package com.skcraft.dtspectrum;

import com.skcraft.dtspectrum.worldgen.ColoredTreePatchFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DtSpectrumRegistries {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, DtSpectrum.MOD_ID);

    public static final DeferredHolder<Feature<?>, ColoredTreePatchFeature> COLORED_TREE_PATCH = FEATURES.register(
            "colored_tree_patch", () -> new ColoredTreePatchFeature(NoneFeatureConfiguration.CODEC));

    private DtSpectrumRegistries() {}
}
