package com.skcraft.dtspectrum;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(DtSpectrum.MOD_ID)
public class DtSpectrum {
    public static final String MOD_ID = "dtspectrum";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DtSpectrum(IEventBus modEventBus) {
        NeoForgeRegistryHandler.setup(MOD_ID, modEventBus);
        DtSpectrumRegistries.FEATURES.register(modEventBus);
    }
}
