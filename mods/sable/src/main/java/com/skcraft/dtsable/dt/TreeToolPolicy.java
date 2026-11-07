package com.skcraft.dtsable.dt;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.dtteam.dynamictrees.tree.species.Species;
import com.dtteam.dynamictrees.utility.EntityUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Native player tool policy: discard wrong-tool cuts without loot or a falling body.
 * Thin twigs remain punchable for partial sticks and occasional seeds.
 */
public final class TreeToolPolicy {
    /** Dynamic Trees radius-2 twigs and thinner. */
    private static final int MAX_THIN_BRANCH_RADIUS = 2;
    /** NetVolumeNode: radius² × 64 raw units per branch block; 4096 raw = 1 log. */
    private static final float LOG_VOLUME_PER_THIN_PIECE = 64.0F / 4096.0F;
    private static final float PUNCH_BREAK_SPEED = 1.5F;
    /**
     * Per destroyed thin piece, chance to contribute a seed roll. Combined as
     * {@code 1 - (1 - p)^pieces} so longer tips are likelier but still rare.
     */
    private static final float SEED_DROP_CHANCE_PER_PIECE = 0.125F;

    private TreeToolPolicy() {}

    private static boolean shouldPunchTwig(BlockState state, Player player) {
        if (player == null || player.isCreative()) {
            return false;
        }

        return isThinBranch(state) && !isAxe(player.getMainHandItem());
    }

    /** Identify survival trunk breaks made without a usable axe. */
    private static boolean shouldDiscardTrunk(BlockState state, Player player) {
        return player != null && !player.isCreative() && TreeHelper.isBranch(state)
                && !isThinBranch(state) && !isAxe(player.getMainHandItem());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (shouldDiscardTrunk(event.getState(), event.getEntity())) {
            event.setNewSpeed(0.2F);
            return;
        }
        if (!shouldPunchTwig(event.getState(), event.getEntity())) {
            return;
        }

        event.setNewSpeed(PUNCH_BREAK_SPEED);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) {
            return;
        }

        if (discardWrongToolTree(event)) return;

        Player player = event.getPlayer();
        BlockState state = event.getState();
        if (!shouldPunchTwig(state, player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        BranchBlock branch = TreeHelper.getBranch(state);
        if (branch == null) {
            return;
        }

        BlockPos pos = event.getPos();
        Direction hitDir = EntityUtils.getHitDirection(player);
        BranchDestructionData data = branch.destroyBranchFromNode(level, pos, hitDir, false, player);

        level.levelEvent(2001, pos, Block.getId(state));
        if (level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            dropPunchLoot(level, pos, data);
        }

        event.setCanceled(true);
    }

    /** Discard only the normal DT cut without calling DT/Sable's falling-tree path. */
    private static boolean discardWrongToolTree(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !shouldDiscardTrunk(event.getState(), event.getPlayer())
                || !(event.getLevel() instanceof ServerLevel level)) return false;
        event.setCanceled(true);
        BranchBlock branch = TreeHelper.getBranch(event.getState());
        boolean drops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
        var previousMode = BranchBlock.destroyMode;
        try {
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false, level.getServer());
            // No player initiator: connected decorations must not invoke playerDestroy loot.
            // Use DT's root-direction calculation, just like a normal cut. Whole-tree
            // destruction would also traverse toward the roots and erase the standing tree.
            branch.destroyBranchFromNode(level, event.getPos(), EntityUtils.getHitDirection(event.getPlayer()), false, null);
            level.levelEvent(2001, event.getPos(), Block.getId(event.getState()));
        } finally {
            BranchBlock.destroyMode = previousMode;
            level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(drops, level.getServer());
        }
        return true;
    }

    private static void dropPunchLoot(ServerLevel level, BlockPos pos, BranchDestructionData data) {
        int pieces = countThinPieces(data);
        if (pieces <= 0) {
            return;
        }

        // DT leftover-log stick rate: sticks ≈ logVolume × 8. One thin piece → ~0.125 sticks.
        int sticks = rollCount(pieces * LOG_VOLUME_PER_THIN_PIECE * 8.0F, level.random);
        if (sticks > 0) {
            Block.popResource(level, pos, new ItemStack(Items.STICK, sticks));
        }

        Species species = data.species;
        if (species == null || species == Species.NULL_SPECIES || !species.hasSeed() || !species.shouldDropSeeds()) {
            return;
        }

        float seedChance = 1.0F - (float) Math.pow(1.0F - SEED_DROP_CHANCE_PER_PIECE, pieces);
        if (level.random.nextFloat() < seedChance) {
            Block.popResource(level, pos, species.getSeedStack(1));
        }
    }

    /** Whole items from an expected count, with a fractional roll for the remainder. */
    private static int rollCount(float expected, RandomSource random) {
        int count = (int) expected;
        if (random.nextFloat() < expected - count) {
            count++;
        }
        return count;
    }

    private static int countThinPieces(BranchDestructionData data) {
        int pieces = 0;
        int branches = data.getNumBranches();
        for (int i = 0; i < branches; i++) {
            if (data.getBranchRadius(i) <= MAX_THIN_BRANCH_RADIUS) {
                pieces++;
            }
        }
        return pieces;
    }

    private static boolean isThinBranch(BlockState state) {
        if (!TreeHelper.isBranch(state)) {
            return false;
        }

        BranchBlock branch = TreeHelper.getBranch(state);
        return branch != null && branch.getRadius(state) <= MAX_THIN_BRANCH_RADIUS;
    }

    private static boolean isAxe(ItemStack stack) {
        // The stack ability also honors mods that disable actions on broken tools.
        return stack.getItem() instanceof AxeItem
                && stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.AXE_DIG);
    }
}
