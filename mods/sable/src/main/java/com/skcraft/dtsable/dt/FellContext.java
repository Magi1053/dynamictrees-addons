package com.skcraft.dtsable.dt;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Synchronous initiator context around DT's futureBreak to dropTree call chain. */
public final class FellContext {
    private static final ThreadLocal<Player> PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CONVERTED = ThreadLocal.withInitial(() -> false);
    private FellContext() {}
    public static void enter(LivingEntity entity) { if (entity instanceof Player player) PLAYER.set(player); }
    public static Player player() { return PLAYER.get(); }
    public static void markConverted() { CONVERTED.set(true); }
    public static boolean converted() { return CONVERTED.get(); }
    public static void exit() { PLAYER.remove(); CONVERTED.remove(); }
}
