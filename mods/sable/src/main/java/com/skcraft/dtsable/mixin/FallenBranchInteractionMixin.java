package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.entity.FallingTreeEntity;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.FallenTreeBody;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
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
