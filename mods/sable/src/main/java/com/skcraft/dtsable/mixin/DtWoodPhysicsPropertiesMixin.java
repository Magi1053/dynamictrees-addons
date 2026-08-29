package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.dt.DtSableConfig;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Raises wood mass and contact friction without altering Sable's body transform or COM. */
@Mixin(value = PhysicsBlockPropertyHelper.class, remap = false)
public abstract class DtWoodPhysicsPropertiesMixin {
    @Inject(method = "getMass", at = @At("RETURN"), cancellable = true)
    private static void dtsable$weightWood(BlockGetter level, BlockPos pos, BlockState state,
                                            CallbackInfoReturnable<Double> cir) {
        if (state.getBlock() instanceof BranchBlock || state.getBlock() instanceof TrunkShellBlock) {
            cir.setReturnValue(cir.getReturnValueD() * DtSableConfig.fallenWoodMassMultiplier());
        }
    }

    @Inject(method = "getFriction", at = @At("RETURN"), cancellable = true)
    private static void dtsable$makeWoodGrip(BlockState state, CallbackInfoReturnable<Double> cir) {
        if (state.getBlock() instanceof BranchBlock || state.getBlock() instanceof TrunkShellBlock) {
            cir.setReturnValue(cir.getReturnValueD() * DtSableConfig.fallenWoodFriction());
        }
    }
}
