package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

/** Original structured text is not the flattened JSON string stored by old custom_name. */
public final class NeoForgeTextComponentImport {
    public static Tag structured(Tag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        version(version);
        if (source instanceof StringTag literal) return canonical(literal.copy(), registries);
        if (source instanceof ListTag list) {
            var result = new ListTag(); for (Tag child : list) result.add(structured(child, version, registries, child(scope, "text_list", result.size())));
            return canonical(result, registries);
        }
        if (!(source instanceof CompoundTag node)) throw invalid("Malformed original structured text");
        keys(node, "structured text", "type", "text", "translate", "fallback", "with", "score", "selector", "separator",
            "keybind", "nbt", "interpret", "block", "entity", "storage", "extra", "color", "bold", "italic", "underlined",
            "strikethrough", "obfuscated", "insertion", "font", "clickEvent", "hoverEvent");
        var shallow = node.copy();
        for (String key : Set.of("extra", "with", "separator", "hoverEvent")) shallow.remove(key);
        var json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, shallow).getAsJsonObject();
        // Structured NBT booleans are bytes. A generic NbtOps->JsonOps conversion would emit1/0,
        // which is not the genuine original JSON component boolean representation.
        for (String key : Set.of("bold", "italic", "underlined", "strikethrough", "obfuscated", "interpret"))
            if (node.contains(key)) json.addProperty(key, bool(node, key));
        Tag fixed = shallow(StringTag.valueOf(json.toString()), version, registries);
        CompoundTag result;
        if (fixed instanceof CompoundTag object) result = object.copy();
        else if (fixed instanceof StringTag literal) { result = new CompoundTag(); result.putString("text", literal.value()); }
        else throw invalid("Structured source text lost its component object");
        for (String key : Set.of("extra", "with")) if (node.contains(key)) {
            var children = new ListTag();
            for (Tag child : list(node, key)) {
                // Translation arguments may be primitive numeric values. Those are literal data,
                // not components or stack-shaped payloads.
                children.add(key.equals("with") && child instanceof net.minecraft.nbt.NumericTag ? child.copy() : structured(child, version, registries, child(scope, key, children.size())));
            }
            result.put(key, children);
        }
        if (node.contains("separator")) result.put("separator", structured(node.get("separator"), version, registries, child(scope, "separator", 0)));
        if (node.contains("hoverEvent")) result.put("hover_event", hover(compound(node, "hoverEvent"), version, registries, child(scope, "hover_event", 0)));
        return canonical(result, registries);
    }
    public static Tag flattened(StringTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        version(version);
        var json = com.google.gson.JsonParser.parseString(source.value());
        if (json.isJsonNull()) throw invalid("Null original flattened text");
        return structured(JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json), version, registries, scope);
    }
    private static Tag shallow(StringTag source, int version, HolderLookup.Provider registries) {
        var fixed = DataFixers.getDataFixer().update(References.TEXT_COMPONENT, new Dynamic<Tag>(NbtOps.INSTANCE, source.copy()),
            version, SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        return canonical(fixed, registries);
    }
    private static Tag hover(CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "hover event", "action", "contents");
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        switch (string(source, "action")) {
            case "show_text" -> {
                var result = new CompoundTag(); result.putString("action", "show_text");
                result.put("value", structured(source.get("contents"), version, registries, child(scope, "text", 0)));
                return HoverEvent.CODEC.encodeStart(ops, HoverEvent.CODEC.parse(ops, result).getOrThrow()).getOrThrow();
            }
            case "show_item" -> {
                var old = compound(source, "contents");
                var stack = ItemStack.CODEC.parse(ops, item(old, version, registries, child(scope, "item", 0))).getOrThrow();
                return HoverEvent.CODEC.encodeStart(ops, new HoverEvent.ShowItem(ItemStackTemplate.fromNonEmptyStack(stack))).getOrThrow();
            }
            case "show_entity" -> {
                var old = compound(source, "contents"); keys(old, "hover entity reference", "type", "id", "name");
                var id = Identifier.parse(string(old, "type"));
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) throw invalid("Original hover entity type needs its reviewed native rename " + id);
                var uuid = UUIDUtil.LENIENT_CODEC.parse(NbtOps.INSTANCE, old.get("id")).getOrThrow();
                var name = old.contains("name") ? ComponentSerialization.CODEC.parse(ops, structured(old.get("name"), version, registries, child(scope, "entity_name", 0))).getOrThrow() : null;
                var info = new HoverEvent.EntityTooltipInfo(BuiltInRegistries.ENTITY_TYPE.getValue(id), uuid, (net.minecraft.network.chat.Component) name);
                return HoverEvent.CODEC.encodeStart(ops, new HoverEvent.ShowEntity(info)).getOrThrow();
            }
            default -> throw invalid("Unknown original hover event action");
        }
    }
    private static Tag canonical(Tag value, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return ComponentSerialization.CODEC.encodeStart(ops, ComponentSerialization.CODEC.parse(ops, value).getOrThrow()).getOrThrow();
    }
    private static void version(int version) { if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original text version"); }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope, String role, int index) {
        return scope == null ? null : scope.item(role, index);
    }
    private NeoForgeTextComponentImport() {}
    /** Binary-compatible unscoped entrypoints; packed values require explicit real owner scope. */
    public static Tag structured(Tag source, int version, HolderLookup.Provider registries) {
        return structured(source, version, registries, null);
    }

    public static Tag flattened(StringTag source, int version, HolderLookup.Provider registries) {
        return flattened(source, version, registries, null);
    }

}
