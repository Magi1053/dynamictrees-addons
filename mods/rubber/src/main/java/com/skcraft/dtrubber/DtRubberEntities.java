package com.skcraft.dtrubber;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DtRubberEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, DtRubber.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<RubberBallProjectile>> RUBBER_BALL_PROJECTILE = ENTITY_TYPES.register(
            "rubber_ball_projectile",
            () -> EntityType.Builder.<RubberBallProjectile>of(RubberBallProjectile::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("rubber_ball_projectile")
    );

    private DtRubberEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
