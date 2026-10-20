package com.simibubi.create.content.contraptions.mounted;

import com.mojang.serialization.Codec;
import com.simibubi.create.foundation.mixin.accessor.MinecartFurnaceAccessor;
import com.simibubi.create.foundation.mixin.accessor.MinecartDisplayAccessor;
import com.simibubi.create.foundation.mixin.accessor.MinecartInventoryAccessor;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Immutable native base-cart snapshot; never another live Container or cargo owner. */
public final class NativeCartState {
    public static final Codec<NativeCartState> CODEC=CompoundTag.CODEC.xmap(NativeCartState::new,NativeCartState::copyTag);
    private final CompoundTag data;
    public NativeCartState(CompoundTag data){this.data=data.copy();if(this.data.getIntOr("Schema",0)!=1)throw new IllegalArgumentException("Unknown packed native cart state");}
    public CompoundTag copyTag(){return data.copy();}
    public static NativeCartState capture(AbstractMinecart cart){
        var tag=new CompoundTag();tag.putInt("Schema",1);var ops=cart.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        if(cart instanceof MinecartChest chest){
            tag.put("Items",ItemStack.OPTIONAL_CODEC.listOf().encodeStart(ops,chest.getItemStacks()).getOrThrow());
            tag.putLong("LootTableSeed",chest.getContainerLootTableSeed());if(chest.getContainerLootTable()!=null)tag.putString("LootTable",chest.getContainerLootTable().identifier().toString());
        }
        if(cart instanceof MinecartFurnace furnace){tag.putInt("Fuel",((MinecartFurnaceAccessor)furnace).create$getFuel());tag.store("Push",Vec3.CODEC,furnace.push);}
        if(cart.getCustomName()!=null)tag.put("Name",ComponentSerialization.CODEC.encodeStart(ops,cart.getCustomName()).getOrThrow());
        ((MinecartDisplayAccessor)cart).create$getCustomDisplayBlockState().ifPresent(state->tag.store("DisplayBlock",net.minecraft.world.level.block.state.BlockState.CODEC,state));tag.putInt("DisplayOffset",cart.getDisplayOffset());
        return new NativeCartState(tag);
    }
    public boolean matches(AbstractMinecart cart){return data.equals(capture(cart).data);}
    /** Full native codecs are checked while this snapshot remains an off-world proposal. */
    public Prepared prepare(AbstractMinecart cart){
        var ops=cart.registryAccess().createSerializationContext(NbtOps.INSTANCE);List<ItemStack> items=List.of();
        if(data.contains("Items")){if(!(cart instanceof MinecartChest chest))throw new IllegalArgumentException("Packed chest inventory requires the actual chest-cart type");items=ItemStack.OPTIONAL_CODEC.listOf().parse(ops,data.get("Items")).getOrThrow();if(items.size()!=chest.getItemStacks().size()&&!((items.size()==36&&chest.getItemStacks().size()==chest.getContainerSize()||items.size()==chest.getContainerSize()&&chest.getItemStacks().size()==36)&&cargoIsActuallyEmpty(chest)))throw new IllegalArgumentException("Native chest-cart snapshot must retain its actual complete backing storage extent");}
        else if(cart instanceof MinecartChest)throw new IllegalArgumentException("Packed chest cart lost its actual inventory record");
        if(cart instanceof MinecartFurnace&&(!data.contains("Fuel")||!data.contains("Push")))throw new IllegalArgumentException("Packed furnace cart lost its actual fuel or push record");
        if(!(cart instanceof MinecartFurnace)&&(data.contains("Fuel")||data.contains("Push")))throw new IllegalArgumentException("Furnace state requires the actual native furnace-cart type");
        int fuel=data.contains("Fuel")?data.getInt("Fuel").orElseThrow(()->new IllegalArgumentException("Invalid actual furnace fuel record")):0;if(fuel<0)throw new IllegalArgumentException("Negative native furnace-cart fuel");
        Vec3 push=data.contains("Push")?data.read("Push",Vec3.CODEC).orElseThrow(()->new IllegalArgumentException("Invalid actual furnace push record")):Vec3.ZERO;if(!Double.isFinite(push.x)||!Double.isFinite(push.y)||!Double.isFinite(push.z))throw new IllegalArgumentException("Nonfinite native furnace push");
        if(cart instanceof MinecartChest&&data.getLong("LootTableSeed").isEmpty()||!(cart instanceof MinecartChest)&&data.contains("LootTableSeed"))throw new IllegalArgumentException("Actual chest loot-table seed record required");
        if(data.getInt("DisplayOffset").isEmpty())throw new IllegalArgumentException("Actual native display offset record required");
        ResourceKey<net.minecraft.world.level.storage.loot.LootTable> loot=data.contains("LootTable")?ResourceKey.create(Registries.LOOT_TABLE,Identifier.parse(data.getStringOr("LootTable",""))):null;
        if(loot!=null&&!(cart instanceof MinecartChest))throw new IllegalArgumentException("Only the actual native chest cart carries a loot table");
        Component name=data.contains("Name")?ComponentSerialization.CODEC.parse(ops,data.get("Name")).getOrThrow():null;
        Optional<net.minecraft.world.level.block.state.BlockState> display=data.contains("DisplayBlock")?Optional.of(data.read("DisplayBlock",net.minecraft.world.level.block.state.BlockState.CODEC).orElseThrow(()->new IllegalArgumentException("Invalid native custom display state"))):Optional.empty();
        return new Prepared(items,loot,data.getLongOr("LootTableSeed",0),fuel,push,name,display,data.getIntOr("DisplayOffset",0));
    }
    private static NonNullList<ItemStack> prepareNativeBacking(List<ItemStack> items){var result=NonNullList.withSize(items.size(),ItemStack.EMPTY);for(int slot=0;slot<items.size();slot++)result.set(slot,items.get(slot));return result;}
    public static final class Prepared {
        private final List<ItemStack> items;
        private final ResourceKey<net.minecraft.world.level.storage.loot.LootTable> loot;
        private final long lootSeed;
        private final int fuel,displayOffset;
        private final Vec3 push;
        private final Component name;
        private final Optional<net.minecraft.world.level.block.state.BlockState> display;
        private final NonNullList<ItemStack> nativeBacking;
        public Prepared(List<ItemStack> items,ResourceKey<net.minecraft.world.level.storage.loot.LootTable> loot,long lootSeed,int fuel,Vec3 push,Component name,Optional<net.minecraft.world.level.block.state.BlockState> display,int displayOffset){this.items=List.copyOf(items);this.loot=loot;this.lootSeed=lootSeed;this.fuel=fuel;this.push=push;this.name=name;this.display=display;this.displayOffset=displayOffset;nativeBacking=prepareNativeBacking(items);}
        public List<ItemStack> items(){return items;}
        public ResourceKey<net.minecraft.world.level.storage.loot.LootTable> loot(){return loot;}
        public long lootSeed(){return lootSeed;}
        public int fuel(){return fuel;}
        public Vec3 push(){return push;}
        public Component name(){return name;}
        public Optional<net.minecraft.world.level.block.state.BlockState> display(){return display;}
        public int displayOffset(){return displayOffset;}
        public Prepared withName(Component actualName){return new Prepared(items,loot,lootSeed,fuel,push,actualName,display,displayOffset);}
        /** Commit-only, after original packed ownership is revoked; direct native fields have no callbacks. */
        public void installCargo(AbstractMinecart cart,com.simibubi.create.content.contraptions.CartForestTransfer.Commit proof){
            proof.requireTargetCart(cart);
            if(cart instanceof MinecartChest chest){var target=chest.getItemStacks();if(target.size()!=items.size()){
                // 26.3 constructs36 real backing slots, while native load resets to logical27.
                // The entire36-slot receipt, including any paid tail cargo, was allocated above.
                // Final raw source/target validation and receipt revocation precede this pure field handoff.
                if(items.size()==36&&target.size()==chest.getContainerSize()){
                    ((MinecartInventoryAccessor)chest).create$setItemStacks(nativeBacking);target=nativeBacking;
                }else if(!(target.size()==36&&items.size()==chest.getContainerSize()))throw new IllegalStateException("Actual native inventory extent changed before handoff");
            }for(int slot=0;slot<items.size();slot++)target.set(slot,items.get(slot));chest.setContainerLootTable(loot);chest.setContainerLootTableSeed(lootSeed);}
            if(cart instanceof MinecartFurnace furnace){((MinecartFurnaceAccessor)furnace).create$setFuel(fuel);furnace.push=push;}
        }
        /** Normal native synchronized callbacks run only after real cargo owners are installed. */
        public void publishMetadata(AbstractMinecart cart){cart.setCustomName(name);cart.setCustomDisplayBlockState(display);cart.setDisplayOffset(displayOffset);if(cart instanceof MinecartFurnace furnace)((MinecartFurnaceAccessor)furnace).create$setHasFuel(fuel>0);}
    }
    /** Real raw fields, without fake-empty views or loot unpacking; used for native empty reservation preflight. */
    public static boolean cargoIsActuallyEmpty(AbstractMinecart cart){
        if(cart instanceof MinecartChest chest&&(chest.getContainerLootTable()!=null||chest.getContainerLootTableSeed()!=0||chest.getItemStacks().stream().anyMatch(stack->!stack.isEmpty())))return false;
        return !(cart instanceof MinecartFurnace furnace)||((MinecartFurnaceAccessor)furnace).create$getFuel()==0&&furnace.push.equals(Vec3.ZERO);
    }
    /** Commit-only: caller first proved matches and owns the full immutable outgoing receipt. */
    public static void clearCargo(AbstractMinecart cart,com.simibubi.create.content.contraptions.CartForestTransfer.Commit proof){
        proof.requireSourceCart(cart);
        if(cart instanceof MinecartChest chest){var source=chest.getItemStacks();for(int slot=0;slot<source.size();slot++)source.set(slot,ItemStack.EMPTY);chest.setContainerLootTable(null);chest.setContainerLootTableSeed(0);}
        if(cart instanceof MinecartFurnace furnace){((MinecartFurnaceAccessor)furnace).create$setFuel(0);furnace.push=Vec3.ZERO;}
    }
    @Override public boolean equals(Object other){return other instanceof NativeCartState state&&data.equals(state.data);}
    @Override public int hashCode(){return data.hashCode();}
}
