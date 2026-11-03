package com.skcraft.dtsable.compat.bluemap3d.mixin;

import com.skcraft.dtsable.compat.bluemap3d.DynamicTreeVolume;
import dev.duzo.bluemap3d.api.BlockVolume;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(targets = "dev.duzo.bluemap3d.bake.VolumeMesher", remap = false)
public abstract class DynamicTreeMesherMixin {
    @ModifyVariable(method = "mesh", at = @At("HEAD"), argsOnly = true)
    private BlockVolume shenanigans$connectedTreeGeometry(BlockVolume volume) {
        return DynamicTreeVolume.wrap(volume);
    }
}
