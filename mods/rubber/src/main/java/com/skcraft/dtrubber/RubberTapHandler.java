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
    private static final ResourceLocation RUBBER_BRANCH_ID =
            ResourceLocation.fromNamespaceAndPath(DtRubber.MOD_ID, "rubber_branch");
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
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer || serverPlayer.isSpectator()) {
            return;
        }

        BlockPos pos = event.getPos();
        int radius = tryStripRubberBranch(level, pos, serverPlayer, stack);
        if (radius >= 0) {
            if (radius >= MIN_STRIP_RADIUS) {
                dropRawLatex((ServerLevel) level, pos);
            }
            succeed(event);
            return;
        }

        if (tryStripRubberLog(level, pos, serverPlayer, stack)) {
            dropRawLatex((ServerLevel) level, pos);
            succeed(event);
        }
    }

    private static void succeed(PlayerInteractEvent.RightClickBlock event) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void dropRawLatex(ServerLevel level, BlockPos pos) {
        int count = level.getRandom().nextInt(3);
        if (count > 0) {
            Block.popResource(level, pos, new ItemStack(DtRubberItems.RAW_LATEX.get(), count));
        }
    }

    private static void applyStrip(Level level, BlockPos pos, BlockState stripped, ServerPlayer player, ItemStack stack) {
        level.setBlock(pos, stripped, Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (!stack.isEmpty() && stack.isDamageableItem()) {
            EquipmentSlot slot = player.getMainHandItem() == stack ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, slot);
        }
    }

    private static int tryStripRubberBranch(Level level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        BranchBlock branch = TreeHelper.getBranch(state);
        if (branch == null || !RUBBER_BRANCH_ID.equals(BuiltInRegistries.BLOCK.getKey(branch))) {
            return -1;
        }

        int radius = branch.getRadius(state);
        Family family = branch.getFamily(state, level, pos);
        boolean stripped = family.canStripBranch(state, level, pos, player, stack)
                && family.stripBranch(state, level, pos, player, stack);

        if (!stripped) {
            var strippedOpt = family.getStrippedBranch();
            if (strippedOpt.isEmpty()) {
                return -1;
            }
            applyStrip(level, pos, strippedOpt.get().getStateForRadius(radius), player, stack);
            stripped = true;
        }

        return stripped ? radius : -1;
    }

    private static boolean tryStripRubberLog(Level level, BlockPos pos, ServerPlayer player, ItemStack stack) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(DtRubberBlocks.RUBBER_LOG.get())) {
            return false;
        }

        applyStrip(
                level,
                pos,
                DtRubberBlocks.STRIPPED_RUBBER_LOG.get()
                        .defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, state.getValue(BlockStateProperties.AXIS)),
                player,
                stack
        );
        return true;
    }
}
