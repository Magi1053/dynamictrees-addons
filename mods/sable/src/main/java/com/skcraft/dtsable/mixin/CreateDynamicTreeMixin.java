package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.simibubi.create.compat.dynamictrees.DynamicTree;
import com.skcraft.dtsable.compat.CreateTreeHarvest;
import com.skcraft.dtsable.dt.DtSableConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiConsumer;

/** Adapts Create's shared DT queue, retaining its saw execution and collection mechanics. */
@Mixin(value = DynamicTree.class, remap = false)
public abstract class CreateDynamicTreeMixin {
    @Shadow private BlockPos startCutPos;

    @Inject(method = "isDynamicBranch", at = @At("HEAD"), cancellable = true)
    private static void dtsable$recognizeWood(Block block, CallbackInfoReturnable<Boolean> cir) {
        if (DtSableConfig.isEnabled() && (block instanceof BranchBlock || block instanceof TrunkShellBlock)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "destroyBlocks", at = @At("HEAD"), cancellable = true)
    private void dtsable$harvest(Level level, ItemStack tool, Player player,
                                 BiConsumer<BlockPos, ItemStack> drops, CallbackInfo ci) {
        if (!DtSableConfig.isEnabled() || !(level instanceof ServerLevel server)) return;
        CreateTreeHarvest.cut(server, startCutPos, tool, player, drops);
        ci.cancel();
    }
}
