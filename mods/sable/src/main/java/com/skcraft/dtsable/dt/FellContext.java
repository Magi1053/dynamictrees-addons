package com.skcraft.dtsable.dt;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import java.util.function.BiConsumer;

/** Synchronous initiator context around DT's futureBreak to dropTree call chain. */
public final class FellContext {
    private static final ThreadLocal<Player> PLAYER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CONVERTED = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<BiConsumer<BlockPos, ItemStack>> HARVEST_DROPS = new ThreadLocal<>();
    private FellContext() {}
    public static void enter(LivingEntity entity) { if (entity instanceof Player player) PLAYER.set(player); }
    public static Player player() { return PLAYER.get(); }
    public static void markConverted() { CONVERTED.set(true); }
    public static boolean converted() { return CONVERTED.get(); }
    public static BiConsumer<BlockPos, ItemStack> harvestDrops() { return HARVEST_DROPS.get(); }

    /** Scoped separately from the DT initiator so futureBreak's exit cannot discard the callback early. */
    public static void withHarvestDrops(BiConsumer<BlockPos, ItemStack> drops, Runnable fell) {
        BiConsumer<BlockPos, ItemStack> previous = HARVEST_DROPS.get();
        HARVEST_DROPS.set(drops);
        try {
            fell.run();
        } finally {
            if (previous == null) HARVEST_DROPS.remove();
            else HARVEST_DROPS.set(previous);
        }
    }
    public static void exit() { PLAYER.remove(); CONVERTED.remove(); }
}
