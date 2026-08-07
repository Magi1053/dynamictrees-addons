package com.skcraft.dtrubber;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class RubberBallProjectile extends ThrowableItemProjectile {
    private static final int MAX_BOUNCES = 2;
    private static final double BOUNCE_DAMPING = 0.62;
    private static final double KNOCKBACK_IMPULSE = 2.55;
    private static final double KNOCKBACK_LIFT = 0.36;
    private int bounces;

    public RubberBallProjectile(EntityType<? extends RubberBallProjectile> type, Level level) {
        super(type, level);
    }

    public RubberBallProjectile(Level level, LivingEntity owner) {
        super(DtRubberEntities.RUBBER_BALL_PROJECTILE.get(), owner, level);
    }

    public RubberBallProjectile(Level level, double x, double y, double z) {
        super(DtRubberEntities.RUBBER_BALL_PROJECTILE.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        return DtRubberItems.RUBBER_BALL.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        Entity target = hitResult.getEntity();
        Vec3 horizontalVelocity = new Vec3(this.getDeltaMovement().x, 0.0, this.getDeltaMovement().z);

        if (horizontalVelocity.lengthSqr() < 1.0E-6) {
            horizontalVelocity = target.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
        }
        if (horizontalVelocity.lengthSqr() < 1.0E-6 && this.getOwner() != null) {
            horizontalVelocity = target.position().subtract(this.getOwner().position()).multiply(1.0, 0.0, 1.0);
        }
        if (horizontalVelocity.lengthSqr() < 1.0E-6) {
            horizontalVelocity = new Vec3(0.0, 0.0, 1.0);
        }

        Vec3 push = horizontalVelocity.normalize().scale(KNOCKBACK_IMPULSE);

        // Register a normal thrown-hit interaction (0 damage), then force movement so knockback is visible.
        target.hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
        Vec3 currentVelocity = target.getDeltaMovement();
        target.setDeltaMovement(currentVelocity.add(push.x, KNOCKBACK_LIFT, push.z));
        target.hasImpulse = true;
        target.hurtMarked = true;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (this.level().isClientSide) {
            return;
        }

        if (this.bounces < MAX_BOUNCES) {
            this.bounces++;
            this.bounce(hitResult);
        } else {
            this.level().addFreshEntity(new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), new ItemStack(this.getDefaultItem())));
            this.discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("RubberBallBounces", this.bounces);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.bounces = tag.getInt("RubberBallBounces");
    }

    private void bounce(HitResult hitResult) {
        Vec3 velocity = this.getDeltaMovement();
        Vec3 normal = switch (hitResult.getType()) {
            case BLOCK -> Vec3.atLowerCornerOf(((BlockHitResult) hitResult).getDirection().getNormal());
            case ENTITY -> this.entityBounceNormal((EntityHitResult) hitResult, velocity);
            default -> new Vec3(0.0, 1.0, 0.0);
        };

        if (normal.lengthSqr() < 1.0E-6) {
            normal = new Vec3(0.0, 1.0, 0.0);
        }
        normal = normal.normalize();

        Vec3 reflected = velocity.subtract(normal.scale(2.0 * velocity.dot(normal))).scale(BOUNCE_DAMPING);
        if (reflected.lengthSqr() < 0.001) {
            reflected = normal.scale(0.2);
        }

        this.setDeltaMovement(reflected);
        this.hasImpulse = true;
        this.hurtMarked = true;
        this.setPos(this.position().add(normal.scale(0.08)));
    }

    private Vec3 entityBounceNormal(EntityHitResult hitResult, Vec3 velocity) {
        Vec3 normal = this.position().subtract(hitResult.getEntity().position()).multiply(1.0, 0.0, 1.0);
        if (normal.lengthSqr() < 1.0E-6) {
            normal = velocity.scale(-1.0).multiply(1.0, 0.0, 1.0);
        }
        if (normal.lengthSqr() < 1.0E-6) {
            normal = new Vec3(0.0, 0.0, 1.0);
        }
        return normal;
    }
}
