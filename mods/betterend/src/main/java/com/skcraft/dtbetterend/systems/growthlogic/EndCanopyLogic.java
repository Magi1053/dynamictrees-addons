package com.skcraft.dtbetterend.systems.growthlogic;

import com.dtteam.dynamictrees.systems.GrowSignal;
import com.dtteam.dynamictrees.systems.growthlogic.GrowthLogicKit;
import com.dtteam.dynamictrees.systems.growthlogic.GrowthLogicKitConfiguration;
import com.dtteam.dynamictrees.systems.growthlogic.context.DirectionManipulationContext;
import com.dtteam.dynamictrees.utility.CoordUtils;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * End canopy growth: stronger upward bias in trunk, wider horizontal spread as energy is spent.
 */
public abstract class EndCanopyLogic extends GrowthLogicKit {
    private final float horizontalBias;
    private final float trunkUpWeight;

    protected EndCanopyLogic(ResourceLocation registryName, float horizontalBias, float trunkUpWeight) {
        super(registryName);
        this.horizontalBias = horizontalBias;
        this.trunkUpWeight = trunkUpWeight;
    }

    @Override
    public int[] populateDirectionProbabilityMap(
            GrowthLogicKitConfiguration configuration, DirectionManipulationContext context) {
        int[] probMap = super.populateDirectionProbabilityMap(configuration, context);
        GrowSignal signal = context.signal();

        probMap[Direction.UP.get3DDataValue()] = Math.max(1, Math.round(trunkUpWeight));

        if (!signal.isInTrunk()) {
            probMap[Direction.UP.get3DDataValue()] = 0;
            probMap[Direction.DOWN.get3DDataValue()] = 0;
            int dirOrdinal = signal.dir.ordinal();
            probMap[dirOrdinal] = Math.max(1, (int) (probMap[dirOrdinal] * 0.4f));
        }

        float spent = signal.delta.getY() / Math.max(1.0f, context.species().getEnergy(context.level(), context.pos()));
        float horizMul = Math.max(1.0f, spent * horizontalBias);
        for (Direction dir : CoordUtils.HORIZONTALS) {
            int i = dir.ordinal();
            probMap[i] = Math.max(0, (int) (probMap[i] * horizMul));
        }

        if (signal.numTurns == 1 && signal.delta.distToCenterSqr(0.0, signal.delta.getY(), 0.0) <= 2.0) {
            for (Direction dir : CoordUtils.HORIZONTALS) {
                if (dir != signal.dir.getOpposite()) {
                    probMap[dir.ordinal()] += 2;
                }
            }
        }

        return probMap;
    }
}
