package com.skcraft.dtsable.tree;

/**
 * Fallen-wood mass and displaced volume.
 * A green log displaces one cell and weighs 0.8 of that water, so {@code mass / volume = 0.8}.
 * The mass multiplier scales inertia and displacement together and leaves that ratio unchanged.
 */
public final class WoodBuoyancy {
    public static final double LOG_MASS = 0.8;
    public static final double LOG_VOLUME = 1.0;

    private WoodBuoyancy() {}

    /**
     * Fraction of one block cell this wood occupies.
     * Trunk shells and radius {@code >=} primary thickness fill the cell.
     * Thinner wood uses {@code (radius / primary)²}.
     */
    public static double occupancy(boolean trunkShell, int radius) {
        if (trunkShell) return 1.0;
        if (radius <= 0) return 0.0;
        double fraction = Math.min(1.0, radius / (double) WoodThickness.PRIMARY);
        return fraction * fraction;
    }

    public static double mass(double baseMass, double occupancy, double multiplier) {
        if (baseMass <= 0.0) return baseMass;
        return LOG_MASS * multiplier * occupancy;
    }

    /** A massless block keeps its original volume so an empty collider cannot become a balloon. */
    public static double volume(double baseMass, double originalVolume, double occupancy, double multiplier) {
        if (baseMass <= 0.0) return originalVolume;
        return LOG_VOLUME * multiplier * occupancy;
    }
}
