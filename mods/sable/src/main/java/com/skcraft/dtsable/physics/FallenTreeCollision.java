package com.skcraft.dtsable.physics;

import com.skcraft.dtsable.dt.DtSableConfig;
import com.skcraft.dtsable.tree.FallenTreeRegistry;
import com.skcraft.dtsable.tree.WoodThickness;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3d;

/** Context-gated collision policy shared by branches, shells, and dynamic leaves. */
public final class FallenTreeCollision implements BlockSubLevelCollisionCallback {
    public static final FallenTreeCollision INSTANCE = new FallenTreeCollision();
    private static final CollisionResult REMOVE_CONTACT = new CollisionResult(JOMLConversion.ZERO, true);
    private FallenTreeCollision() {}

    @Override
    public CollisionResult sable$onCollision(BlockPos pos, BlockPos otherPos, Vector3d impactPosition,
                                             double impactVelocity) {
        ServerLevel level = SubLevelPhysicsSystem.getCurrentlySteppingSystem().getLevel();
        BlockState state = level.getBlockState(pos);
        if (!DtSableConfig.isEnabled() || FallenTreeRegistry.find(level, pos).isEmpty()) {
            return state.is(BlockTags.LEAVES) ? REMOVE_CONTACT : CollisionResult.NONE;
        }
        if (Math.abs(impactVelocity) >= 0.25) FallenTreeRegistry.noteCollision(level, pos);

        BlockPos standingLeaf = findStandingLeaf(level, otherPos, impactPosition);
        if (standingLeaf != null) {
            FallenTreeRegistry.scheduleStandingLeafBreak(level, standingLeaf);
            return REMOVE_CONTACT;
        }

        boolean otherIsLeaf = otherPos != null && level.getBlockState(otherPos).is(BlockTags.LEAVES);
        if (state.is(BlockTags.LEAVES)) {
            if (Math.abs(impactVelocity) >= DtSableConfig.leafCollisionBreakVelocity()) {
                FallenTreeRegistry.scheduleCollisionSnap(level, pos);
                return REMOVE_CONTACT;
            }
            return CollisionResult.NONE;
        }
        // A moving branch never loses to foliage. Standing foliage is removed above;
        // foliage belonging to another moving body is simply ignored for branch snapping.
        if (otherIsLeaf) return REMOVE_CONTACT;
        if (WoodThickness.isRigid(state) || !WoodThickness.isAutoBreakable(state)) return CollisionResult.NONE;
        double threshold = WoodThickness.scaledCollisionThreshold(WoodThickness.radius(state),
                DtSableConfig.branchCollisionBreakVelocity());
        if (Math.abs(impactVelocity) < threshold) return CollisionResult.NONE;
        FallenTreeRegistry.scheduleCollisionSnap(level, pos);
        return REMOVE_CONTACT;
    }

    private static BlockPos findStandingLeaf(ServerLevel level, BlockPos otherPos, Vector3d impactPosition) {
        if (otherPos != null && Sable.HELPER.getContaining(level, otherPos) == null
                && level.getBlockState(otherPos).is(BlockTags.LEAVES)) return otherPos.immutable();
        if (otherPos != null) return null;

        BlockPos best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        double epsilon = 1.0e-4;
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                BlockPos candidate = BlockPos.containing(impactPosition.x + sx * epsilon,
                        impactPosition.y + sy * epsilon, impactPosition.z + sz * epsilon);
                if (!level.getBlockState(candidate).is(BlockTags.LEAVES)
                        || Sable.HELPER.getContaining(level, candidate) != null) continue;
                double distance = candidate.distToCenterSqr(impactPosition.x, impactPosition.y, impactPosition.z);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate.immutable();
                }
            }
        return best;
    }
}
