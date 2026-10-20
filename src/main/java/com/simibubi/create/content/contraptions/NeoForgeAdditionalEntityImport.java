package com.simibubi.create.content.contraptions;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/** Explicit complete source3955 blueprint, potato and package additional schemas. Never a surrogate vanilla entity. */
final class NeoForgeAdditionalEntityImport {
    private static final Set<String> LIVING=Set.of("Box","Health","HurtTime","HurtByTimestamp","DeathTime","AbsorptionAmount","attributes","active_effects","FallFlying","SleepingX","SleepingY","SleepingZ","Brain","Team");
    static Set<String> sourceFields(String type){return switch(type){
        case "crafting_blueprint"->Set.of("Facing","Orientation","Size","TileX","TileY","TileZ");
        case "potato_projectile"->Set.of("Item","AdditionalDamage","AdditionalKnockback","Recovery","Owner","LeftOwner","HasBeenShot","acceleration_power");
        case "package"->LIVING;
        default->throw invalid("Unreviewed additional custom entity type "+type);
    };}
    static void validateOwnPersistentData(String type,CompoundTag source){
        if(!source.contains("NeoForgeData"))return;var old=compound(source,"NeoForgeData");
        if(type.equals("crafting_blueprint"))keys(old,"original blueprint persistent owner","Recipes");
        else if(!old.isEmpty())throw invalid("Custom entity has an unconverted original persistent owner");
    }
    static CompoundTag convert(String type,CompoundTag original,CompoundTag actualFixedBase,int version,HolderLookup.Provider registries){
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original additional entity version");
        var target=actualFixedBase.copy();var scope=NeoForgeMigrationScope.entity(uuid(original,"UUID"));
        switch(type){case "crafting_blueprint"->blueprint(original,target,version,registries,scope);case "potato_projectile"->potato(original,target,version,registries,scope);case "package"->parcel(original,target,version,registries,scope);default->throw invalid("Unknown source additional entity");}
        return target;
    }
    private static void blueprint(CompoundTag source,CompoundTag target,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        var facing=directionByte(source,"Facing");var orientation=directionByte(source,"Orientation");int size=integer(source,"Size");
        if(size<1||size>3||facing.getAxis().isVertical()&&orientation.getAxis().isVertical())throw invalid("Original blueprint size/orientation has no faithful native representation");
        var anchor=new BlockPos(integer(source,"TileX"),integer(source,"TileY"),integer(source,"TileZ"));var position=vector(list(source,"Pos"));
        var actualPosition=BlockPos.containing(((DoubleTag)position.get(0)).doubleValue(),((DoubleTag)position.get(1)).doubleValue(),((DoubleTag)position.get(2)).doubleValue());
        if(!anchor.closerThan(actualPosition,16))throw invalid("Original blueprint anchor is outside its actual saved entity position");
        target.store("block_pos",BlockPos.CODEC,anchor);target.putByte("Facing",(byte)facing.get3DDataValue());target.putByte("Orientation",(byte)orientation.get3DDataValue());target.putInt("Size",size);
        var recipes=source.contains("NeoForgeData")&&compound(source,"NeoForgeData").contains("Recipes")?compound(compound(source,"NeoForgeData"),"Recipes"):new CompoundTag();
        var converted=new CompoundTag();
        for(var key:recipes.keySet()){
            if(key.equals("InferredIcon")){bool(recipes,key);converted.put(key,recipes.get(key).copy());continue;}
            if(!key.matches("[0-8]"))throw invalid("Unknown original blueprint recipe section "+key);
            int section=Integer.parseInt(key);var original=compound(recipes,key);
            if(original.isEmpty()){converted.put(key,new CompoundTag());continue;}
            // Genuine NeoForge ItemStackHandler wrote INT Slot, unlike the native
            // blueprint ghost metadata writer, which deliberately writes BYTE Slot.
            var dense=itemHandler(original,11,version,registries,scope.item("recipes",section));var rows=new ListTag();
            for(int slot=0;slot<11;slot++){var item=((CompoundTag)dense.get(slot)).copy();if(item.isEmpty())continue;item.putByte("Slot",(byte)slot);rows.add(item);}
            var nativeSection=new CompoundTag();nativeSection.putInt("Size",11);nativeSection.put("Items",rows);converted.put(key,nativeSection);
        }
        target.put("Recipes",converted);
    }
    private static Direction directionByte(CompoundTag source,String key){if(!(source.get(key) instanceof ByteTag value)||value.byteValue()<0||value.byteValue()>5)throw invalid("Original blueprint direction must be its actual source byte enum");return Direction.from3DDataValue(value.byteValue());}
    private static void potato(CompoundTag source,CompoundTag target,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        var item=item(compound(source,"Item"),version,registries,scope.item("item",0));target.put("Item",item);
        for(var key:List.of("AdditionalDamage","AdditionalKnockback","Recovery")){floating(source,key);target.put(key,source.get(key).copy());}
        if(!(source.get("acceleration_power") instanceof DoubleTag acceleration)||!Double.isFinite(acceleration.doubleValue()))throw invalid("Original potato projectile lost genuine finite saved acceleration");target.put("acceleration_power",acceleration.copy());
        bool(source,"HasBeenShot");target.put("HasBeenShot",source.get("HasBeenShot").copy());
        if(source.contains("LeftOwner")){bool(source,"LeftOwner");target.put("LeftOwner",source.get("LeftOwner").copy());}
        if(source.contains("Owner"))target.store("Owner",UUIDUtil.CODEC,uuid(source,"Owner"));
        // The old serializer never saved a stuck entity/offset/fall-speed reference.
        // No such historical reference or native command permission is invented.
    }
    private static void parcel(CompoundTag source,CompoundTag target,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        for(var key:List.of("Health","AbsorptionAmount")){floating(source,key);target.put(key,source.get(key).copy());}
        for(var key:List.of("HurtTime","DeathTime")){if(!(source.get(key) instanceof ShortTag))throw invalid("Original package living timer lost actual source short type");target.put(key,source.get(key).copy());}
        bool(source,"FallFlying");target.put("FallFlying",source.get("FallFlying").copy());
        target.put(NeoForgePackageHistory.KEY,new NeoForgePackageHistory(version,integer(source,"HurtByTimestamp")).write());
        target.put("attributes",attributes(list(source,"attributes"),registries));
        var brain=compound(source,"Brain");keys(brain,"original core package brain","memories");
        if(!compound(brain,"memories").isEmpty())throw invalid("Core package has no source registered memory owner for nonempty brain state");
        var ops=registries.createSerializationContext(NbtOps.INSTANCE);var nativeBrain=Brain.Packed.CODEC.parse(ops,brain).getOrThrow();target.put("Brain",Brain.Packed.CODEC.encodeStart(ops,nativeBrain).getOrThrow());
        if(source.contains("active_effects")){
            var effects=new ListTag();var seen=new HashSet<String>();
            for(var value:list(source,"active_effects")){
                if(!(value instanceof CompoundTag old))throw invalid("Malformed original package active effect");var id=Identifier.parse(string(old,"id"));
                if(!seen.add(id.toString())||!Set.of("minecraft","create").contains(id.getNamespace())||!BuiltInRegistries.MOB_EFFECT.containsKey(id))throw invalid("Original package effect needs its actual unique registered native core identity");
                var prepared=effect(old,id,true);var actual=MobEffectInstance.CODEC.parse(ops,prepared).getOrThrow();effects.add(MobEffectInstance.CODEC.encodeStart(ops,actual).getOrThrow());
            }target.put("active_effects",effects);
        }
        int sleeping=0;for(var key:List.of("SleepingX","SleepingY","SleepingZ"))if(source.contains(key))sleeping++;
        if(sleeping!=0&&sleeping!=3)throw invalid("Original package sleeping location lost one genuine coordinate");
        if(sleeping==3)target.store("sleeping_pos",BlockPos.CODEC,new BlockPos(integer(source,"SleepingX"),integer(source,"SleepingY"),integer(source,"SleepingZ")));
        if(source.contains("Team"))target.putString("Team",string(source,"Team"));
        var box=item(compound(source,"Box"),version,registries,scope.item("box",0));if(!box.isEmpty())target.put("Box",box);
    }
    private static CompoundTag effect(CompoundTag original,Identifier id,boolean root){
        keys(original,"original core effect",root?new String[]{"id","amplifier","duration","ambient","show_particles","show_icon","hidden_effect","neoforge:cures"}:new String[]{"amplifier","duration","ambient","show_particles","show_icon","hidden_effect","neoforge:cures"});
        var expected=id.toString().equals("minecraft:poison")?Set.of("milk","protected_by_totem","honey"):Set.of("milk","protected_by_totem");var actual=new HashSet<String>();
        // Genuine NeoForge defaults were persisted. Absence means the original
        // effect had no cures, which must not silently become native defaults.
        for(var value:list(original,"neoforge:cures"))if(!(value instanceof StringTag name)||!actual.add(name.value()))throw invalid("Malformed/duplicate original effect cure");
        if(!actual.equals(expected))throw invalid("Original custom effect cure state needs its actual native behavior bridge");
        var result=original.copy();result.remove("neoforge:cures");if(original.contains("hidden_effect"))result.put("hidden_effect",effect(compound(original,"hidden_effect"),id,false));return result;
    }
    private static ListTag attributes(ListTag original,HolderLookup.Provider registries){
        var result=new ListTag();var seen=new HashSet<Identifier>();
        for(var value:original){
            if(!(value instanceof CompoundTag row))throw invalid("Malformed original package attribute");keys(row,"original attribute","id","base","modifiers");
            var targetId=ATTRIBUTE_IDS.get(string(row,"id"));if(targetId==null||!seen.add(Identifier.parse(targetId)))throw invalid("Original attribute has no unique reviewed core native alias");
            if(!(row.get("base") instanceof DoubleTag base)||!Double.isFinite(base.doubleValue()))throw invalid("Original attribute base must retain finite source double");
            var next=row.copy();next.putString("id",targetId);var modifiers=new HashSet<Identifier>();
            if(row.contains("modifiers"))for(var entry:list(row,"modifiers")){
                if(!(entry instanceof CompoundTag modifier))throw invalid("Malformed source attribute modifier");keys(modifier,"original attribute modifier","id","amount","operation");
                var id=Identifier.parse(string(modifier,"id"));if(!modifiers.add(id)||!(modifier.get("amount") instanceof DoubleTag amount)||!Double.isFinite(amount.doubleValue()))throw invalid("Duplicate/nonfinite original attribute modifier");
                AttributeModifier.CODEC.parse(NbtOps.INSTANCE,modifier).getOrThrow();
            }
            var ops=registries.createSerializationContext(NbtOps.INSTANCE);var actual=AttributeInstance.Packed.CODEC.parse(ops,next).getOrThrow();result.add(AttributeInstance.Packed.CODEC.encodeStart(ops,actual).getOrThrow());
        }
        return result;
    }
    // Generated below from the pinned3955 Attributes.register literals; no arbitrary prefix stripping.
    private static final Map<String,String> ATTRIBUTE_IDS=Map.ofEntries(
        Map.entry("minecraft:generic.armor","minecraft:armor"),
        Map.entry("minecraft:generic.armor_toughness","minecraft:armor_toughness"),
        Map.entry("minecraft:generic.attack_damage","minecraft:attack_damage"),
        Map.entry("minecraft:generic.attack_knockback","minecraft:attack_knockback"),
        Map.entry("minecraft:generic.attack_speed","minecraft:attack_speed"),
        Map.entry("minecraft:generic.burning_time","minecraft:burning_time"),
        Map.entry("minecraft:generic.explosion_knockback_resistance","minecraft:explosion_knockback_resistance"),
        Map.entry("minecraft:generic.fall_damage_multiplier","minecraft:fall_damage_multiplier"),
        Map.entry("minecraft:generic.flying_speed","minecraft:flying_speed"),
        Map.entry("minecraft:generic.follow_range","minecraft:follow_range"),
        Map.entry("minecraft:generic.gravity","minecraft:gravity"),
        Map.entry("minecraft:generic.jump_strength","minecraft:jump_strength"),
        Map.entry("minecraft:generic.knockback_resistance","minecraft:knockback_resistance"),
        Map.entry("minecraft:generic.luck","minecraft:luck"),
        Map.entry("minecraft:generic.max_absorption","minecraft:max_absorption"),
        Map.entry("minecraft:generic.max_health","minecraft:max_health"),
        Map.entry("minecraft:generic.movement_efficiency","minecraft:movement_efficiency"),
        Map.entry("minecraft:generic.movement_speed","minecraft:movement_speed"),
        Map.entry("minecraft:generic.oxygen_bonus","minecraft:oxygen_bonus"),
        Map.entry("minecraft:generic.safe_fall_distance","minecraft:safe_fall_distance"),
        Map.entry("minecraft:generic.scale","minecraft:scale"),
        Map.entry("minecraft:generic.step_height","minecraft:step_height"),
        Map.entry("minecraft:generic.water_movement_efficiency","minecraft:water_movement_efficiency"),
        Map.entry("minecraft:player.block_break_speed","minecraft:block_break_speed"),
        Map.entry("minecraft:player.block_interaction_range","minecraft:block_interaction_range"),
        Map.entry("minecraft:player.entity_interaction_range","minecraft:entity_interaction_range"),
        Map.entry("minecraft:player.mining_efficiency","minecraft:mining_efficiency"),
        Map.entry("minecraft:player.sneaking_speed","minecraft:sneaking_speed"),
        Map.entry("minecraft:player.submerged_mining_speed","minecraft:submerged_mining_speed"),
        Map.entry("minecraft:player.sweeping_damage_ratio","minecraft:sweeping_damage_ratio"),
        Map.entry("minecraft:zombie.spawn_reinforcements","minecraft:spawn_reinforcements"));
    private NeoForgeAdditionalEntityImport(){}
}
