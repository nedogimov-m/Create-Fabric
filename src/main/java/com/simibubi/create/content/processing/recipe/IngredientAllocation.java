package com.simibubi.create.content.processing.recipe;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.function.BiPredicate;

/** Capacitated bipartite matching, so overlapping tags cannot consume a specific ingredient's only match. */
public final class IngredientAllocation {
    public static long[] allocate(long[] available, long[] required, BiPredicate<Integer, Integer> matches) {
        int supplyStart = 1 + required.length;
        int sink = supplyStart + available.length;
        long[][] residual = new long[sink + 1][sink + 1];
        long total = 0;
        for (int ingredient = 0; ingredient < required.length; ingredient++) {
            if (required[ingredient] <= 0) throw new IllegalArgumentException("Ingredient amounts must be positive");
            total = Math.addExact(total, required[ingredient]);
            residual[0][ingredient + 1] = required[ingredient];
            for (int slot = 0; slot < available.length; slot++) {
                if (matches.test(ingredient, slot)) residual[ingredient + 1][supplyStart + slot] = required[ingredient];
            }
        }
        for (int slot = 0; slot < available.length; slot++) {
            if (available[slot] < 0) throw new IllegalArgumentException("Negative storage amount");
            residual[supplyStart + slot][sink] = available[slot];
        }
        long moved = 0;
        while (moved < total) {
            int[] previous = new int[sink + 1];
            Arrays.fill(previous, -1);
            previous[0] = 0;
            var queue = new ArrayDeque<Integer>();
            queue.add(0);
            while (!queue.isEmpty() && previous[sink] < 0) {
                int from = queue.removeFirst();
                for (int to = 1; to <= sink; to++) {
                    if (previous[to] < 0 && residual[from][to] > 0) { previous[to] = from; queue.add(to); }
                }
            }
            if (previous[sink] < 0) return null;
            long amount = total - moved;
            for (int node = sink; node != 0; node = previous[node]) amount = Math.min(amount, residual[previous[node]][node]);
            for (int node = sink; node != 0; node = previous[node]) {
                residual[previous[node]][node] -= amount;
                residual[node][previous[node]] += amount;
            }
            moved += amount;
        }
        long[] consumed = new long[available.length];
        for (int slot = 0; slot < available.length; slot++) consumed[slot] = available[slot] - residual[supplyStart + slot][sink];
        return consumed;
    }
    private IngredientAllocation() {}
}
