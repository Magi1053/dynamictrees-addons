package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TreeAssemblyPartitionTest {
    private record Pos(int x, int y, int z) {
        List<Pos> neighbors() {
            return List.of(new Pos(x - 1, y, z), new Pos(x + 1, y, z), new Pos(x, y - 1, z),
                    new Pos(x, y + 1, z), new Pos(x, y, z - 1), new Pos(x, y, z + 1));
        }
    }

    @Test
    void cutJunctionProducesIndependentBodiesEvenWhenCanopyLeavesTouchBoth() {
        Pos leftBase = new Pos(-1, 1, 0);
        Pos rightBase = new Pos(1, 1, 0);
        Set<Pos> wood = Set.of(leftBase, new Pos(-1, 2, 0), rightBase, new Pos(1, 2, 0));
        Set<Pos> sharedCanopy = Set.of(new Pos(-1, 3, 0), new Pos(0, 3, 0), new Pos(1, 3, 0));

        List<TreeAssemblyPartition.Component<Pos>> result =
                TreeAssemblyPartition.partition(wood, sharedCanopy, Pos::neighbors);

        assertEquals(2, result.size());
        assertEquals(List.of(2, 2), result.stream().map(component -> component.wood().size()).toList());
        assertEquals(3, result.stream().mapToInt(component -> component.leaves().size()).sum());
    }
}
