package com.skcraft.dtbetterend.systems.genfeature;

import com.dtteam.dynamictrees.systems.genfeature.VinesGenFeature;
import net.minecraft.resources.ResourceLocation;

/**
 * Ceiling / hanging flower vines for BetterEnd canopy species (filalux, tenanea flowers).
 * Behaviour matches Dynamic Trees vines with a dedicated registry name for species JSON.
 */
public class FlowerVinesGenFeature extends VinesGenFeature {
    public FlowerVinesGenFeature(ResourceLocation registryName) {
        super(registryName);
    }
}
