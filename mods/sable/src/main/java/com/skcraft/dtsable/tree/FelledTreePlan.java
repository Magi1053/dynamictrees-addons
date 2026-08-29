package com.skcraft.dtsable.tree;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.tree.species.Species;
import com.skcraft.dtsable.dt.DtIntegration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
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
        List<Node> branches,
        List<Node> leaves,
        List<ItemStack> cutDrops,
        int deferredLogs,
        Set<BlockPos> sourcePositions,
        int estimatedBlocks) {

    public record Node(BlockPos pos, BlockState state, int radius) {}

    public static FelledTreePlan create(ServerLevel level, BranchDestructionData data, List<ItemStack> suppliedDrops) {
        BlockPos cut = DtIntegration.cutPos(data);
        List<Node> branches = new ArrayList<>();
        List<Node> leaves = new ArrayList<>();
        Set<BlockPos> used = new HashSet<>();
        Set<BlockPos> source = new HashSet<>();
        used.add(cut);
        source.add(cut);
        int shells = 0;
        for (int i = 0; i < DtIntegration.branchCount(data); i++) {
            BlockPos pos = DtIntegration.branchPos(data, i);
            source.add(pos);
            if (pos.equals(cut) || !used.add(pos)) continue;
            BlockState state = DtIntegration.branchState(data, i);
            int radius = DtIntegration.branchRadius(data, i);
            if (state == null || state.isAir() || radius <= 0) continue;
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

        List<ItemStack> drops = suppliedDrops == null || suppliedDrops.isEmpty()
                ? DtIntegration.branchDrops(level, data) : suppliedDrops;
        List<ItemStack> cutDrops = new ArrayList<>();
        int logCount = 0;
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            if (stack.is(ItemTags.LOGS) || stack.is(DtIntegration.primitiveLog(data).asItem())) logCount += stack.getCount();
            else cutDrops.add(stack.copy());
        }
        cutDrops.addAll(DtIntegration.leavesDrops(data));
        cutDrops.add(new ItemStack(DtIntegration.primitiveLog(data)));
        return new FelledTreePlan(cut.immutable(), DtIntegration.species(data), List.copyOf(branches),
                List.copyOf(leaves), copy(cutDrops), Math.max(0, logCount - 1), Set.copyOf(source),
                branches.size() + leaves.size() + shells);
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
