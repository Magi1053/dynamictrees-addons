package com.skcraft.dtsable.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/** Dependency-free graph and weighted-center algorithms. */
public final class TreeTopology {
    public record Center(double x, double y, double z) {}
    private TreeTopology() {}

    public static <P> List<Set<P>> connectedComponents(Set<P> positions,
                                                        Function<P, Iterable<P>> neighbors) {
        Set<P> remaining = new HashSet<>(positions);
        List<Set<P>> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            P first = remaining.iterator().next();
            remaining.remove(first);
            Set<P> component = new HashSet<>();
            ArrayDeque<P> queue = new ArrayDeque<>();
            component.add(first);
            queue.add(first);
            while (!queue.isEmpty()) {
                P current = queue.removeFirst();
                for (P next : neighbors.apply(current)) if (remaining.remove(next)) {
                    component.add(next);
                    queue.add(next);
                }
            }
            components.add(Set.copyOf(component));
        }
        components.sort((left, right) -> Integer.compare(right.size(), left.size()));
        return List.copyOf(components);
    }

    /**
     * Nodes from {@code cut} out to the tips. The thickest endpoint is the base, so a look there
     * covers the whole graph and each step away leaves the near side behind.
     */
    public static <P> Set<P> cascade(Set<P> nodes, P cut, ToIntFunction<P> radius, Function<P, Iterable<P>> neighbors) {
        if (nodes.isEmpty() || !nodes.contains(cut)) return Set.of();
        return awayFrom(nodes, cut, baseEnd(nodes, radius, neighbors), neighbors);
    }

    /** Nodes from {@code cut} away from {@code root}. The root itself counts the whole graph. */
    public static <P> Set<P> awayFrom(Set<P> nodes, P cut, P root, Function<P, Iterable<P>> neighbors) {
        if (nodes.isEmpty() || cut == null || !nodes.contains(cut)) return Set.of();
        if (root == null || !nodes.contains(root) || root.equals(cut)) return new HashSet<>(nodes);
        Map<P, P> parent = new HashMap<>();
        ArrayDeque<P> queue = new ArrayDeque<>();
        parent.put(root, root);
        queue.add(root);
        while (!queue.isEmpty()) {
            P current = queue.removeFirst();
            for (P next : neighbors.apply(current)) {
                if (nodes.contains(next) && parent.putIfAbsent(next, current) == null) queue.add(next);
            }
        }
        if (!parent.containsKey(cut)) return Set.of(cut);
        P towardRoot = parent.get(cut);
        Set<P> cascade = new HashSet<>();
        ArrayDeque<P> flood = new ArrayDeque<>();
        cascade.add(cut);
        flood.add(cut);
        while (!flood.isEmpty()) {
            P current = flood.removeFirst();
            for (P next : neighbors.apply(current)) {
                if (nodes.contains(next) && !next.equals(towardRoot) && cascade.add(next)) flood.add(next);
            }
        }
        return cascade;
    }

    private static <P> P baseEnd(Set<P> nodes, ToIntFunction<P> radius, Function<P, Iterable<P>> neighbors) {
        P best = null;
        int bestRadius = Integer.MIN_VALUE;
        int bestDegree = Integer.MAX_VALUE;
        for (P pos : nodes) {
            int degree = 0;
            for (P next : neighbors.apply(pos)) if (nodes.contains(next)) degree++;
            int at = radius.applyAsInt(pos);
            if (best == null || at > bestRadius || (at == bestRadius && degree < bestDegree)) {
                best = pos;
                bestRadius = at;
                bestDegree = degree;
            }
        }
        return best;
    }

    public static <P> Set<P> flood(P start, Set<P> allowed, Function<P, Iterable<P>> neighbors) {
        if (!allowed.contains(start)) return Set.of();
        for (Set<P> component : connectedComponents(allowed, neighbors)) if (component.contains(start)) return component;
        return Set.of();
    }

    public static <P> Center weightedCenter(Set<P> positions, ToDoubleFunction<P> weight,
                                             ToDoubleFunction<P> x, ToDoubleFunction<P> y,
                                             ToDoubleFunction<P> z) {
        double sx = 0.0, sy = 0.0, sz = 0.0, total = 0.0;
        for (P pos : positions) {
            double value = weight.applyAsDouble(pos);
            if (!Double.isFinite(value) || value <= 0.0) continue;
            sx += x.applyAsDouble(pos) * value;
            sy += y.applyAsDouble(pos) * value;
            sz += z.applyAsDouble(pos) * value;
            total += value;
        }
        return total == 0.0 ? null : new Center(sx / total, sy / total, sz / total);
    }
}
