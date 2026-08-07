package com.skcraft.dtvinery.integration.emi;

import com.skcraft.dtvinery.PrimitiveSaplingIds;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

@EmiEntrypoint
public final class PrimitiveSaplingEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.removeEmiStacks(stack -> PrimitiveSaplingIds.isBlocked(stack.getId()));
    }
}
