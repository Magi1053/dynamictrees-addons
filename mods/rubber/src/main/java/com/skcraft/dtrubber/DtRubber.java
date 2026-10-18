package com.skcraft.dtrubber;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(DtRubber.MOD_ID)
public class DtRubber {
    public static final String MOD_ID = "dtrubber";
    public static final WoodType RUBBER_WOOD_TYPE = WoodType.register(
            new WoodType(ResourceLocation.fromNamespaceAndPath(MOD_ID, "rubber").toString(), BlockSetType.OAK)
    );

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
