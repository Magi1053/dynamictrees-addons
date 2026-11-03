package com.skcraft.dtsable.compat.bluemap3d.mixin;

import com.flowpowered.math.vector.Vector2i;
import com.skcraft.dtsable.compat.bluemap3d.RefreshRegions;
import de.bluecolored.bluemap.api.BlueMapMap;
import de.bluecolored.bluemap.api.RenderManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.duzo.bluemap3d.publish.TileRefreshQueue", remap = false)
public abstract class TreeTileRefreshMixin {
    @Redirect(method = "lambda$tick$3", at = @At(value = "INVOKE", target =
            "Lde/bluecolored/bluemap/api/RenderManager;scheduleMapUpdateTask(Lde/bluecolored/bluemap/api/BlueMapMap;Ljava/util/Collection;Z)Z"))
    private boolean shenanigans$forceCorrectRegions(RenderManager manager, BlueMapMap map,
            Collection<Vector2i> tiles, boolean force) {
        Vector2i size = map.getTileSize();
        Vector2i offset = map.getTileOffset();
        Set<Vector2i> regions = new HashSet<>();
        for (Vector2i tile : tiles) {
            for (RefreshRegions.Region region : RefreshRegions.fromTile(tile.getX(), tile.getY(),
                    size.getX(), size.getY(), offset.getX(), offset.getY())) {
                regions.add(new Vector2i(region.x(), region.z()));
            }
        }
        // Keep the original hires tile ids in the browser feed; only scheduler arguments change.
        return manager.scheduleMapUpdateTask(map, regions, true);
    }
}
