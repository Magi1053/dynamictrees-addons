package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.skcraft.dtsable.compat.SawCut;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only managed fallen trees need custom harvesting; Create owns standing-tree cutting. */
@Mixin(value = SawBlockEntity.class, remap = false)
public abstract class MechanicalSawMixin {
    @Shadow
    public abstract void dropItemFromCutTree(BlockPos pos, ItemStack stack);

    @Inject(method = "onBlockBroken", at = @At("HEAD"), cancellable = true)
    private void dtsable$cutTree(BlockState state, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel level)) return;
        BlockPos breaking = ((BreakingPosAccessor) this).dtsable$breakingPos();
        BlockPos pos = breaking == null ? self.getBlockPos() : breaking;
        BlockPos fallen = SawCut.projectedFallenPosition();
        if (FallenTreeRegistry.find(level, fallen == null ? pos : fallen).isEmpty()) return;
        // Use Create's drop callback so Sable applies motion and Simulated can collect
        // into its connected storage. A projected fallen target uses the breaking
        // position to avoid measuring ejection distance across different plots.
        if (SawCut.harvestFallen(level, pos,
                (at, stack) -> dropItemFromCutTree(fallen == null ? at : pos, stack))) ci.cancel();
    }
}
