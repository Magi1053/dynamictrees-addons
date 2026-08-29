package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TreeTopologyTest {
    private record Pos(int x, int y, int z) {
        List<Pos> neighbors() { return List.of(new Pos(x-1,y,z), new Pos(x+1,y,z), new Pos(x,y-1,z),
                new Pos(x,y+1,z), new Pos(x,y,z-1), new Pos(x,y,z+1)); }
    }

    @Test void splitsSixConnectedWoodAndKeepsLargestFirst() {
        Set<Pos> blocks = Set.of(new Pos(0,0,0), new Pos(0,1,0), new Pos(0,2,0),
                new Pos(8,0,0), new Pos(8,1,0));
        assertEquals(List.of(3, 2), TreeTopology.connectedComponents(blocks, Pos::neighbors)
                .stream().map(Set::size).toList());
    }

    @Test void floodStopsAtExcludedCore() {
        Pos start = new Pos(0,0,0);
        Set<Pos> thin = Set.of(start, new Pos(1,0,0), new Pos(3,0,0));
        assertEquals(Set.of(start, new Pos(1,0,0)), TreeTopology.flood(start, thin, Pos::neighbors));
    }

    @Test void centerUsesRadiusSquaredWeights() {
        Pos twig = new Pos(0,0,0), trunk = new Pos(10,0,0);
        TreeTopology.Center center = TreeTopology.weightedCenter(Set.of(twig, trunk),
                pos -> pos.equals(twig) ? 1.0 : 64.0, pos -> pos.x + .5, pos -> pos.y + .5, pos -> pos.z + .5);
        assertNotNull(center);
        assertEquals((.5 + 64.0 * 10.5) / 65.0, center.x(), 1.0e-9);
        assertEquals(.5, center.y(), 1.0e-9);
    }
}
