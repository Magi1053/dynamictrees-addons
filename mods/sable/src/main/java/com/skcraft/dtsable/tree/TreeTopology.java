package com.skcraft.dtsable.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

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
