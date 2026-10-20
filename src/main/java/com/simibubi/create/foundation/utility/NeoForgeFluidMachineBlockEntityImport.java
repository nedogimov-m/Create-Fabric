package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

/** Pinned original actual tanks/open-end buffers are stock; pipe flow samples and search geometry are metadata. */
public final class NeoForgeFluidMachineBlockEntityImport {
    private static final Set<String> TYPES = Set.of("hose_pulley", "spout", "mechanical_pump", "smart_fluid_pipe", "fluid_pipe", "encased_fluid_pipe", "glass_fluid_pipe", "fluid_valve");
    public static Optional<CompoundTag> convert(Identifier type, CompoundTag source, int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        Objects.requireNonNull(type, "type");
        if (!type.getNamespace().equals("create") || !TYPES.contains(type.getPath())) return Optional.empty();
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original fluid machine version");
        var context = new Context(version, Objects.requireNonNull(registries, "registries"), scope); var result = Objects.requireNonNull(source, "source").copy();
        switch (type.getPath()) {
            case "hose_pulley" -> {
                float offset = lerped(compound(source, "Offset"));
                if (offset < 0 || offset > Integer.MAX_VALUE - 1.0) throw invalid("Unrepresentable original hose extension");
                result.put("Tank", NeoForgeFluidStackImport.tank(compound(source, "Tank"), version, registries, child(scope, "tank", 0)).slot(registries));
                // The original constructor restores this transient to true, requiring a real first native tick.
                result.putBoolean("Moving", true);
                var search = new CompoundTag();
                if (source.contains("Infinite") && !bool(source, "Infinite")) throw invalid("Original search infinity marker is only written true");
                search.putBoolean("Infinite", source.contains("Infinite"));
                for (String field : Set.of("LastPos", "AffectedAreaFrom", "AffectedAreaTo")) if (source.contains(field)) {
                    search.put(field, context.encode(BlockPos.CODEC, pos(compound(source, field)))); result.remove(field);
                }
                if (source.contains("AffectedAreaFrom") != source.contains("AffectedAreaTo")) throw invalid("Incomplete original fluid search bounds");
                // Both genuine original behaviours read the same shared root fields. Neither stores physical fluid.
                result.put("Draining", search.copy()); result.put("Filling", search.copy()); result.remove("Infinite");
            }
            case "spout" -> {
                var tanks = list(source, "Tanks"); if (tanks.size() != 1 || !(tanks.get(0) instanceof CompoundTag segment)) throw invalid("Original spout must have one actual tank segment");
                keys(segment, "spout tank segment", "TankContent", "Level"); lerped(compound(segment, "Level"));
                result.put("Tank", NeoForgeFluidStackImport.tank(compound(segment, "TankContent"), version, registries, child(scope, "tank_segment", 0)).slot(registries)); result.remove("Tanks");
                int ticks = integer(source, "ProcessingTicks"); result.putBoolean("OriginalProcessing", ticks >= 0);
                for (String field : Set.of("ChocolateBerries", "HoneyApple", "SweetRoll"))
                    if (source.contains(field) && !bool(source, field)) throw invalid("Original food advancement marker is only written true");
                // There is no original ProcessingInput/Target/Pending output receipt. Real loaded held stock supplies the handoff.
            }
            default -> {
                transport(source, result, context);
                if (source.contains("Bracket")) result.put("Bracket", NeoForgeBlockStateImport.convert(compound(source, "Bracket"), version, registries));
                if (type.getPath().equals("mechanical_pump") && source.contains("Reversed")) bool(source, "Reversed");
                if (type.getPath().equals("fluid_valve")) lerped(compound(source, "Pointer"));
            }
        }
        return Optional.of(result);
    }
    private record Context(int version, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        <T> Tag encode(Codec<T> codec, T value) { return codec.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), value).getOrThrow(); }
    }
    private static void transport(CompoundTag source, CompoundTag result, Context context) {
        var transport = new CompoundTag();
        for (Direction side : Direction.values()) if (source.contains(side.getName())) {
            var row = compound(source, side.getName()); keys(row, "pipe connection", "Pressure", "OpenEnd", "Flow"); var next = new CompoundTag();
            if (row.contains("Pressure")) {
                var pressures = list(row, "Pressure"); if (pressures.size() != 2) throw invalid("Original pipe pressure is an actual two-float couple");
                for (int index = 0; index < 2; index++) {
                    if (!(pressures.get(index) instanceof FloatTag value) || !Float.isFinite(value.floatValue()) || value.floatValue() < 0) throw invalid("Malformed original pipe pressure");
                    next.putFloat(index == 0 ? "Inbound" : "Outward", value.floatValue());
                }
            }
            if (row.contains("Flow")) {
                var flow = compound(row, "Flow"); keys(flow, "pipe flow sample", "Fluid", "In", "Progress"); var fluid = NeoForgeFluidStackImport.convert(compound(flow, "Fluid"), context.version, context.registries, child(context.scope, "pipe_flow", side.get3DDataValue()));
                var nextFlow = new CompoundTag(); nextFlow.put("Fluid", context.encode(FluidVariant.CODEC, fluid.variant())); nextFlow.putBoolean("In", bool(flow, "In"));
                nextFlow.putFloat("Progress", flow.contains("Progress") ? lerped(compound(flow, "Progress")) : 1); next.put("Flow", nextFlow);
            }
            if (row.contains("OpenEnd")) {
                var end = compound(row, "OpenEnd"); keys(end, "open pipe actual buffer", "Fluid", "Pulling", "Location"); bool(end, "Pulling");
                var location = compound(end, "Location"); keys(location, "open pipe source location", "Pos", "Face"); pos(compound(location, "Pos"));
                if (!string(location, "Face").equals(side.name())) throw invalid("Original open-end source face disagrees with its actual pipe connection");
                var wrapper = new CompoundTag(); if (end.contains("Fluid")) wrapper.put("Fluid", compound(end, "Fluid").copy());
                var fluid = NeoForgeFluidStackImport.tank(wrapper, context.version, context.registries, child(context.scope, "open_end", side.get3DDataValue())); var nextEnd = new CompoundTag();
                if (!fluid.variant().isBlank()) nextEnd.put("Fluid", context.encode(FluidVariant.CODEC, fluid.variant())); nextEnd.putLong("Amount", fluid.amount()); next.put("OpenEnd", nextEnd);
            }
            transport.put(side.getName(), next); result.remove(side.getName());
        }
        result.put("Transport", transport);
    }
    private static float lerped(CompoundTag source) {
        keys(source, "LerpedFloat", "Speed", "Target", "Value", "Force"); floating(source, "Speed"); floating(source, "Target");
        if (source.contains("Force")) bool(source, "Force"); return floating(source, "Value");
    }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope,String role,int index){return scope==null?null:scope.item(role,index);}
    private NeoForgeFluidMachineBlockEntityImport() { }
}
