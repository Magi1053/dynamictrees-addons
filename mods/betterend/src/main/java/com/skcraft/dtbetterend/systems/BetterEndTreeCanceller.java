package com.skcraft.dtbetterend.systems;

import com.dtteam.dynamictrees.api.worldgen.BiomePropertySelectors;
import com.dtteam.dynamictrees.api.worldgen.FeatureCanceller;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Cancels BetterEnd tree and bush feature types so Dynamic Trees worldgen owns End canopies.
 */
public class BetterEndTreeCanceller extends FeatureCanceller {
    private static final Set<String> FEATURES = Set.of(
            "betterend:tenanea",
            "betterend:tenanea_bush",
            "betterend:pythadendron_tree",
            "betterend:bush_feature",
            "betterend:bush_with_outer_feature",
            "betterend:lucernia",
            "betterend:lacugrove",
            "betterend:dragon_tree");

    public BetterEndTreeCanceller(ResourceLocation registryName) {
        super(registryName);
    }

    @Override
    public boolean shouldCancel(
            ConfiguredFeature<?, ?> configuredFeature, BiomePropertySelectors.NormalFeatureCancellation cancellation) {
        ResourceLocation key = BuiltInRegistries.FEATURE.getKey(configuredFeature.feature());
        return key != null && FEATURES.contains(key.toString());
    }
}
