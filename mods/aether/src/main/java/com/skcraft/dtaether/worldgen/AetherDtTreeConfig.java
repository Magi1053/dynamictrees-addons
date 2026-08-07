package com.skcraft.dtaether.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import java.util.List;

public record AetherDtTreeConfig(List<WeightedSpecies> species) implements FeatureConfiguration {
    public static final Codec<AetherDtTreeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    WeightedSpecies.CODEC.listOf().fieldOf("species").forGetter(AetherDtTreeConfig::species))
            .apply(instance, AetherDtTreeConfig::new));

    public record WeightedSpecies(ResourceLocation species, int weight) {
        public static final Codec<WeightedSpecies> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        ResourceLocation.CODEC.fieldOf("species").forGetter(WeightedSpecies::species),
                        Codec.INT.fieldOf("weight").forGetter(WeightedSpecies::weight))
                .apply(instance, WeightedSpecies::new));
    }

    public ResourceLocation pickSpecies(net.minecraft.util.RandomSource random) {
        int total = 0;
        for (WeightedSpecies entry : species) {
            total += entry.weight;
        }
        if (total <= 0) {
            return species.getFirst().species();
        }
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (WeightedSpecies entry : species) {
            cumulative += entry.weight;
            if (roll < cumulative) {
                return entry.species();
            }
        }
        return species.getLast().species();
    }
}
