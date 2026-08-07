package com.skcraft.dtmeadow;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(DtMeadow.MOD_ID)
public class DtMeadow {
    public static final String MOD_ID = "dtmeadow";

    public DtMeadow(IEventBus modEventBus) {
        NeoForgeRegistryHandler.setup(MOD_ID, modEventBus);
    }
}
