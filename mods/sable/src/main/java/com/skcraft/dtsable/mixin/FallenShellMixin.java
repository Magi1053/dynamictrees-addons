package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.FallenTreeBody;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import com.skcraft.dtsable.tree.WoodThickness;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TrunkShellBlock.class, remap = false)
public abstract class FallenShellMixin {
    @Inject(method = "getDestroyProgress", at = @At("HEAD"), cancellable = true)
    private void dtsable$coreProgress(BlockState state, Player player, BlockGetter getter, BlockPos pos,
                                      CallbackInfoReturnable<Float> cir) {
        if (!DtSableConfig.isEnabled() || !(getter instanceof Level level)) return;
        if (!FallenTreeRegistry.hasFallenMiningContext(level, pos)) return;
        FallenTreeBody body = FallenTreeRegistry.find(level, pos).orElse(null);
        BlockPos core;
        if (body != null) {
            core = body.shellOwners().get(pos);
        } else {
            TrunkShellBlock shell = (TrunkShellBlock) state.getBlock();
            TrunkShellBlock.ShellMuse muse = shell.getMuse(getter, state, pos);
            core = muse == null ? null : muse.pos();
        }
        if (core == null) return;
        BlockState coreState = getter.getBlockState(core);
        if (!(coreState.getBlock() instanceof com.dtteam.dynamictrees.block.branch.BranchBlock branch)) return;
        float scale = Math.max(1.0f, WoodThickness.radius(coreState) / 8.0f);
        cir.setReturnValue(DtIntegration.primitiveLog(branch).defaultBlockState().getDestroyProgress(player, getter, core)
                * DtSableConfig.fallenWoodMiningSpeed() / scale);
    }

    @Inject(method = "onDestroyedByPlayer", at = @At("HEAD"), cancellable = true)
    private void dtsable$harvestCore(BlockState state, Level level, BlockPos pos, Player player,
                                     boolean harvest, FluidState fluid, CallbackInfoReturnable<Boolean> cir) {
        if (DtSableConfig.isEnabled() && level instanceof net.minecraft.server.level.ServerLevel server
                && FallenTreeRegistry.find(level, pos).isPresent()) {
            TreeHarvest.breakAt(server, pos, player, false);
            cir.setReturnValue(false);
        }
    }
}
