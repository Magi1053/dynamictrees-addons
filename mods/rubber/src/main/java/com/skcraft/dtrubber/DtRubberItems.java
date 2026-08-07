package com.skcraft.dtrubber;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

public final class DtRubberItems {
    public static final DeferredItem<Item> RAW_LATEX = DtRubberBlocks.ITEMS.register(
            "raw_latex",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> COAGULATED_LATEX = DtRubberBlocks.ITEMS.register(
            "coagulated_latex",
            () -> new Item(new Item.Properties())
    );

    public static final DeferredItem<Item> RUBBER_BALL = DtRubberBlocks.ITEMS.register(
            "rubber_ball",
            () -> new RubberBallItem(new Item.Properties())
    );

    @Nullable
    public static DeferredItem<Item> RUBBER_SHEET;

    private DtRubberItems() {}

    public static void register() {
        if (!TfmgCompat.isLoaded()) {
            RUBBER_SHEET = DtRubberBlocks.ITEMS.register(
                    "rubber_sheet",
                    () -> new Item(new Item.Properties())
            );
        }
    }

    /** Must run during mod construction so deferred registrars are populated before RegisterEvent. */
    public static void ensureRegistered() {
        RAW_LATEX.getId();
        COAGULATED_LATEX.getId();
        RUBBER_BALL.getId();
        if (RUBBER_SHEET != null) {
            RUBBER_SHEET.getId();
        }
    }
}
