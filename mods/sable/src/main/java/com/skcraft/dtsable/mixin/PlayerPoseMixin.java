package com.skcraft.dtsable.mixin;

import com.skcraft.dtsable.physics.FallenPose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pose fitting ignores fallen-tree wood, so a trunk cannot force crawl or swim. */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"))
    private void dtsable$ignoreFallenWood(CallbackInfo ci) {
        FallenPose.enter();
    }

    @Inject(method = "updatePlayerPose", at = @At("RETURN"))
    private void dtsable$restoreFallenWood(CallbackInfo ci) {
        FallenPose.exit();
    }
}
