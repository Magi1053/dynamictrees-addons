package com.skcraft.dtsable.tree;

/**
 * Pays fallen wood from the volume Dynamic Trees grew for it.
 *
 * A body carries one unpaid volume per branch-block type, normalised to the tree's base
 * radius (see {@link #volumeForRadius(int, int)}). Wood that leaves the body
 * becomes credit; whole logs are paid from credit as soon as it holds one, and sticks
 * are paid only from the part of the unpaid volume that can never complete another log.
 * Over the life of a body this pays exactly {@code floor(total / 4096)} logs and
 * {@code (total % 4096) / stickCost} sticks of its normalised volume.
 */
public final class WoodYield {
    public static final int FULL_LOG_VOLUME = 4_096;
    /** DT's own rate: its branch loot table turns every 512 of sub-log volume into one stick. */
    public static final int DT_STICKS_PER_LOG = 8;
    public static final int STICK_VOLUME = FULL_LOG_VOLUME / DT_STICKS_PER_LOG;
    /**
     * Vanilla leaf loot chances for 1–2 sticks, indexed by Fortune. A broken branch uses the
     * same table. Config value 4 is this rate; 0 never drops; 8 is twice as often.
     */
    private static final float[] VANILLA_STICK_CHANCES = {0.02F, 0.022222223F, 0.025F, 0.033333335F, 0.1F};

    private WoodYield() {}

    /**
     * Items owed now, and the volume still owed to the body afterwards. {@link #paidVolume()} is in
     * DT loot units, where a stick is always 512, whatever it cost the budget.
     */
    public record Payment(int logs, int sticks, int remainingUnpaid) {
        public int paidVolume() {
            return logs * FULL_LOG_VOLUME + sticks * STICK_VOLUME;
        }
    }

    /**
     * Budget one stick consumes. Fewer sticks per log means each stick eats more of the sub-log
     * remainder; zero means no sticks at all and the remainder is simply lost.
     */
    public static int stickCost(int sticksPerLog) {
        if (sticksPerLog <= 0) return Integer.MAX_VALUE;
        return FULL_LOG_VOLUME / Math.min(sticksPerLog, DT_STICKS_PER_LOG);
    }

    /**
     * Chance that one branch block drops sticks when it is destroyed or collides.
     * Matches vanilla leaf sticks at {@code sticksPerLog == 4}.
     */
    public static float branchStickChance(int fortune, int sticksPerLog) {
        if (sticksPerLog <= 0) return 0F;
        int step = Math.clamp(fortune, 0, VANILLA_STICK_CHANCES.length - 1);
        return Math.min(1F, VANILLA_STICK_CHANCES[step] * sticksPerLog / 4F);
    }

    /** {@code roll} is in {@code [0, 1)}. */
    public static boolean branchDropsStick(float roll, int fortune, int sticksPerLog) {
        return roll < branchStickChance(fortune, sticksPerLog);
    }

    /** Thinnest base radius that still counts as a grown trunk for normalisation. */
    public static final int MIN_UNIT_RADIUS = WoodThickness.THRESHOLD;

    /**
     * Volume of one grown block, normalised to the tree it came from.
     *
     * {@code unit} is the tree's base radius clamped to 4..8: the thickest block of any grown
     * species is one log. Every block pays by cross-section relative to that unit,
     * {@code (radius / unit)²}, which is DT's own area rule rescaled per tree. A thin species'
     * trunk pays about one log per block while its twigs still pay almost nothing; a mega core
     * above radius 8 pays several logs. Linear-in-radius was tried and paid half a birch's yield
     * from radius 1-3 canopy twigs.
     */
    public static int volumeForRadius(int radius, int baseRadius) {
        if (radius <= 0) return 0;
        int unit = Math.clamp(baseRadius, MIN_UNIT_RADIUS, WoodThickness.PRIMARY);
        return (int) Math.round(FULL_LOG_VOLUME * (double) (radius * radius) / (unit * unit));
    }

    /** Dynamic Trees' own volume for a radius-8 trunk: cross-section area only. */
    public static int volumeForRadius(int radius) {
        return volumeForRadius(radius, WoodThickness.PRIMARY);
    }

    /** Applies the species loot scale the same way DT does once per felled tree. */
    public static int scaledVolume(int rawVolume, double scale) {
        if (rawVolume <= 0 || scale <= 0.0) return 0;
        return (int) Math.round(rawVolume * scale);
    }

    /**
     * @param unpaid          volume still owed for this branch type before the break
     * @param remainingVolume scaled volume of this branch type still standing in the body after the break
     */
    public static Payment pay(int unpaid, int remainingVolume) {
        return pay(unpaid, remainingVolume, STICK_VOLUME);
    }

    /** @param stickCost budget volume one stick consumes, from {@link #stickCost(int)} */
    public static Payment pay(int unpaid, int remainingVolume, int stickCost) {
        int owed = Math.max(0, unpaid);
        int credit = Math.max(0, owed - Math.max(0, remainingVolume));
        int logs = credit / FULL_LOG_VOLUME;
        int afterLogs = owed - logs * FULL_LOG_VOLUME;
        int creditLeft = credit - logs * FULL_LOG_VOLUME;
        int sticks = stickCost > FULL_LOG_VOLUME ? 0
                : Math.min(creditLeft / stickCost, (afterLogs % FULL_LOG_VOLUME) / stickCost);
        return new Payment(logs, sticks, afterLogs - sticks * stickCost);
    }

    /** Volume that follows wood moving to a new body when one splits. */
    public static int splitShare(int unpaid, int movedVolume, int totalRemainingVolume) {
        if (unpaid <= 0 || movedVolume <= 0) return 0;
        if (movedVolume >= totalRemainingVolume) return unpaid;
        return (int) ((long) unpaid * movedVolume / totalRemainingVolume);
    }
}
