package com.skcraft.dtsable.compat;

import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.mojang.authlib.GameProfile;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.dt.FellContext;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import com.skcraft.dtsable.tree.WoodChop;
import com.skcraft.dtsable.tree.WoodThickness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * What a Create breaker is cutting: a standing Dynamic Tree, or one fallen-body block.
 * A saw only harvests managed fallen wood here; standing-tree cutting belongs to Create.
 * A drill fells standing wood into a falling body and pays the cut log.
 */
public final class SawCut {
    /** Plot position of a fallen block occupying the saw's breaking space, if any. */
    private static final ThreadLocal<BlockPos> FALLEN = new ThreadLocal<>();
    private static final GameProfile SAW = new GameProfile(
            UUID.nameUUIDFromBytes("dtsable-mechanical-saw".getBytes(StandardCharsets.UTF_8)),
            "DT Sable Saw");

    private SawCut() {}

    public static BlockPos projectedFallenPosition() {
        return FALLEN.get();
    }

    public static boolean isCuttable(BlockState state) {
        return state.getBlock() instanceof BranchBlock
                || state.getBlock() instanceof TrunkShellBlock
                || DtIntegration.isLeaves(state);
    }

    /** Drills and saws harvesting managed bodies time a cut against the species' primitive log. */
    public static float breakingHardness(BlockState state, BlockGetter level, BlockPos pos,
                                         boolean projectedFallen) {
        WoodTarget target = woodTarget(level, pos, state);
        if (target == null) {
            float hardness = state.getDestroySpeed(level, pos);
            return hardness < 0.0F ? 2.0F : hardness;
        }
        int radius = Math.max(1, WoodThickness.radius(target.state()));
        boolean standing = !projectedFallen
                && (!(level instanceof Level world) || FallenTreeRegistry.find(world, pos).isEmpty());
        float configured = standing ? DtSableConfig.standingWoodMiningSpeed()
                : DtSableConfig.fallenWoodMiningSpeed();
        float relative = WoodChop.relativeToPrimitiveLog(configured, radius, standing);
        float logHardness = DtIntegration.primitiveLog(target.branch()).defaultBlockState().getDestroySpeed(level, pos);
        return WoodChop.breakerHardness(logHardness, relative);
    }

    /**
     * The block in front of a stationary saw or drill. Fallen bodies live in a plot, so a world-space
     * lookup is air; a clip projects into the body the way mining does.
     */
    public static BlockState stateInFront(Level level, BlockPos breakingPos, BlockPos sawPos, Direction facing) {
        FALLEN.remove();
        BlockState there = level.getBlockState(breakingPos);
        if (isCuttable(there) && FallenTreeRegistry.find(level, breakingPos).isPresent()) {
            FALLEN.set(breakingPos.immutable());
            return there;
        }
        if (isCuttable(there)) return there;
        if (!(level instanceof ServerLevel)) return there;
        Vec3 start = Vec3.atCenterOf(sawPos);
        Vec3 end = start.add(facing.getStepX() * 1.8, facing.getStepY() * 1.8, facing.getStepZ() * 1.8);
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) return there;
        BlockPos plot = hit.getBlockPos();
        if (FallenTreeRegistry.find(level, plot).isEmpty() || !isCuttable(level.getBlockState(plot))) return there;
        FALLEN.set(plot.immutable());
        return level.getBlockState(plot);
    }

    /** Saw: harvest managed fallen wood, leaving standing trees to Create. */
    public static boolean harvestFallen(ServerLevel level, BlockPos pos,
                                         BiConsumer<BlockPos, ItemStack> drops) {
        BlockPos projected = takeProjectedFallenPosition();
        return TreeHarvest.breakManually(level, projected == null ? pos : projected, null, drops);
    }

    /** Stationary drill: consume its projected hit before falling back to the direct target. */
    public static boolean fell(ServerLevel level, BlockPos pos, BlockState state, BlockPos machinePos,
                               BiConsumer<BlockPos, ItemStack> drops) {
        BlockPos projected = takeProjectedFallenPosition();
        if (projected != null) return TreeHarvest.breakManually(level, projected, null, drops);
        return fellAtTarget(level, pos, state, machinePos, drops);
    }

    /** Moving drills already resolved their target and never consume stationary projected hits. */
    public static boolean fellAtTarget(ServerLevel level, BlockPos pos, BlockState state, BlockPos machinePos,
                                       BiConsumer<BlockPos, ItemStack> drops) {
        if (FallenTreeRegistry.find(level, pos).isPresent()) {
            return TreeHarvest.breakManually(level, pos, null, drops);
        }
        WoodTarget target = woodTarget(level, pos, state);
        if (target == null) {
            return DtIntegration.isLeaves(state) && TreeHarvest.breakStandingLeaf(level, pos, drops);
        }
        FellContext.withHarvestDrops(drops, () -> target.branch.futureBreak(
                target.state, level, target.pos, cutter(level, machinePos, target.pos)));
        return true;
    }

    private static BlockPos takeProjectedFallenPosition() {
        BlockPos pos = FALLEN.get();
        FALLEN.remove();
        return pos;
    }

    record WoodTarget(BranchBlock branch, BlockState state, BlockPos pos) {}

    static WoodTarget woodTarget(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BranchBlock branch) return new WoodTarget(branch, state, pos);
        if (!(state.getBlock() instanceof TrunkShellBlock shell)) return null;
        TrunkShellBlock.ShellMuse muse = shell.getMuse(level, state, pos);
        if (muse == null) return null;
        BranchBlock branch = TreeHelper.getBranch(muse.state());
        if (branch == null) return null;
        return new WoodTarget(branch, muse.state(), muse.pos());
    }

    /** Stands at the saw and looks at the cut so DT's hit direction and the fall direction match the saw. */
    static Player cutter(ServerLevel level, BlockPos sawPos, BlockPos treePos) {
        Player player = FakePlayerFactory.get(level, SAW);
        Vec3 from = Vec3.atCenterOf(sawPos);
        Vec3 delta = Vec3.atCenterOf(treePos).subtract(from);
        player.setPos(from.x, sawPos.getY(), from.z);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        player.setYRot((float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F);
        player.setXRot((float) (-(Mth.atan2(delta.y, horizontal) * Mth.RAD_TO_DEG)));
        player.setYHeadRot(player.getYRot());
        return player;
    }
}
