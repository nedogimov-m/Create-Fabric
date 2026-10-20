package com.simibubi.create.content.trains.schedule;

import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Strict original schedule/runtime schemas; unknown native fallbacks cannot replace instructions. */
public final class NeoForgeTrainScheduleImport {
    public static CompoundTag schedule(CompoundTag source, int sourceVersion, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        version(sourceVersion);
        keys(source, "train schedule", "Entries", "Cyclic", "Progress");
        bool(source, "Cyclic");
        if (source.contains("Progress") && integer(source, "Progress") <= 0)
            throw invalid("Original schedule progress marker must be positive");
        var result = source.copy(); var entries = new ListTag();
        for (Tag value : list(source, "Entries")) {
            if (!(value instanceof CompoundTag entry)) throw invalid("Malformed original schedule entry");
            keys(entry, "schedule entry", "Instruction", "Conditions");
            var instruction = operation(compound(entry, "Instruction"), true, sourceVersion, registries, child(child(scope, "entry", entries.size()), "instruction", 0));
            var actualInstruction = ScheduleInstruction.fromTag(registries, instruction);
            var next = new CompoundTag(); next.put("Instruction", instruction);
            if (actualInstruction.supportsConditions()) {
                var columns = new ListTag();
                for (Tag column : list(entry, "Conditions")) {
                    if (!(column instanceof ListTag conditions)) throw invalid("Original schedule condition column is not a list");
                    var converted = new ListTag();
                    for (Tag condition : conditions) {
                        if (!(condition instanceof CompoundTag saved)) throw invalid("Malformed original schedule condition");
                        converted.add(operation(saved, false, sourceVersion, registries, child(child(child(scope, "entry", entries.size()), "column", columns.size()), "condition", converted.size())));
                    }
                    columns.add(converted);
                }
                next.put("Conditions", columns);
            } else if (entry.contains("Conditions"))
                throw invalid("Original instruction does not own condition columns");
            entries.add(next);
        }
        result.put("Entries", entries);
        if (source.contains("Progress") && integer(source, "Progress") > entries.size())
            throw invalid("Original saved schedule progress exceeds its actual entries");
        var nativeSchedule = Schedule.fromTag(registries, result);
        // The genuine native reader/writer must retain the complete normalized source payload.
        if (!nativeSchedule.write(registries).equals(result))
            throw invalid("Native schedule decoding would normalize or drop original schedule data");
        return result;
    }

    public static CompoundTag runtime(CompoundTag source, int sourceVersion, HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        version(sourceVersion);
        keys(source, "schedule runtime", "CurrentEntry", "AutoSchedule", "Paused", "Completed", "Schedule",
            "State", "ConditionProgress", "ConditionContext", "TransitTimes");
        int entry = integer(source, "CurrentEntry");
        if (entry < 0) throw invalid("Negative original schedule runtime entry cannot be clamped");
        bool(source, "AutoSchedule"); bool(source, "Paused"); bool(source, "Completed");
        ScheduleRuntime.State.valueOf(string(source, "State"));
        int[] progress = ints(source, "ConditionProgress"), transits = ints(source, "TransitTimes");
        var contexts = list(source, "ConditionContext");
        if (progress.length != contexts.size()) throw invalid("Original runtime condition progress/context cardinality disagrees");
        for (int value : progress) if (value < 0) throw invalid("Negative original runtime condition progress");
        for (int transit : transits) if (transit < -1) throw invalid("Invalid original runtime transit prediction");
        for (Tag value : contexts) {
            if (!(value instanceof CompoundTag context)) throw invalid("Malformed original schedule condition context");
            keys(context, "schedule condition context", "Time", "LastChecked", "CurrentDisplay", "PrevPlayerCount", "StatusVersion");
            for (String key : context.keySet()) integer(context, key);
        }
        var result = source.copy();
        if (source.contains("Schedule")) {
            var actualSchedule = schedule(compound(source, "Schedule"), sourceVersion, registries, child(scope, "schedule", 0));
            int count = list(actualSchedule, "Entries").size();
            if (entry > count || transits.length != count)
                throw invalid("Original runtime entry/transit ownership disagrees with its actual schedule");
            result.put("Schedule", actualSchedule);
        } else if (transits.length != 0)
            throw invalid("Original runtime predictions cannot be detached from their absent schedule");
        return result;
    }

