package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.ThickBranchBlock;
import com.skcraft.dtsable.dt.DtSableConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DT {@code getRadius} returns 0 when {@code isSameTree} fails. That happens
 * inside Sable plots and makes thick trunks render as empty cores and
 * classify as twigs. The state's {@code RADIUS_DOUBLE} is authoritative.
 */
@Mixin(value = ThickBranchBlock.class, remap = false)
public abstract class ThickBranchRadiusMixin {
    @Inject(method = "getRadius", at = @At("HEAD"), cancellable = true)
    private void dtsable$radiusFromState(BlockState state, CallbackInfoReturnable<Integer> cir) {
        if (!DtSableConfig.isEnabled()) return;
        IntegerProperty radiusDouble = ThickBranchRadiusAccessor.dtsable$radiusDouble();
        if (state.hasProperty(radiusDouble)) {
            cir.setReturnValue(Mth.clamp(state.getValue(radiusDouble), 1, ThickBranchBlock.MAX_RADIUS_THICK));
        }
    }
}
