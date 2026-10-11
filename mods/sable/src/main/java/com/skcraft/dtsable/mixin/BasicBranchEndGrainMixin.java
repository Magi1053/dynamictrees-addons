package com.skcraft.dtsable.mixin;

import com.dtteam.dynamictrees.model.baked.BasicBranchBlockBakedModel;
import com.dtteam.dynamictrees.model.modeldata.ModelConnections;
import com.skcraft.dtsable.client.FallenEndGrain;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/**
 * A fallen branch draws its exposed end from the bark core whenever the
 * neighbor is thinner than this block, or the block has more than one
 * connection. The cut uses the ring texture. Side bark follows the axis with
 * the most full-radius connections, so one earlier branch cannot turn the log.
 * Standing trees stay on Dynamic Trees' own models.
 */
@Mixin(value = BasicBranchBlockBakedModel.class, remap = false)
public abstract class BasicBranchEndGrainMixin {
    /** Matches {@code cores}: Y, then Z, then X. Down/up, north/south, west/east. */
    private static final Direction.Axis[] CORE_AXIS = {
            Direction.Axis.Y, Direction.Axis.Z, Direction.Axis.X
    };
    private static final int[] DIR_AXIS = {0, 0, 1, 1, 2, 2};

    @Shadow
    @Final
    protected BakedModel[][] cores;

    @Shadow
    @Final
    protected BakedModel[] rings;

    @Redirect(
            method = "getQuads",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/model/BakedModel;getQuads(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/util/RandomSource;Lnet/neoforged/neoforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)Ljava/util/List;"
            ),
            remap = true
    )
    private List<BakedQuad> dtsable$grainOnFallenCoreEnds(BakedModel model, BlockState state, Direction face,
                                                          RandomSource random, ModelData data, RenderType type) {
        if (!FallenEndGrain.isFallen(data) || face == null || this.cores == null || this.rings == null) {
            return model.getQuads(state, face, random, data, type);
        }
        int bakedAxis = -1;
        int index = -1;
        for (int axis = 0; axis < this.cores.length && axis < CORE_AXIS.length; axis++) {
            int found = indexOf(this.cores[axis], model);
            if (found < 0) continue;
            bakedAxis = axis;
            index = found;
            break;
        }
        if (bakedAxis < 0 || index >= this.rings.length) {
            return model.getQuads(state, face, random, data, type);
        }
        int[] radii = dtsable$radii(data);
        int axis = dtsable$logAxis(radii, bakedAxis, index + 1);
        boolean end = face.getAxis() == CORE_AXIS[axis];
        // End grain stays on the vertical ring model. Rotating it with the bark
        // stripe angle turns the cut. Side bark follows the run of the log.
        BakedModel replacement = end ? this.rings[index]
                : axis == bakedAxis ? model : this.cores[axis][index];
        if (replacement == model) return model.getQuads(state, face, random, data, type);
        return replacement.getQuads(state, face, random, data, type);
    }

    @Unique
    private static int[] dtsable$radii(ModelData data) {
        ModelConnections connections = data == null ? null : data.get(ModelConnections.CONNECTIONS_PROPERTY);
        if (connections == null) return null;
        int[] radii = connections.getAllRadii();
        return radii != null && radii.length >= DIR_AXIS.length ? radii : null;
    }

    /**
     * Dynamic Trees keeps the first neighbor that reaches this radius. A sideways
     * run of two full connections then loses to one earlier vertical connection,
     * and the bark stripes stay vertical. A thinner side branch does not count:
     * the block is still the vertical core.
     */
    @Unique
    private static int dtsable$logAxis(int[] radii, int bakedAxis, int radius) {
        if (radii == null) return bakedAxis;
        int best = 0;
        for (int along : radii) if (along > best) best = along;
        if (best < radius) return bakedAxis;
        int[] count = new int[CORE_AXIS.length];
        for (int dir = 0; dir < DIR_AXIS.length; dir++) {
            if (radii[dir] == best) count[DIR_AXIS[dir]]++;
        }
        int chosen = bakedAxis;
        int chosenCount = count[bakedAxis];
        boolean unique = true;
        for (int axis = 0; axis < count.length; axis++) {
            if (count[axis] > chosenCount) {
                chosenCount = count[axis];
                chosen = axis;
                unique = true;
            } else if (axis != chosen && count[axis] == chosenCount) {
                unique = false;
            }
        }
        return unique ? chosen : bakedAxis;
    }

    private static int indexOf(BakedModel[] models, BakedModel model) {
        if (models == null) return -1;
        for (int i = 0; i < models.length; i++) if (models[i] == model) return i;
        return -1;
    }
}
