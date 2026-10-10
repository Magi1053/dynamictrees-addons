package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import com.simibubi.create.content.kinetics.drill.DrillBlock;
import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.kinetics.saw.SawBlockEntity;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.skcraft.dtsable.compat.SawCut;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.WoodChop;
import com.skcraft.dtsable.tree.WoodThickness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The saw and drill break the tree in front of them, including a fallen body. */
@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public abstract class SawBreakerMixin {
    @Inject(method = "canBreak", at = @At("HEAD"), cancellable = true)
    private void dtsable$drillCanBreak(BlockState state, float hardness, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof DrillBlockEntity && SawCut.isCuttable(state)) cir.setReturnValue(true);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState dtsable$seeTree(Level level, BlockPos pos) {
        if (!dtsable$cutsTrees()) return level.getBlockState(pos);
        BlockEntity self = (BlockEntity) (Object) this;
        Direction facing = self.getBlockState().getValue(
                (Object) this instanceof DrillBlockEntity ? DrillBlock.FACING : SawBlock.FACING);
        return SawCut.stateInFront(level, pos, self.getBlockPos(), facing);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
    private float dtsable$cuttableHardness(BlockState state, BlockGetter level, BlockPos pos) {
        float hardness = state.getDestroySpeed(level, pos);
        if (!dtsable$cutsTrees() || !SawCut.isCuttable(state)) return hardness;
        int radius = dtsable$chopRadius(state, level, pos);
        if (radius <= 0) return hardness < 0.0F ? 2.0F : hardness;
        boolean standing = SawCut.FALLEN.get() == null
                && (!(level instanceof net.minecraft.world.level.Level world)
                || FallenTreeRegistry.find(world, pos).isEmpty());
        float configured = standing
                ? DtSableConfig.standingWoodMiningSpeed()
                : DtSableConfig.fallenWoodMiningSpeed();
        float relative = WoodChop.relativeToPrimitiveLog(configured, radius, standing);
        return WoodChop.breakerHardness(dtsable$logHardness(state, level, pos), relative);
    }

    /** Core radius for a shell, branch radius otherwise. Leaves stay on their own hardness. */
    private static int dtsable$chopRadius(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getBlock() instanceof TrunkShellBlock shell) {
            TrunkShellBlock.ShellMuse muse = shell.getMuse(level, state, pos);
            if (muse == null) return 0;
            return Math.max(1, WoodThickness.radius(muse.state()));
        }
        if (state.getBlock() instanceof BranchBlock) return Math.max(1, WoodThickness.radius(state));
        return 0;
    }

    private static float dtsable$logHardness(BlockState state, BlockGetter level, BlockPos pos) {
        BranchBlock branch = null;
        if (state.getBlock() instanceof BranchBlock direct) branch = direct;
        else if (state.getBlock() instanceof TrunkShellBlock shell) {
            TrunkShellBlock.ShellMuse muse = shell.getMuse(level, state, pos);
            if (muse != null) branch = TreeHelper.getBranch(muse.state());
        }
        if (branch == null) return 2.0F;
        Block log = DtIntegration.primitiveLog(branch);
        float speed = log.defaultBlockState().getDestroySpeed(level, pos);
        return speed > 0.0F ? speed : 2.0F;
    }

    private boolean dtsable$cutsTrees() {
        return (Object) this instanceof SawBlockEntity || (Object) this instanceof DrillBlockEntity;
    }
}
