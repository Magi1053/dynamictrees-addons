package com.skcraft.dtaether;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(DtAether.MOD_ID)
public class DtAether {
    public static final String MOD_ID = "dtaether";

    public DtAether(IEventBus modEventBus) {
        NeoForgeRegistryHandler.setup(MOD_ID, modEventBus);
        DtAetherRegistries.FEATURES.register(modEventBus);
    }
}
