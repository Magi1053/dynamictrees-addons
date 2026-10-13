package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.skcraft.dtsable.compat.SawCut;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The saw and drill break the tree in front of them, including a fallen body. */
@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public abstract class SawBreakerMixin {
    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true)
    private void dtsable$drillCanBreak(BlockState state, float hardness, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof DrillBlockEntity && SawCut.isCuttable(state)) cir.setReturnValue(true);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState dtsable$seeTree(Level level, BlockPos pos) {
        if (!dtsable$cutsTrees()) return level.getBlockState(pos);
        BlockEntity self = (BlockEntity) (Object) this;
        Direction facing = self.getBlockState().getValue(
                (Object) this instanceof DrillBlockEntity ? DrillBlock.FACING : SawBlock.FACING);
        return SawCut.stateInFront(level, pos, self.getBlockPos(), facing);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float dtsable$cuttableHardness(BlockState state, BlockGetter level, BlockPos pos) {
        if (!dtsable$cutsTrees() || !SawCut.isCuttable(state)) return state.getDestroySpeed(level, pos);
        return SawCut.breakingHardness(state, level, pos, SawCut.projectedFallenPosition() != null);
    }

    private boolean dtsable$cutsTrees() {
        return (Object) this instanceof SawBlockEntity || (Object) this instanceof DrillBlockEntity;
    }
}
