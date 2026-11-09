package com.skcraft.dtrubber;

import com.dtteam.dynamictrees.registry.NeoForgeRegistryHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

import java.util.function.Supplier;

@Mod(DtRubber.MOD_ID)
public class DtRubber {
    public static final String MOD_ID = "dtrubber";
    public static final String WOOD_NAME = MOD_ID + ":rubber";

    public static final BlockSetType RUBBER_SET = BlockSetType.register(new BlockSetType(WOOD_NAME));
    public static final WoodType RUBBER_WOOD_TYPE = WoodType.register(new WoodType(WOOD_NAME, RUBBER_SET));

    public DtRubber(IEventBus modEventBus) {
        NeoForgeRegistryHandler.setup(MOD_ID, modEventBus);
        DtRubberEntities.register(modEventBus);
        DtRubberBlocks.register(modEventBus);
        DtRubberItems.register();
        modEventBus.addListener(DtRubberCreative::buildCreativeTabContents);
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onAddSignBlocks);
        NeoForge.EVENT_BUS.register(RubberTapHandler.class);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(DtRubberBlocks::registerDispenserBehaviors);
    }

    private void onAddSignBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(
                BlockEntityType.SIGN,
                DtRubberBlocks.RUBBER_SIGN.get(),
                DtRubberBlocks.RUBBER_WALL_SIGN.get()
        );
        event.modify(
                BlockEntityType.HANGING_SIGN,
                DtRubberBlocks.RUBBER_HANGING_SIGN.get(),
                DtRubberBlocks.RUBBER_WALL_HANGING_SIGN.get()
        );
    }

    /** Referenced by META-INF/enumextensions.json for Boat.Type. */
    @SuppressWarnings("unused")
    public static Object getBoatTypeParameter(int idx, Class<?> type) {
        return switch (idx) {
            case 0 -> type.cast((Supplier<Block>) () -> DtRubberBlocks.RUBBER_PLANKS.get());
            case 1 -> type.cast(WOOD_NAME);
            case 2 -> type.cast((Supplier<Item>) () -> DtRubberBlocks.RUBBER_BOAT_ITEM.get());
            case 3 -> type.cast((Supplier<Item>) () -> DtRubberBlocks.RUBBER_CHEST_BOAT_ITEM.get());
            case 4 -> type.cast((Supplier<Item>) () -> Items.STICK);
            case 5 -> false;
            default -> throw new IllegalArgumentException("Unexpected boat type parameter index: " + idx);
        };
    }
}
