package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WoodBuoyancyTest {
    @Test
    void fullTrunkAndShellsMatchLogDensity() {
        double trunk = WoodBuoyancy.occupancy(false, 8);
        double shell = WoodBuoyancy.occupancy(true, 4);
        assertEquals(1.0, trunk, 1.0e-9);
        assertEquals(1.0, shell, 1.0e-9);
        assertEquals(6.4, WoodBuoyancy.mass(1.0, trunk, 8.0), 1.0e-9);
        assertEquals(8.0, WoodBuoyancy.volume(1.0, 1.0, trunk, 8.0), 1.0e-9);
        assertEquals(1.25, WoodBuoyancy.volume(1.0, 1.0, shell, 8.0)
                / WoodBuoyancy.mass(1.0, shell, 8.0), 1.0e-9);
    }

    @Test
    void radiusOneIsOneSixtyFourthOfAFullTrunk() {
        double full = WoodBuoyancy.mass(1.0, WoodBuoyancy.occupancy(false, 8), 8.0);
        double twig = WoodBuoyancy.mass(1.0, WoodBuoyancy.occupancy(false, 1), 8.0);
        assertEquals(full / 64.0, twig, 1.0e-9);
        assertEquals(0.25, WoodBuoyancy.occupancy(false, 4), 1.0e-9);
    }

    @Test
    void radiusAbovePrimaryDoesNotExceedOneCell() {
        assertEquals(WoodBuoyancy.occupancy(false, 8), WoodBuoyancy.occupancy(false, 24), 1.0e-9);
    }

    @Test
    void multiplierScalesMassAndVolumeWithoutChangingDensity() {
        for (double multiplier : new double[] {1.0, 8.0, 16.0}) {
            double mass = WoodBuoyancy.mass(1.0, 1.0, multiplier);
            double volume = WoodBuoyancy.volume(1.0, 1.0, 1.0, multiplier);
            assertEquals(1.25, volume / mass, 1.0e-9);
        }
    }

    @Test
    void masslessWoodKeepsZeroMassAndOriginalVolume() {
        assertEquals(0.0, WoodBuoyancy.mass(0.0, 1.0, 8.0), 1.0e-9);
        assertEquals(1.0, WoodBuoyancy.volume(0.0, 1.0, 1.0, 8.0), 1.0e-9);
    }
}
