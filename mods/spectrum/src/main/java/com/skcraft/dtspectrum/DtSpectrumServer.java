package com.skcraft.dtspectrum;

import de.dafuqs.revelationary.RevelationaryNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/** Pushes updated revelation registries to clients after datapack reload. */
@EventBusSubscriber(modid = DtSpectrum.MOD_ID)
public final class DtSpectrumServer {
    private DtSpectrumServer() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            RevelationaryNetworking.sendRevelations(player);
        }
    }
}
