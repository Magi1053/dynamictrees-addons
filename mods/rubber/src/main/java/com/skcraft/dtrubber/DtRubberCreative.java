package com.skcraft.dtrubber;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

public final class DtRubberCreative {
    private DtRubberCreative() {}

    public static void buildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(DtRubberBlocks.RUBBER_PLANKS_ITEM);
            event.accept(DtRubberBlocks.STRIPPED_RUBBER_WOOD_ITEM);
            event.accept(DtRubberBlocks.STRIPPED_RUBBER_LOG_ITEM);
            event.accept(DtRubberBlocks.RUBBER_LOG_ITEM);
        }

        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(DtRubberItems.RAW_LATEX);
            event.accept(DtRubberItems.COAGULATED_LATEX);
            event.accept(DtRubberItems.RUBBER_BALL);
            if (DtRubberItems.RUBBER_SHEET != null) {
                event.accept(DtRubberItems.RUBBER_SHEET);
            }
        }
    }
}
