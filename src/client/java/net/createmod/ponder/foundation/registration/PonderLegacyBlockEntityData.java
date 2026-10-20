package net.createmod.ponder.foundation.registration;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelConnection;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelPosition;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity.SchematicannonOptions;
import java.util.HashSet;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit legacy fields in the pinned Ponder assets, never a saved-world owner import. */
final class PonderLegacyBlockEntityData {
    static void normalize(CompoundTag structure, int sourceVersion, HolderLookup.Provider registries) {
        for (Tag block : structure.getListOrEmpty("blocks")) {
            if (!(block instanceof CompoundTag row) || !(row.get("nbt") instanceof CompoundTag data)) continue;
            var type = Identifier.tryParse(data.getStringOr("id", ""));
            if (type == null || !type.getNamespace().equals("create")) continue;
            if (type.getPath().equals("smart_fluid_pipe")
                    && data.get("Filter") instanceof CompoundTag filter && filter.contains("Count")) {
                var converted = stack(filter, registries);
                // FluidFilter.save omits empty samples; load uses the required ItemStack codec.
                if (converted.isEmpty()) data.remove("Filter");
                else data.put("Filter", converted);
            } else stackField(data, "Filter", registries);
            if (data.get("Filters") instanceof ListTag filters)
                for (Tag value : filters) stackField(asCompound(value, "Ponder sided filter"), "Filter", registries);
            switch (type.getPath()) {
                case "copycat", "water_wheel", "large_water_wheel" -> {
                    if (data.get("Material") instanceof CompoundTag material && material.contains("Name"))
                        data.put("Material", material(material, sourceVersion, registries));
                    if (type.getPath().equals("copycat")) stackField(data, "Item", registries);
                }
                case "chute", "smart_chute", "placard" -> stackField(data, "Item", registries);
                case "mechanical_arm", "deployer" -> stackField(data, "HeldItem", registries);
                case "depot" -> {
                    if (data.get("HeldItem") instanceof CompoundTag transported)
                        stackField(transported, "Item", registries);
                }
                case "brass_tunnel" -> stackField(data, "StackToDistribute", registries);
                case "track" -> curvePositions(data, registries);
                case "redstone_link" -> {
                    stackField(data, "FrequencyFirst", registries);
                    stackField(data, "FrequencyLast", registries);
                    panelReferences(data, "LinkedGauges", false, registries);
                }
                case "display_link" -> panelReferences(data, "LinkedGauges", false, registries);
                case "factory_panel" -> {
                    for (String position : List.of("top_left", "top_right", "bottom_left", "bottom_right"))
                        if (data.get(position) instanceof CompoundTag panel) {
                            stackField(panel, "Filter", registries);
                            panelReferences(panel, "Targeting", false, registries);
                            panelReferences(panel, "TargetedBy", true, registries);
                            panelReferences(panel, "TargetedByLinks", true, registries);
                        }
                }
                case "toolbox" -> {
                    if (data.get("Inventory") instanceof CompoundTag inventory
                            && inventory.get("Compartments") instanceof ListTag compartments) {
                        var next = new ListTag();
                        for (Tag value : compartments) next.add(stack(asCompound(value, "Ponder toolbox compartment"), registries));
                        inventory.put("Compartments", next);
                    }
                }
                case "mechanical_crafter" -> crafter(data, registries);
                case "belt" -> {
                    if (data.get("Dye") instanceof StringTag && string(data, "Dye").equals(string(data, "Dye").toUpperCase(java.util.Locale.ROOT)))
                        data.put("Dye", encode(DyeColor.CODEC, DyeColor.valueOf(string(data, "Dye")), registries));
                }
                case "basin" -> {
                    if (data.get("PreferredSpoutput") instanceof StringTag && string(data, "PreferredSpoutput").equals(string(data, "PreferredSpoutput").toUpperCase(java.util.Locale.ROOT)))
                        data.put("PreferredSpoutput", encode(Direction.CODEC, Direction.valueOf(string(data, "PreferredSpoutput")), registries));
                }
                case "schematicannon" -> {
                    if (data.get("Options") instanceof CompoundTag options && options.contains("ReplaceMode")) {
                        keys(options, "legacy Ponder cannon options", "ReplaceMode", "ReplaceTileEntities", "SkipMissing");
                        data.put("Options", encode(SchematicannonOptions.CODEC,
                            new SchematicannonOptions(integer(options, "ReplaceMode"), bool(options, "SkipMissing"), bool(options, "ReplaceTileEntities")), registries));
                    }
                }
                case "redstone_requester" -> {
                    if (data.get("EncodedRequest") instanceof CompoundTag request && request.contains("Entries")) {
                        keys(request, "legacy Ponder request", "Entries");
                        if (!list(request, "Entries").isEmpty()) throw invalid("Unreviewed nonempty legacy Ponder request entries");
                        data.put("EncodedRequest", encode(PackageOrderWithCrafts.CODEC, PackageOrderWithCrafts.empty(), registries));
                    }
                }
                case "packager", "repackager" -> {
                    stackField(data, "HeldBox", registries);
                    stackField(data, "InsertedBox", registries);
                    if (data.get("LastSummary") instanceof CompoundTag summary && summary.contains("List")) {
                        keys(summary, "legacy Ponder inventory summary", "List");
                        if (!list(summary, "List").isEmpty()) throw invalid("Unreviewed nonempty legacy Ponder inventory summary");
                        data.put("LastSummary", encode(InventorySummary.CODEC, new InventorySummary(), registries));
                    }
                }
                default -> { }
            }
        }
    }

