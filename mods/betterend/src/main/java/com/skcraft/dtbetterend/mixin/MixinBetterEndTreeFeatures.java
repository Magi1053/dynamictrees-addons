package com.skcraft.dtbetterend.mixin;

import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import org.betterx.betterend.world.features.bushes.BushFeature;
import org.betterx.betterend.world.features.bushes.BushWithOuterFeature;
import org.betterx.betterend.world.features.bushes.TenaneaBushFeature;
import org.betterx.betterend.world.features.trees.DragonTreeFeature;
import org.betterx.betterend.world.features.trees.LacugroveFeature;
import org.betterx.betterend.world.features.trees.LucerniaFeature;
import org.betterx.betterend.world.features.trees.PythadendronTreeFeature;
import org.betterx.betterend.world.features.trees.TenaneaFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hard-cancels BetterEnd tree and bush placement so Dynamic Trees owns those canopies.
 * DT's biome canceller alone misses BetterEnd land biomes (they are not in {@code #minecraft:is_end}).
 */
@Mixin(
        value = {
            BushFeature.class,
            BushWithOuterFeature.class,
            DragonTreeFeature.class,
            LacugroveFeature.class,
            LucerniaFeature.class,
            PythadendronTreeFeature.class,
            TenaneaBushFeature.class,
            TenaneaFeature.class
        },
        remap = false)
public class MixinBetterEndTreeFeatures {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true, remap = false)
    private void dtbetterend$cancelPlace(FeaturePlaceContext<?> context, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
