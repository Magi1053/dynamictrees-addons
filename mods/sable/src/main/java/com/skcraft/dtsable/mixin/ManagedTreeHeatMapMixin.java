package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.leaves.DynamicLeavesBlock;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DTSable performs deterministic component splitting for its own bodies. */
@Mixin(value = SubLevelHeatMapManager.class, remap = false)
public abstract class ManagedTreeHeatMapMixin {
    @Shadow @Final private ServerSubLevel subLevel;
    @Unique private Boolean dtsable$containsTreeBlocks;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void dtsable$skipGenericSplitterForManagedTree(CallbackInfo ci) {
        if (this.subLevel.isRemoved()) {
            ci.cancel();
            return;
        }
        // Remember ownership on the manager itself. The registry intentionally forgets a body
        // when its final core is harvested, before Sable finishes retiring the empty plot.
        if (FallenTreeRegistry.byId(this.subLevel.getUniqueId()).isPresent()) {
            this.dtsable$containsTreeBlocks = true;
        }
        if (this.dtsable$containsTreeBlocks()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean dtsable$containsTreeBlocks() {
        if (this.dtsable$containsTreeBlocks != null) return this.dtsable$containsTreeBlocks;
        var bounds = this.subLevel.getPlot().getBoundingBox();
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            var block = this.subLevel.getLevel().getBlockState(pos).getBlock();
            if (block instanceof BranchBlock || block instanceof DynamicLeavesBlock) {
                return this.dtsable$containsTreeBlocks = true;
            }
        }
        this.dtsable$containsTreeBlocks = false;
        return false;
    }
}
