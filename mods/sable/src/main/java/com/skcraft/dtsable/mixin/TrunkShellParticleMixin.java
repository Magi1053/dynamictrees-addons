package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.dt.DtSableConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Invisible trunk shells have no bark model; their particles belong to the core. */
@Mixin(TerrainParticle.class)
public abstract class TrunkShellParticleMixin extends TextureSheetParticle {
    protected TrunkShellParticleMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
            at = @At("RETURN"))
    private void dtsable$coreParticle(ClientLevel level, double x, double y, double z,
                                      double xSpeed, double ySpeed, double zSpeed, BlockState state,
                                      BlockPos pos, CallbackInfo ci) {
        dtsable$useCoreSprite(level, state, pos);
    }

    // NeoForge replaces the constructor sprite with a position-aware lookup.
    @Inject(method = "updateSprite", at = @At("RETURN"), remap = false)
    private void dtsable$coreModelData(BlockState state, BlockPos pos,
                                       CallbackInfoReturnable<TerrainParticle> cir) {
        dtsable$useCoreSprite(Minecraft.getInstance().level, state, pos);
    }

    @Unique
    private void dtsable$useCoreSprite(ClientLevel level, BlockState state, BlockPos pos) {
        if (!DtSableConfig.isEnabled() || level == null || pos == null
                || !(state.getBlock() instanceof TrunkShellBlock shell)) return;
        TrunkShellBlock.ShellMuse muse = shell.getMuse(level, state, pos);
        if (muse == null) return;
        setSprite(Minecraft.getInstance().getBlockRenderer()
                .getBlockModelShaper().getTexture(muse.state(), level, muse.pos()));
    }
}
