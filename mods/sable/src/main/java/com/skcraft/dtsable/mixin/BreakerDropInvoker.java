package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.base.BlockBreakingKineticBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reuses Create's item-drop callback, including Simulated's storage interception. */
@Mixin(value = BlockBreakingKineticBlockEntity.class, remap = false)
public interface BreakerDropInvoker {
    @Invoker("lambda$onBlockBroken$0")
    void dtsable$dropItem(Vec3 position, ItemStack stack);
}
