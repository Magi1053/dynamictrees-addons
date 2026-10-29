package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WoodChopTest {
    @Test
    void sawHardnessMatchesPrimitiveLogAtOneBlockAndScalesWithStandingArea() {
        for (float logHardness : new float[]{2f, 3f, 5f}) {
            for (int radius : new int[]{4, 8, 12, 16, 24}) {
                float relative = WoodChop.relativeToPrimitiveLog(1f, radius, true);
                assertEquals(logHardness * radius * radius / 64f,
                        WoodChop.breakerHardness(logHardness, relative), 1.0e-4f);
            }
        }
    }

    @Test
    void configuredSawSpeedScalesTheWholeThicknessRange() {
        for (int radius : new int[]{4, 8, 12, 16, 24}) {
            float relative = WoodChop.relativeToPrimitiveLog(0.85f, radius, true);
            assertEquals(2f * radius * radius / 64f / 0.85f,
                    WoodChop.breakerHardness(2f, relative), 1.0e-4f);
        }
    }

    @Test
    void standingChopKeepsRadius8SpeedAndScalesWithArea() {
        assertEquals(0.85f, WoodChop.relativeToPrimitiveLog(0.85f, 8, true), 1.0e-4f);
        assertEquals(3.4f, WoodChop.relativeToPrimitiveLog(0.85f, 4, true), 1.0e-4f);
        assertEquals(0.2125f, WoodChop.relativeToPrimitiveLog(0.85f, 16, true), 1.0e-4f);
        assertEquals(0.85f / 9f, WoodChop.relativeToPrimitiveLog(0.85f, 24, true), 1.0e-4f);
        assertEquals(2.353f, WoodChop.breakerHardness(2f, 0.85f), 1.0e-3f);
    }

    @Test
    void fallenMiningStaysFasterThanVanillaUntilTheTrunkExceedsRadius8() {
        assertEquals(2f, WoodChop.relativeToPrimitiveLog(2f, 8, false), 1.0e-4f);
        assertEquals(2f, WoodChop.relativeToPrimitiveLog(2f, 3, false), 1.0e-4f);
        assertEquals(1f, WoodChop.relativeToPrimitiveLog(2f, 16, false), 1.0e-4f);
        assertEquals(2f / 3f, WoodChop.relativeToPrimitiveLog(2f, 24, false), 1.0e-4f);
    }
}
