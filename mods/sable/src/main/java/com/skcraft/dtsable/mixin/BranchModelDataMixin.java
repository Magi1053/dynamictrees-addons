package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.model.baked.BasicBranchBlockBakedModel;
import com.skcraft.dtsable.client.FallenEndGrain;
import com.skcraft.dtsable.dt.DtSableConfig;
import dev.ryanhcode.sable.Sable;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Notes whether the branch being meshed belongs to a fallen Sable body. */
@Mixin(value = BasicBranchBlockBakedModel.class, remap = false)
public abstract class BranchModelDataMixin {
    @Inject(method = "getModelData", at = @At("RETURN"), cancellable = true)
    private void dtsable$markFallenPlot(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData extra,
                                        CallbackInfoReturnable<ModelData> cir) {
        boolean fallen = dtsable$inFallenPlot(level, pos);
        FallenEndGrain.FALLEN.set(fallen);
        if (fallen) {
            cir.setReturnValue(cir.getReturnValue().derive().with(FallenEndGrain.PROPERTY, Boolean.TRUE).build());
        }
    }

    private static boolean dtsable$inFallenPlot(BlockAndTintGetter getter, BlockPos pos) {
        if (!DtSableConfig.isEnabled() || pos == null) return false;
        Level level = getter instanceof Level world ? world
                : getter instanceof RenderChunkRegion ? ((RenderChunkRegionAccessor) getter).dtsable$level()
                : null;
        if (level == null) return false;
        try {
            return Sable.HELPER.getContaining(level, pos) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