    private static CompoundTag operation(CompoundTag source, boolean instruction, int sourceVersion,
            HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        keys(source, "schedule operation", "Id", "Data");
        String id = string(source, "Id"); var data = compound(source, "Data"); var next = data.copy();
        if (instruction) {
            switch (id) {
                case "create:destination", "create:package_retrieval", "create:rename" -> {
                    keys(data, "text schedule instruction", "Text"); optionalString(data, "Text");
                }
                case "create:package_delivery" -> keys(data, "package delivery instruction");
                case "create:throttle" -> { keys(data, "throttle schedule instruction", "Value"); optionalInteger(data, "Value"); }
                default -> throw invalid("Unknown original schedule instruction/schema " + id);
            }
        } else {
            switch (id) {
                case "create:delay", "create:idle" -> {
                    keys(data, "timed schedule condition", "Value", "TimeUnit"); optionalInteger(data, "Value"); ordinal(data, "TimeUnit", 3);
                }
                case "create:time_of_day" -> {
                    keys(data, "time schedule condition", "Hour", "Minute", "Rotation");
                    optionalInteger(data, "Hour"); optionalInteger(data, "Minute"); ordinal(data, "Rotation", 10);
                }
                case "create:fluid_threshold", "create:item_threshold" -> {
                    String stack = id.equals("create:fluid_threshold") ? "Bucket" : "Item";
                    keys(data, "cargo threshold condition", "Operator", "Threshold", "Measure", stack);
                    ordinal(data, "Operator", 3); optionalInteger(data, "Measure");
                    if (data.contains("Threshold")) {
                        try { Integer.parseInt(string(data, "Threshold")); }
                        catch (NumberFormatException invalid) { throw invalid("Original cargo threshold cannot become an invented zero"); }
                    }
                    if (data.contains(stack)) next.put(stack, item(compound(data, stack), sourceVersion, registries, child(scope, stack.toLowerCase(java.util.Locale.ROOT), 0)));
                }
                case "create:redstone_link" -> {
                    keys(data, "redstone schedule condition", "Inverted", "Frequency"); optionalInteger(data, "Inverted");
                    if (data.contains("Frequency")) {
                        var original = list(data, "Frequency");
                        if (original.size() != 2) throw invalid("Original schedule link requires exactly two frequencies");
                        var frequencies = new ListTag();
                        for (Tag value : original) {
                            if (!(value instanceof CompoundTag stack)) throw invalid("Malformed original schedule frequency stack");
                            frequencies.add(item(stack, sourceVersion, registries, child(scope, "frequency", frequencies.size())));
                        }
                        next.put("Frequency", frequencies);
                    }
                }
                case "create:player_count" -> { keys(data, "player count condition", "Count", "Exact"); optionalInteger(data, "Count"); optionalInteger(data, "Exact"); }
                case "create:unloaded", "create:powered" -> keys(data, "station status condition");
                default -> throw invalid("Unknown original schedule condition/schema " + id);
            }
        }
        var result = new CompoundTag(); result.putString("Id", id); result.put("Data", next);
        CompoundTag encoded;
        if (instruction) encoded = ScheduleInstruction.fromTag(registries, result).write(registries);
        else {
            var condition = ScheduleWaitCondition.fromTag(registries, result);
            if (condition == null) throw invalid("Original condition has no actual native implementation");
            encoded = condition.write(registries);
        }
        if (!encoded.equals(result))
            throw invalid("Native operation reader/writer would normalize or drop original condition/instruction data " + id);
        return result;
    }

    private static void optionalInteger(CompoundTag data, String key) { if (data.contains(key)) integer(data, key); }
    private static void optionalString(CompoundTag data, String key) { if (data.contains(key)) string(data, key); }
    private static void ordinal(CompoundTag data, String key, int cardinality) {
        if (data.contains(key)) { int value = integer(data, key); if (value < 0 || value >= cardinality) throw invalid("Invalid original schedule enum ordinal " + key); }
    }
    private static int[] ints(CompoundTag data, String key) {
        if (!(data.get(key) instanceof IntArrayTag)) throw invalid("Malformed original schedule integer array " + key);
        return data.getIntArray(key).orElseThrow();
    }
    private static void version(int sourceVersion) {
        if (sourceVersion != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original train schedule data version");
    }
    private NeoForgeTrainScheduleImport() {}
    public static CompoundTag schedule(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) { return schedule(source, sourceVersion, registries, null); }
    public static CompoundTag runtime(CompoundTag source, int sourceVersion, HolderLookup.Provider registries) { return runtime(source, sourceVersion, registries, null); }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope, String role, int index) { return scope == null ? null : scope.item(role, index); }

}
