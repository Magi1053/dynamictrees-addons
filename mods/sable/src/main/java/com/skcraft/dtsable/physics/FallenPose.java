package com.skcraft.dtsable.physics;

/** Set while the player pose fit test runs, so fallen wood is not a ceiling. */
public final class FallenPose {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private FallenPose() {}

    public static void enter() { DEPTH.set(DEPTH.get() + 1); }

    public static void exit() { DEPTH.set(Math.max(0, DEPTH.get() - 1)); }

    public static boolean ignoring() { return DEPTH.get() > 0; }
}
