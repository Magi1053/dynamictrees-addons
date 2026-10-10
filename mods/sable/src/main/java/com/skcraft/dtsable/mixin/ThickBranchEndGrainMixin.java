package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.model.baked.ThickBranchBlockBakedModel;
import com.skcraft.dtsable.client.FallenEndGrain;
import com.skcraft.dtsable.dt.DtSableConfig;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * Fallen thick trunks were capping their ends with bark, and the bottom cap
 * asked the top-ring model for a down face, which has none. Use the log end
 * texture: top rings upward, bottom rings downward. Standing trees are unchanged.
 */
@Mixin(value = ThickBranchBlockBakedModel.class, remap = false)
public abstract class ThickBranchEndGrainMixin {
    @Shadow
    @Final
    private BakedModel[] trunksTopBark;

    @Shadow
    @Final
    private BakedModel[] trunksTopRings;

    @Shadow
    @Final
    private BakedModel[] trunksBotRings;

    @Redirect(
            method = "getQuads",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/BakedModel;getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;"
            ),
            remap = true
    )
    private List<BakedQuad> dtsable$logEndOnFallenCaps(BakedModel model, BlockState state, Direction face,
                                                      RandomSource random, ModelData data, RenderType type) {
        if (DtSableConfig.isEnabled() && Boolean.TRUE.equals(FallenEndGrain.FALLEN.get()) && face != null) {
            int bark = indexOf(this.trunksTopBark, model);
            if (bark >= 0) {
                BakedModel rings = face == Direction.DOWN ? this.trunksBotRings[bark] : this.trunksTopRings[bark];
                return rings.getQuads(state, face, random, data, type);
            }
            int top = indexOf(this.trunksTopRings, model);
            if (top >= 0 && face == Direction.DOWN) {
                return this.trunksBotRings[top].getQuads(state, face, random, data, type);
            }
        }
        return model.getQuads(state, face, random, data, type);
    }

    private static int indexOf(BakedModel[] models, BakedModel model) {
        if (models == null) return -1;
        for (int i = 0; i < models.length; i++) if (models[i] == model) return i;
        return -1;
    }
}
