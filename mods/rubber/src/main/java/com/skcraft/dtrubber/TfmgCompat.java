package com.skcraft.dtrubber;

import net.neoforged.fml.ModList;

public final class TfmgCompat {
    public static final String MOD_ID = "tfmg";

    private TfmgCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    public static String rubberSheetId() {
        return isLoaded() ? MOD_ID + ":rubber_sheet" : DtRubber.MOD_ID + ":rubber_sheet";
    }
}
