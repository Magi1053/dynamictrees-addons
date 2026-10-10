package com.skcraft.dtsable.dt;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DtSableConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue enabled;
    public static final ModConfigSpec.DoubleValue impulse_force;
    public static final ModConfigSpec.DoubleValue impulse_torque;
    public static final ModConfigSpec.DoubleValue branch_collision_break_velocity;
    public static final ModConfigSpec.DoubleValue leaf_collision_break_velocity;
    public static final ModConfigSpec.IntValue max_blocks;
    public static final ModConfigSpec.DoubleValue standing_wood_mining_speed;
    public static final ModConfigSpec.DoubleValue fallen_wood_mining_speed;
    public static final ModConfigSpec.DoubleValue fallen_wood_mass_multiplier;
    public static final ModConfigSpec.DoubleValue fallen_wood_friction;
    public static final ModConfigSpec.IntValue fallen_sticks_per_log;
    public static final ModConfigSpec.BooleanValue leaf_break_on_impact;
    public static final ModConfigSpec.IntValue leaf_decay_ticks;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        enabled = builder
                .comment("Assemble felled Dynamic Trees into Sable SubLevels.")
                .define("enabled", true);
        impulse_force = builder
                .comment("Topple linear speed (m/s), away from the player. Applied as mass-scaled impulse.")
                .defineInRange("impulse_force", 0.6D, 0.0D, 50.0D);
        impulse_torque = builder
                .comment("Topple spin (rad/s). The crown falls away from the player; sneak reverses that. Axis is horizontal, perpendicular to the fall direction.")
                .defineInRange("impulse_torque", 0.40D, 0.0D, 50.0D);
        branch_collision_break_velocity = builder
                .comment("Impact velocity (m/s) that breaks DT branches in a falling Sable SubLevel.")
                .defineInRange("branch_collision_break_velocity", 2.0D, 0.1D, 50.0D);
        leaf_collision_break_velocity = builder
                .comment("Impact velocity (m/s) that breaks leaves in a falling Sable SubLevel.")
                .defineInRange("leaf_collision_break_velocity", 0.25D, 0.0D, 50.0D);
        max_blocks = builder
                .comment("Skip physics and use the original DT falling entity above this many placed blocks.")
                .defineInRange("max_blocks", 2000, 16, 20000);
        standing_wood_mining_speed = builder
                .comment("Chop speed of a standing radius-8 trunk, relative to its primitive log. Thinner wood is faster and thicker wood is slower, linearly with radius. 1 matches a vanilla log.")
                .defineInRange("standing_wood_mining_speed", 0.85D, 0.1D, 100.0D);
        fallen_wood_mining_speed = builder
                .comment("Mining-speed multiplier for branches and trunk shells in managed Sable bodies.")
                .defineInRange("fallen_wood_mining_speed", 2.0D, 0.1D, 100.0D);
        fallen_wood_mass_multiplier = builder
                .comment("Scales Dynamic Trees wood mass and displaced volume together. Density stays at a green log (mass / volume = 0.8).")
                .defineInRange("fallen_wood_mass_multiplier", 8.0D, 1.0D, 100.0D);
        fallen_wood_friction = builder
                .comment("Friction multiplier for Dynamic Trees wood in Sable physics bodies.")
                .defineInRange("fallen_wood_friction", 2.0D, 0.0D, 10.0D);
        fallen_sticks_per_log = builder
                .comment("How often a broken or collided branch rolls sticks, relative to vanilla leaves. 4 is the leaf rate (2% at no Fortune, 1 or 2 sticks); 0 never drops; 8 is twice as often. Logs are not affected.")
                .defineInRange("fallen_sticks_per_log", 4, 0, 8);
        leaf_break_on_impact = builder
                .comment("Unused: Sable fragile collision already breaks leaves on ground impact.")
                .define("leaf_break_on_impact", false);
        leaf_decay_ticks = builder
                .comment("Unused: leftover leaves are not force-decayed after settle.")
                .defineInRange("leaf_decay_ticks", 40, 0, 1200);
        SPEC = builder.build();
    }

    private DtSableConfig() {
    }

    public static boolean isEnabled() {
        try {
            return enabled.get();
        } catch (IllegalStateException notLoadedYet) {
            // Block-state caches are baked before COMMON config loading.
            // Every mixin must be a native-DT no-op during that bootstrap window.
            return false;
        }
    }

    public static double impulseForce() {
        return impulse_force.get();
    }

    public static double impulseTorque() {
        return impulse_torque.get();
    }

    public static double branchCollisionBreakVelocity() {
        return branch_collision_break_velocity.get();
    }

    public static double leafCollisionBreakVelocity() {
        return leaf_collision_break_velocity.get();
    }

    public static int maxBlocks() {
        return max_blocks.get();
    }

    public static float fallenWoodMiningSpeed() {
        return fallen_wood_mining_speed.get().floatValue();
    }

    public static float standingWoodMiningSpeed() {
        return standing_wood_mining_speed.get().floatValue();
    }

    public static double fallenWoodMassMultiplier() {
        return fallen_wood_mass_multiplier.get();
    }

    public static double fallenWoodFriction() {
        return fallen_wood_friction.get();
    }

    public static int fallenSticksPerLog() {
        return fallen_sticks_per_log.get();
    }

    public static boolean leafBreakOnImpact() {
        return leaf_break_on_impact.get();
    }

    public static int leafDecayTicks() {
        return leaf_decay_ticks.get();
    }
}
