package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.state.BlockState;

/** Original Name/Properties through actual vanilla BLOCK_STATE DFU, with registry/property loss checks. */
public final class NeoForgeBlockStateImport {
    public static Tag convert(CompoundTag source, int version, HolderLookup.Provider registries) {
        if (version != NeoForgeSavedDataImport.SOURCE_DATA_VERSION) throw invalid("Unknown original block state version");
        keys(source, "block state", "Name", "Properties"); string(source, "Name");
        if (source.contains("Properties")) for (String key : compound(source, "Properties").keySet()) string(compound(source, "Properties"), key);
        Tag fixed = DataFixers.getDataFixer().update(References.BLOCK_STATE, new Dynamic<Tag>(NbtOps.INSTANCE, source.copy()), version,
            SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        if (!(fixed instanceof CompoundTag modern)) throw invalid("Vanilla fixer produced a noncompound block state");
        keys(modern, "fixed block state", "id", "properties"); var id = Identifier.parse(string(modern, "id"));
        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw invalid("Original block state needs unavailable block " + id);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var state = BlockState.CODEC.parse(ops, modern).getOrThrow();
        if (state.getBlock() != BuiltInRegistries.BLOCK.getValue(id)) throw invalid("Native block-state codec changed original block identity");
        if (modern.contains("properties")) for (String name : compound(modern, "properties").keySet()) {
            String requested = string(compound(modern, "properties"), name);
            var property = state.getBlock().getStateDefinition().getProperty(name);
            if (property == null || property.getValue(requested).isEmpty()) throw invalid("Unknown original block property " + name);
            boolean retained = state.getValues().anyMatch(value -> value.property() == property && value.valueName().equals(requested));
            if (!retained) throw invalid("Unrepresentable original block property " + name);
        }
        // The real current codec compactly encodes a default state as a StringTag.
        // Nondefault states may be compounds. Keep its genuine accepted representation.
        return BlockState.CODEC.encodeStart(ops, state).getOrThrow();
    }
    private NeoForgeBlockStateImport() { }
}
