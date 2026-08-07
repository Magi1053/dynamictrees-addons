package com.skcraft.dtbetterend.mixin;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import org.betterx.betterend.world.features.bushes.TenaneaBushFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-cancels BetterEnd tenanea bush placement when DT worldgen already owns the biome.
 */
@Mixin(value = TenaneaBushFeature.class, remap = false)
public class MixinTenaneaBushFeature {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true, remap = false)
    private void dtbetterend$cancelPlace(
            FeaturePlaceContext<NoneFeatureConfiguration> context, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
