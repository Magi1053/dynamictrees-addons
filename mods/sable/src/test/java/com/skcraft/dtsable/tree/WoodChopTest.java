package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WoodChopTest {
    @Test
    void sawHardnessMatchesPrimitiveLogAtOneBlockAndScalesBeyondOneBlock() {
        for (float logHardness : new float[]{2f, 3f, 5f}) {
            for (int radius : new int[]{4, 8, 12, 16, 24}) {
                float relative = WoodChop.relativeToPrimitiveLog(1f, radius, true);
                assertEquals(logHardness * radius / 8f,
                        WoodChop.breakerHardness(logHardness, relative), 1.0e-4f);
            }
        }
    }

    @Test
    void configuredSawSpeedScalesTheWholeThicknessRange() {
        for (int radius : new int[]{4, 8, 12, 16, 24}) {
            float relative = WoodChop.relativeToPrimitiveLog(0.85f, radius, true);
            assertEquals(2f * radius / 8f / 0.85f,
                    WoodChop.breakerHardness(2f, relative), 1.0e-4f);
        }
    }

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
