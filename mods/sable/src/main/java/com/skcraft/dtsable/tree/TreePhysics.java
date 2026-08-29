package com.skcraft.dtsable.tree;

import com.skcraft.dtsable.dt.DtSableConfig;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.server.level.ServerLevel;
import org.joml.Vector3d;

public final class TreePhysics {
    private TreePhysics() {}

    /** Applies velocities only. Never edits mass, COM, pose, or vertical velocity. */
    public static boolean applyToppleVelocity(ServerSubLevel body, ServerLevel level, double dx, double dz) {
        PhysicsPipeline pipeline = pipeline(level);
        if (pipeline == null || body.isRemoved()) return false;
        double length = Math.hypot(dx, dz);
        if (length < 1.0e-6) { dx = 0.0; dz = 1.0; length = 1.0; }
        dx /= length;
        dz /= length;
        try {
            pipeline.addLinearAndAngularVelocity(body,
                    new Vector3d(dx * DtSableConfig.impulseForce(), 0.0,
                            dz * DtSableConfig.impulseForce()),
                    new Vector3d(dz * DtSableConfig.impulseTorque(), 0.0,
                            -dx * DtSableConfig.impulseTorque()));
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

    private static PhysicsPipeline pipeline(ServerLevel level) {
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        return container == null ? null : container.physicsSystem().getPipeline();
    }
}
