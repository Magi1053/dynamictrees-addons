package com.skcraft.dtsable.compat;

import com.dtteam.dynamictrees.api.network.Connections;
import com.dtteam.dynamictrees.block.branch.BranchBlock;
import com.dtteam.dynamictrees.systems.nodemapper.NetVolumeNode;
import com.dtteam.dynamictrees.tree.TreeHelper;
import com.dtteam.dynamictrees.tree.species.Species;
import com.skcraft.dtsable.dt.DtIntegration;
import com.skcraft.dtsable.tree.TreeTopology;
import com.skcraft.dtsable.tree.WoodBudget;
import com.skcraft.dtsable.tree.WoodThickness;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dynamic Trees' own log count, limited to the wood from the looked-at block out to the tips. */
public final class StandingTreeVolume {
    private StandingTreeVolume() {}

    public static NetVolumeNode.Volume cascade(Level level, BlockPos lookedAt, Species species) {
        BlockPos core = TreeHelper.dereferenceTrunkShell(level, lookedAt);
        BlockState coreState = level.getBlockState(core);
        if (!TreeHelper.isBranch(coreState)) return null;
        Map<BlockPos, List<BlockPos>> links = new HashMap<>();
        Set<BlockPos> wood = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        wood.add(core);
        queue.add(core);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            BlockState state = level.getBlockState(pos);
            if (!TreeHelper.isBranch(state)) continue;
            BranchBlock branch = TreeHelper.getBranch(state);
            Connections connections = branch.getConnectionData(level, pos, state);
            int[] radii = connections.getAllRadii();
            List<BlockPos> neighbors = new java.util.ArrayList<>();
            for (Direction direction : Direction.values()) {
                int index = direction.get3DDataValue();
                if (index >= radii.length || radii[index] <= 0) continue;
                BlockPos next = pos.relative(direction);
                neighbors.add(next);
                if (wood.add(next)) queue.add(next);
            }
            links.put(pos, neighbors);
        }
        BlockPos soil = TreeHelper.findRootNode(level, core);
        BlockPos base = null;
        if (soil != null && !BlockPos.ZERO.equals(soil)) {
            for (Direction direction : Direction.values()) {
                BlockPos next = soil.relative(direction);
                if (wood.contains(next)) { base = next; break; }
            }
        }
        java.util.function.Function<BlockPos, java.lang.Iterable<BlockPos>> neighbors =
                pos -> links.getOrDefault(pos, List.of());
        Set<BlockPos> counted = base == null
                ? TreeTopology.cascade(wood, core, pos -> radius(level, pos), neighbors)
                : TreeTopology.awayFrom(wood, core, base, neighbors);
        int baseRadius = 0;
        for (BlockPos pos : wood) {
            // Same grown radius the fell uses, so a trunk stripped while standing does not shrink the unit.
            BlockState state = level.getBlockState(pos);
            baseRadius = Math.max(baseRadius, DtIntegration.yieldRadius(state, WoodThickness.radius(state)));
        }
        double scale = species == null ? 1.0 : DtIntegration.lootVolumeScale(species);
        NetVolumeNode.Volume volume = new NetVolumeNode.Volume();
        for (BlockPos pos : counted) {
            BlockState state = level.getBlockState(pos);
            int block = WoodBudget.volumeOf(state, WoodThickness.radius(state), baseRadius, scale);
            int index = DtIntegration.branchIndex(state);
            if (block <= 0 || index < 0) continue;
            volume.addVolume(block, index);
        }
        return volume;
    }

    private static int radius(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return TreeHelper.isBranch(state) ? TreeHelper.getBranch(state).getRadius(state) : 0;
    }
}
