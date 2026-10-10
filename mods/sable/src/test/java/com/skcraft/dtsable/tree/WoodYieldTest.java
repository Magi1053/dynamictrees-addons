package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WoodYieldTest {
    private static final int RADIUS_3 = WoodYield.volumeForRadius(3);

    @Test
    void oakBlocksPayDtsOwnAreaRule() {
        assertEquals(64, WoodYield.volumeForRadius(1));
        assertEquals(1_024, WoodYield.volumeForRadius(4)); // quarter log
        assertEquals(4_096, WoodYield.volumeForRadius(8));
        assertEquals(16_384, WoodYield.volumeForRadius(16));
        assertEquals(36_864, WoodYield.volumeForRadius(24));
        assertEquals(0, WoodYield.volumeForRadius(0));
        assertEquals(0, WoodYield.volumeForRadius(-1));
    }

    @Test
    void thinSpeciesNormaliseToTheirOwnBaseRadius() {
        // A grown birch base of 6: its thickest trunk block is one whole log.
        assertEquals(4_096, WoodYield.volumeForRadius(6, 6));
        assertEquals(1_024, WoodYield.volumeForRadius(3, 6)); // quarter log, same as an oak radius 4
        // Seven-tall birch trunk 6,5,5,4,4,3,3 pays almost four logs, not two.
        int[] trunk = {6, 5, 5, 4, 4, 3, 3};
        int total = 0;
        for (int radius : trunk) total += WoodYield.volumeForRadius(radius, 6);
        assertEquals(3, WoodYield.pay(total, 0).logs());
        assertEquals(6, WoodYield.pay(total, 0).sticks());
        // A birch canopy of twigs stays cheap: 14 radius-2 and 9 radius-1 blocks are under two logs.
        assertEquals(1, WoodYield.pay(14 * WoodYield.volumeForRadius(2, 6) + 9 * WoodYield.volumeForRadius(1, 6), 0).logs());
        // The same trunk measured by DT's radius-8 area rule is only two logs.
        int dtTotal = 0;
        for (int radius : trunk) dtTotal += radius * radius * 64;
        assertEquals(2, dtTotal / WoodYield.FULL_LOG_VOLUME);
    }

    @Test
    void baseRadiusIsClampedSoSaplingsAndMegaTrunksStayOnTheOakCurve() {
        // Young birch, base 2: clamps to 4, so a radius-2 block is a quarter log, not a full one.
        assertEquals(1_024, WoodYield.volumeForRadius(2, 2));
        // Mega spruce, base 24: clamps to 8, so radius 8 is still one log and 24 is nine.
        assertEquals(4_096, WoodYield.volumeForRadius(8, 24));
        assertEquals(36_864, WoodYield.volumeForRadius(24, 24));
    }

    @Test
    void volumeIsMonotoneInRadiusForEveryBase() {
        for (int base = 1; base <= 24; base++) {
            int previous = 0;
            for (int radius = 1; radius <= 24; radius++) {
                int volume = WoodYield.volumeForRadius(radius, base);
                if (volume < previous) throw new AssertionError("base " + base + " radius " + radius);
                previous = volume;
            }
        }
    }

    @Test
    void thickCoresPayMoreThanOneLogFromOneBlock() {
        assertEquals(1, WoodYield.pay(WoodYield.volumeForRadius(8), 0).logs());
        WoodYield.Payment mega = WoodYield.pay(WoodYield.volumeForRadius(16), 0);
        assertEquals(4, mega.logs());
        assertEquals(0, mega.sticks());
        assertEquals(9, WoodYield.pay(WoodYield.volumeForRadius(24), 0).logs());
    }

    @Test
    void shellsAddNoVolume() {
        int core = WoodYield.volumeForRadius(16);
        // Eight shells around the core contribute nothing: the body budget is the core alone.
        assertEquals(core, WoodYield.pay(core, 0).paidVolume());
    }

    @Test
    void branchSticksUseTheVanillaLeafChance() {
        assertEquals(0.02F, WoodYield.branchStickChance(0, 4), 0.0001F);
        assertEquals(0.1F, WoodYield.branchStickChance(4, 4), 0.0001F);
        assertEquals(0F, WoodYield.branchStickChance(0, 0), 0.0001F);
        assertEquals(0.04F, WoodYield.branchStickChance(0, 8), 0.0001F);
        assertTrue(WoodYield.branchDropsStick(0.019F, 0, 4));
        assertFalse(WoodYield.branchDropsStick(0.02F, 0, 4));
        assertFalse(WoodYield.branchDropsStick(0F, 0, 0));
    }

    @Test
    void fewerSticksPerLogMakesEachStickCostMoreRemainder() {
        assertEquals(512, WoodYield.stickCost(8));
        assertEquals(1_024, WoodYield.stickCost(4));
        assertEquals(Integer.MAX_VALUE, WoodYield.stickCost(0));
        // 4096 + 3072 of remainder: DT pays 6 sticks, four-per-log pays 3, zero pays none.
        WoodYield.Payment dt = WoodYield.pay(7_168, 0, WoodYield.stickCost(8));
        assertEquals(1, dt.logs());
        assertEquals(6, dt.sticks());
        WoodYield.Payment half = WoodYield.pay(7_168, 0, WoodYield.stickCost(4));
        assertEquals(1, half.logs());
        assertEquals(3, half.sticks());
        assertEquals(1 * 4_096 + 3 * 512, half.paidVolume(), "DT loot volume still counts a stick as 512");
        assertEquals(0, WoodYield.pay(7_168, 0, WoodYield.stickCost(0)).sticks());
    }

    @Test
    void singleTwigPaysNothingOnItsOwn() {
        WoodYield.Payment twig = WoodYield.pay(WoodYield.volumeForRadius(1), 0);
        assertEquals(0, twig.logs());
        assertEquals(0, twig.sticks()); // 64 volume is below one stick; it only counts in a sum
    }

    @Test
    void thinRunSumsToALogAndCarriesTheRemainder() {
        int total = RADIUS_3 * 8; // 8 × 576 = 4608 = one log plus one stick
        int unpaid = total;
        int logs = 0;
        int sticks = 0;
        for (int removed = 1; removed <= 8; removed++) {
            int remaining = total - RADIUS_3 * removed;
            WoodYield.Payment payment = WoodYield.pay(unpaid, remaining);
            logs += payment.logs();
            sticks += payment.sticks();
            unpaid = payment.remainingUnpaid();
            if (removed < 8) assertEquals(0, logs, "no log before enough wood has left the body");
        }
        assertEquals(1, logs);
        assertEquals(1, sticks);
        assertEquals(0, unpaid);
    }

    @Test
    void partialHarvestsMatchPayingAllAtOnce() {
        int[] radii = {8, 4, 4, 3, 3, 2, 1, 1};
        int total = 0;
        for (int radius : radii) total += WoodYield.volumeForRadius(radius);
        WoodYield.Payment whole = WoodYield.pay(total, 0);

        int unpaid = total;
        int remaining = total;
        int logs = 0;
        int sticks = 0;
        int[][] harvests = {{8, 4}, {4, 3, 3}, {2, 1, 1}};
        for (int[] harvest : harvests) {
            for (int radius : harvest) remaining -= WoodYield.volumeForRadius(radius);
            WoodYield.Payment payment = WoodYield.pay(unpaid, remaining);
            logs += payment.logs();
            sticks += payment.sticks();
            unpaid = payment.remainingUnpaid();
        }
        assertEquals(whole.logs(), logs);
        assertEquals(whole.sticks(), sticks);
        assertEquals(0, remaining);
    }

    @Test
    void sticksNeverEatAFutureLog() {
        // 4096 + 64: sub-stick wood leaves first. Nothing is paid and nothing is lost.
        WoodYield.Payment first = WoodYield.pay(4_160, 4_096);
        assertEquals(0, first.logs());
        assertEquals(0, first.sticks());
        assertEquals(4_160, first.remainingUnpaid());
        // 4096 + 1024: a quarter-log block leaves first. Only two sticks fit without losing the log.
        WoodYield.Payment second = WoodYield.pay(5_120, 4_096);
        assertEquals(0, second.logs());
        assertEquals(2, second.sticks());
        WoodYield.Payment last = WoodYield.pay(second.remainingUnpaid(), 0);
        assertEquals(1, last.logs());
        assertEquals(0, last.sticks());
    }

    @Test
    void scaledVolumeFollowsLootMultiplier() {
        assertEquals(4_096, WoodYield.scaledVolume(4_096, 1.0));
        assertEquals(8_192, WoodYield.scaledVolume(4_096, 2.0));
        assertEquals(0, WoodYield.scaledVolume(4_096, 0.0));
    }

    @Test
    void splitDividesUnpaidByMovedVolume() {
        assertEquals(1_024, WoodYield.splitShare(4_096, 256, 1_024));
        assertEquals(4_096, WoodYield.splitShare(4_096, 1_024, 1_024));
        assertEquals(0, WoodYield.splitShare(4_096, 0, 1_024));
    }
}
