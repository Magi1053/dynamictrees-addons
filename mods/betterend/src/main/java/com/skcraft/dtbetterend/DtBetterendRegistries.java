package com.skcraft.dtbetterend;

import com.dtteam.dynamictrees.api.registry.Registry;
import com.dtteam.dynamictrees.api.worldgen.FeatureCanceller;
import com.dtteam.dynamictrees.block.leaves.LeavesProperties;
import com.dtteam.dynamictrees.event.ApplierRegistryEvent;
import com.dtteam.dynamictrees.event.RegistryEvent;
import com.dtteam.dynamictrees.event.TypeRegistryEvent;
import com.dtteam.dynamictrees.systems.genfeature.GenFeature;
import com.dtteam.dynamictrees.systems.growthlogic.GrowthLogicKit;
import com.google.gson.JsonElement;
import com.skcraft.dtbetterend.systems.BetterEndTreeCanceller;
import com.skcraft.dtbetterend.systems.genfeature.FlowerVinesGenFeature;
import com.skcraft.dtbetterend.systems.growthlogic.DragonTreeLogic;
import com.skcraft.dtbetterend.systems.growthlogic.LacugroveTreeLogic;
import com.skcraft.dtbetterend.systems.growthlogic.LucerniaTreeLogic;
import com.skcraft.dtbetterend.systems.growthlogic.PythadendronTreeLogic;
import com.skcraft.dtbetterend.systems.growthlogic.TenaneaTreeLogic;
import com.skcraft.dtbetterend.systems.leaves.FurOuterLeaveProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;

public final class DtBetterendRegistries {
    private DtBetterendRegistries() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.register(DtBetterendRegistries.class);
    }

    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(DtBetterend.MOD_ID, path);
    }

    @SubscribeEvent
    public static void onGrowthLogicRegistry(RegistryEvent<GrowthLogicKit> event) {
        if (!event.isEntryOfType(GrowthLogicKit.class)) {
            return;
        }
        Registry<GrowthLogicKit> registry = event.getRegistry();
        registry.registerAll(
                new DragonTreeLogic(location("dragon_tree")),
                new LacugroveTreeLogic(location("lacugrove")),
                new LucerniaTreeLogic(location("lucernia")),
                new PythadendronTreeLogic(location("pythadendron")),
                new TenaneaTreeLogic(location("tenanea")));
    }

    @SubscribeEvent
    public static void onGenFeatureRegistry(RegistryEvent<GenFeature> event) {
        if (!event.isEntryOfType(GenFeature.class)) {
            return;
        }
        event.getRegistry().register(new FlowerVinesGenFeature(location("flower_vines")));
    }

    @SubscribeEvent
    public static void onFeatureCancellerRegistry(RegistryEvent<FeatureCanceller> event) {
        if (!event.isEntryOfType(FeatureCanceller.class)) {
            return;
        }
        event.getRegistry().register(new BetterEndTreeCanceller(location("betterend_tree")));
    }

    @SubscribeEvent
    public static void onLeavesTypeRegistry(TypeRegistryEvent<LeavesProperties> event) {
        if (!event.isEntryOfType(LeavesProperties.class)) {
            return;
        }
        event.registerType(location("fur"), FurOuterLeaveProperties.TYPE);
    }

    @SubscribeEvent
    public static void onLeavesAppliers(ApplierRegistryEvent.Reload<LeavesProperties, JsonElement> event) {
        if (!"leaves_properties".equals(event.getIdentifier())) {
            return;
        }
        event.getAppliers()
                .register(
                        "outer_block",
                        FurOuterLeaveProperties.class,
                        Block.class,
                        FurOuterLeaveProperties::setOuterBlock);
    }
}
