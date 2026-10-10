package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public interface BreakingPosAccessor {
    @Accessor("breakingPos")
    BlockPos dtsable$breakingPos();

    @Accessor("destroyProgress")
    int dtsable$destroyProgress();

    @Accessor("ticksUntilNextProgress")
    int dtsable$ticksUntilNextProgress();
}
