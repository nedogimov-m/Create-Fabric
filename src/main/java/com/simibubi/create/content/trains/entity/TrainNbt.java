package com.simibubi.create.content.trains.entity;

import java.util.function.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;

/** Real source-shaped train lists and legacy component migration with the actual native registries. */
final class TrainNbt {
    static <T> ListTag writeCompoundList(Iterable<T> source,Function<T,CompoundTag> encoder){var list=new ListTag();for(var value:source){var encoded=encoder.apply(value);if(encoded!=null)list.add(encoded);}return list;}
    static void iterateCompoundList(ListTag source,Consumer<CompoundTag> action){for(var value:source){if(!(value instanceof CompoundTag compound))throw new IllegalArgumentException("Malformed actual persisted Train compound list");action.accept(compound);}}
    static java.util.UUID uuid(CompoundTag source,String key){return source.read(key,UUIDUtil.CODEC).orElseThrow(()->new IllegalArgumentException("Missing or malformed actual Train UUID "+key));}
    static Component component(CompoundTag source,String key,HolderLookup.Provider registries){
        var legacy=source.getString(key);
        if(legacy.isPresent()){
            try{var json=com.google.gson.JsonParser.parseString(legacy.get());var parsed=ComponentSerialization.CODEC.parse(registries.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),json).result();if(parsed.isPresent())return parsed.get();}catch(com.google.gson.JsonParseException invalid){/* Native literal components are not JSON documents. */}
        }
        return source.read(key,ComponentSerialization.CODEC,registries.createSerializationContext(NbtOps.INSTANCE)).orElseThrow(()->new IllegalArgumentException("Invalid actual train display component"));
    }
    private TrainNbt(){}
}
