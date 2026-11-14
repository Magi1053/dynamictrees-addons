package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.physics.FallenTreeCollision;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = DynamicLeavesBlock.class, remap = false)
public abstract class DynamicLeavesCollisionMixin implements BlockWithSubLevelCollisionCallback {
    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return FallenTreeCollision.INSTANCE;
    }

    /** Native hydration and water removal otherwise bypass the managed body's harvest transaction. */
    @WrapOperation(method = {"updateHydro", "removeIfInvalid"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean dtsable$harvestBeforeLeafReplacement(LevelAccessor level, BlockPos pos, BlockState replacement,
            int flags, Operation<Boolean> original) {
        if (!(replacement.getBlock() instanceof DynamicLeavesBlock) && dtsable$harvestManagedLeaf(level, pos)
                && replacement.isAir()) return true;
        return original.call(level, pos, replacement, flags);
    }

    @WrapOperation(method = "removeIfInvalid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/LevelAccessor;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean dtsable$harvestBeforeShadeRemoval(LevelAccessor level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        return dtsable$harvestManagedLeaf(level, pos) || original.call(level, pos, moving);
    }

    @Unique private static boolean dtsable$harvestManagedLeaf(LevelAccessor level, BlockPos pos) {
        return DtSableConfig.isEnabled() && !DtIntegration.isMutatingBody() && level instanceof ServerLevel server
                && FallenTreeRegistry.find(server, pos).map(body -> body.leaves().contains(pos)).orElse(false)
                && TreeHarvest.snapFrom(server, pos);
    }
}