    /** Historical asset references keep their absolute coordinates; source scenes control later graph edits. */
    private static void panelReferences(CompoundTag parent, String field, boolean connections,
            HolderLookup.Provider registries) {
        if (!(parent.get(field) instanceof ListTag entries) || entries.isEmpty()) return;
        var next = new ListTag(); boolean changed = false;
        for (Tag value : entries) {
            if (!(value instanceof CompoundTag entry)
                    || !(entry.contains("X") || entry.contains("Y") || entry.contains("Z") || entry.contains("Slot")
                        || connections && (entry.contains("Amount") || entry.contains("ArrowBending")))) {
                next.add(value.copy());
                continue;
            }
            if (connections) keys(entry, "legacy Ponder panel connection", "X", "Y", "Z", "Slot", "Amount", "ArrowBending");
            else keys(entry, "legacy Ponder panel position", "X", "Y", "Z", "Slot");
            int slot = integer(entry, "Slot"); var slots = PanelSlot.values();
            if (slot < 0 || slot >= slots.length) throw invalid("Invalid legacy Ponder panel slot");
            var position = new FactoryPanelPosition(
                new BlockPos(integer(entry, "X"), integer(entry, "Y"), integer(entry, "Z")), slots[slot]);
            next.add(connections
                ? encode(FactoryPanelConnection.CODEC,
                    new FactoryPanelConnection(position, integer(entry, "Amount"), integer(entry, "ArrowBending")), registries)
                : encode(FactoryPanelPosition.CODEC, position, registries));
            changed = true;
        }
        if (changed) parent.put(field, next);
    }

    /** Pre-Pos-wrapper curve records store the exact same relative endpoint directly as XYZ. */
    private static void curvePositions(CompoundTag data, HolderLookup.Provider registries) {
        if (!(data.get("Connections") instanceof ListTag connections)) return;
        for (Tag value : connections) {
            var curve = asCompound(value, "Ponder track curve");
            if (!(curve.get("Positions") instanceof ListTag positions) || positions.isEmpty()
                    || !(positions.get(0) instanceof CompoundTag first) || !first.contains("X")) continue;
            if (positions.size() != 2) throw invalid("Ponder track curve requires two endpoints");
            var wrapped = new ListTag();
            for (Tag endpoint : positions) {
                var entry = new CompoundTag();
                entry.put("Pos", encode(BlockPos.CODEC, pos(asCompound(endpoint, "Ponder curve endpoint")), registries));
                wrapped.add(entry);
            }
            // Starts, axes, normals, primary/girder/material and smoothing remain unchanged.
            curve.put("Positions", wrapped);
        }
    }

