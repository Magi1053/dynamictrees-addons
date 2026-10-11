package com.skcraft.dtsable.client;

import com.skcraft.dtsable.dt.DtSableConfig;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Marks branch model data that belongs to a fallen Sable plot. */
public final class FallenEndGrain {
    public static final ModelProperty<Boolean> PROPERTY = new ModelProperty<>();
    public static final ThreadLocal<Boolean> FALLEN = ThreadLocal.withInitial(() -> false);

    private FallenEndGrain() {}

    public static boolean isFallen(ModelData data) {
        if (!DtSableConfig.isEnabled()) return false;
        if (data != null && Boolean.TRUE.equals(data.get(PROPERTY))) return true;
        return Boolean.TRUE.equals(FALLEN.get());
    }
}
