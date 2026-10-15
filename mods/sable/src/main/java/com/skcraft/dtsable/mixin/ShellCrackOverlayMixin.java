package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.block.branch.TrunkShellBlock;
import com.skcraft.dtsable.dt.DtSableConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Shells have no visible model. Draw player and machine cracks on their visible core. */
@Mixin(LevelRenderer.class)
public abstract class ShellCrackOverlayMixin {
    @Shadow private ClientLevel level;

    @ModifyVariable(method = "destroyBlockProgress", at = @At("HEAD"), argsOnly = true)
    private BlockPos dtsable$cracksOnCore(BlockPos pos) {
        if (!DtSableConfig.isEnabled() || level == null) return pos;
        if (!(level.getBlockState(pos).getBlock() instanceof TrunkShellBlock shell)) return pos;
        TrunkShellBlock.ShellMuse muse = shell.getMuse(level, pos);
        // Vanilla clears progress by breaker id, even if the shell was removed meanwhile.
        return muse == null ? pos : muse.pos();
    }
}
