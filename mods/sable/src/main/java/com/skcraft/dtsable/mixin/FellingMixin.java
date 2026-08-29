package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.systems.nodemapper.NetVolumeNode;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.dt.FellContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BranchBlock.class, remap = false)
public abstract class FellingMixin {
    @Inject(method = "futureBreak", at = @At("HEAD"))
    private void dtsable$rememberInitiator(BlockState state, Level level, BlockPos pos,
                                           LivingEntity entity, CallbackInfo ci) {
        FellContext.enter(entity);
    }

    @Inject(method = "futureBreak", at = @At("RETURN"))
    private void dtsable$forgetInitiator(BlockState state, Level level, BlockPos pos,
                                         LivingEntity entity, CallbackInfo ci) {
        FellContext.exit();
    }

    @Inject(method = "damageAxe", at = @At("HEAD"), cancellable = true)
    private void dtsable$oneDamageForConvertedFell(LivingEntity entity, ItemStack tool, int radius,
                                                   NetVolumeNode.Volume volume, boolean blockBreak, CallbackInfo ci) {
        if (DtSableConfig.isEnabled() && FellContext.converted()) ci.cancel();
    }
}
