package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WoodThicknessTest {
    @Test
    void collisionThresholdScalesAcrossThinRadii() {
        assertEquals(2.0 / 3.0, WoodThickness.scaledCollisionThreshold(1, 2.0), 1.0e-9);
        assertEquals(4.0 / 3.0, WoodThickness.scaledCollisionThreshold(2, 2.0), 1.0e-9);
        assertEquals(2.0, WoodThickness.scaledCollisionThreshold(3, 2.0), 1.0e-9);
    }
}
