package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.skcraft.dtsable.physics.FallenTreeCollision;
import dev.ryanhcode.sable.api.block.BlockWithSubLevelCollisionCallback;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = DynamicLeavesBlock.class, remap = false)
public abstract class DynamicLeavesCollisionMixin implements BlockWithSubLevelCollisionCallback {
    @Override
    public BlockSubLevelCollisionCallback sable$getCallback() {
        return FallenTreeCollision.INSTANCE;
    }
}
