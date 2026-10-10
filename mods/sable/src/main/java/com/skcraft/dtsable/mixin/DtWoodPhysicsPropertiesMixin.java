package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.WoodBuoyancy;
import com.skcraft.dtsable.tree.WoodThickness;
import dev.ryanhcode.sable.physics.config.block_properties.PhysicsBlockPropertyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scales fallen-wood mass and displaced volume together, and raises contact friction. */
@Mixin(value = PhysicsBlockPropertyHelper.class, remap = false)
public abstract class DtWoodPhysicsPropertiesMixin {
    @Inject(method = "getMass", at = @At("RETURN"), cancellable = true)
    private static void dtsable$weightWood(BlockGetter level, BlockPos pos, BlockState state,
                                            CallbackInfoReturnable<Double> cir) {
        double occupancy = occupancy(state);
        if (occupancy <= 0.0) return;
        cir.setReturnValue(WoodBuoyancy.mass(cir.getReturnValueD(), occupancy,
                DtSableConfig.fallenWoodMassMultiplier()));
    }

    @Inject(method = "getVolume", at = @At("RETURN"), cancellable = true)
    private static void dtsable$displaceWood(BlockState state, CallbackInfoReturnable<Double> cir) {
        double occupancy = occupancy(state);
        if (occupancy <= 0.0) return;
        // Collider baking asks for volume from the state alone. Massless wood is an empty
        // collision shape, and Sable drops that collider before buoyancy can use this value.
        cir.setReturnValue(WoodBuoyancy.volume(1.0, cir.getReturnValueD(), occupancy,
                DtSableConfig.fallenWoodMassMultiplier()));
    }

    @Inject(method = "getFriction", at = @At("RETURN"), cancellable = true)
    private static void dtsable$makeWoodGrip(BlockState state, CallbackInfoReturnable<Double> cir) {
        if (!DtSableConfig.isEnabled()) return;
        if (state.getBlock() instanceof BranchBlock || state.getBlock() instanceof TrunkShellBlock) {
            cir.setReturnValue(cir.getReturnValueD() * DtSableConfig.fallenWoodFriction());
        }
    }

    private static double occupancy(BlockState state) {
        if (!DtSableConfig.isEnabled()) return 0.0;
        boolean shell = state.getBlock() instanceof TrunkShellBlock;
        if (!shell && !(state.getBlock() instanceof BranchBlock)) return 0.0;
        return WoodBuoyancy.occupancy(shell, WoodThickness.radius(state));
    }
}
