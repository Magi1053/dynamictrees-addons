package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.ThickBranchBlock;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ThickBranchBlock.class)
public interface ThickBranchRadiusAccessor {
    @Accessor(value = "RADIUS_DOUBLE", remap = false)
    static IntegerProperty dtsable$radiusDouble() {
        throw new AssertionError();
    }
}
