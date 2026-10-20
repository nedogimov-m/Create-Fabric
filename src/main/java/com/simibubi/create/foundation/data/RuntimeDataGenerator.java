package com.simibubi.create.foundation.data;

import com.google.gson.*;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.mixin.ConcretePowderBlockAccessor;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ConcretePowderBlock;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Original registry-driven wood and concrete recipes, rebuilt for each datapack reload. */
public final class RuntimeDataGenerator {
	private static final Pattern STRIPPED_WOODS_PREFIX_REGEX = Pattern.compile("(\\w*)??stripped_(\\w*)(_log|_wood|_stem|_hyphae|_block|(?<!_)wood)()$");
	// (1. wood name)(2. type)(3. variant_suffix, optional)_stripped(4. 2nd variant_suffix, optional)
	private static final Pattern STRIPPED_WOOD_SUFFIX_REGEX = Pattern.compile("(\\w*)(_log|_wood|_stem|_hyphae|_block|(?<!_)wood)(\\w*)_stripped(\\w*)");
	// startofline(not preceded by stripped_)(1. wood_name)(2. type)(3. (4. variant suffix), optional, that doesn't end in _stripped, can be null)endofline
	private static final Pattern NON_STRIPPED_WOODS_REGEX = Pattern.compile("^(?!stripped_)([a-z_]+)(_log|_wood|_stem|_hyphae|(?<!bioshroom)_block)(([a-z_]+)(?<!_stripped))?$");
    private static final Map<Identifier, Identifier> MISMATCHED_WOOD_NAMES = Map.of(
            Identifier.parse("ars_nouveau:blue_archwood"), Identifier.parse("ars_nouveau:archwood"),
            Identifier.parse("deeperdarker:blooming"), Identifier.parse("deeperdarker:bloom"));
    private final Map<Identifier, byte[]> files = new LinkedHashMap<>();
    private final Map<Identifier, Set<String>> tags = new LinkedHashMap<>();
    public static Map<Identifier, byte[]> generate() {
        var generator = new RuntimeDataGenerator();
        for (var id : BuiltInRegistries.ITEM.keySet()) {
            generator.cuttingRecipes(id);
            var block = BuiltInRegistries.BLOCK.getValue(id);
            if (block instanceof ConcretePowderBlock powder) generator.recipe("splashing", id.getNamespace(), id.getPath(),
                    id.toString(), BuiltInRegistries.BLOCK.getKey(((ConcretePowderBlockAccessor) powder).create$getConcrete()), 1);
        }
        generator.tags.forEach((id, items) -> {
            var json = new JsonObject(); var values = new JsonArray(); items.forEach(values::add); json.add("values", values);
            generator.put(id.withPrefix("tags/item/"), json);
        });
        Create.LOGGER.info("Created {} runtime recipe/tag resources", generator.files.size());
        return Map.copyOf(generator.files);
    }
	private void cuttingRecipes(Identifier itemId) {
		String path = itemId.getPath();

		Matcher match = STRIPPED_WOODS_PREFIX_REGEX.matcher(path);
		boolean hasFoundMatch = match.find();
		boolean strippedInPrefix = hasFoundMatch;

		if (!hasFoundMatch) {
			match = STRIPPED_WOOD_SUFFIX_REGEX.matcher(path);
			hasFoundMatch = match.find();
		}

		// Last ditch attempt. Try to find logs without stripped variants
		boolean noStrippedVariant = false;
		if (!hasFoundMatch && !BuiltInRegistries.ITEM.containsKey(itemId.withPrefix("stripped_"))
			&& !BuiltInRegistries.ITEM.containsKey(itemId.withSuffix("_stripped"))) {
			match = NON_STRIPPED_WOODS_REGEX.matcher(path);
			hasFoundMatch = match.find();
			noStrippedVariant = true;
		}

		if (hasFoundMatch) {
			String prefix = strippedInPrefix && match.group(1) != null ? match.group(1) : "";
			String suffix = !strippedInPrefix && !noStrippedVariant ? match.group(3) + match.group(4) : "";
			String type = match.group(strippedInPrefix ? 3 : 2);
			Identifier matched_name = itemId.withPath(match.group(strippedInPrefix ? 2 : 1));
			// re-add 'wood' to wood types such as Botania's livingwood
			Identifier base = matched_name.withSuffix(type.equals("wood") ? "wood" : "");
			base = MISMATCHED_WOOD_NAMES.getOrDefault(base, base);
			Identifier nonStrippedId = matched_name.withSuffix(type).withPrefix(prefix).withSuffix(suffix);
			Identifier planksId = base.withSuffix("_planks");
			Identifier stairsId = base.withSuffix(base.getNamespace().equals("botania") ? "_planks_stairs" : "_stairs");
			Identifier slabId = base.withSuffix(base.getNamespace().equals("botania") ? "_planks_slab" : "_slab");
			Identifier fenceId = base.withSuffix("_fence");
			Identifier fenceGateId = base.withSuffix("_fence_gate");
			Identifier doorId = base.withSuffix("_door");
			Identifier trapdoorId = base.withSuffix("_trapdoor");
			Identifier pressurePlateId = base.withSuffix("_pressure_plate");
			Identifier buttonId = base.withSuffix("_button");
			Identifier signId = base.withSuffix("_sign");
			// Bamboo, GotD whistlecane
			int planksCount = type.contains("block") ? 3 : 6;

			if (!noStrippedVariant) {
				// Catch mods like JNE that have a non-stripped log prefixed but not the stripped log
				if (BuiltInRegistries.ITEM.containsKey(nonStrippedId)) {
					simpleWoodRecipe(nonStrippedId, itemId);
				}
				simpleWoodRecipe(itemId, planksId, planksCount);
			} else if (BuiltInRegistries.ITEM.containsKey(planksId)) {
				Identifier tag = Create.asResource("runtime_generated/compat/" + itemId.getNamespace() + "/" + base.getPath());
				tags.computeIfAbsent(tag, key -> new java.util.TreeSet<>()).add(itemId.toString());

				simpleWoodRecipe(TagKey.create(Registries.ITEM, tag), planksId, planksCount);
			}

			if (!path.contains("_wood") && !path.contains("_hyphae") && BuiltInRegistries.ITEM.containsKey(planksId)) {
				simpleWoodRecipe(planksId, stairsId);
				simpleWoodRecipe(planksId, slabId, 2);
				simpleWoodRecipe(planksId, fenceId);
				simpleWoodRecipe(planksId, fenceGateId);
				simpleWoodRecipe(planksId, doorId);
				simpleWoodRecipe(planksId, trapdoorId);
				simpleWoodRecipe(planksId, pressurePlateId);
				simpleWoodRecipe(planksId, buttonId);
				simpleWoodRecipe(planksId, signId);
			}
		}
	}

