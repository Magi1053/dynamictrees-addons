package com.skcraft.dtsable.tree;

import java.util.function.DoubleSupplier;

/**
 * Seeds a fallen tree owes, paid only when its leaves break or collide.
 * One seed is guaranteed. A second is common and a third is frequent, and a
 * large canopy can add a few more. The owed seeds are spread across the leaves.
 */
public final class SeedYield {
    private SeedYield() {}

    /** At least one, commonly two or more. No leaves means nothing is owed. */
    public static int roll(int leaves, DoubleSupplier random) {
        if (leaves <= 0) return 0;
        int count = 1;
        if (random.getAsDouble() < 0.80D) count++;
        if (random.getAsDouble() < 0.45D) count++;
        int canopy = Math.min(4, leaves / 25);
        for (int i = 0; i < canopy; i++) if (random.getAsDouble() < 0.40D) count++;
        return count;
    }

    /**
     * How many of {@code pending} owed seeds fall on this break.
     * {@code breaking} leaves out of {@code total} still on the tree, without replacement,
     * so clearing every leaf pays whatever is still owed.
     */
    public static int take(int pending, int total, int breaking, DoubleSupplier random) {
        if (pending <= 0 || total <= 0 || breaking <= 0) return 0;
        if (breaking >= total) return pending;
        int marked = pending;
        int pool = total;
        int dropped = 0;
        int draws = Math.min(breaking, pool);
        for (int i = 0; i < draws && marked > 0; i++) {
            if (random.getAsDouble() < (double) marked / pool) {
                dropped++;
                marked--;
            }
            pool--;
        }
        // A partial shatter must still pay. One owed seed comes out of the first leaves that break.
        return Math.max(dropped, 1);
    }
}
