package com.skcraft.dtrubber;

import net.minecraft.world.item.Item;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

public final class DtRubberItems {
    public static final DeferredItem<Item> RAW_LATEX = DtRubberBlocks.ITEMS.registerSimpleItem("raw_latex");
    public static final DeferredItem<Item> COAGULATED_LATEX = DtRubberBlocks.ITEMS.registerSimpleItem("coagulated_latex");
    public static final DeferredItem<Item> RUBBER_BALL = DtRubberBlocks.ITEMS.register(
            "rubber_ball",
            () -> new RubberBallItem(new Item.Properties())
    );

    /** Present only when TFMG is absent (TFMG ships its own sheet). */
    @Nullable
    public static DeferredItem<Item> RUBBER_SHEET;

    private DtRubberItems() {}

    public static void register() {
        if (!ModList.get().isLoaded("tfmg")) {
            RUBBER_SHEET = DtRubberBlocks.ITEMS.registerSimpleItem("rubber_sheet");
        }
    }
}
