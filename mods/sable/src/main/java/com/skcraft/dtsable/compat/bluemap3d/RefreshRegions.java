package com.skcraft.dtsable.compat.bluemap3d;

import java.util.HashSet;
import java.util.Set;

/** BlueMap 5.7 schedules Anvil regions, while the web refresh feed identifies hires tiles. */
public final class RefreshRegions {
    private RefreshRegions() {}
    public record Region(int x, int z) {}

    public static Set<Region> fromTile(int x, int z, int sizeX, int sizeZ, int offsetX, int offsetZ) {
        if (sizeX <= 0 || sizeZ <= 0) throw new IllegalArgumentException("Positive tile sizes required");
        long minX = (long) x * sizeX + offsetX;
        long minZ = (long) z * sizeZ + offsetZ;
        long maxX = minX + sizeX - 1;
        long maxZ = minZ + sizeZ - 1;
        Set<Region> regions = new HashSet<>();
        for (long rx = Math.floorDiv(minX, 512); rx <= Math.floorDiv(maxX, 512); rx++) {
            for (long rz = Math.floorDiv(minZ, 512); rz <= Math.floorDiv(maxZ, 512); rz++) {
                regions.add(new Region(Math.toIntExact(rx), Math.toIntExact(rz)));
            }
        }
        return regions;
    }
}
