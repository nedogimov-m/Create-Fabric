package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.kinetics.transmission.sequencer.Instruction;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;

/** Pinned original3955 machine metadata and actual fluid owners; shared base fields remain root-owned. */
public final class NeoForgeMachineBlockEntityImport {
    private static final Set<String> TYPES = Set.of("motor", "creative_fluid_tank", "fluid_tank", "mechanical_piston", "rope_pulley",
        "windmill_bearing", "mechanical_bearing", "clockwork_bearing", "cart_assembler", "chassis", "sequenced_gearshift",
        "rotation_speed_controller", "funnel", "redstone_link", "analog_lever", "pulse_repeater", "pulse_extender", "pulse_timer");

    public static Optional<CompoundTag> convert(Identifier type, CompoundTag source, int version,
                                               HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        Objects.requireNonNull(type, "sourceType");
        if (!type.getNamespace().equals("create") || !TYPES.contains(type.getPath())) return Optional.empty();
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unsupported original machine version " + version);
        var context = new Context(version, Objects.requireNonNull(registries, "registries"), scope);
        var result = Objects.requireNonNull(source, "source").copy();
        switch (type.getPath()) {
            case "motor" -> integer(source, "ScrollValue");
            case "rotation_speed_controller" -> integer(source, "ScrollValue");
            case "chassis" -> { if (integer(source, "ScrollValue") < 1) throw invalid("Invalid original chassis range"); }
            case "creative_fluid_tank", "fluid_tank" -> tank(source, result, context);
            case "mechanical_bearing", "windmill_bearing" -> {
                bool(source, "Running"); floating(source, "Angle"); optionalLimit(source, "SequencedAngleLimit");
                if (type.getPath().equals("windmill_bearing")) {
                    range(integer(source, "ScrollValue"), 0, 1, "windmill rotation direction");
                    floating(source, "LastGenerated"); bool(source, "QueueAssembly");
                } else {
                    int mode = integer(source, "ScrollValue"); range(mode, 0, 2, "bearing movement mode"); result.putInt("MovementMode", mode);
                }
                assemblyError(source, result, context);
                // Original running bodies reconnect through actual loaded controlled entities.
                // No MovedContraption UUID, graph receipt or completion flag is manufactured here.
            }
            case "clockwork_bearing" -> {
                bool(source, "Running"); floating(source, "HourAngle"); floating(source, "MinuteAngle");
                range(integer(source, "ScrollValue"), 0, 2, "clockwork hand mode"); assemblyError(source, result, context);
            }
            case "mechanical_piston" -> {
                int length = integer(source, "ExtensionLength"); float offset = floating(source, "Offset");
                if (length < 0 || offset < 0 || offset > length) throw invalid("Original piston progress exceeds its saved actual extension geometry");
                bool(source, "Running"); bool(source, "Waiting"); optionalLimit(source, "SequencedOffsetLimit");
                range(integer(source, "ScrollValue"), 0, 2, "linear movement mode"); assemblyError(source, result, context);
            }
            case "rope_pulley" -> {
                int initial = integer(source, "InitialOffset"); float offset = floating(source, "Offset");
                if (initial < 0 || offset < 0 || offset > Integer.MAX_VALUE - 1.0) throw invalid("Unrepresentable original rope geometry");
                bool(source, "Running"); bool(source, "Waiting"); optionalLimit(source, "SequencedOffsetLimit");
                range(integer(source, "ScrollValue"), 0, 2, "linear movement mode"); assemblyError(source, result, context);
                if (source.contains("MirrorParent")) {
                    result.put("OriginalMirrorParent", context.encode(BlockPos.CODEC, pos(compound(source, "MirrorParent"))));
                    result.remove("MirrorParent"); result.putFloat("Offset", 0); // Actual original reader resets passive mirrors.
                }
                if (source.contains("MirrorChildren")) {
                    var children = new java.util.LinkedHashSet<BlockPos>();
                    for (Tag child : list(source, "MirrorChildren")) {
                        var row = asCompound(child, "pulley mirror child"); keys(row, "pulley mirror child", "Pos");
                        children.add(pos(compound(row, "Pos")));
                    }
                    result.put("OriginalMirrorChildren", context.encode(BlockPos.CODEC.listOf(), java.util.List.copyOf(children)));
                    result.remove("MirrorChildren");
                }
            }
            case "cart_assembler" -> { range(integer(source, "ScrollValue"), 0, 2, "cart movement mode"); assemblyError(source, result, context); }
            case "sequenced_gearshift" -> {
                var instructions = list(source, "Instructions");
                for (Tag value : instructions) {
                    var row = asCompound(value, "sequencer instruction"); keys(row, "sequencer instruction", "Type", "Modifier", "Value");
                    string(row, "Type"); string(row, "Modifier"); integer(row, "Value");
                }
                result.put("Instructions", instructions.isEmpty() ? context.encode(Instruction.LIST_CODEC, Instruction.createDefault()) : context.canonical(Instruction.LIST_CODEC, instructions));
                integer(source, "InstructionIndex"); integer(source, "InstructionDuration"); floating(source, "InstructionProgress");
                integer(source, "Timer"); bool(source, "PrevPowered");
            }
            case "funnel" -> {
                // Every negative original cooldown has the same source ready-to-transfer meaning.
                result.putInt("TransferCooldown", Math.max(0, integer(source, "TransferCooldown")));
            }
            case "redstone_link" -> {
                bool(source, "Transmitter"); bool(source, "ReceivedChanged");
                range(integer(source, "Transmit"), 0, 15, "link transmit strength"); range(integer(source, "Receive"), 0, 15, "link receive strength");
            }
            case "analog_lever" -> {
                range(integer(source, "State"), 0, 15, "analogue lever state"); range(integer(source, "ChangeTimer"), 0, 15, "analogue lever animation countdown");
            }
            case "pulse_extender", "pulse_repeater", "pulse_timer" -> {
                integer(source, "ScrollValue"); integer(source, "State");
            }
            default -> throw new AssertionError(type);
        }
        return Optional.of(result);
    }