    private static void crafter(CompoundTag data, HolderLookup.Provider registries) {
        if (data.get("GroupedItems") instanceof CompoundTag grouped && grouped.contains("Grid")) {
            keys(grouped, "legacy Ponder grouped items", "Grid");
            var cells = new HashSet<String>(); var next = new ListTag();
            for (Tag value : list(grouped, "Grid")) {
                var old = asCompound(value, "Ponder crafter grid cell");
                keys(old, "Ponder crafter grid cell", "x", "y", "item");
                if (!cells.add(integer(old, "x") + "/" + integer(old, "y"))) throw invalid("Duplicate Ponder crafter grid cell");
                var cell = old.copy(); cell.put("item", stack(compound(old, "item"), registries)); next.add(cell);
            }
            data.put("GroupedItems", next);
        }
        if (data.get("ConnectedInput") instanceof CompoundTag input) {
            // Same exact source ownership semantics as NeoForgeRemainingBlockEntityImport,
            // without pretending the whole asset has that importer's pinned DataVersion.
            keys(input, "legacy Ponder connected input", "Controller", "Data");
            boolean root = bool(input, "Controller"); var seen = new HashSet<BlockPos>(); var positions = new ListTag();
            for (Tag value : list(input, "Data")) {
                var offset = pos(asCompound(value, "Ponder crafter input offset"));
                if (!seen.add(offset)) throw invalid("Duplicate Ponder connected-input offset");
                positions.add(encode(BlockPos.CODEC, offset, registries));
            }
            if (positions.isEmpty() || root && !seen.contains(BlockPos.ZERO) || !root && (positions.size() != 1 || seen.contains(BlockPos.ZERO)))
                throw invalid("Invalid original Ponder crafter input owner map");
            if (data.contains("OriginalConnectedInput")) throw invalid("Mixed original and native Ponder input ownership");
            var original = new CompoundTag(); original.putBoolean("Controller", root); original.put("Data", positions);
            data.put("OriginalConnectedInput", original); data.remove("ConnectedInput");
        }
    }

    private static void stackField(CompoundTag parent, String field, HolderLookup.Provider registries) {
        if (parent.get(field) instanceof CompoundTag source && source.contains("Count")) parent.put(field, stack(source, registries));
    }

    /** Count/id is a verified field schema, independent of the containing template's DataVersion. */
    private static CompoundTag stack(CompoundTag source, HolderLookup.Provider registries) {
        if (!source.contains("Count")) return source.copy();
        keys(source, "legacy Ponder item stack", "id", "Count", "tag");
        if (!(source.get("Count") instanceof ByteTag countTag)) throw invalid("Ponder legacy Count must be a byte");
        int count = countTag.byteValue();
        var id = Identifier.parse(string(source, "id"));
        if (count < 0) throw invalid("Negative Ponder item stack count");
        if (id.equals(Identifier.parse("minecraft:air")) || count == 0)
            return asCompound(encode(ItemStack.OPTIONAL_CODEC, ItemStack.EMPTY, registries), "empty Ponder item stack");
        var projected = new CompoundTag(); projected.putString("id", id.toString()); projected.putInt("count", count);
        if (source.contains("tag")) {
            var old = compound(source, "tag"); keys(old, "legacy Ponder item tag", "Damage");
            var components = new CompoundTag(); components.putInt("minecraft:damage", integer(old, "Damage")); projected.put("components", components);
        }
        var item = ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), projected).getOrThrow();
        if (item.isEmpty() || item.getCount() != count || !BuiltInRegistries.ITEM.getKey(item.getItem()).equals(id))
            throw invalid("Ponder item projection changed item identity or count");
        return asCompound(encode(ItemStack.OPTIONAL_CODEC, item, registries), "native Ponder item stack");
    }

    private static Tag material(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) {
        keys(source, "Ponder material", "Name", "Properties");
        var id = Identifier.parse(string(source, "Name"));
        Tag fixed = DataFixers.getDataFixer().update(References.BLOCK_STATE, new Dynamic<Tag>(NbtOps.INSTANCE, source.copy()),
            sourceVersion, SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        var modern = asCompound(fixed, "fixed Ponder material"); keys(modern, "fixed Ponder material", "id", "properties");
        if (!Identifier.parse(string(modern, "id")).equals(id) || !BuiltInRegistries.BLOCK.containsKey(id))
            throw invalid("Ponder material identity was changed or is unavailable: " + id);
        var state = BlockState.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), modern).getOrThrow();
        if (state.getBlock() != BuiltInRegistries.BLOCK.getValue(id)) throw invalid("Ponder material codec changed block identity");
        if (source.contains("Properties")) for (String name : compound(source, "Properties").keySet()) {
            String value = string(compound(source, "Properties"), name);
            var property = state.getBlock().getStateDefinition().getProperty(name);
            if (property == null || property.getValue(value).isEmpty()
                    || !state.getValues().anyMatch(entry -> entry.property() == property && entry.valueName().equals(value)))
                throw invalid("Ponder material property was lost: " + id + "[" + name + "=" + value + "]");
        }
        return encode(BlockState.CODEC, state, registries);
    }

    private static <T> Tag encode(Codec<T> codec, T value, HolderLookup.Provider registries) {
        return codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow();
    }
    private static CompoundTag asCompound(Tag value, String context) {
        if (!(value instanceof CompoundTag compound)) throw invalid("Malformed " + context);
        return compound;
    }
    private PonderLegacyBlockEntityData() { }
}
