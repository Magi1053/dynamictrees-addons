package com.skcraft.dtrubber;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.dtteam.dynamictrees.tree.family.Family;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class RubberTapHandler {
    private static final ResourceLocation RUBBER_BRANCH_ID = ResourceLocation.fromNamespaceAndPath(DtRubber.MOD_ID, "rubber_branch");
    private static final int MIN_STRIP_RADIUS = 5;

    private RubberTapHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (!stack.canPerformAction(ItemAbilities.AXE_STRIP)) {
            return;
        }

        Player player = event.getEntity();
        if (!isRealPlayer(player)) {
            return;
        }

        BlockPos pos = event.getPos();
        ServerPlayer serverPlayer = (ServerPlayer) player;

        int radius = tryStripRubberBranch(level, pos, player, stack);
        if (radius >= 0) {
            if (radius >= MIN_STRIP_RADIUS) {
                dropRawLatex((ServerLevel) level, pos);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (tryStripRubberLog(level, pos, serverPlayer, stack)) {
            dropRawLatex((ServerLevel) level, pos);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean isRealPlayer(Player player) {
        return player instanceof ServerPlayer serverPlayer
                && !(player instanceof FakePlayer)
                && !serverPlayer.isSpectator();
    }

    private static void dropRawLatex(ServerLevel level, BlockPos pos) {
        int count = level.getRandom().nextInt(3);
        if (count <= 0) {
            return;
        }

        Block.popResource(level, pos, new ItemStack(DtRubberItems.RAW_LATEX.get(), count));
    }

    private static void damageAxe(ItemStack stack, ServerPlayer player) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return;
        }

        EquipmentSlot slot = player.getMainHandItem() == stack ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        stack.hurtAndBreak(1, player, slot);
    }

    private static boolean forceStripBranch(
            Level level,
            BlockPos pos,
            BlockState state,
            ServerPlayer player,
            ItemStack stack,
            BranchBlock branch,
            Family family) {
        var strippedOpt = family.getStrippedBranch();
        if (strippedOpt.isEmpty()) {
            return false;
        }

        BranchBlock strippedBranch = strippedOpt.get();
        int radius = branch.getRadius(state);
        BlockState strippedState = strippedBranch.getStateForRadius(radius);
        level.setBlock(pos, strippedState, Block.UPDATE_ALL);

        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        damageAxe(stack, player);
        return true;
    }

    private static int tryStripRubberBranch(Level level, BlockPos pos, Player player, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        BranchBlock branch = TreeHelper.getBranch(state);
        if (branch == null) {
            return -1;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(branch);
        if (!RUBBER_BRANCH_ID.equals(blockId)) {
            return -1;
        }

        int radius = branch.getRadius(state);
        Family family = branch.getFamily(state, level, pos);
        boolean stripped = false;

        if (family.canStripBranch(state, level, pos, player, stack)) {
            stripped = family.stripBranch(state, level, pos, player, stack);
        }

        if (!stripped && player instanceof ServerPlayer serverPlayer) {
            stripped = forceStripBranch(level, pos, state, serverPlayer, stack, branch, family);
        }

        return stripped ? radius : -1;
    }

    private static boolean tryStripRubberLog(Level level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(DtRubberBlocks.RUBBER_LOG.get())) {
            return false;
        }

        BlockState strippedState = DtRubberBlocks.STRIPPED_RUBBER_LOG.get()
                .defaultBlockState()
                .setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS));
        level.setBlock(pos, strippedState, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        damageAxe(stack, player);
        return true;
    }
}
