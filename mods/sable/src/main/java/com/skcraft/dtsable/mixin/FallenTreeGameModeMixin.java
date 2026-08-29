package com.skcraft.dtsable.mixin;

import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolves moving Sable blocks before vanilla looks up the stale packet position. */
@Mixin(ServerPlayerGameMode.class)
abstract class FallenTreeGameModeMixin {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void dtsable$destroyProjectedFallenWood(BlockPos packetPos,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (!DtSableConfig.isEnabled()) return;
        BlockPos resolved = resolve(level, player);
        if (FallenTreeRegistry.find(level, resolved).isEmpty()) {
            if (FallenTreeRegistry.find(level, packetPos).isEmpty()) return;
            resolved = packetPos;
        }
        BlockState state = level.getBlockState(resolved);
        boolean removed = TreeHarvest.breakManually(level, resolved, player);
        if (removed && !player.isCreative()) {
            player.getMainHandItem().mineBlock(level, state, resolved, player);
        }
        cir.setReturnValue(removed);
    }

    private static BlockPos resolve(ServerLevel level, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(player.blockInteractionRange() + 2.0));
        BlockHitResult hit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : BlockPos.ZERO;
    }
}
