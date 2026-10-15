package com.skcraft.dtsable.compat;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.fruit.FruitBlock;
import com.dtteam.dynamictrees.block.pod.PodBlock;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import com.skcraft.dtsable.tree.WoodBudget;
import com.skcraft.dtsable.tree.WoodYield;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** DT destruction and grown-volume loot behind Create's native tree queue and drop callbacks. */
public final class CreateTreeHarvest {
    private record CollectedDrop(BlockPos pos, ItemStack stack) {}
    private record HarvestCollection(ServerLevel level, List<CollectedDrop> drops) {
        void add(BlockPos pos, ItemStack stack) {
            if (!stack.isEmpty()) drops.add(new CollectedDrop(pos.immutable(), stack.copy()));
        }
    }
    private static final ThreadLocal<HarvestCollection> COLLECTION = new ThreadLocal<>();

    private CreateTreeHarvest() {}

    /** Capture native addon drops, after their loot rules and modifiers have run. */
    public static void collectExtraDrops(EntityJoinLevelEvent event) {
        HarvestCollection collection = COLLECTION.get();
        if (collection == null || collection.level() != event.getLevel() || event.loadedFromDisk()) return;
        if (event.getEntity() instanceof ItemEntity item) {
            collection.add(item.blockPosition(), item.getItem());
            event.setCanceled(true);
        } else if (event.getEntity() instanceof FallingBlockEntity falling) {
            BlockState state = falling.getBlockState();
            // Some fruit/pod addons turn unsupported produce into a falling block
            // instead of dropping items. Harvest its native loot rather than let
            // the entity escape the machine and drop later, outside this scope.
            if (!(state.getBlock() instanceof FruitBlock || state.getBlock() instanceof PodBlock)) return;
            for (ItemStack stack : Block.getDrops(state, collection.level(), falling.blockPosition(), null)) {
                collection.add(falling.blockPosition(), stack);
            }
            event.setCanceled(true);
        }
    }

    public static void cut(ServerLevel level, BlockPos pos, ItemStack tool, Player player,
                           BiConsumer<BlockPos, ItemStack> drops) {
        HarvestCollection previous = COLLECTION.get();
        HarvestCollection collection = new HarvestCollection(level, new ArrayList<>());
        COLLECTION.set(collection);
        try {
            cutTree(level, pos, tool, player, collection::add);
        } finally {
            if (previous == null) COLLECTION.remove(); else COLLECTION.set(previous);
            // Create may spawn overflow here. Flush outside collection so those
            // items are not captured again, including fixed saws with no storage.
            for (CollectedDrop drop : collection.drops()) drops.accept(drop.pos(), drop.stack());
        }
    }

    private static void cutTree(ServerLevel level, BlockPos pos, ItemStack tool, Player player,
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
        // Replay effects from the recorded cut even when DT already removed the block.
        // Effects need the recorded state: looking up an already removed block yields air.
        DtIntegration.mutateWithoutNetworkDestroy(() -> {
            for (int i = 0; i < data.getNumBranches(); i++) {
                BlockPos branch = DtIntegration.branchPos(data, i);
                BlockState state = DtIntegration.branchState(data, i);
                if (state == null) continue;
                if (branch.equals(target.pos()) || level.random.nextBoolean()) breakEffect(level, branch, state);
            }
            for (int i = 0; i < DtIntegration.leafCount(data); i++) {
                BlockPos leaf = DtIntegration.leafPos(data, i);
                BlockState state = DtIntegration.leafState(data, i);
                if (state == null) continue;
                if (DtIntegration.isLeaves(level.getBlockState(leaf))) {
                    level.setBlock(leaf, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
                if (level.random.nextInt(8) == 0) breakEffect(level, leaf, state);
            }
            return true;
        });
        for (BranchBlock.ItemStackPos leafDrop : data.leavesDrops) {
            if (!leafDrop.stack.isEmpty()) drops.accept(data.cutPos.offset(leafDrop.pos), leafDrop.stack);
        }
    }

    private static void breakEffect(ServerLevel level, BlockPos pos, BlockState state) {
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
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
