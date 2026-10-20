package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.nbt.*;

/** Real inert hive capacity, not an entity template, bee type, UUID or material receipt. */
public record NeoForgeInertHiveSlots(List<NeoForgeInertHiveSlots.Slot> slots) {
    public static final String KEY="CreateOriginalInertHiveSlots",COMPONENT_KEY="create:inert_hive_slots";
    public static final NeoForgeInertHiveSlots EMPTY=new NeoForgeInertHiveSlots(List.of());
    public static final Codec<NeoForgeInertHiveSlots> CODEC=CompoundTag.CODEC.xmap(NeoForgeInertHiveSlots::read,NeoForgeInertHiveSlots::write);
    public record Slot(int sourceIndex,int index,int ticksInHive,int minTicksInHive,boolean entityDataPresent){}
    public NeoForgeInertHiveSlots {
        slots=List.copyOf(slots);int previous=-1;var sourceIndexes=new HashSet<Integer>();
        for(var slot:slots){
            if(slot.sourceIndex()<0||slot.index()<=previous||!sourceIndexes.add(slot.sourceIndex()))throw invalid("Invalid/duplicate original inert hive slot index");
            previous=slot.index();
        }
    }
    public boolean isEmpty(){return slots.isEmpty();}
    public int size(){return slots.size();}
    public void validateNativeCount(int genuineBeeCount){
        int total=Math.addExact(genuineBeeCount,size());
        if(genuineBeeCount<0||!isEmpty()&&slots.getLast().index()>=total)throw invalid("Inert hive slot is outside actual combined native occupancy");
    }
    public NeoForgeInertHiveSlots tick(){
        if(isEmpty())return this;
        return new NeoForgeInertHiveSlots(slots.stream().map(slot->new Slot(slot.sourceIndex(),slot.index(),slot.ticksInHive()+1,slot.minTicksInHive(),slot.entityDataPresent())).toList());
    }
    /** Vanilla only removes surviving BeeData objects or appends new ones; never reorders retained bees. */
    public NeoForgeInertHiveSlots afterNativeChange(List<?> previous,List<?> current){
        if(isEmpty())return this;validateNativeCount(previous.size());
        var retained=new ArrayList<Object>();for(var old:previous)if(containsIdentity(current,old))retained.add(old);
        int cursor=0;boolean appended=false;
        for(var now:current){
            if(containsIdentity(previous,now)){
                if(appended||cursor>=retained.size()||retained.get(cursor++)!=now)throw invalid("Native hive reordered actual retained bee owners");
            }else appended=true;
        }
        if(cursor!=retained.size())throw invalid("Native hive lost a retained bee owner during inert reconciliation");
        var next=new ArrayList<Slot>();
        for(int inert=0;inert<slots.size();inert++){
            var slot=slots.get(inert);int genuineBefore=slot.index()-inert,removed=0;
            for(int index=0;index<genuineBefore;index++)if(!containsIdentity(current,previous.get(index)))removed++;
            next.add(new Slot(slot.sourceIndex(),slot.index()-removed,slot.ticksInHive(),slot.minTicksInHive(),slot.entityDataPresent()));
        }
        var result=new NeoForgeInertHiveSlots(next);result.validateNativeCount(current.size());return result;
    }
    private static boolean containsIdentity(List<?> list,Object value){for(var element:list)if(element==value)return true;return false;}

    public static boolean isInertOriginal(CompoundTag row){
        keys(row,"original hive occupant","entity_data","ticks_in_hive","min_ticks_in_hive");
        integer(row,"ticks_in_hive");integer(row,"min_ticks_in_hive");
        return !row.contains("entity_data")||compound(row,"entity_data").isEmpty();
    }
    public static NeoForgeInertHiveSlots fromOriginal(ListTag original){
        var slots=new ArrayList<Slot>();
        for(int index=0;index<original.size();index++){
            if(!(original.get(index) instanceof CompoundTag row))throw invalid("Malformed original hive occupant");
            if(isInertOriginal(row))slots.add(new Slot(index,index,integer(row,"ticks_in_hive"),integer(row,"min_ticks_in_hive"),row.contains("entity_data")));
        }
        var result=new NeoForgeInertHiveSlots(slots);result.validateNativeCount(original.size()-result.size());return result;
    }
    /** Source row indexes remain the scope of genuine templates even after inert rows are separated. */
    public static ListTag realOriginalRows(ListTag original){
        var result=new ListTag();for(Tag value:original){if(!(value instanceof CompoundTag row))throw invalid("Malformed original hive row");if(!isInertOriginal(row))result.add(row.copy());}return result;
    }
    public ListTag combinedRows(ListTag genuine){
        validateNativeCount(genuine.size());var result=new ListTag();int inert=0,real=0;
        for(int index=0;index<genuine.size()+size();index++){
            if(inert<size()&&slots.get(inert).index()==index){
                var slot=slots.get(inert++);var row=new CompoundTag();
                row.putInt("ticks_in_hive",slot.ticksInHive());row.putInt("min_ticks_in_hive",slot.minTicksInHive());
                if(slot.entityDataPresent())row.put("entity_data",new CompoundTag());result.add(row);
            }else result.add(genuine.get(real++).copy());
        }
        return result;
    }
    public CompoundTag write(){
        var result=new CompoundTag();result.putInt("source_data_version",3955);var rows=new ListTag();
        for(var slot:slots){var row=new CompoundTag();row.putInt("source_index",slot.sourceIndex());row.putInt("index",slot.index());row.putInt("ticks_in_hive",slot.ticksInHive());row.putInt("min_ticks_in_hive",slot.minTicksInHive());row.putBoolean("entity_data_present",slot.entityDataPresent());rows.add(row);}
        result.put("slots",rows);return result;
    }
    public static NeoForgeInertHiveSlots read(CompoundTag source){
        keys(source,"native inert hive owner","source_data_version","slots");
        if(integer(source,"source_data_version")!=3955)throw invalid("Unknown original inert hive provenance version");var slots=new ArrayList<Slot>();
        for(Tag value:list(source,"slots")){
            if(!(value instanceof CompoundTag row))throw invalid("Malformed native inert hive slot");
            keys(row,"native inert hive slot","source_index","index","ticks_in_hive","min_ticks_in_hive","entity_data_present");
            slots.add(new Slot(integer(row,"source_index"),integer(row,"index"),integer(row,"ticks_in_hive"),integer(row,"min_ticks_in_hive"),bool(row,"entity_data_present")));
        }
        return new NeoForgeInertHiveSlots(slots);
    }
}
