package com.skcraft.dtsable.tree;

import com.skcraft.dtsable.dt.DtIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Per branch-block-index scaled volume maps for a fallen body. */
public final class WoodBudget {
    private WoodBudget() {}

    /** Adds one grown branch to a budget, normalised to the tree's base radius. Shells and non-wood add nothing. */
    public static void add(Map<Integer, Integer> budget, BlockState state, int radius, int baseRadius, double scale) {
        int index = DtIntegration.branchIndex(state);
        if (index < 0) return;
        int volume = volumeOf(state, radius, baseRadius, scale);
        if (volume > 0) budget.merge(index, volume, Integer::sum);
    }

    /** Scaled, normalised volume one block pays for. Stripped blocks pay for their pre-strip radius. */
    public static int volumeOf(BlockState state, int radius, int baseRadius, double scale) {
        int yieldRadius = DtIntegration.yieldRadius(state, radius);
        return WoodYield.scaledVolume(WoodYield.volumeForRadius(yieldRadius, baseRadius), scale);
    }

    /** Scaled, normalised volume of the wood currently at these plot positions. */
    public static Map<Integer, Integer> measure(ServerLevel level, Set<BlockPos> wood, int baseRadius, double scale) {
        Map<Integer, Integer> result = new HashMap<>();
        for (BlockPos pos : wood) {
            BlockState state = level.getBlockState(pos);
            add(result, state, WoodThickness.radius(state), baseRadius, scale);
        }
        return result;
    }

    /** Pays every branch type. Returns volume to drop per index and writes what is still owed to {@code unpaidOut}. */
    public static Map<Integer, Integer> pay(Map<Integer, Integer> unpaid, Map<Integer, Integer> remaining,
                                            Map<Integer, Integer> unpaidOut) {
        return pay(unpaid, remaining, unpaidOut, WoodYield.STICK_VOLUME);
    }

    /** @param stickCost budget volume one stick consumes, see {@link WoodYield#stickCost(int)} */
    public static Map<Integer, Integer> pay(Map<Integer, Integer> unpaid, Map<Integer, Integer> remaining,
                                            Map<Integer, Integer> unpaidOut, int stickCost) {
        Map<Integer, Integer> paid = new HashMap<>();
        Set<Integer> indices = new HashSet<>(unpaid.keySet());
        indices.addAll(remaining.keySet());
        for (int index : indices) {
            WoodYield.Payment payment = WoodYield.pay(unpaid.getOrDefault(index, 0),
                    remaining.getOrDefault(index, 0), stickCost);
            if (payment.paidVolume() > 0) paid.put(index, payment.paidVolume());
            if (payment.remainingUnpaid() > 0) unpaidOut.put(index, payment.remainingUnpaid());
        }
        return paid;
    }

    /**
     * Moves up to {@code volume} owed on branch type {@code from} to {@code to}, in place. A stripped
     * block keeps its whole debt under the stripped branch so it pays stripped logs. Returns the
     * volume moved.
     */
    public static int transfer(Map<Integer, Integer> unpaid, int from, int to, int volume) {
        if (from < 0 || to < 0 || from == to || volume <= 0) return 0;
        int owed = unpaid.getOrDefault(from, 0);
        int moved = Math.min(owed, volume);
        if (moved <= 0) return 0;
        if (owed - moved > 0) unpaid.put(from, owed - moved); else unpaid.remove(from);
        unpaid.merge(to, moved, Integer::sum);
        return moved;
    }

    /**
     * When every core has been stripped, leftover twig volume on the unstripped branch type
     * would still pay a normal log. Move it onto the stripped type so the tree pays stripped.
     */
    public static void foldUnstrippedIfAllCoresStripped(Map<Integer, Integer> unpaid, int strippedIndex) {
        if (strippedIndex < 0 || unpaid.isEmpty()) return;
        for (int index : new HashSet<>(unpaid.keySet())) {
            if (index == strippedIndex) continue;
            transfer(unpaid, index, strippedIndex, unpaid.getOrDefault(index, 0));
        }
    }

    /** Volume that leaves with wood moving to a split body, removed from {@code unpaid} in place. */
    public static Map<Integer, Integer> takeSplitShare(Map<Integer, Integer> unpaid, Map<Integer, Integer> moved,
                                                       Map<Integer, Integer> totalRemaining) {
        Map<Integer, Integer> share = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : moved.entrySet()) {
            int index = entry.getKey();
            int taken = WoodYield.splitShare(unpaid.getOrDefault(index, 0), entry.getValue(),
                    totalRemaining.getOrDefault(index, 0));
            if (taken <= 0) continue;
            share.put(index, taken);
            int left = unpaid.getOrDefault(index, 0) - taken;
            if (left > 0) unpaid.put(index, left); else unpaid.remove(index);
        }
        return share;
    }
}
