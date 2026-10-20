package com.simibubi.create.content.kinetics.crafter;

import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.Create;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import java.util.*;

public final class RecipeGridHandler {
    public record Cell(int x, int y) {}
    public record Entry(int x, int y, ItemStack stack) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(Codec.INT.fieldOf("x").forGetter(Entry::x), Codec.INT.fieldOf("y").forGetter(Entry::y), ItemStack.OPTIONAL_CODEC.fieldOf("item").forGetter(Entry::stack)).apply(i, Entry::new));
    }
    public static final class GroupedItems {
        public final Map<Cell, ItemStack> grid = new LinkedHashMap<>();
        public GroupedItems() {}
        public GroupedItems(ItemStack stack) { grid.put(new Cell(0, 0), stack.copy()); }
        public boolean onlyEmptyItems() { return grid.values().stream().allMatch(ItemStack::isEmpty); }
        public GroupedItems copy() { var copy = new GroupedItems(); grid.forEach((cell, stack) -> copy.grid.put(cell, stack.copy())); return copy; }
        public void mergeOnto(GroupedItems other, Pointing pointing) {
            int dx = pointing == Pointing.LEFT ? 1 : pointing == Pointing.RIGHT ? -1 : 0;
            int dy = pointing == Pointing.DOWN ? 1 : pointing == Pointing.UP ? -1 : 0;
            grid.forEach((cell, stack) -> {
                var target = new Cell(cell.x + dx, cell.y + dy);
                if (other.grid.containsKey(target)) throw new IllegalStateException("Overlapping mechanical crafting grid");
                other.grid.put(target, stack);
            });
        }
        public List<Entry> entries() { return grid.entrySet().stream().map(e -> new Entry(e.getKey().x, e.getKey().y, e.getValue())).toList(); }
        public void load(List<Entry> entries) { grid.clear(); entries.forEach(e -> grid.put(new Cell(e.x, e.y), e.stack)); }
        public CraftingInput input() {
            var occupied = grid.entrySet().stream().filter(e -> !e.getValue().isEmpty()).toList();
            if (occupied.isEmpty()) return CraftingInput.EMPTY;
            int minX = occupied.stream().mapToInt(e -> e.getKey().x).min().orElse(0), maxX = occupied.stream().mapToInt(e -> e.getKey().x).max().orElse(0);
            int minY = occupied.stream().mapToInt(e -> e.getKey().y).min().orElse(0), maxY = occupied.stream().mapToInt(e -> e.getKey().y).max().orElse(0);
            if (maxX - minX >= 9 || maxY - minY >= 9) return CraftingInput.EMPTY;
            var stacks = new ArrayList<ItemStack>();
            for (int y = maxY; y >= minY; y--) for (int x = minX; x <= maxX; x++) stacks.add(grid.getOrDefault(new Cell(x, y), ItemStack.EMPTY).copy());
            return CraftingInput.of(maxX - minX + 1, maxY - minY + 1, stacks);
        }
    }
    public static MechanicalCrafterBlockEntity getTargetingCrafter(MechanicalCrafterBlockEntity crafter) {
        var next = CrafterHelper.getCrafter(crafter.getLevel(), crafter.getBlockPos().relative(crafter.getTargetDirection()));
        return next != null && next.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING) == crafter.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING) ? next : null;
    }
    public static List<MechanicalCrafterBlockEntity> getPrecedingCrafters(MechanicalCrafterBlockEntity crafter) {
        var preceding = new ArrayList<MechanicalCrafterBlockEntity>();
        for (var side : Direction.values()) {
            if (side.getAxis() == crafter.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING).getAxis()) continue;
            var other = CrafterHelper.getCrafter(crafter.getLevel(), crafter.getBlockPos().relative(side));
            if (other != null && getTargetingCrafter(other) == crafter) preceding.add(other);
        }
        return preceding;
    }
    public static List<MechanicalCrafterBlockEntity> getAllCraftersOfChain(MechanicalCrafterBlockEntity start) {
        var visited = new LinkedHashSet<MechanicalCrafterBlockEntity>(); var queue = new ArrayDeque<MechanicalCrafterBlockEntity>(); queue.add(start);
        while (!queue.isEmpty()) {
            var current = queue.removeFirst(); if (!visited.add(current)) continue;
            if (visited.size() > 4096) return null;
            for (var side : Direction.values()) if (side.getAxis() != current.getBlockState().getValue(MechanicalCrafterBlock.HORIZONTAL_FACING).getAxis()
                    && !current.getLevel().isLoaded(current.getBlockPos().relative(side))) return null;
            var target = getTargetingCrafter(current); if (target != null) queue.add(target); queue.addAll(getPrecedingCrafters(current));
        }
        for (var current : visited) {
            var path = new HashSet<MechanicalCrafterBlockEntity>();
            for (var next = current; next != null; next = getTargetingCrafter(next)) if (!path.add(next)) return null;
        }
        return List.copyOf(visited);
    }
    private static final TagKey<RecipeSerializer<?>> AUTOMATION_IGNORE = TagKey.create(Registries.RECIPE_SERIALIZER, Create.asResource("automation_ignore"));
    public static ItemStack tryToApplyRecipe(ServerLevel level, GroupedItems items) {
        var input = items.input(); if (input.isEmpty()) return null;
        var manager = level.getServer().getRecipeManager();
        var config=com.simibubi.create.infrastructure.config.AllConfigs.server().recipes;
        var regular = config.allowRegularCraftingInCrafter.get()?manager.getRecipeFor(RecipeType.CRAFTING, input, level).filter(holder -> {
            var recipe = holder.value();
            return !(recipe instanceof FireworkRocketRecipe && input.ingredientCount() > config.maxFireworkIngredientsInCrafter.get())
                    && !holder.id().identifier().getPath().endsWith("_manual_only")
                    && !BuiltInRegistries.RECIPE_SERIALIZER.wrapAsHolder(recipe.getSerializer()).is(AUTOMATION_IGNORE);
        }):Optional.<RecipeHolder<CraftingRecipe>>empty();
        if (regular.isPresent()) return regular.get().value().assemble(input);
        return manager.getRecipeFor(AllRecipeTypes.MECHANICAL_CRAFTING, input, level).map(holder -> holder.value().assemble(input)).orElse(null);
    }
    private RecipeGridHandler() {}
}
