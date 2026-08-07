package com.skcraft.dtvinery;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = DtVinery.MOD_ID)
final class PrimitiveSaplingBlocker {
    private PrimitiveSaplingBlocker() {
    }

    @SubscribeEvent
    static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock());
        if (PrimitiveSaplingIds.isBlocked(id)) {
            event.setCanceled(true);
        }
    }
}
