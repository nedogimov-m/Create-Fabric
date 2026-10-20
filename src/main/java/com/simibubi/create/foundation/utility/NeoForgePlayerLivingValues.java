package com.simibubi.create.foundation.utility;
import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
/** Explicit actual original player living values; no vanilla mob surrogate or invented attacker. */
final class NeoForgePlayerLivingValues {
    static void validate(CompoundTag source,HolderLookup.Provider registries){
        for(String key:Set.of("Health","AbsorptionAmount"))floating(source,key);
        for(String key:Set.of("HurtTime","DeathTime"))if(!(source.get(key) instanceof ShortTag))throw invalid("Actual complete source player timer lost its source short");
        integer(source,"HurtByTimestamp");bool(source,"FallFlying");attributes(list(source,"attributes"),registries);
        var brain=compound(source,"Brain");keys(brain,"actual original core player brain","memories");if(!compound(brain,"memories").isEmpty())throw invalid("Original player brain has no reviewed registered core memory owner");
        Brain.Packed.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),brain).getOrThrow();effects(source,registries);
        int sleeping=0;for(String key:Set.of("SleepingX","SleepingY","SleepingZ"))if(source.contains(key)){integer(source,key);sleeping++;}if(sleeping!=0&&sleeping!=3)throw invalid("Original player sleeping position lost an actual coordinate");
    }
    static void finish(CompoundTag source,CompoundTag target,HolderLookup.Provider registries){
        validate(source,registries);target.put("attributes",attributes(list(source,"attributes"),registries));
        if(source.contains("active_effects"))target.put("active_effects",effects(source,registries));
        var brain=compound(source,"Brain");var ops=registries.createSerializationContext(NbtOps.INSTANCE);target.put("Brain",Brain.Packed.CODEC.encodeStart(ops,Brain.Packed.CODEC.parse(ops,brain).getOrThrow()).getOrThrow());
        if(source.contains("SleepingX")){target.store("sleeping_pos",BlockPos.CODEC,new BlockPos(integer(source,"SleepingX"),integer(source,"SleepingY"),integer(source,"SleepingZ")));for(String key:Set.of("SleepingX","SleepingY","SleepingZ"))target.remove(key);}
        if(target.contains("HurtByTimestamp")&&integer(target,"HurtByTimestamp")!=integer(source,"HurtByTimestamp"))throw invalid("Vanilla PLAYER fixer changed an unowned historical attacker timestamp");target.remove("HurtByTimestamp");
        for(String field:Set.of("last_hurt_by_player","last_hurt_by_player_memory_time","last_hurt_by_mob","ticks_since_last_hurt_by_mob"))if(target.contains(field))throw invalid("Original3955 player has no saved actual attacker identity for native attacker fields");
    }
    static ListTag effects(CompoundTag source,HolderLookup.Provider registries){
        var output=new ListTag();if(!source.contains("active_effects"))return output;var seen=new HashSet<Identifier>();var ops=registries.createSerializationContext(NbtOps.INSTANCE);
        for(var value:list(source,"active_effects")){if(!(value instanceof CompoundTag effect))throw invalid("Malformed actual original player effect");var id=Identifier.parse(string(effect,"id"));if(!Set.of("minecraft","create").contains(id.getNamespace())||!BuiltInRegistries.MOB_EFFECT.containsKey(id)||!seen.add(id))throw invalid("Original player effect has no unique actual registered core native identity");var actual=MobEffectInstance.CODEC.parse(ops,effect(effect,id,true)).getOrThrow();output.add(MobEffectInstance.CODEC.encodeStart(ops,actual).getOrThrow());}return output;
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
    static ListTag attributes(ListTag original,HolderLookup.Provider registries){
        var result=new ListTag();var seen=new HashSet<Identifier>();
        for(var value:original){
            if(!(value instanceof CompoundTag row))throw invalid("Malformed original player attribute");keys(row,"original attribute","id","base","modifiers");
            String originalId=string(row,"id");
            if(!(row.get("base") instanceof DoubleTag base)||!Double.isFinite(base.doubleValue()))throw invalid("Original attribute base must retain finite source double");
            if(originalId.equals("neoforge:swim_speed")||originalId.equals("neoforge:creative_flight")){
                if(!seen.add(Identifier.parse(originalId)))throw invalid("Duplicate genuine original NeoForge player attribute");
                double identity=originalId.equals("neoforge:swim_speed")?1:0;
                if(Double.compare(base.doubleValue(),identity)!=0||row.contains("modifiers")&&!list(row,"modifiers").isEmpty())throw invalid("Nondefault original "+originalId+" requires its actual native movement/permission compatibility behavior");
                continue; // Proven pinned identity behavior; the exact row survives in typed player history.
            }
            var targetId=ATTRIBUTE_IDS.get(originalId);if(targetId==null||!seen.add(Identifier.parse(targetId)))throw invalid("Original attribute has no unique reviewed core native alias");
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
        Map.entry("neoforge:nametag_distance","minecraft:name_tag_distance"),
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
    static void validateHistoricalAttributes(ListTag rows){
        var seen=new HashSet<String>();for(var value:rows){if(!(value instanceof CompoundTag row))throw invalid("Malformed historical actual player attribute");keys(row,"historical original player attribute","id","base","modifiers");String id=string(row,"id");if(!ATTRIBUTE_IDS.containsKey(id)&&!Set.of("neoforge:swim_speed","neoforge:creative_flight").contains(id)||!seen.add(id)||!(row.get("base") instanceof DoubleTag base)||!Double.isFinite(base.doubleValue()))throw invalid("Unproved historical source player attribute");if(row.contains("modifiers"))for(var modifier:list(row,"modifiers")){if(!(modifier instanceof CompoundTag tag))throw invalid("Malformed actual historical source attribute modifier");keys(tag,"historical source attribute modifier","id","amount","operation");AttributeModifier.CODEC.parse(NbtOps.INSTANCE,tag).getOrThrow();}}
    }
    static void validateHistoricalEffects(ListTag rows){
        var seen=new HashSet<Identifier>();for(var value:rows){
            if(!(value instanceof CompoundTag row))throw invalid("Malformed historical original living effect");
            var id=Identifier.parse(string(row,"id"));
            if(!Set.of("minecraft","create").contains(id.getNamespace())||!BuiltInRegistries.MOB_EFFECT.containsKey(id)||!seen.add(id))throw invalid("Historical effect has no unique actual registered core identity");
            effect(row,id,true);
        }
    }
    private NeoForgePlayerLivingValues(){}
}
