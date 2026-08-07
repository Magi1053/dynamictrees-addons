package com.skcraft.dtvinery;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(DtVinery.MOD_ID)
public class DtVinery {
    public static final String MOD_ID = "dtvinery";

    public DtVinery(IEventBus modEventBus) {
        NeoForgeRegistryHandler.setup(MOD_ID, modEventBus);
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(VineryTradePatcher::patchTrades);
    }
}
