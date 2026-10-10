package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CascadeWoodTest {
    private record Pos(int x, int y, int z) {
        List<Pos> neighbors() {
            return List.of(new Pos(x - 1, y, z), new Pos(x + 1, y, z), new Pos(x, y - 1, z),
                    new Pos(x, y + 1, z), new Pos(x, y, z - 1), new Pos(x, y, z + 1));
        }
    }

    @Test
    void lookingUpATrunkDropsTheBlocksLeftBehind() {
        Pos base = new Pos(0, 0, 0);
        Pos mid = new Pos(0, 1, 0);
        Pos tip = new Pos(0, 2, 0);
        Set<Pos> wood = Set.of(base, mid, tip);
        Map<Pos, Integer> radius = Map.of(base, 8, mid, 6, tip, 4);

        assertEquals(wood, TreeTopology.cascade(wood, base, radius::get, Pos::neighbors));
        assertEquals(Set.of(mid, tip), TreeTopology.cascade(wood, mid, radius::get, Pos::neighbors));
        assertEquals(Set.of(tip), TreeTopology.cascade(wood, tip, radius::get, Pos::neighbors));
    }

    @Test
    void aSplitPartRecalculatesFromItsOwnThickEnd() {
        Pos base = new Pos(0, 0, 0);
        Pos upper = new Pos(0, 1, 0);
        Set<Pos> part = Set.of(base, upper);
        Map<Pos, Integer> radius = Map.of(base, 8, upper, 5);

        assertEquals(part, TreeTopology.cascade(part, base, radius::get, Pos::neighbors));
        assertEquals(Set.of(upper), TreeTopology.cascade(part, upper, radius::get, Pos::neighbors));
    }

    @Test
    void aSideBranchCountsOnlyThatLimb() {
        Pos base = new Pos(0, 0, 0);
        Pos fork = new Pos(0, 1, 0);
        Pos tip = new Pos(0, 2, 0);
        Pos limb = new Pos(1, 1, 0);
        Set<Pos> wood = Set.of(base, fork, tip, limb);
        Map<Pos, Integer> radius = Map.of(base, 8, fork, 6, tip, 4, limb, 3);

        assertEquals(wood, TreeTopology.cascade(wood, base, radius::get, Pos::neighbors));
        assertEquals(Set.of(fork, tip, limb), TreeTopology.cascade(wood, fork, radius::get, Pos::neighbors));
        assertEquals(Set.of(limb), TreeTopology.cascade(wood, limb, radius::get, Pos::neighbors));
    }

    @Test
    void anExplicitRootBeatsTheThickEnd() {
        Pos soilSide = new Pos(0, 0, 0);
        Pos knot = new Pos(0, 1, 0);
        Pos tip = new Pos(0, 2, 0);
        Set<Pos> wood = Set.of(soilSide, knot, tip);

        assertEquals(wood, TreeTopology.awayFrom(wood, soilSide, soilSide, Pos::neighbors));
        assertEquals(Set.of(knot, tip), TreeTopology.awayFrom(wood, knot, soilSide, Pos::neighbors));
        assertEquals(Set.of(tip), TreeTopology.awayFrom(wood, tip, soilSide, Pos::neighbors));
    }
}
