package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WoodChopTest {
    @Test
    void standingChopIsNearVanillaAtRadius8AndScalesWithThickness() {
        assertEquals(0.85f, WoodChop.relativeToPrimitiveLog(0.85f, 8, true), 1.0e-4f);
        assertEquals(1.7f, WoodChop.relativeToPrimitiveLog(0.85f, 4, true), 1.0e-4f);
        assertEquals(0.425f, WoodChop.relativeToPrimitiveLog(0.85f, 16, true), 1.0e-4f);
        assertEquals(2.353f, WoodChop.breakerHardness(2f, 0.85f), 1.0e-3f);
    }

    @Test
    void fallenMiningStaysFasterThanVanillaUntilTheTrunkExceedsRadius8() {
        assertEquals(2f, WoodChop.relativeToPrimitiveLog(2f, 8, false), 1.0e-4f);
        assertEquals(2f, WoodChop.relativeToPrimitiveLog(2f, 3, false), 1.0e-4f);
        assertEquals(1f, WoodChop.relativeToPrimitiveLog(2f, 16, false), 1.0e-4f);
    }
}
