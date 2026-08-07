package com.skcraft.dtspectrum;

import de.dafuqs.revelationary.ClientAdvancements;
import de.dafuqs.revelationary.ClientRevelationHolder;
import de.dafuqs.revelationary.RevelationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Re-applies revelation item/block cloaks after datapack reload.
 * Revelationary only calls {@link ClientRevelationHolder#cloakAll()} on login sync, not after /reload.
 */
@EventBusSubscriber(modid = DtSpectrum.MOD_ID, value = Dist.CLIENT)
public final class DtSpectrumClient {
    public static final PreparableReloadListener REVELATION_REFRESH_LISTENER = new PreparableReloadListener() {
        @Override
        public CompletableFuture<Void> reload(
                PreparationBarrier barrier,
                ResourceManager resourceManager,
                ProfilerFiller preparationsProfiler,
                ProfilerFiller reloadProfiler,
                Executor backgroundExecutor,
                Executor gameExecutor) {
            return barrier.wait(null).thenRunAsync(DtSpectrumClient::refreshCloaks, gameExecutor);
        }

        @Override
        public String getName() {
            return DtSpectrum.MOD_ID + "_revelation_refresh";
        }
    };

    private DtSpectrumClient() {}

    @SubscribeEvent
    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(REVELATION_REFRESH_LISTENER);
    }

    static void refreshCloaks() {
        if (Minecraft.getInstance().level == null) {
            return;
        }

        ClientRevelationHolder.cloakAll();

        Set<ResourceLocation> revealed = new HashSet<>();
        for (ResourceLocation advancement : RevelationRegistry.getItemEntries().keySet()) {
            if (ClientAdvancements.hasDone(advancement)) {
                revealed.add(advancement);
            }
        }
        for (ResourceLocation advancement : RevelationRegistry.getBlockStateEntries().keySet()) {
            if (ClientAdvancements.hasDone(advancement)) {
                revealed.add(advancement);
            }
        }

        if (!revealed.isEmpty()) {
            ClientRevelationHolder.processNewAdvancements(revealed, false);
        }
    }
}