    /** Original ordinary fluid-tank writeSafe persists geometry/window only, without fluid stock. */
    public static Optional<CompoundTag> convertSafe(Identifier type, CompoundTag source, int version,
                                                   HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        Objects.requireNonNull(type, "sourceType");
        if (!type.equals(Identifier.parse("create:fluid_tank"))) return Optional.empty();
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unsupported original fluid-tank safe version");
        var result = source.copy();
        if (source.contains("TankContent") || source.contains("Boiler") || source.contains("Controller"))
            throw invalid("Original partial fluid-tank safe writer cannot supply full physical storage/boiler owners");
        if (source.contains("Size") || source.contains("Height") || source.contains("Window")) {
            dimensions(source); bool(source, "Window");
        }
        return Optional.of(result);
    }

    private record Context(int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        <T> T decode(Codec<T> codec, Tag value) { return codec.parse(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow(); }
        <T> Tag encode(Codec<T> codec, T value) { return codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow(); }
        <T> Tag canonical(Codec<T> codec, Tag value) { return encode(codec, decode(codec, value)); }
    }
    private static void tank(CompoundTag source, CompoundTag result, Context context) {
        range(integer(source, "Luminosity"), 0, 15, "tank luminosity");
        // Absence of this actual source marker means initialized, not the native default true.
        if (source.contains("Uninitialized") && !bool(source, "Uninitialized")) throw invalid("Original tank uninitialized marker is only written true");
        result.putBoolean("Uninitialized", source.contains("Uninitialized"));
        for (String field : Set.of("Controller", "LastKnownPos")) if (source.contains(field))
            result.put(field, context.encode(BlockPos.CODEC, pos(compound(source, field))));
        if (source.contains("Controller")) {
            if (source.contains("TankContent") || source.contains("Size") || source.contains("Height") || source.contains("Window"))
                throw invalid("Original noncontroller tank cannot also own aggregate fluid and geometry");
        } else {
            dimensions(source); bool(source, "Window");
            result.put("TankContent", NeoForgeFluidStackImport.tank(compound(source, "TankContent"), context.version, context.registries, child(context.scope, "tank_content", 0)).slot(context.registries));
        }
        var boiler = compound(source, "Boiler"); keys(boiler, "boiler data", "Supply", "ActiveHeat", "PassiveHeat", "Engines", "Whistles", "Update");
        // Supply is already source millibuckets/tick in BOTH actual serializers, not stored fluid.
        if (floating(boiler, "Supply") < 0 || integer(boiler, "Engines") < 0 || integer(boiler, "Whistles") < 0)
            throw invalid("Negative original boiler supply/attachment count");
        if (integer(boiler, "ActiveHeat") < 0) throw invalid("Negative original boiler heat level");
        bool(boiler, "PassiveHeat"); bool(boiler, "Update");
    }
    private static void dimensions(CompoundTag source) {
        int width = integer(source, "Size"), height = integer(source, "Height");
        if (width < 1 || width > 3 || height < 1) throw invalid("Invalid original fluid tank dimensions");
        try { Math.multiplyExact(width * width, height); }
        catch (ArithmeticException overflow) { throw invalid("Original tank aggregate geometry overflows its actual native integer dimensions"); }
    }
    private static void assemblyError(CompoundTag source, CompoundTag result, Context context) {
        if (!source.contains("LastException")) return;
        var original = compound(source, "LastException"); keys(original, "assembly exception", "Component", "Position");
        Tag fixed = NeoForgeTextComponentImport.flattened(StringTag.valueOf(string(original, "Component")), context.version, context.registries, child(context.scope, "assembly_exception_text", 0));
        var component = context.decode(ComponentSerialization.CODEC, fixed); var next = new CompoundTag();
        next.putString("Component", ComponentSerialization.CODEC.encodeStart(context.registries.createSerializationContext(JsonOps.INSTANCE), component).getOrThrow().toString());
        if (original.contains("Position")) {
            if (!(original.get("Position") instanceof LongTag)) throw invalid("Original assembly exception position is not its genuine packed long");
            next.put("Position", original.get("Position").copy());
        }
        var wrapper = new CompoundTag(); wrapper.put("LastException", next); result.put("AssemblyError", wrapper); result.remove("LastException");
    }
    private static void optionalLimit(CompoundTag source, String field) {
        if (source.contains(field) && number(source, field) < 0) throw invalid("Negative original optional movement limit " + field);
    }
    private static CompoundTag asCompound(Tag value, String context) {
        if (!(value instanceof CompoundTag compound)) throw invalid("Malformed original " + context + " compound"); return compound;
    }
    private static void range(double value, double min, double max, String context) {
        if (!Double.isFinite(value) || value < min || value > max) throw invalid("Unrepresentable original " + context);
    }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope,String role,int index){return scope==null?null:scope.item(role,index);}
    private NeoForgeMachineBlockEntityImport() { }
}
