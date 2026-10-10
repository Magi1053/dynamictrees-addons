package com.skcraft.dtsable.tree;

/** Velocity corrections applied while a fallen trunk is in fluid. No world types, so tests can call it. */
public final class WaterDrag {
    public static final double LINEAR = 0.08;
    public static final double ANGULAR = 0.12;

    private WaterDrag() {}

    /** Bleeds this fraction of velocity on every axis, including upward. Does not zero it. */
    public static double[] correction(double lx, double ly, double lz, double ax, double ay, double az) {
        return new double[] {
                -lx * LINEAR, -ly * LINEAR, -lz * LINEAR,
                -ax * ANGULAR, -ay * ANGULAR, -az * ANGULAR
        };
    }
}
