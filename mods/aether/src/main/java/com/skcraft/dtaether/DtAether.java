package com.skcraft.dtaether;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import com.skcraft.dtaether.worldgen.AetherDtTreeConfig;
import com.skcraft.dtaether.worldgen.AetherDtTreeFeature;
import com.skcraft.dtaether.worldgen.CrystalIslandFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DtAether.MOD_ID)
public class DtAether {
    public static final String MOD_ID = "dtaether";

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);

    public DtAether(IEventBus modEventBus) {
        NeoForgeRegistryHandler.setup(MOD_ID, modEventBus);

        FEATURES.register("aether_dt_tree", () -> new AetherDtTreeFeature(AetherDtTreeConfig.CODEC));
        FEATURES.register("crystal_island", CrystalIslandFeature::new);
        FEATURES.register(modEventBus);
    }
}
