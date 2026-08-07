package com.skcraft.dtrubber;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = DtRubber.MOD_ID, value = Dist.CLIENT)
public final class DtRubberClient {
    private DtRubberClient() {}

    @SubscribeEvent
    public static void onRegisterEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DtRubberEntities.RUBBER_BALL_PROJECTILE.get(), ThrownItemRenderer::new);
    }
}
