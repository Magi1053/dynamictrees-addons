package com.skcraft.dtsable.compat;

import com.dtteam.dynamictrees.api.network.BranchDestructionData;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.mojang.authlib.GameProfile;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.dt.FellContext;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.TreeHarvest;
import com.skcraft.dtsable.tree.WoodBudget;
import com.skcraft.dtsable.tree.WoodYield;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * What a Create breaker is cutting: a standing Dynamic Tree, or one fallen-body block.
 * A saw mills the cut and everything supported through it into drops. A drill fells that
 * same portion into a falling body and pays one log.
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

    public static void clearProjectedFallenPosition() {
        FALLEN.remove();
    }

    public static boolean isCuttable(BlockState state) {
        return state.getBlock() instanceof BranchBlock
                || state.getBlock() instanceof TrunkShellBlock
                || DtIntegration.isLeaves(state);
    }

    /** Both stationary breakers and moving saws time a cut against the species' vanilla log. */
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
     * The block in front of a horizontal saw. Fallen bodies live in a plot, so a world-space
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

    /** Saw: mill the supported crown through the caller's native drop callback. Fallen wood is one chop. */
    public static boolean cut(ServerLevel level, BlockPos pos, BlockState state, BlockPos machinePos,
                              BiConsumer<BlockPos, ItemStack> drops) {
        return breakTree(level, pos, state, machinePos, false, drops);
    }

    /** Drill: fell the cut and the crown it supports. Fallen wood is one chop. */
    public static boolean fell(ServerLevel level, BlockPos pos, BlockState state, BlockPos machinePos,
                               BiConsumer<BlockPos, ItemStack> drops) {
        return breakTree(level, pos, state, machinePos, true, drops);
    }

    private static boolean breakTree(ServerLevel level, BlockPos pos, BlockState state, BlockPos machinePos,
                                     boolean fellBody, BiConsumer<BlockPos, ItemStack> drops) {
        BlockPos fallen = FALLEN.get();
        if (fallen != null) {
            FALLEN.remove();
            return TreeHarvest.breakManually(level, fallen, null, drops);
        }
        if (FallenTreeRegistry.find(level, pos).isPresent()) {
            return TreeHarvest.breakManually(level, pos, null, drops);
        }
        WoodTarget target = woodTarget(level, pos, state);
        if (target == null) {
            if (DtIntegration.isLeaves(state)) return TreeHarvest.breakStandingLeaf(level, pos, drops);
            return false;
        }
        if (fellBody) {
            FellContext.withHarvestDrops(drops, () -> target.branch.futureBreak(
                    target.state, level, target.pos, cutter(level, machinePos, target.pos)));
            return true;
        }
        return mill(level, target, machinePos, drops);
    }

    /**
     * Removes the cut node and every branch DT would not walk back to the root, then pays that
     * wood and its leaves as items. Does not call {@code dropTree}, so the crown does not fall.
     */
    private static boolean mill(ServerLevel level, WoodTarget target, BlockPos machinePos,
                                BiConsumer<BlockPos, ItemStack> drops) {
        BranchDestructionData data = target.branch.destroyBranchFromNode(
                level, target.pos, Direction.DOWN, false, cutter(level, machinePos, target.pos));
        if (data == null || data.species == null || data.getNumBranches() <= 0) return false;
        dropMilledWood(level, data, drops);
        for (BranchBlock.ItemStackPos drop : data.leavesDrops) {
            if (drop.stack.isEmpty()) continue;
            drops.accept(data.cutPos.offset(drop.pos), drop.stack);
        }
        showDestructionParticles(level, data);
        return true;
    }

    private static void showDestructionParticles(ServerLevel level, BranchDestructionData data) {
        for (int i = 0; i < data.getNumBranches(); i++) {
            BlockState broken = DtIntegration.branchState(data, i);
            if (broken == null || broken.isAir()) continue;
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, DtIntegration.branchPos(data, i), Block.getId(broken));
        }
        for (int i = 0; i < DtIntegration.leafCount(data); i++) {
            BlockState broken = DtIntegration.leafState(data, i);
            if (broken == null || broken.isAir()) continue;
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, DtIntegration.leafPos(data, i), Block.getId(broken));
        }
    }

    /** Whole-log payment for every destroyed branch, including the cut, dropped at the cut. */
    private static void dropMilledWood(ServerLevel level, BranchDestructionData data,
                                       BiConsumer<BlockPos, ItemStack> drops) {
        int baseRadius = 0;
        BlockState baseState = data.basePos == null ? null : level.getBlockState(data.basePos);
        if (baseState != null && !baseState.isAir()) {
            baseRadius = DtIntegration.yieldRadius(baseState, DtIntegration.radius(baseState));
        }
        int fallback = 0;
        for (int i = 0; i < data.getNumBranches(); i++) {
            BlockState state = DtIntegration.branchState(data, i);
            if (state == null) continue;
            fallback = Math.max(fallback, DtIntegration.yieldRadius(state, DtIntegration.branchRadius(data, i)));
        }
        baseRadius = Math.max(baseRadius, fallback);
        double scale = DtIntegration.lootVolumeScale(data.species);
        Map<Integer, Integer> budget = new HashMap<>();
        for (int i = 0; i < data.getNumBranches(); i++) {
            BlockState state = DtIntegration.branchState(data, i);
            if (state == null) continue;
            WoodBudget.add(budget, state, DtIntegration.branchRadius(data, i), baseRadius, scale);
        }
        for (Map.Entry<Integer, Integer> entry : budget.entrySet()) {
            int paid = WoodYield.pay(entry.getValue(), 0).paidVolume();
            if (paid <= 0) continue;
            for (ItemStack stack : DtIntegration.branchDrops(level, data.species, entry.getKey(), paid, ItemStack.EMPTY)) {
                if (stack.isEmpty()) continue;
                drops.accept(data.cutPos, stack);
            }
        }
    }

    private record WoodTarget(BranchBlock branch, BlockState state, BlockPos pos) {}

    private static WoodTarget woodTarget(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof BranchBlock branch) return new WoodTarget(branch, state, pos);
        if (!(state.getBlock() instanceof TrunkShellBlock shell)) return null;
        TrunkShellBlock.ShellMuse muse = shell.getMuse(level, state, pos);
        if (muse == null) return null;
        BranchBlock branch = TreeHelper.getBranch(muse.state());
        if (branch == null) return null;
        return new WoodTarget(branch, muse.state(), muse.pos());
    }

    /** Stands at the saw and looks at the cut so DT's hit direction and the fall direction match the saw. */
    private static Player cutter(ServerLevel level, BlockPos sawPos, BlockPos treePos) {
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
