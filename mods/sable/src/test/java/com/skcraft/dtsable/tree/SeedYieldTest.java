package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeedYieldTest {
    @Test
    void everyTreeWithLeavesOwesAtLeastOneSeed() {
        assertEquals(0, SeedYield.roll(0, () -> 0.0));
        assertTrue(SeedYield.roll(1, () -> 0.99) >= 1);
        assertTrue(SeedYield.roll(80, () -> 0.99) >= 1);
    }

    @Test
    void moreThanOneSeedIsCommon() {
        Random random = new Random(1);
        int trees = 0;
        int extra = 0;
        for (int i = 0; i < 4000; i++) {
            int owed = SeedYield.roll(40, random::nextDouble);
            trees++;
            if (owed >= 2) extra++;
        }
        assertTrue(extra > trees * 0.7, "owed two or more on " + extra + " of " + trees);
    }

    @Test
    void firstBrokenLeafPaysAnOwedSeed() {
        assertEquals(1, SeedYield.take(2, 115, 1, () -> 0.99));
        assertEquals(2, SeedYield.take(2, 115, 115, () -> 0.99));
    }

    @Test
    void clearingTheCanopyPaysEveryOwedSeed() {
        assertEquals(4, SeedYield.take(4, 10, 10, () -> 0.99));
        assertEquals(1, SeedYield.take(1, 1, 1, () -> 0.0));
    }

    @Test
    void breakingLeavesOneAtATimePaysTheSameQuota() {
        Random random = new Random(7);
        int pending = 5;
        int total = 20;
        int paid = 0;
        while (total > 0) {
            int dropped = SeedYield.take(pending, total, 1, random::nextDouble);
            paid += dropped;
            pending -= dropped;
            total--;
        }
        assertEquals(5, paid);
        assertEquals(0, pending);
    }
}
