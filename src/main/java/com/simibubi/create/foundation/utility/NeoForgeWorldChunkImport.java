package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.content.contraptions.NeoForgeStandaloneEntityImport;
import java.util.*;
import java.util.function.UnaryOperator;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

/** Original level-chunk owners are converted off-world, before the native parser/factories. */
public final class NeoForgeWorldChunkImport {
    private static final Set<String> STATUSES = Set.of("empty", "structure_starts", "structure_references", "biomes", "noise", "surface", "carvers", "features", "initialize_light", "light", "spawn", "full");
    private record Section(CompoundTag blockStates, List<BlockState> palette, SimpleBitStorage data) {
        BlockState at(BlockPos absolute) {
            int index = (Math.floorMod(absolute.getY(), 16) << 8) | (Math.floorMod(absolute.getZ(), 16) << 4) | Math.floorMod(absolute.getX(), 16);
            int state = data == null ? 0 : data.get(index);
            if (state < 0 || state >= palette.size()) throw invalid("Original terrain references an absent palette state");
            return palette.get(state);
        }
    }
    public static CompoundTag convert(CompoundTag original, ChunkPos requested, ServerLevel actualLevel,
                                      UnaryOperator<CompoundTag> actualNativeUpgrade) {
        Objects.requireNonNull(actualNativeUpgrade);
        if (!(original.get("DataVersion") instanceof IntTag version) || version.intValue() != 3955)
            throw invalid("Level chunk migration requires its genuine original3955 envelope");
        if (!(original.get("xPos") instanceof IntTag x) || !(original.get("zPos") instanceof IntTag z)
                || !requested.equals(new ChunkPos(x.intValue(), z.intValue())))
            throw invalid("Original level-chunk coordinates disagree with its actual requested native record");
        var status = Identifier.parse(string(original, "Status"));
        if (!status.getNamespace().equals("minecraft") || !STATUSES.contains(status.getPath()))
            throw invalid("Unknown original terrain generation status cannot become an empty chunk");
        var registries = actualLevel.registryAccess(); var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        var sections = new HashMap<Integer, Section>(); var allSections = new HashSet<Integer>();
        var originalSections = original.contains("sections") ? list(original, "sections") : new ListTag();
        if (originalSections.size() > 256) throw invalid("Original terrain has more than its byte-addressed section domain");
        for (Tag value : originalSections) {
            if (!(value instanceof CompoundTag section) || !(section.get("Y") instanceof ByteTag y))
                throw invalid("Original section has no genuine signed-byte vertical address");
            int sectionY = y.byteValue(); if (!allSections.add(sectionY)) throw invalid("Duplicate actual original terrain section");
            if (!section.contains("block_states")) continue;
            var sourceStates = compound(section, "block_states"); keys(sourceStates, "terrain block states", "palette", "data");
            var sourcePalette = list(sourceStates, "palette");
            if (sourcePalette.isEmpty() || sourcePalette.size() > 4096) throw invalid("Original block palette exceeds its actual section extent");
            var palette = new ArrayList<BlockState>(); var nativePalette = new ListTag();
            for (Tag entry : sourcePalette) {
                if (!(entry instanceof CompoundTag state)) throw invalid("Malformed original terrain block state");
                var converted = NeoForgeBlockStateImport.convert(state, 3955, registries);
                var actual = BlockState.CODEC.parse(ops, converted).getOrThrow(); palette.add(actual);
                nativePalette.add(NbtUtils.writeBlockState(actual));
            }
            SimpleBitStorage data = null;
            if (palette.size() > 1) {
                if (!(sourceStates.get("data") instanceof LongArrayTag packed)) throw invalid("Original multi-state section lost its real packed block addresses");
                int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
                data = new SimpleBitStorage(bits, 4096, packed.getAsLongArray());
                for (int i = 0; i < 4096; i++) if (data.get(i) >= palette.size()) throw invalid("Original terrain palette index lies outside its actual palette");
            } else if (sourceStates.contains("data") && (!(sourceStates.get("data") instanceof LongArrayTag packed) || packed.getAsLongArray().length != 0))
                throw invalid("Original singleton palette has incompatible packed data");
            var nativeStates = sourceStates.copy(); nativeStates.put("palette", nativePalette);
            sections.put(sectionY, new Section(nativeStates, List.copyOf(palette), data));
        }
        var blockEntities = new ListTag(); var positions = new HashSet<BlockPos>();
        var sourceBEs = original.contains("block_entities") ? list(original, "block_entities") : new ListTag();
        if (sourceBEs.size() > originalSections.size() * 4096) throw invalid("Original block-entity count exceeds its real section positions");
        for (Tag entry : sourceBEs) {
            if (!(entry instanceof CompoundTag source) || !(source.get("x") instanceof IntTag bx)
                    || !(source.get("y") instanceof IntTag by) || !(source.get("z") instanceof IntTag bz))
                throw invalid("Original world block entity lost its actual integer coordinates");
            var position = new BlockPos(bx.intValue(), by.intValue(), bz.intValue());
            if (!requested.equals(ChunkPos.containing(position)) || !positions.add(position)) throw invalid("Original world block entity has a foreign/duplicate real chunk position");
            var section = sections.get(Math.floorDiv(position.getY(), 16));
            if (section == null) throw invalid("Original world block entity has no actual containing block palette");
            var actualState = section.at(position); var sourceType = Identifier.parse(string(source, "id"));
            if (!sourceType.equals(NeoForgeBlockEntityImport.originalTypeFor(actualState)))
                throw invalid("Original block-entity identity disagrees with its actual terrain block factory");
            blockEntities.add(NeoForgeBlockEntityImport.convert(sourceType, source, 3955, registries,
                NeoForgeMigrationScope.worldBlock(actualLevel.dimension().identifier(), position)));
        }
        ListTag entities = null;
        if (original.contains("entities")) entities = NeoForgeStandaloneEntityImport.convertRoots(list(original, "entities"), 3955, registries);
        // Only original bytes enter this one genuine containing terrain DFU. Converted current
        // owner images are installed afterwards and never interpreted under the old version.
        var result = actualNativeUpgrade.apply(original.copy());
        if (!(result.get("DataVersion") instanceof IntTag targetVersion)
                || targetVersion.intValue() != SharedConstants.getCurrentVersion().dataVersion().version())
            throw invalid("Actual native terrain upgrade did not establish its current data version");
        if (integer(result, "xPos") != requested.x() || integer(result, "zPos") != requested.z())
            throw invalid("Native upgrade changed the actual original terrain record coordinates");
        var remaining = new HashSet<>(sections.keySet());
        for (Tag value : result.contains("sections") ? list(result, "sections") : new ListTag()) {
            if (!(value instanceof CompoundTag section)) throw invalid("Native terrain upgrade produced a malformed section");
            int y = integer(section, "Y"); var converted = sections.get(y);
            if (converted != null) {
                if (!remaining.remove(y)) throw invalid("Native upgrade duplicated an actual source section");
                section.put("block_states", converted.blockStates.copy());
            }
        }
        if (!remaining.isEmpty()) throw invalid("Native upgrade lost actual source block sections");
        if (original.contains("block_entities")) result.put("block_entities", blockEntities);
        if (entities != null) result.put("entities", entities);
        return result;
    }
    private NeoForgeWorldChunkImport() {}
}
