package com.skcraft.dtrubber;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(DtRubber.MOD_ID)
public class DtRubber {
    public static final String MOD_ID = "dtrubber";

    public DtRubber(IEventBus modEventBus) {
        NeoForgeRegistryHandler.setup(MOD_ID, modEventBus);
        DtRubberEntities.register(modEventBus);
        DtRubberBlocks.register(modEventBus);
        DtRubberItems.register();
        DtRubberItems.ensureRegistered();
        modEventBus.addListener(DtRubberCreative::buildCreativeTabContents);
        NeoForge.EVENT_BUS.register(RubberTapHandler.class);
    }
}
