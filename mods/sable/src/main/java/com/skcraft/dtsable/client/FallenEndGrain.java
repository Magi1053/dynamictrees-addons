package com.skcraft.dtsable.client;

/** Set while a branch model is being meshed inside a fallen Sable plot. */
public final class FallenEndGrain {
    public static final ThreadLocal<Boolean> FALLEN = ThreadLocal.withInitial(() -> false);

    private FallenEndGrain() {}
}
