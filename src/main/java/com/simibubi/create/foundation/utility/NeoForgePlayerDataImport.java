package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Dynamic;
import com.simibubi.create.content.contraptions.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Complete actual3955 PlayerData -> one original PLAYER DFU -> typed native postpass; no player/entity factory. */
public final class NeoForgePlayerDataImport {
    private static final Set<String> SOURCE_FIELDS=Set.of("id","DataVersion","UUID","Pos","Motion","Rotation","FallDistance","Fire","Air","OnGround","Invulnerable","PortalCooldown","CustomName","CustomNameVisible","Silent","NoGravity","Glowing","HasVisualFire","Tags","TicksFrozen","NeoForgeData","neoforge:attachments","Passengers",
        "Health","HurtTime","HurtByTimestamp","DeathTime","AbsorptionAmount","attributes","active_effects","FallFlying","SleepingX","SleepingY","SleepingZ","Brain",
        "Inventory","SelectedItemSlot","SleepTimer","XpP","XpLevel","XpTotal","XpSeed","Score","foodLevel","foodTickTimer","foodSaturationLevel","foodExhaustionLevel","abilities","EnderItems","ShoulderEntityLeft","ShoulderEntityRight","LastDeathLocation","current_explosion_impact_pos","ignore_fall_damage_from_current_explosion","current_impulse_context_reset_grace_time",
        "warden_spawn_tracker","playerGameType","previousPlayerGameType","seenCredits","enteredNetherPosition","RootVehicle","recipeBook","Dimension","SpawnX","SpawnY","SpawnZ","SpawnForced","SpawnAngle","SpawnDimension","spawn_extra_particles_on_fall","raid_omen_position");
    private static final Map<Integer,String> EQUIPMENT=Map.of(100,"feet",101,"legs",102,"chest",103,"head",150,"offhand");
    public static CompoundTag convert(CompoundTag actualSource,UUID actualFileUuid,ServerLevel actualSourceLevel){
        Objects.requireNonNull(actualSourceLevel);Objects.requireNonNull(actualFileUuid);validate(actualSource,actualFileUuid,actualSourceLevel);int version=NeoForgeSavedDataImport.SOURCE_DATA_VERSION;var registries=actualSourceLevel.registryAccess();var scope=NeoForgeMigrationScope.entity(actualFileUuid);
        var originalInventory=sourceRows(list(actualSource,"Inventory"),true);var originalEnder=sourceRows(list(actualSource,"EnderItems"),false);var prepared=actualSource.copy();
        prepared.put("Inventory",prepareRows(originalInventory,version,registries,scope.item("inventory",0)));prepared.put("EnderItems",prepareRows(originalEnder,version,registries,scope.item("ender_items",0)));
        // Whole original vanilla data still has its original3955 vehicle/shoulder trees.
        // No already-current nested body is ever passed under this containing old PLAYER fixer.
        var value=DataFixers.getDataFixer().update(References.PLAYER,new Dynamic<Tag>(NbtOps.INSTANCE,prepared),version,SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
        if(!(value instanceof CompoundTag fixed))throw invalid("Actual native PLAYER fixer returned a noncompound player record");var result=fixed.copy();
        if(!actualFileUuid.equals(uuid(result,"UUID")))throw invalid("Actual PLAYER fixer changed the real source filename/player UUID");
        inventory(originalInventory,result,version,registries,scope.item("inventory",0));ender(originalEnder,result,version,registries,scope.item("ender_items",0));
        NeoForgePlayerLivingValues.finish(actualSource,result,registries);
        if(actualSource.contains("CustomName"))result.put("CustomName",NeoForgeTextComponentImport.flattened((StringTag)actualSource.get("CustomName"),version,registries,scope.item("customname",0)));
        result.putDouble("fall_distance",floating(actualSource,"FallDistance"));result.remove("FallDistance");
        for(String shoulder:Set.of("ShoulderEntityLeft","ShoulderEntityRight"))if(actualSource.contains(shoulder)){
            var source=compound(actualSource,shoulder);if(source.isEmpty())result.put(shoulder,source.copy());else result.put(shoulder,NeoForgeStandaloneEntityImport.convertTree(source,version,registries));
        }
        if(actualSource.contains("Passengers"))result.put("Passengers",NeoForgeStandaloneEntityImport.convertRoots(list(actualSource,"Passengers"),version,registries));
        persistent(actualSource,result,actualFileUuid,actualSourceLevel);
        if(actualSource.contains("enteredNetherPosition")){var source=compound(actualSource,"enteredNetherPosition");result.store("entered_nether_pos",Vec3.CODEC,new Vec3(doubleValue(source,"x"),doubleValue(source,"y"),doubleValue(source,"z")));result.remove("enteredNetherPosition");}
        for(String key:Set.of("current_explosion_impact_pos","last_explosion_impact_pos"))if(result.contains(key))result.read(key,Vec3.CODEC).orElseThrow(()->invalid("Actual PLAYER fixer lost a genuine original explosion vector"));
        // Exact separately converted original source boundaries replace only these current fields.
        NeoForgePlayerVehicleImport.prepareVehicleFields(actualSource,result,actualSourceLevel,version);
        Abilities.Packed.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),compound(result,"abilities")).getOrThrow();
        NbtUtils.addCurrentDataVersion(result);return result;
    }
    private static void validate(CompoundTag source,UUID actualFileUuid,ServerLevel actualLevel){
        NeoForgePlayerVehicleContext.validateCompletePlayer(source);if(!uuid(source,"UUID").equals(actualFileUuid))throw invalid("Actual original player UUID differs from its actual source filename");
        if(!Identifier.parse(string(source,"Dimension")).equals(actualLevel.dimension().identifier()))throw invalid("Actual source player level differs from saved original Dimension");
        for(String key:source.keySet())if(!SOURCE_FIELDS.contains(key))throw invalid("Unknown original complete player root owner "+key);
        if(source.contains("neoforge:attachments")&&!compound(source,"neoforge:attachments").isEmpty())throw invalid("Nonempty original player attachment has no reviewed actual owner bridge");
        int selected=integer(source,"SelectedItemSlot");if(selected<0||selected>8)throw invalid("Original selected hotbar index lies outside actual source inventory");
        for(String key:Set.of("playerGameType","previousPlayerGameType"))if(source.contains(key)){int mode=integer(source,key);if(mode<0||mode>3)throw invalid("Original player mode has no actual vanilla enum identity");}
        NeoForgePlayerLivingValues.validate(source,actualLevel.registryAccess());
        if(source.contains("NeoForgeData"))NeoForgePlayerHistory.validateCoreData(compound(source,"NeoForgeData"));
        var identities=new HashSet<UUID>();identities.add(actualFileUuid);
        for(String field:Set.of("ShoulderEntityLeft","ShoulderEntityRight"))if(source.contains(field)&&!compound(source,field).isEmpty())collect(compound(source,field),identities);
        if(source.contains("RootVehicle"))collect(compound(compound(source,"RootVehicle"),"Entity"),identities);
        if(source.contains("Passengers"))for(var value:list(source,"Passengers")){if(!(value instanceof CompoundTag child))throw invalid("Malformed actual saved player passenger");collect(child,identities);}
        if(source.contains("enteredNetherPosition")){var vector=compound(source,"enteredNetherPosition");keys(vector,"original player nether vector","x","y","z");for(String axis:Set.of("x","y","z"))doubleValue(vector,axis);}
        if(source.contains("current_explosion_impact_pos"))vector(list(source,"current_explosion_impact_pos"));
        var recipes=compound(source,"recipeBook");var recipeFlags=Set.of("isGuiOpen","isFilteringCraftable","isFurnaceGuiOpen","isFurnaceFilteringCraftable","isBlastingFurnaceGuiOpen","isBlastingFurnaceFilteringCraftable","isSmokerGuiOpen","isSmokerFilteringCraftable");for(String key:recipes.keySet())if(!recipeFlags.contains(key)&&!Set.of("recipes","toBeDisplayed").contains(key))throw invalid("Unknown original player recipe-book field");for(String key:recipeFlags)if(recipes.contains(key))bool(recipes,key);for(String key:Set.of("recipes","toBeDisplayed")){var seen=new HashSet<Identifier>();for(var value:list(recipes,key)){if(!(value instanceof StringTag text)||!seen.add(Identifier.parse(text.value())))throw invalid("Malformed or duplicated actual source player recipe id");}}
        var tracker=compound(source,"warden_spawn_tracker");keys(tracker,"actual original player warden tracker","ticks_since_last_warning","warning_level","cooldown_ticks");net.minecraft.world.entity.monster.warden.WardenSpawnTracker.CODEC.parse(actualLevel.registryAccess().createSerializationContext(NbtOps.INSTANCE),tracker).getOrThrow();
        if(source.contains("ignore_fall_damage_from_current_explosion"))bool(source,"ignore_fall_damage_from_current_explosion");if(source.contains("current_impulse_context_reset_grace_time"))integer(source,"current_impulse_context_reset_grace_time");
        int spawn=0;for(String field:Set.of("SpawnX","SpawnY","SpawnZ"))if(source.contains(field)){integer(source,field);spawn++;}if(spawn!=0&&spawn!=3)throw invalid("Original player spawn lost an actual source coordinate");if(source.contains("SpawnForced"))bool(source,"SpawnForced");if(source.contains("SpawnAngle"))floating(source,"SpawnAngle");if(source.contains("SpawnDimension"))Identifier.parse(string(source,"SpawnDimension"));if(source.contains("spawn_extra_particles_on_fall"))bool(source,"spawn_extra_particles_on_fall");
    }
    private static double doubleValue(CompoundTag source,String field){if(!(source.get(field) instanceof DoubleTag value)||!Double.isFinite(value.doubleValue()))throw invalid("Original player coordinate must retain its finite source double");return value.doubleValue();}
    private static void collect(CompoundTag source,Set<UUID> global){
        record Visit(CompoundTag source,int depth){}var queue=new ArrayDeque<Visit>();queue.add(new Visit(source,0));int count=0;
        while(!queue.isEmpty()){var node=queue.removeFirst();if(node.depth>64||++count>1024||!global.add(uuid(node.source,"UUID")))throw invalid("Actual player/shoulder/vehicle/passenger body UUID has duplicate ownership or excessive bounds");if(string(node.source,"id").equals("minecraft:player"))throw invalid("A saved player cannot duplicate a physical player entity body");if(node.source.contains("Passengers"))for(var value:list(node.source,"Passengers")){if(!(value instanceof CompoundTag child))throw invalid("Malformed actual original player-owned body tree");queue.add(new Visit(child,node.depth+1));}}
    }
    private static LinkedHashMap<Integer,CompoundTag> sourceRows(ListTag source,boolean inventory){
        var result=new LinkedHashMap<Integer,CompoundTag>();for(var value:source){if(!(value instanceof CompoundTag row)||!(row.get("Slot") instanceof ByteTag slot))throw invalid("Actual original player inventory requires sparse source BYTE Slot rows");int index=Byte.toUnsignedInt(slot.byteValue());if(!(inventory?(index<36||EQUIPMENT.containsKey(index)):index<27)||result.putIfAbsent(index,row.copy())!=null)throw invalid("Original player inventory slot is duplicated or outside genuine source bounds");var item=row.copy();item.remove("Slot");keys(item,"actual original player item","id","count","components");if(item.isEmpty())throw invalid("Original player writer never saved a blank physical inventory row");}return result;
    }
    private static ListTag prepareRows(Map<Integer,CompoundTag> source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){var rows=new ListTag();source.forEach((slot,row)->{var item=row.copy();item.remove("Slot");var prepared=NeoForgeItemComponentImport.prepareItemForVanillaFixer(item,version,registries,scope.item("slot",slot));prepared.put("Slot",row.get("Slot").copy());rows.add(prepared);});return rows;}
    private static LinkedHashMap<Integer,CompoundTag> fixedRows(CompoundTag fixed,String field){
        var rows=new LinkedHashMap<Integer,CompoundTag>();for(var value:list(fixed,field)){if(!(value instanceof CompoundTag row)||!(row.get("Slot") instanceof ByteTag slot))throw invalid("Native PLAYER fixer lost an actual sparse byte slot");int index=Byte.toUnsignedInt(slot.byteValue());if(rows.putIfAbsent(index,row.copy())!=null)throw invalid("Native PLAYER fixer duplicated actual player inventory custody");}return rows;
    }
    private static CompoundTag same(CompoundTag originalRow,CompoundTag fixedItem,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        var original=originalRow.copy();original.remove("Slot");var finished=NeoForgeItemComponentImport.finishItemAfterVanillaFixer(original,fixedItem,version,registries,scope);keys(finished,"actual fixed player item","id","count","components");var ops=registries.createSerializationContext(NbtOps.INSTANCE);var stack=ItemStack.CODEC.parse(ops,finished).getOrThrow();var canonical=(CompoundTag)ItemStack.CODEC.encodeStart(ops,stack).getOrThrow();
        if(!item(original,version,registries,scope).equals(canonical))throw invalid("Actual PLAYER item did not retain its genuine source item/components/count lineage");return canonical;
    }
    private static CompoundTag withoutSlot(CompoundTag source){var copy=source.copy();copy.remove("Slot");return copy;}
    private static void inventory(Map<Integer,CompoundTag> source,CompoundTag fixed,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        var rows=fixedRows(fixed,"Inventory");var equipment=fixed.contains("equipment")?compound(fixed,"equipment").copy():new CompoundTag();EntityEquipment.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),equipment).getOrThrow();var output=new ListTag();var nativeEquipment=new CompoundTag();var expected=new HashSet<String>();
        for(var entry:source.entrySet()){
            int slot=entry.getKey();var row=entry.getValue();var leaf=scope.item("slot",slot);
            if(slot<36){var candidate=rows.remove(slot);if(candidate==null)throw invalid("Native PLAYER fixer lost genuine main inventory material");var canonical=same(row,withoutSlot(candidate),version,registries,leaf);canonical.put("Slot",row.get("Slot").copy());ItemStackWithSlot.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),canonical).getOrThrow();output.add(canonical);}
            else {String name=EQUIPMENT.get(slot);expected.add(name);var residual=rows.remove(slot);CompoundTag canonical;
                if(equipment.contains(name)){canonical=same(row,compound(equipment,name),version,registries,leaf);if(residual!=null)same(row,withoutSlot(residual),version,registries,leaf);}
                else if(residual!=null)canonical=same(row,withoutSlot(residual),version,registries,leaf);
                else throw invalid("Native PLAYER fixer lost actual original equipped inventory material");nativeEquipment.put(name,canonical);
            }
        }
        if(!rows.isEmpty())throw invalid("Native PLAYER fixer invented player inventory material or an unowned obsolete slot remainder");
        for(String key:equipment.keySet())if(!expected.contains(key)&&!ItemStack.OPTIONAL_CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),equipment.get(key)).getOrThrow().isEmpty())throw invalid("Native PLAYER fixer introduced equipment without a genuine source slot owner");
        fixed.put("Inventory",output);if(nativeEquipment.isEmpty())fixed.remove("equipment");else fixed.put("equipment",EntityEquipment.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),EntityEquipment.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),nativeEquipment).getOrThrow()).getOrThrow());
    }
    private static void ender(Map<Integer,CompoundTag> source,CompoundTag fixed,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){var rows=fixedRows(fixed,"EnderItems");var output=new ListTag();for(var entry:source.entrySet()){var row=rows.remove(entry.getKey());if(row==null)throw invalid("Actual PLAYER fixer lost original EnderItems material");var canonical=same(entry.getValue(),withoutSlot(row),version,registries,scope.item("slot",entry.getKey()));canonical.put("Slot",entry.getValue().get("Slot").copy());ItemStackWithSlot.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE),canonical).getOrThrow();output.add(canonical);}if(!rows.isEmpty())throw invalid("Actual PLAYER fixer invented EnderItems material");fixed.put("EnderItems",output);}
    private static void persistent(CompoundTag source,CompoundTag target,UUID actualPlayer,ServerLevel actualLevel){
        var data=source.contains("NeoForgeData")?compound(source,"NeoForgeData"):new CompoundTag();var history=new NeoForgePlayerHistory(3955,actualPlayer,actualLevel.dimension().identifier(),integer(source,"HurtByTimestamp"),data,list(source,"attributes"));target.put(NeoForgePlayerHistory.KEY,history.write());target.remove("NeoForgeData");target.remove("neoforge:attachments");
        if(target.contains("fabric:attachments"))throw invalid("Original PLAYER fixer cannot create independent native player attachments");var attachments=new CompoundTag();
        if(data.contains("CreateToolboxData"))attachments.put("create:toolbox_bindings",NeoForgePlayerHistory.toolbox(compound(data,"CreateToolboxData")));
        if(data.contains("TrainHat"))attachments.putBoolean("create:train_hat",true);
        for(String key:Set.of("ContraptionMountLocation","ContraptionDismountLocation"))if(data.contains(key))attachments.put(key.equals("ContraptionMountLocation")?"create:contraption_mount_location":"create:historical_contraption_dismount_location",data.get(key).copy());
        if(!attachments.isEmpty())target.put("fabric:attachments",attachments);
        if(data.contains("LavaGrounded"))target.putBoolean("CreateArmorLavaGrounded",true);if(data.contains("ContraptionGrounded"))target.putBoolean("CreatePassengerGrounded",true);
    }
    private NeoForgePlayerDataImport(){}
}
