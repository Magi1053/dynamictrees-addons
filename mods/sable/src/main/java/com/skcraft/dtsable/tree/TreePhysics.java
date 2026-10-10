package com.skcraft.dtsable.tree;

import com.skcraft.dtsable.dt.DtSableConfig;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

public final class TreePhysics {
    private TreePhysics() {}

    /** Horizontal velocity holds the stump. Torque tips the crown. Never edits mass, COM, or pose. */
    public static boolean applyToppleVelocity(ServerSubLevel body, ServerLevel level, double dx, double dz) {
        PhysicsPipeline pipeline = pipeline(level);
        if (pipeline == null || body.isRemoved()) return false;
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) { dx = 0.0; dz = 1.0; length = 1.0; }
        dx /= length;
        dz /= length;
        try {
            double torque = DtSableConfig.impulseTorque();
            double wx = dz * torque;
            double wz = -dx * torque;
            double[] linear = hingeLinear(body, dx, dz, wx, wz);
            pipeline.addLinearAndAngularVelocity(body,
                    new Vector3d(linear[0], 0.0, linear[1]),
                    new Vector3d(wx, 0.0, wz));
            pipeline.wakeUp(body);
            return true;
        } catch (RuntimeException notInPhysicsSceneYet) {
            return false;
        }
    }

    /** Contact-only damping: suppress rebound and let a fallen trunk settle instead of oscillating. */
    public static void dampAfterContact(ServerSubLevel body, ServerLevel level) {
        PhysicsPipeline pipeline = pipeline(level);
        if (pipeline == null || body.isRemoved()) return;
        try {
            Vector3d linear = pipeline.getLinearVelocity(body, new Vector3d());
            Vector3d angular = pipeline.getAngularVelocity(body, new Vector3d());
            Vector3d linearCorrection = new Vector3d(-linear.x * 0.08,
                    linear.y > 0.0 ? -linear.y : 0.0, -linear.z * 0.08);
            Vector3d angularCorrection = new Vector3d(angular).mul(-0.20);
            pipeline.addLinearAndAngularVelocity(body, linearCorrection, angularCorrection);
        } catch (RuntimeException ignoredDuringRetirement) {
            // The body may have been removed by a collision harvest earlier in this tick.
        }
    }

    /** Fluid drag. Shrinks every velocity component and leaves buoyancy able to lift the trunk. */
    public static void dampInWater(ServerSubLevel body, ServerLevel level) {
        PhysicsPipeline pipeline = pipeline(level);
        if (pipeline == null || body.isRemoved()) return;
        try {
            Vector3d linear = pipeline.getLinearVelocity(body, new Vector3d());
            Vector3d angular = pipeline.getAngularVelocity(body, new Vector3d());
            double[] drag = WaterDrag.correction(linear.x, linear.y, linear.z, angular.x, angular.y, angular.z);
            pipeline.addLinearAndAngularVelocity(body,
                    new Vector3d(drag[0], drag[1], drag[2]),
                    new Vector3d(drag[3], drag[4], drag[5]));
        } catch (RuntimeException ignoredDuringRetirement) {
            // The body may have been removed by a collision harvest earlier in this tick.
        }
    }

    /** Horizontal center-of-mass velocity that leaves the lowest wood unmoved by the spin. */
    private static double[] hingeLinear(ServerSubLevel body, double dx, double dz, double wx, double wz) {
        double[] stump = lowestWood(body);
        if (stump == null) return new double[] {dx * DtSableConfig.impulseForce(), dz * DtSableConfig.impulseForce()};
        var com = body.getMassTracker().getCenterOfMass();
        double ry = stump[1] - com.y();
        return new double[] {wz * ry, -wx * ry};
    }

    private static double[] lowestWood(ServerSubLevel body) {
        var tree = FallenTreeRegistry.byId(body.getUniqueId()).orElse(null);
        if (tree == null || tree.wood().isEmpty()) return null;
        int minY = Integer.MAX_VALUE;
        for (BlockPos pos : tree.wood()) minY = Math.min(minY, pos.getY());
        double sx = 0.0, sy = 0.0, sz = 0.0;
        int count = 0;
        for (BlockPos pos : tree.wood()) {
            if (pos.getY() != minY) continue;
            sx += pos.getX() + 0.5;
            sy += pos.getY() + 0.5;
            sz += pos.getZ() + 0.5;
            count++;
        }
        return new double[] {sx / count, sy / count, sz / count};
    }

    private static PhysicsPipeline pipeline(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.physicsSystem().getPipeline();
    }
}
