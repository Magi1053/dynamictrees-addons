package com.skcraft.dtsable.tree;

/**
 * How fast Dynamic Trees wood breaks compared with its primitive log.
 * 1 matches that log. Standing chops scale linearly with radius, so a radius-8
 * trunk uses the configured speed and a thicker trunk takes longer.
 */
public final class WoodChop {
    private WoodChop() {}

    /**
     * @param configuredAtFullLog speed at radius 8, relative to the primitive log
     * @param standing            standing wood scales through thin radii; fallen wood
     *                            only slows down above radius 8
     */
    public static float relativeToPrimitiveLog(float configuredAtFullLog, int radius, boolean standing) {
        int r = Math.max(radius, 1);
        if (standing) return configuredAtFullLog * WoodThickness.PRIMARY / r;
        float thickness = Math.max(1f, r / (float) WoodThickness.PRIMARY);
        return configuredAtFullLog / thickness;
    }

    /** Destroy speed a saw or drill should see so its break time matches {@code relativeSpeed}. */
    public static float breakerHardness(float primitiveLogHardness, float relativeSpeed) {
        float log = primitiveLogHardness > 0f ? primitiveLogHardness : 2f;
        return log / Math.max(relativeSpeed, 0.05f);
    }
}
