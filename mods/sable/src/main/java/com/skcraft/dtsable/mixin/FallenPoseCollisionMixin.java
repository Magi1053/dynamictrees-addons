package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.physics.FallenPose;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The pose test reads {@code cache.collisionShape} and never calls {@code getShape}.
 * Fallen wood, shells, and leaves are empty for that test only.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class FallenPoseCollisionMixin {
    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("HEAD"), cancellable = true)
    private void dtsable$ignoreFallenCeiling(BlockGetter getter, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (!FallenPose.ignoring() || !(getter instanceof Level level)) return;
        BlockState state = (BlockState) (Object) this;
        Block block = state.getBlock();
        if (!(block instanceof BranchBlock) && !(block instanceof TrunkShellBlock) && !DtIntegration.isLeaves(state)) return;
        if (FallenTreeRegistry.hasFallenMiningContext(level, pos)) cir.setReturnValue(Shapes.empty());
    }
}
