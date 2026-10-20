package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** Typed original actor rows, including their real original local position and behaviour-owned data. */
public final class NeoForgeMovementContextImport {
    public static CompoundTag convert(BlockState actualState, CompoundTag source, int version,
                                      HolderLookup.Provider registries, NeoForgeMigrationScope scope) {
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original actor version");
        keys(source, "actor row", "Pos", "Motion", "RelativeMotion", "Position", "Stall", "FirstMovement", "Data");
        var id = BuiltInRegistries.BLOCK.getKey(actualState.getBlock());
        var behaviour = com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(actualState);
        if (behaviour == null || !id.getNamespace().equals("create") && !id.equals(net.minecraft.resources.Identifier.withDefaultNamespace("dispenser")))
            throw invalid("Original actor has no reviewed behaviour: " + id);
        var result = source.copy();
        result.store("Pos", BlockPos.CODEC, pos(compound(source, "Pos")));
        result.put("Motion", vector(list(source, "Motion")));
        result.put("RelativeMotion", vector(list(source, "RelativeMotion")));
        if (source.contains("Position")) result.put("Position", vector(list(source, "Position")));
        bool(source, "Stall"); bool(source, "FirstMovement");
        var data = compound(source, "Data"); var next = data.copy();
        var allowed = new HashSet<String>(Set.of("StationaryTimer"));
        var positions = new HashSet<String>();
        switch (id.getPath()) {
            case "mechanical_drill", "mechanical_saw", "mechanical_harvester", "mechanical_plough", "mechanical_roller" -> {
                allowed.addAll(Set.of("BreakerId", "Progress", "TicksUntilNextProgress", "WaitingTicks"));
                positions.addAll(Set.of("BreakingPos", "LastPos"));
                if (id.getPath().equals("mechanical_roller")) positions.add("ReferencePos");
            }
            case "deployer" -> allowed.addAll(Set.of("HeldItem", "Timer"));
            case "portable_storage_interface", "portable_fluid_interface" -> positions.addAll(Set.of("WorkingPos", "ClientPrevPos"));
            case "redstone_contact", "elevator_contact" -> positions.add("lastContact");
            case "blaze_burner" -> allowed.add("Conductor");
            default -> {
                if (behaviour instanceof com.simibubi.create.content.contraptions.actors.seat.SeatMovementBehaviour) allowed.add("SeatIndex");
                else if (behaviour instanceof com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour) allowed.add("Open");
                // Other pinned behaviours persist no own Data fields. Unknown fields are refused below.
            }
        }
        allowed.addAll(positions);
        keys(data, "actor " + id + " Data", allowed.toArray(String[]::new));
        for (String key : positions) if (data.contains(key)) next.store(key, BlockPos.CODEC, pos(compound(data, key)));
        for (String key : Set.of("StationaryTimer", "BreakerId", "Progress", "TicksUntilNextProgress", "WaitingTicks", "SeatIndex", "Timer"))
            if (data.contains(key)) integer(data, key);
        for (String key : Set.of("Open", "Conductor")) if (data.contains(key)) bool(data, key);
        if (data.contains("HeldItem")) next.put("HeldItem", item(compound(data, "HeldItem"), version, registries, scope == null ? null : scope.item("actor_held_item", 0)));
        if (data.contains("Progress") && (integer(data, "Progress") < 0 || integer(data, "Progress") > 9))
            throw invalid("Original actor breaking progress is outside native range");
        result.put("Data", next); return result;
    }
    public static CompoundTag convert(BlockState state, CompoundTag source, int version, HolderLookup.Provider registries) {
        return convert(state, source, version, registries, null);
    }
    private NeoForgeMovementContextImport() {}
}
