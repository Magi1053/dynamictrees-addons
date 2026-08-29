package com.skcraft.dtsable.tree;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.dt.DtIntegration;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Single thickness classifier for fallen-body auto-break.
 * Collision and harvest cascade must both call these methods.
 *
 * {@code radius >= THRESHOLD} is a core: never snaps, never cascades.
 * {@code radius < THRESHOLD} is a limb: snaps on impact and cascades
 * when any connected auto-breakable piece is removed.
 */
public final class WoodThickness {
    /**
     * Auto-break / cascade cutoff. DT radius 8 is a full-block log; most of a
     * normal trunk tapers through 4–7 and must still count as a core.
     * Twigs are 1–3.
     */
    public static final int THRESHOLD = 4;
    /** DT primary thickness. Trunk shells exist only above this. */
    public static final int PRIMARY = 8;

    private WoodThickness() {
    }

    public static int radius(BlockState state) {
        return DtIntegration.radius(state);
    }

    public static boolean isRigid(BlockState state) {
        if (state.getBlock() instanceof TrunkShellBlock) {
            return true;
        }
        return state.getBlock() instanceof BranchBlock && radius(state) >= THRESHOLD;
    }

    public static boolean isThinWood(BlockState state) {
        if (state.getBlock() instanceof TrunkShellBlock) {
            return false;
        }
        return state.getBlock() instanceof BranchBlock && radius(state) < THRESHOLD;
    }

    public static boolean isAutoBreakable(BlockState state) {
        return state.is(BlockTags.LEAVES) || isThinWood(state);
    }

    public static double scaledCollisionThreshold(int radius, double thickestThinThreshold) {
        int thinRadius = Math.clamp(radius, 1, THRESHOLD - 1);
        return thickestThinThreshold * thinRadius / (THRESHOLD - 1.0);
    }
}
