package com.skcraft.dtsable;

import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DtSable.MOD_ID)
public class DtSable {
    public static final String MOD_ID = "dtsable";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public DtSable(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, DtSableConfig.SPEC);
        NeoForge.EVENT_BUS.register(FallenTreeRegistry.class);
    }
}
