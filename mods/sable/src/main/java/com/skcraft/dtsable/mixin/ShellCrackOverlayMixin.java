package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.dt.DtSableConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trunk shells are {@code RenderShape.INVISIBLE}, so vanilla crack overlay
 * never draws on the bark sides. Mirror destroy progress onto the muse core;
 * that model's geometry includes the sides the player is hitting.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class ShellCrackOverlayMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    public abstract int getDestroyStage();

    @Unique
    private BlockPos dtsable$mirroredMuse;

    @Inject(method = "startDestroyBlock", at = @At("RETURN"))
    private void dtsable$mirrorShellCracksStart(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        dtsable$mirrorShellCracks(pos);
    }

    @Inject(method = "continueDestroyBlock", at = @At("RETURN"))
    private void dtsable$mirrorShellCracksContinue(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        dtsable$mirrorShellCracks(pos);
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"))
    private void dtsable$clearShellCracks(CallbackInfo ci) {
        dtsable$clearMirroredMuse();
    }

    @Unique
    private void dtsable$mirrorShellCracks(BlockPos pos) {
        if (!DtSableConfig.isEnabled() || this.minecraft.level == null || this.minecraft.player == null) {
            return;
        }
        BlockState state = this.minecraft.level.getBlockState(pos);
        if (!(state.getBlock() instanceof TrunkShellBlock shell)) {
            dtsable$clearMirroredMuse();
            return;
        }
        TrunkShellBlock.ShellMuse muse = shell.getMuse(this.minecraft.level, pos);
        if (muse == null) {
            return;
        }
        BlockPos musePos = muse.pos();
        if (this.dtsable$mirroredMuse != null && !this.dtsable$mirroredMuse.equals(musePos)) {
            this.minecraft.level.destroyBlockProgress(this.minecraft.player.getId(), this.dtsable$mirroredMuse, -1);
        }
        this.dtsable$mirroredMuse = musePos.immutable();
        this.minecraft.level.destroyBlockProgress(
                this.minecraft.player.getId(),
                this.dtsable$mirroredMuse,
                this.getDestroyStage());
    }

    @Unique
    private void dtsable$clearMirroredMuse() {
        if (this.dtsable$mirroredMuse != null && this.minecraft.level != null && this.minecraft.player != null) {
            this.minecraft.level.destroyBlockProgress(this.minecraft.player.getId(), this.dtsable$mirroredMuse, -1);
        }
        this.dtsable$mirroredMuse = null;
    }
}
