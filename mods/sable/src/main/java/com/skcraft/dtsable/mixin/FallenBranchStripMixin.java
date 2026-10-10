package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.TreeHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stripping a block on a fallen body swaps its branch type. The body's grown-volume budget
 * is keyed by branch type, so the block's debt moves with it and later pays stripped logs.
 */
@Mixin(value = BranchBlock.class, remap = false)
public abstract class FallenBranchStripMixin {
    @Inject(method = "stripBranch(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;I)V",
            at = @At("TAIL"))
    private void dtsable$moveDebtToStrippedBranch(BlockState state, LevelAccessor level, BlockPos pos, int radius,
                                                   CallbackInfo ci) {
        if (!(level instanceof ServerLevel server) || !DtSableConfig.isEnabled()) return;
        TreeHarvest.onStripped(server, pos, state, server.getBlockState(pos));
    }
}
