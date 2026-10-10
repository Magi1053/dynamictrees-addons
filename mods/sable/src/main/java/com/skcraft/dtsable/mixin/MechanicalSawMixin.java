package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.skcraft.dtsable.compat.SawCut;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A horizontal mechanical saw mills a standing Dynamic Tree and chops one block of a fallen one. */
@Mixin(value = SawBlockEntity.class, remap = false)
public abstract class MechanicalSawMixin {
    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true)
    private void dtsable$allowDynamicWood(BlockState state, float hardness, CallbackInfoReturnable<Boolean> cir) {
        if (!SawCut.isCuttable(state)) return;
        cir.setReturnValue(true);
    }

    @Inject(method = "onBlockBroken", at = @At("HEAD"), cancellable = true)
    private void dtsable$cutTree(BlockState state, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) return;
        BlockPos breaking = ((BreakingPosAccessor) this).dtsable$breakingPos();
        BlockPos pos = breaking == null ? self.getBlockPos() : breaking;
        BlockPos fallen = SawCut.FALLEN.get();
        BlockState cutState = fallen != null ? level.getBlockState(fallen) : state;
        if (SawCut.cut(level, pos, cutState, self.getBlockPos())) ci.cancel();
    }
}
