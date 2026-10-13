package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.skcraft.dtsable.compat.SawCut;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fell before Create destroys the cut node, preserving DT's connected crown. */
@Mixin(value = BlockBreakingMovementBehaviour.class, remap = false)
public abstract class ContraptionDrillMixin {
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void dtsable$fellTree(MovementContext context, BlockPos pos, CallbackInfo ci) {
        if (!((Object) this instanceof DrillMovementBehaviour)
                || !(context.world instanceof ServerLevel level)) return;
        BlockState state = level.getBlockState(pos);
        if (!SawCut.isCuttable(state)) return;
        BlockPos machine = context.position == null ? pos : BlockPos.containing(context.position);
        BlockBreakingMovementBehaviour self = (BlockBreakingMovementBehaviour) (Object) this;
        if (SawCut.fellAtTarget(level, pos, state, machine,
                (at, stack) -> self.collectOrDropItem(context, stack))) ci.cancel();
    }
}
