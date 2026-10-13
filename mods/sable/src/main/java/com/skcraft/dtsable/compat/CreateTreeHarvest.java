package com.skcraft.dtsable.compat;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import com.skcraft.dtsable.tree.WoodBudget;
import com.skcraft.dtsable.tree.WoodYield;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** DT destruction and grown-volume loot behind Create's native tree queue and drop callbacks. */
public final class CreateTreeHarvest {
    private CreateTreeHarvest() {}

    public static void cut(ServerLevel level, BlockPos pos, ItemStack tool, Player player,
                           BiConsumer<BlockPos, ItemStack> drops) {
        if (FallenTreeRegistry.find(level, pos).isPresent()) {
            TreeHarvest.breakManually(level, pos, player, drops);
            return;
        }
        SawCut.WoodTarget target = SawCut.woodTarget(level, pos, level.getBlockState(pos));
        if (target == null) return;
        BranchDestructionData data = target.branch().destroyBranchFromNode(level, target.pos(),
                Direction.DOWN, false, player == null ? SawCut.cutter(level, pos, target.pos()) : player);
        if (data == null || data.species == null || data.getNumBranches() == 0) return;
        dropWood(level, data, tool, drops);
        // DT records leaf positions and loot, but Create's queue must finish removing
        // the foliage. This never calls dropTree or assembles a Sable body.
        DtIntegration.mutateWithoutNetworkDestroy(() -> {
            for (int i = 0; i < DtIntegration.leafCount(data); i++) {
                BlockPos leaf = DtIntegration.leafPos(data, i);
                BlockState state = level.getBlockState(leaf);
                if (!DtIntegration.isLeaves(state)) continue;
                level.setBlock(leaf, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, leaf, Block.getId(state));
            }
            return true;
        });
        for (BranchBlock.ItemStackPos leafDrop : data.leavesDrops) {
            if (!leafDrop.stack.isEmpty()) drops.accept(data.cutPos.offset(leafDrop.pos), leafDrop.stack);
        }
    }

    private static void dropWood(ServerLevel level, BranchDestructionData data, ItemStack tool,
                                  BiConsumer<BlockPos, ItemStack> drops) {
        BlockState base = data.basePos == null ? null : level.getBlockState(data.basePos);
        int radius = base == null || base.isAir() ? 0
                : DtIntegration.yieldRadius(base, DtIntegration.radius(base));
        for (int i = 0; i < data.getNumBranches(); i++) {
            BlockState state = DtIntegration.branchState(data, i);
            if (state != null) radius = Math.max(radius,
                    DtIntegration.yieldRadius(state, DtIntegration.branchRadius(data, i)));
        }
        Map<Integer, Integer> budget = new HashMap<>();
        double scale = DtIntegration.lootVolumeScale(data.species);
        for (int i = 0; i < data.getNumBranches(); i++) {
            BlockState state = DtIntegration.branchState(data, i);
            if (state != null) WoodBudget.add(budget, state, DtIntegration.branchRadius(data, i), radius, scale);
        }
        budget.forEach((index, volume) -> {
            int paid = WoodYield.pay(volume, 0).paidVolume();
            for (ItemStack stack : DtIntegration.branchDrops(level, data.species, index, paid, tool)) {
                if (!stack.isEmpty()) drops.accept(data.cutPos, stack);
            }
        });
    }
}
