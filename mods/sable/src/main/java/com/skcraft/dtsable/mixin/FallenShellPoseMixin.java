package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.physics.FallenPose;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fallen trunk shells are not a ceiling during the player's pose test. */
@Mixin(value = TrunkShellBlock.class, remap = false)
public abstract class FallenShellPoseMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void dtsable$notACeiling(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context,
                                     CallbackInfoReturnable<VoxelShape> cir) {
        if (!FallenPose.ignoring() || !(getter instanceof Level level)) return;
        if (!FallenTreeRegistry.hasFallenMiningContext(level, pos)) return;
        cir.setReturnValue(Shapes.empty());
    }
}
