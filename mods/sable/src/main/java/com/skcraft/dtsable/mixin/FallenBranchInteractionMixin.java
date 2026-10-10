package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.entity.FallingTreeEntity;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.FallenTreeBody;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BranchBlock.class, remap = false)
public abstract class FallenBranchInteractionMixin {
    @Inject(method = "onDestroyedByPlayer", at = @At("HEAD"), cancellable = true)
    private void dtsable$harvest(BlockState state, Level level, BlockPos pos, Player player,
                                 boolean harvest, FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof ServerLevel server) || !DtSableConfig.isEnabled()) return;
        BlockPos resolved = resolve(server, player, pos);
        FallenTreeBody original = FallenTreeRegistry.find(server, resolved).orElse(null);
        if (original == null) {
            original = FallenTreeRegistry.find(server, pos).orElse(null);
            if (original == null) return;
            resolved = pos;
        }
        TreeHarvest.breakAt(server, resolved, player, false);
        cir.setReturnValue(false);
    }

    /**
     * DT's axe strip walks to a root node. A fallen body has none, so standing strip never
     * runs here. Resolve the looked-at plot block and strip that one branch ourselves.
     */
    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void dtsable$stripFallen(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                     Player player, InteractionHand hand, BlockHitResult hit,
                                     CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (!DtSableConfig.isEnabled()) return;
        // Sable often calls this with an empty stack and a plot-local pos. DT's canBeStripped
        // then returns false even when the player is holding an axe.
        ItemStack tool = stack.isEmpty() ? player.getItemInHand(hand) : stack;
        if (tool.isEmpty()) tool = player.getMainHandItem();
        if (tool.isEmpty()) tool = player.getOffhandItem();
        BranchBlock self = (BranchBlock) (Object) this;
        if (level.isClientSide) {
            if (Sable.HELPER.getContaining(level, pos) != null
                    && self.canBeStripped(state, level, pos, player, tool)) {
                cir.setReturnValue(ItemInteractionResult.SUCCESS);
            }
            return;
        }
        if (!(level instanceof ServerLevel server)) return;
        BlockPos resolved = resolve(server, player, pos);
        FallenTreeBody body = FallenTreeRegistry.find(server, resolved).orElse(null);
        if (body == null) {
            body = FallenTreeRegistry.find(server, pos).orElse(null);
            if (body == null) return;
            resolved = pos;
        }
        if (!body.wood().contains(resolved)) resolved = pos;
        if (!body.wood().contains(resolved) && !body.owns(resolved)) return;
        BlockPos core = body.shellOwners().getOrDefault(resolved, resolved);
        BlockState before = server.getBlockState(core);
        if (before.isAir()) before = state;
        int radius = com.skcraft.dtsable.tree.WoodThickness.radius(before);
        boolean alreadyStripped = self.isStrippedBranch() || DtIntegration.isStrippedBranch(before);
        boolean dtCanStrip = self.canBeStripped(before, server, core, player, tool);
        boolean axe = dtCanStrip || com.dtteam.dynamictrees.platform.Services.INTERACTION.canToolAxeStrip(tool);
        if (alreadyStripped || radius <= 0 || !axe) return;
        if (!self.getFamily().hasStrippedBranch()) return;
        self.stripBranch(before, server, core, player, tool);
        cir.setReturnValue(ItemInteractionResult.SUCCESS);
    }

    @Inject(method = "sloppyBreak", at = @At("HEAD"), cancellable = true)
    private void dtsable$blockNetworkDestroy(Level level, BlockPos pos, FallingTreeEntity.DestroyType type,
                                             CallbackInfo ci) {
        if (DtSableConfig.isEnabled() && (DtIntegration.isMutatingBody()
                || FallenTreeRegistry.isManagedBody(level, pos))) ci.cancel();
    }

    private static BlockPos resolve(ServerLevel level, Player player, BlockPos fallback) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 2.0));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : fallback;
    }
}
