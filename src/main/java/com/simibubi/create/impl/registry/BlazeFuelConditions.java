package com.simibubi.create.impl.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceCondition;
import net.fabricmc.fabric.api.resource.conditions.v1.ResourceConditions;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;

/** Source entry conditions evaluated by registered native Fabric conditions against this reload's lookup. */
final class BlazeFuelConditions {
    static Optional<JsonElement> unwrap(JsonElement value, RegistryOps.RegistryInfoLookup lookup) {
        if (!value.isJsonObject()) return Optional.of(value);
        var object = value.getAsJsonObject();
        var conditions = new ArrayList<ResourceCondition>();
        if (object.has("neoforge:conditions")) {
            for (var raw : object.getAsJsonArray("neoforge:conditions"))
                conditions.add(ResourceCondition.CODEC.parse(JsonOps.INSTANCE, translate(raw)).getOrThrow());
        }
        if (object.has(ResourceConditions.CONDITIONS_KEY))
            conditions.add(ResourceCondition.CONDITION_CODEC.parse(JsonOps.INSTANCE,
                object.get(ResourceConditions.CONDITIONS_KEY)).getOrThrow());
        // Decode every condition before testing, just as the source conditional codec does.
        if (conditions.stream().anyMatch(condition -> !condition.test(lookup))) return Optional.empty();
        return Optional.of(object.has("neoforge:conditions") && object.has("neoforge:value")
            ? object.get("neoforge:value") : value);
    }

    private static JsonObject translate(JsonElement raw) {
        var result = raw.getAsJsonObject().deepCopy();
        var type = Identifier.CODEC.parse(JsonOps.INSTANCE, result.remove("type")).getOrThrow();
        String target = type.toString();
        if (type.getNamespace().equals("neoforge")) {
            switch (type.getPath()) {
                case "true", "false" -> target = "fabric:" + type.getPath();
                case "and", "or" -> {
                    target = "fabric:" + type.getPath();
                    var translated = new JsonArray();
                    for (var child : result.getAsJsonArray("values")) translated.add(translate(child));
                    result.add("values", translated);
                }
                case "not" -> {
                    target = "fabric:not";
                    result.add("value", translate(result.get("value")));
                }
                case "mod_loaded" -> {
                    target = "fabric:all_mods_loaded";
                    var mods = new JsonArray();
                    mods.add(Codec.STRING.parse(JsonOps.INSTANCE, result.remove("modid")).getOrThrow());
                    result.add("values", mods);
                }
                case "item_exists" -> {
                    target = "fabric:registry_contains";
                    var items = new JsonArray();
                    items.add(Identifier.CODEC.parse(JsonOps.INSTANCE, result.remove("item")).getOrThrow().toString());
                    result.addProperty("registry", "minecraft:item");
                    result.add("values", items);
                }
                case "tag_empty" -> target = "create:item_tag_empty";
                default -> { /* Ported extensions may register this exact identifier in Fabric. */ }
            }
        }
        result.addProperty("condition", target);
        return result;
    }

    private BlazeFuelConditions() {}
}
