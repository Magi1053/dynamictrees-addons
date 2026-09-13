package com.skcraft.dtrubber.integration.emi;

import java.util.List;

import com.skcraft.dtrubber.DtRubber;
import com.skcraft.dtrubber.DtRubberBlocks;
import com.skcraft.dtrubber.DtRubberItems;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Information page for Raw Latex; stripping has no crafting recipe. */
@EmiEntrypoint
public final class LatexEmiPlugin implements EmiPlugin {
    private static final String INFO_KEY = "emi.dtrubber.raw_latex";
    private static final ResourceLocation RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath(DtRubber.MOD_ID, "/info/raw_latex");

    @Override
    public void register(EmiRegistry registry) {
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(DtRubberItems.RAW_LATEX.get()), EmiStack.of(DtRubberBlocks.RUBBER_LOG.get())),
                List.of(Component.translatable(INFO_KEY)),
                RECIPE_ID));
    }
}
