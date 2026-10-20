package com.simibubi.create.content.trains.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** Native registry-aware serialization for the original schedule NBT format. */
public final class ScheduleSerialization {
    private ScheduleSerialization() {}

    public static <T> ListTag writeCompoundList(Iterable<T> values, Function<T, CompoundTag> writer) {
        var list = new ListTag();
        for (T value : values) list.add(writer.apply(value));
        return list;
    }

    public static <T> List<T> readCompoundList(ListTag list, Function<CompoundTag, T> reader) {
        var values = new ArrayList<T>();
        iterateCompoundList(list, tag -> values.add(reader.apply(tag)));
        return values;
    }

    public static void iterateCompoundList(ListTag list, Consumer<CompoundTag> reader) {
        for (var value : list) {
            if (!(value instanceof CompoundTag tag))
                throw new IllegalArgumentException("Malformed schedule compound list");
            reader.accept(tag);
        }
    }

    public static <T extends Enum<T>> void writeEnum(CompoundTag tag, String key, T value) {
        tag.putString(key, value.name());
    }

    public static <T extends Enum<T>> T readEnum(CompoundTag tag, String key, Class<T> type) {
        String name = tag.getStringOr(key, "");
        for (T value : type.getEnumConstants()) if (value.name().equals(name)) return value;
        return type.getEnumConstants()[0];
    }

    public static CompoundTag writeItem(HolderLookup.Provider registries, ItemStack stack) {
        return (CompoundTag) ItemStack.OPTIONAL_CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack).getOrThrow();
    }

    public static ItemStack readItem(HolderLookup.Provider registries, CompoundTag tag) {
        return ItemStack.OPTIONAL_CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).getOrThrow();
    }

    public static <T> StreamCodec<RegistryFriendlyByteBuf, T> ofLegacyNbtWithRegistries(
            BiFunction<T, HolderLookup.Provider, CompoundTag> writer,
            BiFunction<HolderLookup.Provider, CompoundTag, T> reader) {
        return StreamCodec.of(
                (buffer, value) -> ByteBufCodecs.COMPOUND_TAG.encode(buffer, writer.apply(value, buffer.registryAccess())),
                buffer -> reader.apply(buffer.registryAccess(), ByteBufCodecs.COMPOUND_TAG.decode(buffer)));
    }
}
