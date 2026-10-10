package com.skcraft.dtsable.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads saw speed. The field lives on the kinetic base, not the breaker subclass. */
@Mixin(value = KineticBlockEntity.class, remap = false)
public interface KineticSpeedAccessor {
    @Accessor("speed")
    float dtsable$speed();
}
