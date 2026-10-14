package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.saw.SawMovementBehaviour;
import com.skcraft.dtsable.compat.SawCut;
import com.skcraft.dtsable.dt.DtSableConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public abstract class ContraptionSawSpeedMixin {
    @Inject(method = "tickBreaker", at = @At("HEAD"), cancellable = true)
    private void dtsable$discardDistantCut(MovementContext context, CallbackInfo ci) {
        if (!DtSableConfig.isEnabled() || !((Object) this instanceof SawMovementBehaviour)
                || context.world.isClientSide || context.position == null
                || !context.data.contains("BreakingPos")) return;
        BlockPos target = NbtUtils.readBlockPos(context.data, "BreakingPos").orElse(null);
        if (target == null) return;
        // Create uses this same active-area block to acquire the target. At zero
        // progress it can retain a vanished target after the bearing moves away.
        if (!target.equals(BlockPos.containing(context.position))) {
            ((BlockBreakingMovementBehaviour) (Object) this).cancelStall(context);
            ci.cancel();
        }
    }

    @Redirect(method = "tickBreaker", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float dtsable$cuttingHardness(BlockState state, BlockGetter level, BlockPos pos) {
        if ((Object) this instanceof SawMovementBehaviour && SawCut.isCuttable(state)) {
            return SawCut.breakingHardness(state, level, pos, false);
        }
        return state.getDestroySpeed(level, pos);
    }
}
