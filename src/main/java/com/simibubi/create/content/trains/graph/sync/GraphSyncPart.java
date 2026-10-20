package com.simibubi.create.content.trains.graph.sync;

import com.simibubi.create.Create;
import java.security.*;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Native payload fragments. A connection nonce, epoch, sequence and digest bind the complete frame.
 */
public record GraphSyncPart(
    UUID nonce,
    UUID epoch,
    UUID transfer,
    long sequence,
    boolean snapshot,
    int length,
    int index,
    int count,
    byte[] digest,
    byte[] bytes)
    implements CustomPacketPayload {
  public static final int PART_BYTES = 24 * 1024, MAX_BYTES = 64 * 1024 * 1024;
  public static final long MAX_NBT_HEAP = 256L * 1024 * 1024;
  public static final Type<GraphSyncPart> TYPE = new Type<>(Create.asResource("rail_graph_frame"));
  public static final StreamCodec<RegistryFriendlyByteBuf, GraphSyncPart> CODEC =
      CustomPacketPayload.codec(
          (packet, buf) -> {
            buf.writeUUID(packet.nonce);
            buf.writeUUID(packet.epoch);
            buf.writeUUID(packet.transfer);
            buf.writeVarLong(packet.sequence);
            buf.writeBoolean(packet.snapshot);
            buf.writeVarInt(packet.length);
            buf.writeVarInt(packet.index);
            buf.writeVarInt(packet.count);
            buf.writeByteArray(packet.digest);
            buf.writeByteArray(packet.bytes);
          },
          buf ->
              new GraphSyncPart(
                  buf.readUUID(),
                  buf.readUUID(),
                  buf.readUUID(),
                  buf.readVarLong(),
                  buf.readBoolean(),
                  buf.readVarInt(),
                  buf.readVarInt(),
                  buf.readVarInt(),
                  buf.readByteArray(32),
                  buf.readByteArray(PART_BYTES)));

  public GraphSyncPart {
    Objects.requireNonNull(nonce);
    Objects.requireNonNull(epoch);
    Objects.requireNonNull(transfer);
    if (sequence < 0
        || length < 1
        || length > MAX_BYTES
        || count != (length + PART_BYTES - 1) / PART_BYTES
        || index < 0
        || index >= count
        || digest.length != 32
        || bytes.length != Math.min(PART_BYTES, length - index * PART_BYTES))
      throw new IllegalArgumentException("Invalid railway graph fragment");
    digest = digest.clone();
    bytes = bytes.clone();
  }

  @Override
  public byte[] digest() {
    return digest.clone();
  }

  @Override
  public byte[] bytes() {
    return bytes.clone();
  }

  @Override
  public Type<GraphSyncPart> type() {
    return TYPE;
  }

  public static byte[] digest(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  public static List<GraphSyncPart> split(UUID nonce, GraphSyncFrame frame, byte[] bytes) {
    if (bytes.length < 1 || bytes.length > MAX_BYTES)
      throw new IllegalArgumentException("Invalid railway frame length");
    int count = (bytes.length + PART_BYTES - 1) / PART_BYTES;
    var transfer = UUID.randomUUID();
    var digest = digest(bytes);
    var result = new ArrayList<GraphSyncPart>();
    for (int index = 0; index < count; index++)
      result.add(
          new GraphSyncPart(
              nonce,
              frame.epoch(),
              transfer,
              frame.sequence(),
              frame.snapshot(),
              bytes.length,
              index,
              count,
              digest,
              Arrays.copyOfRange(
                  bytes, index * PART_BYTES, Math.min(bytes.length, (index + 1) * PART_BYTES))));
    return List.copyOf(result);
  }

  public static final class Assembly {
    private final GraphSyncPart header;
    private final byte[][] parts;
    private int received;
    private boolean complete;

    public Assembly(GraphSyncPart first) {
      header = first;
      parts = new byte[first.count][];
    }

    public int reservedBytes() {
      return header.length;
    }

    public Optional<byte[]> accept(GraphSyncPart packet) {
      if (complete) return Optional.empty();
      if (!packet.nonce.equals(header.nonce)
          || !packet.epoch.equals(header.epoch)
          || !packet.transfer.equals(header.transfer)
          || packet.sequence != header.sequence
          || packet.snapshot != header.snapshot
          || packet.length != header.length
          || packet.count != header.count
          || !Arrays.equals(packet.digest, header.digest))
        throw new IllegalArgumentException("Mixed railway graph transfers");
      var previous = parts[packet.index];
      if (previous != null) {
        if (!Arrays.equals(previous, packet.bytes))
          throw new IllegalArgumentException("Conflicting railway graph fragment");
        return Optional.empty();
      }
      parts[packet.index] = packet.bytes.clone();
      if (++received != parts.length) return Optional.empty();
      var bytes = new byte[header.length];
      for (int index = 0; index < parts.length; index++)
        System.arraycopy(parts[index], 0, bytes, index * PART_BYTES, parts[index].length);
      if (!Arrays.equals(digest(bytes), header.digest))
        throw new IllegalArgumentException("Railway frame digest mismatch");
      complete = true;
      return Optional.of(bytes);
    }
  }
}
