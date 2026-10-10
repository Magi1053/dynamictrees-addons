package com.skcraft.dtsable.tree;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WoodBudgetTest {
    private static final int OAK = 0;
    private static final int STRIPPED_OAK = 1;

    @Test
    void strippingMovesTheBlocksDebtToTheStrippedBranch() {
        Map<Integer, Integer> unpaid = new HashMap<>(Map.of(OAK, 4_096 * 3));
        assertEquals(4_096, WoodBudget.transfer(unpaid, OAK, STRIPPED_OAK, 4_096));
        assertEquals(Map.of(OAK, 4_096 * 2, STRIPPED_OAK, 4_096), unpaid);
    }

    @Test
    void transferNeverMovesMoreThanIsOwedAndDropsEmptyKeys() {
        Map<Integer, Integer> unpaid = new HashMap<>(Map.of(OAK, 2_048));
        assertEquals(2_048, WoodBudget.transfer(unpaid, OAK, STRIPPED_OAK, 4_096));
        assertFalse(unpaid.containsKey(OAK));
        assertEquals(Map.of(STRIPPED_OAK, 2_048), unpaid);
    }

    @Test
    void transferIgnoresNoOps() {
        Map<Integer, Integer> unpaid = new HashMap<>(Map.of(OAK, 4_096));
        assertEquals(0, WoodBudget.transfer(unpaid, OAK, OAK, 4_096));
        assertEquals(0, WoodBudget.transfer(unpaid, -1, STRIPPED_OAK, 4_096));
        assertEquals(0, WoodBudget.transfer(unpaid, OAK, -1, 4_096));
        assertEquals(0, WoodBudget.transfer(unpaid, STRIPPED_OAK, OAK, 4_096));
        assertEquals(Map.of(OAK, 4_096), unpaid);
    }

    @Test
    void leftoverTwigVolumeFoldsOntoStrippedWhenEveryCoreIsStripped() {
        Map<Integer, Integer> unpaid = new HashMap<>(Map.of(OAK, 4_438, STRIPPED_OAK, 13_084));
        WoodBudget.foldUnstrippedIfAllCoresStripped(unpaid, STRIPPED_OAK);
        assertEquals(Map.of(STRIPPED_OAK, 17_522), unpaid);
    }

    @Test
    void aStrippedLogStillPaysOneStrippedLogWhenItLeaves() {
        // One radius-8 oak block: owed 4096 under oak. Stripping moves it under stripped oak.
        Map<Integer, Integer> unpaid = new HashMap<>(Map.of(OAK, 4_096));
        WoodBudget.transfer(unpaid, OAK, STRIPPED_OAK, 4_096);
        // DT shaved it to radius 7, but it is measured at its pre-strip radius, so nothing is
        // paid while it still stands and one whole stripped log is paid when it is removed.
        Map<Integer, Integer> standing = Map.of(STRIPPED_OAK, 4_096);
        Map<Integer, Integer> stillOwed = new HashMap<>();
        assertEquals(Map.of(), WoodBudget.pay(unpaid, standing, stillOwed));
        Map<Integer, Integer> paid = WoodBudget.pay(stillOwed, Map.of(), new HashMap<>());
        assertEquals(Map.of(STRIPPED_OAK, 4_096), paid);
    }
}
