package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.saw.SawMovementBehaviour;
import com.skcraft.dtsable.compat.SawCut;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Moving saws harvest before Create removes the start block, then collect every drop. */
@Mixin(value = SawMovementBehaviour.class, remap = false)
public abstract class ContraptionSawMixin {
    @Shadow
    public abstract void dropItemFromCutTree(MovementContext context, BlockPos pos, ItemStack stack);

    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true)
    private void dtsable$allowDynamicWood(Level level, BlockPos pos, BlockState state,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (SawCut.isCuttable(state)) cir.setReturnValue(true);
    }

    @Inject(method = "shouldDestroyStartBlock", at = @At("HEAD"), cancellable = true)
    private void dtsable$preserveCut(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (SawCut.isCuttable(state)) cir.setReturnValue(false);
    }

    @Inject(method = "onBlockBroken", at = @At("HEAD"), cancellable = true)
    private void dtsable$collectTree(MovementContext context, BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!SawCut.isCuttable(state) || !(context.world instanceof ServerLevel level)) return;
        BlockPos machine = context.position == null ? pos : BlockPos.containing(context.position);
        // Moving actors have their own target; do not consume a stationary breaker's ray hit.
        SawCut.clearProjectedFallenPosition();
        // Fallen drops use plot coordinates; overflow must spawn beside the moving saw.
        SawCut.cut(level, pos, state, machine,
                (at, stack) -> dropItemFromCutTree(context, pos, stack));
        ci.cancel();
    }
}
