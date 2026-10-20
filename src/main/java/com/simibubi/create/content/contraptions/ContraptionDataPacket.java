package com.simibubi.create.content.contraptions;

import com.simibubi.create.Create;
import java.io.*;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Structure data is split below vanilla's custom-payload bound; inventories stay private. */
public record ContraptionDataPacket(int entityId, UUID entityUuid, UUID transfer, boolean full, int index, int count, byte[] bytes) implements CustomPacketPayload {
    public static final int PART_SIZE = 65536, MAX_PARTS = 1024, MAX_BYTES = PART_SIZE * MAX_PARTS;
    public static final Type<ContraptionDataPacket> TYPE = new Type<>(Create.asResource("contraption_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContraptionDataPacket> CODEC = CustomPacketPayload.codec((packet, buf) -> {
        buf.writeVarInt(packet.entityId); buf.writeUUID(packet.entityUuid); buf.writeUUID(packet.transfer); buf.writeBoolean(packet.full); buf.writeVarInt(packet.index); buf.writeVarInt(packet.count); buf.writeByteArray(packet.bytes);
    }, buf -> new ContraptionDataPacket(buf.readVarInt(), buf.readUUID(), buf.readUUID(), buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(PART_SIZE)));
    public ContraptionDataPacket {
        Objects.requireNonNull(entityUuid); Objects.requireNonNull(transfer); Objects.requireNonNull(bytes);
        if (count < 1 || count > MAX_PARTS || index < 0 || index >= count || bytes.length < 1 || bytes.length > PART_SIZE) throw new IllegalArgumentException("Invalid contraption data part");
        bytes = bytes.clone();
    }
    @Override public byte[] bytes() { return bytes.clone(); }
    @Override public Type<ContraptionDataPacket> type() { return TYPE; }
    public static List<ContraptionDataPacket> split(int entityId, UUID entityUuid, boolean full, CompoundTag data) {
        try {
            var output = new ByteArrayOutputStream(); NbtIo.writeCompressed(data, output); byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Contraption network data exceeds 64 MiB");
            int count = Math.max(1, (bytes.length + PART_SIZE - 1) / PART_SIZE); var transfer = UUID.randomUUID(); var parts = new ArrayList<ContraptionDataPacket>(count);
            for (int i = 0; i < count; i++) parts.add(new ContraptionDataPacket(entityId, entityUuid, transfer, full, i, count, Arrays.copyOfRange(bytes, i * PART_SIZE, Math.min(bytes.length, (i + 1) * PART_SIZE))));
            return List.copyOf(parts);
        } catch (IOException exception) { throw new UncheckedIOException(exception); }
    }
    public static final class Assembly {
        private final ContraptionDataPacket first;
        private final byte[][] parts;
        private int received, size;
        public Assembly(ContraptionDataPacket first) { this.first = first; parts = new byte[first.count][]; }
        public Optional<CompoundTag> accept(ContraptionDataPacket packet) {
            if (packet.entityId != first.entityId || !packet.entityUuid.equals(first.entityUuid) || !packet.transfer.equals(first.transfer) || packet.full != first.full || packet.count != parts.length) throw new IllegalArgumentException("Mixed contraption data transfers");
            if (parts[packet.index] != null) {
                if (!Arrays.equals(parts[packet.index], packet.bytes)) throw new IllegalArgumentException("Conflicting contraption data part");
                return Optional.empty();
            }
            parts[packet.index] = packet.bytes.clone(); received++; size = Math.addExact(size, packet.bytes.length);
            if (size > MAX_BYTES) throw new IllegalArgumentException("Contraption data exceeds limit");
            if (received != parts.length) return Optional.empty();
            try {
                var output = new ByteArrayOutputStream(size); for (var part : parts) output.write(part);
                return Optional.of(NbtIo.readCompressed(new ByteArrayInputStream(output.toByteArray()), NbtAccounter.create(MAX_BYTES)));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
    }
}
