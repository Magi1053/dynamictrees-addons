package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.skcraft.dtsable.compat.SawCut;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
        BlockPos fallen = SawCut.projectedFallenPosition();
        BlockState cutState = fallen != null ? level.getBlockState(fallen) : state;
        if (!SawCut.isCuttable(cutState) && fallen == null) return;
        Vec3 dropPosition = Vec3.atCenterOf(pos).add(
                (level.random.nextFloat() - 0.5F) * 0.25F,
                (level.random.nextFloat() - 0.5F) * 0.25F,
                (level.random.nextFloat() - 0.5F) * 0.25F);
        if (SawCut.fell(level, pos, cutState, self.getBlockPos(),
                (at, stack) -> ((BreakerDropInvoker) this).dtsable$dropItem(dropPosition, stack))) ci.cancel();
    }
}