    private void put(Identifier id, JsonObject json) { files.put(id.withSuffix(".json"), json.toString().getBytes(StandardCharsets.UTF_8)); }
    private void simpleWoodRecipe(Identifier input, Identifier output) { simpleWoodRecipe(input, output, 1); }
    private void simpleWoodRecipe(Identifier input, Identifier output, int count) {
        if (BuiltInRegistries.ITEM.containsKey(input) && BuiltInRegistries.ITEM.containsKey(output)) recipe("cutting", input.getNamespace(), input.getPath(), input.toString(), output, count);
    }
    private void simpleWoodRecipe(TagKey<Item> tag, Identifier output, int count) {
        if (BuiltInRegistries.ITEM.containsKey(output)) recipe("cutting", tag.location().getNamespace(), "tag_" + tag.location().getPath(), "#" + tag.location(), output, count);
    }
    private void recipe(String type, String namespace, String from, String input, Identifier output, int count) {
        var json = new JsonObject(); json.addProperty("type", "create:" + type);
        var ingredients = new JsonArray(); ingredients.add(input); json.add("ingredients", ingredients);
        var result = new JsonObject(); result.addProperty("id", output.toString()); result.addProperty("count", count);
        var results = new JsonArray(); results.add(result); json.add("results", results);
        if (type.equals("cutting")) json.addProperty("processing_time", 50);
        put(Create.asResource("recipe/" + type + "/runtime_generated/compat/" + namespace + "/" + from + "_to_" + output.getPath()), json);
    }
}
