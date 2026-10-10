package com.skcraft.dtsable.tree;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.tree.species.Species;
import com.skcraft.dtsable.dt.DtIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fully validated, immutable description of a fell before the world is mutated. */
public record FelledTreePlan(
        BlockPos cutPos,
        Species species,
        int baseRadius,
        boolean establishedTree,
        List<Node> branches,
        List<Node> leaves,
        List<ItemStack> cutDrops,
        Set<BlockPos> sourcePositions,
        int estimatedBlocks) {

    public record Node(BlockPos pos, BlockState state, int radius) {}

    public static FelledTreePlan create(ServerLevel level, BranchDestructionData data) {
        BlockPos cut = DtIntegration.cutPos(data);
        List<Node> branches = new ArrayList<>();
        List<Node> leaves = new ArrayList<>();
        Set<BlockPos> used = new HashSet<>();
        Set<BlockPos> source = new HashSet<>();
        BlockPos base = DtIntegration.basePos(data);
        BlockState baseState = base == null ? null : level.getBlockState(base);
        int baseRadius = baseState == null ? 0 : DtIntegration.yieldRadius(baseState, DtIntegration.radius(baseState));
        int fallbackRadius = 0;
        used.add(cut);
        source.add(cut);
        int shells = 0;
        BlockState cutBranch = null;
        for (int i = 0; i < DtIntegration.branchCount(data); i++) {
            BlockPos pos = DtIntegration.branchPos(data, i);
            source.add(pos);
            BlockState state = DtIntegration.branchState(data, i);
            int radius = DtIntegration.branchRadius(data, i);
            if (pos.equals(cut)) {
                if (state != null && !state.isAir()) cutBranch = state;
                continue;
            }
            if (!used.add(pos)) continue;
            if (state == null || state.isAir() || radius <= 0) continue;
            // A tree stripped while standing lost one radius per block; normalise to what it grew.
            int grownRadius = DtIntegration.yieldRadius(state, radius);
            fallbackRadius = Math.max(fallbackRadius, grownRadius);
            if (pos.equals(base)) baseRadius = grownRadius;
            branches.add(new Node(pos.immutable(), state, radius));
            if (radius > WoodThickness.PRIMARY) shells += 8;
        }
        for (int i = 0; i < DtIntegration.leafCount(data); i++) {
            BlockPos pos = DtIntegration.leafPos(data, i);
            source.add(pos);
            if (!used.add(pos)) continue;
            BlockState state = DtIntegration.leafState(data, i);
            if (state != null && !state.isAir()) leaves.add(new Node(pos.immutable(), state, 0));
        }

        List<ItemStack> cutDrops = List.of(new ItemStack(cutBranch == null
                ? DtIntegration.primitiveLog(data)
                : DtIntegration.primitiveLog(cutBranch, DtIntegration.species(data))));
        baseRadius = Math.max(baseRadius, fallbackRadius);
        boolean establishedTree = isEstablishedTree(baseRadius,
                Math.max(DtIntegration.branchCount(data), DtIntegration.trunkHeight(data)),
                DtIntegration.endpointCount(data));
        return new FelledTreePlan(cut.immutable(), DtIntegration.species(data), baseRadius, establishedTree,
                List.copyOf(branches),
                List.copyOf(leaves), copy(cutDrops), Set.copyOf(source),
                branches.size() + leaves.size() + shells);
    }

    /** Standing-tree classification persisted with the body. Loot does not read it. */
    public static boolean isEstablishedTree(int baseRadius, int woodBlocks, int endpoints) {
        return baseRadius >= WoodThickness.THRESHOLD && woodBlocks >= 5 && endpoints >= 2;
    }

    public boolean validate(int maxBlocks) {
        if (branches.isEmpty() || estimatedBlocks <= 0 || estimatedBlocks > maxBlocks) return false;
        Set<BlockPos> positions = new HashSet<>();
        for (Node node : branches) if (!positions.add(node.pos()) || node.radius() <= 0) return false;
        for (Node node : leaves) if (!positions.add(node.pos())) return false;
        return !positions.contains(cutPos);
    }

    private static List<ItemStack> copy(List<ItemStack> input) {
        return input.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }
}
