package com.skcraft.dtsable.compat.bluemap3d;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DynamicTreeMapGeometryTest {
    @Test void thinBranchesReachAdjacentBlockCentersWithoutGaps() {
        var boxes = BranchBoxes.create(2, new int[] {0, 0, 0, 0, 2, 2});
        assertEquals(3, boxes.size());
        assertEquals(6, boxes.get(0).minX());
        assertEquals(10, boxes.get(0).maxX());
        assertEquals(0, boxes.get(1).minX());
        assertEquals(boxes.get(0).minX(), boxes.get(1).maxX());
        assertEquals(boxes.get(0).maxX(), boxes.get(2).minX());
        assertEquals(16, boxes.get(2).maxX());
        assertEquals(0, boxes.get(2).axis());
    }

    @Test void connectionsUseNeighborThicknessAndDoNotGrowIntoAir() {
        var boxes = BranchBoxes.create(4, new int[] {0, 2, 0, 0, 0, 0});
        assertEquals(2, boxes.size());
        var arm = boxes.get(1);
        assertEquals(6, arm.minX());
        assertEquals(10, arm.maxX());
        assertEquals(12, arm.minY());
        assertEquals(16, arm.maxY());
        assertEquals(1, BranchBoxes.create(2, new int[6]).size());
    }

    @Test void fullAndThickTrunksRetainTheirDiameter() {
        assertEquals(1, BranchBoxes.create(8, new int[] {8, 8, 0, 0, 0, 0}).size());
        var thick = BranchBoxes.create(24, new int[] {24, 24, 0, 0, 0, 0}).getFirst();
        assertEquals(-16, thick.minX());
        assertEquals(32, thick.maxX());
        assertEquals(0, thick.minY());
        assertEquals(16, thick.maxY());
    }

    @Test void fallenTreeRefreshTargetsItsActualAnvilRegion() {
        assertEquals(java.util.Set.of(new RefreshRegions.Region(-1, 1)),
                RefreshRegions.fromTile(-2, 17, 32, 32, 0, 0));
        assertEquals(java.util.Set.of(new RefreshRegions.Region(0, 0)),
                RefreshRegions.fromTile(8, 8, 32, 32, 0, 0));
    }

    @Test void tilesStraddlingRegionBoundariesRefreshBothRegions() {
        assertEquals(java.util.Set.of(new RefreshRegions.Region(-1, 0), new RefreshRegions.Region(0, 0)),
                RefreshRegions.fromTile(0, 0, 32, 32, -16, 0));
    }
}
