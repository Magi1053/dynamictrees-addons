package com.skcraft.dtsable.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BetterEnd's production implementation of this method is a no-op. In a
 * ModDev client it instead creates debug items after NeoForge's registration
 * window, leaving intrusive holders behind and preventing an aggregated dev
 * pack from starting.
 */
@Pseudo
@Mixin(targets = "org.betterx.betterend.registry.EndItems", remap = false)
abstract class BetterEndDebugMixin {
    @Inject(method = "ensureStaticallyLoaded", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void dtsable$skipLateDevItems(CallbackInfo ci) {
        ci.cancel();
    }
}
