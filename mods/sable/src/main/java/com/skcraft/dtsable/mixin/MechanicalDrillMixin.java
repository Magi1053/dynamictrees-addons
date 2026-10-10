package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.skcraft.dtsable.compat.SawCut;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A mechanical drill fells the Dynamic Tree in front of it instead of voiding the crown. */
@Mixin(value = DrillBlockEntity.class, remap = false)
public abstract class MechanicalDrillMixin {
    @Inject(method = "onBlockBroken", at = @At("HEAD"), cancellable = true)
    private void dtsable$fellTree(BlockState state, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) return;
        BlockPos breaking = ((BreakingPosAccessor) this).dtsable$breakingPos();
        BlockPos pos = breaking == null ? self.getBlockPos() : breaking;
        BlockPos fallen = SawCut.FALLEN.get();
        BlockState cutState = fallen != null ? level.getBlockState(fallen) : state;
        if (!SawCut.isCuttable(cutState) && fallen == null) return;
        if (SawCut.fell(level, pos, cutState, self.getBlockPos())) ci.cancel();
    }
}
