package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.saw.SawMovementBehaviour;
import com.skcraft.dtsable.compat.SawCut;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public abstract class ContraptionSawSpeedMixin {
    @Redirect(method = "tickBreaker", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float dtsable$cuttingHardness(BlockState state, BlockGetter level, BlockPos pos) {
        if ((Object) this instanceof SawMovementBehaviour && SawCut.isCuttable(state)) {
            return SawCut.breakingHardness(state, level, pos, false);
        }
        return state.getDestroySpeed(level, pos);
    }
}
