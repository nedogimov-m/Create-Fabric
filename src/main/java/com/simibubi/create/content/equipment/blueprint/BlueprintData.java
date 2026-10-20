package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Actual native tracked/persistent ghost metadata; no ghost sample is a physical inventory owner. */
public final class BlueprintData {
 public static final EntityDataSerializer<CompoundTag> SERIALIZER=new EntityDataSerializer<>(){
  @Override public StreamCodec<RegistryFriendlyByteBuf,CompoundTag> codec(){return ByteBufCodecs.COMPOUND_TAG.cast();}
  @Override public CompoundTag copy(CompoundTag value){return value.copy();}
 };
 public static void register(){FabricEntityDataRegistry.register(Create.asResource("blueprint_recipes"),SERIALIZER);}
 public static SimpleContainer readItems(CompoundTag section,HolderLookup.Provider registries){var items=new SimpleContainer(11);var encoded=section.getListOrEmpty("Items");var ops=registries.createSerializationContext(NbtOps.INSTANCE);for(int i=0;i<encoded.size();i++){var entry=encoded.getCompoundOrEmpty(i);int slot=entry.getIntOr("Slot",-1);if(slot>=0&&slot<11)items.setItem(slot,ItemStack.OPTIONAL_CODEC.parse(ops,entry).result().orElse(ItemStack.EMPTY));}return items;}
 public static CompoundTag writeItems(SimpleContainer items,HolderLookup.Provider registries){var section=new CompoundTag();section.putInt("Size",11);var encoded=new ListTag();var ops=registries.createSerializationContext(NbtOps.INSTANCE);for(int slot=0;slot<11;slot++){var sample=items.getItem(slot);if(sample.isEmpty())continue;var entry=(CompoundTag)ItemStack.CODEC.encodeStart(ops,sample).getOrThrow();entry.putByte("Slot",(byte)slot);encoded.add(entry);}section.put("Items",encoded);return section;}
 private BlueprintData(){}
}
