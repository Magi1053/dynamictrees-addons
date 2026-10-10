package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreePhysicsTest {
    @Test
    void waterDragBleedsEveryAxis() {
        double[] drag = WaterDrag.correction(1.0, -2.0, 3.0, 4.0, -5.0, 6.0);
        assertEquals(-0.08, drag[0], 1.0e-9);
        assertEquals(0.16, drag[1], 1.0e-9);
        assertEquals(-0.24, drag[2], 1.0e-9);
        assertEquals(-0.48, drag[3], 1.0e-9);
        assertEquals(0.60, drag[4], 1.0e-9);
        assertEquals(-0.72, drag[5], 1.0e-9);
    }
}
