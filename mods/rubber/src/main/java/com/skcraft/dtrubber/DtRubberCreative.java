package com.skcraft.dtrubber;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;

public final class DtRubberCreative {
    private DtRubberCreative() {}

    public static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            accept(
                    event,
                    DtRubberBlocks.RUBBER_LOG_ITEM,
                    DtRubberBlocks.RUBBER_WOOD_ITEM,
                    DtRubberBlocks.STRIPPED_RUBBER_LOG_ITEM,
                    DtRubberBlocks.STRIPPED_RUBBER_WOOD_ITEM,
                    DtRubberBlocks.RUBBER_PLANKS_ITEM,
                    DtRubberBlocks.RUBBER_STAIRS_ITEM,
                    DtRubberBlocks.RUBBER_SLAB_ITEM,
                    DtRubberBlocks.RUBBER_FENCE_ITEM,
                    DtRubberBlocks.RUBBER_FENCE_GATE_ITEM,
                    DtRubberBlocks.RUBBER_DOOR_ITEM,
                    DtRubberBlocks.RUBBER_TRAPDOOR_ITEM,
                    DtRubberBlocks.RUBBER_PRESSURE_PLATE_ITEM,
                    DtRubberBlocks.RUBBER_BUTTON_ITEM
            );
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            accept(event, DtRubberBlocks.RUBBER_SIGN_ITEM, DtRubberBlocks.RUBBER_HANGING_SIGN_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            accept(event, DtRubberBlocks.RUBBER_BOAT_ITEM, DtRubberBlocks.RUBBER_CHEST_BOAT_ITEM);
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            accept(event, DtRubberItems.RAW_LATEX, DtRubberItems.COAGULATED_LATEX, DtRubberItems.RUBBER_BALL);
            if (DtRubberItems.RUBBER_SHEET != null) {
                event.accept(DtRubberItems.RUBBER_SHEET);
            }
        }
    }

    @SafeVarargs
    private static void accept(BuildCreativeModeTabContentsEvent event, DeferredItem<?>... items) {
        for (DeferredItem<?> item : items) {
            event.accept(item);
        }
    }
}
