package com.skcraft.dtsable.tree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Partitions post-cut wood without allowing foliage to reconnect severed branches. */
public final class TreeAssemblyPartition {
    public record Component<P>(Set<P> wood, Set<P> leaves) {}

    private TreeAssemblyPartition() {}

    public static <P> List<Component<P>> partition(Set<P> wood, Set<P> leaves,
                                                    Function<P, Iterable<P>> neighbors) {
        List<Set<P>> woodComponents = TreeTopology.connectedComponents(wood, neighbors);
        List<Set<P>> assignedLeaves = new ArrayList<>();
        for (int i = 0; i < woodComponents.size(); i++) assignedLeaves.add(new HashSet<>());

        for (Set<P> leafGroup : TreeTopology.connectedComponents(leaves, neighbors)) {
            int owner = -1;
            for (int i = 0; i < woodComponents.size(); i++) {
                if (touches(leafGroup, woodComponents.get(i), neighbors)) {
                    owner = i;
                    break; // Components are largest-first, giving shared canopies a stable owner.
                }
            }
            if (owner >= 0) assignedLeaves.get(owner).addAll(leafGroup);
        }

        List<Component<P>> result = new ArrayList<>();
        for (int i = 0; i < woodComponents.size(); i++) {
            result.add(new Component<>(woodComponents.get(i), Set.copyOf(assignedLeaves.get(i))));
        }
        return List.copyOf(result);
    }

    private static <P> boolean touches(Set<P> source, Set<P> target,
                                       Function<P, Iterable<P>> neighbors) {
        for (P pos : source) {
            for (P neighbor : neighbors.apply(pos)) if (target.contains(neighbor)) return true;
        }
        return false;
    }
}
