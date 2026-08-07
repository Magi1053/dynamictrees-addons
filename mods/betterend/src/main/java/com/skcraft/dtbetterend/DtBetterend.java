package com.skcraft.dtbetterend;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(DtBetterend.MOD_ID)
public class DtBetterend {
    public static final String MOD_ID = "dtbetterend";

    public DtBetterend(IEventBus modEventBus) {
        NeoForgeRegistryHandler.setup(MOD_ID, modEventBus);
        DtBetterendRegistries.register(modEventBus);
    }
}
