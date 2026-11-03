package com.skcraft.dtsable.compat.bluemap3d;

import java.util.ArrayList;
import java.util.List;

/** Block-local cuboids in six-direction order: down, up, north, south, west, east. */
public final class BranchBoxes {
    private BranchBoxes() {}

    public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ, int axis) {}

    public static List<Box> create(int radius, int[] connections) {
        if (radius <= 0 || connections.length != 6) return List.of();
        int core = Math.min(radius, 8);
        int axis = 1;
        int strongest = -1;
        for (int side = 0; side < 6; side++) {
            if (connections[side] > strongest) {
                strongest = connections[side];
                axis = side < 2 ? 1 : side < 4 ? 2 : 0;
            }
        }
        float[] min = {8 - radius, 8 - radius, 8 - radius};
        float[] max = {8 + radius, 8 + radius, 8 + radius};
        min[axis] = 8 - core;
        max[axis] = 8 + core;
        List<Box> boxes = new ArrayList<>();
        boxes.add(box(min, max, axis));
        for (int side = 0; side < 6; side++) {
            int arm = Math.min(core, connections[side]);
            if (arm <= 0 || core >= 8) continue;
            int armAxis = side < 2 ? 1 : side < 4 ? 2 : 0;
            float[] from = {8 - arm, 8 - arm, 8 - arm};
            float[] to = {8 + arm, 8 + arm, 8 + arm};
            if ((side & 1) == 0) {
                from[armAxis] = 0;
                to[armAxis] = 8 - core;
            } else {
                from[armAxis] = 8 + core;
                to[armAxis] = 16;
            }
            boxes.add(box(from, to, armAxis));
        }
        return List.copyOf(boxes);
    }

    private static Box box(float[] min, float[] max, int axis) {
        return new Box(min[0], min[1], min[2], max[0], max[1], max[2], axis);
    }
}
