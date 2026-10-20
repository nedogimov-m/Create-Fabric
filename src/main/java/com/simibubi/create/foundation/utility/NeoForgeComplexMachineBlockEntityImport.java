package com.simibubi.create.foundation.utility;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.kinetics.deployer.DeployerInventory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Original machine cycles and actual player slots; bodies and connected inventories reconnect in the real world. */
public final class NeoForgeComplexMachineBlockEntityImport {
    private static final Set<String> TYPES=Set.of("andesite_tunnel","elevator_pulley","elevator_contact","portable_storage_interface","portable_fluid_interface","mechanical_press","mechanical_mixer","deployer");
    public static Optional<CompoundTag> convert(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        Objects.requireNonNull(type,"type");if(!type.getNamespace().equals("create")||!TYPES.contains(type.getPath()))return Optional.empty();
        check(version,registries);var result=Objects.requireNonNull(source,"source").copy();
        switch(type.getPath()){
            case "andesite_tunnel"->tunnel(source,result);
            case "portable_storage_interface","portable_fluid_interface"->{integer(source,"Timer");floating(source,"Distance");bool(source,"Powered");}
            case "elevator_pulley"->elevator(source,result,version,registries,scope);
            case "elevator_contact"->{
                string(source,"ShortName");string(source,"LongName");bool(source,"Activate");DoorControl.valueOf(string(source,"DoorControl"));
                if(source.contains("LastReportedCurrentFloor"))string(source,"LastReportedCurrentFloor");
                if(source.contains("ColumnTarget"))integer(source,"ColumnTarget");
                if(source.contains("ColumnActive")&&(!source.contains("ColumnTarget")||!bool(source,"ColumnActive")))throw invalid("Original active column marker requires its real target and is only written true");
                // There is no original column-owner UUID. A real matching loaded cabin may adopt the marker.
            }
            case "mechanical_press"->{
                bool(source,"Running");bool(source,"Finished");integer(source,"Ticks");int mode=integer(source,"Mode");
                if(mode<0||mode>2)throw invalid("Unknown original pressing mode");if(source.contains("TracksCreated"))integer(source,"TracksCreated");
                // Original has no paid/Pressed receipt; native derives the already-passed impact boundary from actual Ticks.
            }
            case "mechanical_mixer"->{
                boolean running=bool(source,"Running");int ticks=integer(source,"Ticks");
                // Source recipe/countdown are transient. Only actual loaded basin stock can bind a new payable recipe.
                result.putBoolean("OriginalProcessing",running&&ticks<=20);
            }
            case "deployer"->deployer(source,result,version,registries,scope);
            default->throw new AssertionError(type);
        }return Optional.of(result);
    }
    public static Optional<CompoundTag> convertSafe(Identifier type,CompoundTag source,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        if(!type.getNamespace().equals("create")||!Set.of("andesite_tunnel","deployer").contains(type.getPath()))return Optional.empty();check(version,registries);
        var result=source.copy();
        if(type.getPath().equals("andesite_tunnel")){keys(source,"partial tunnel safe writer","components","Flaps","Sides");tunnel(source,result);}
        else {keys(source,"partial Deployer safe writer","components","Mode","Filter","FilterAmount","UpTo");if(!Set.of("USE","PUNCH").contains(string(source,"Mode")))throw invalid("Invalid original safe Deployer mode");}
        return Optional.of(result);
    }
    private static void tunnel(CompoundTag source,CompoundTag result){
        for(String key:List.of("Flaps","Sides")){
            var values=new LinkedHashSet<Integer>();
            for(Tag tag:list(source,key)){
                if(!(tag instanceof IntTag value)||value.intValue()<0||value.intValue()>5)throw invalid("Invalid original tunnel direction ordinal");
                values.add(value.intValue());
            }
            var converted=new ListTag();for(int value:values)converted.add(IntTag.valueOf(value));result.put(key,converted);
        }
    }
    private static void elevator(CompoundTag source,CompoundTag result,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        int initial=integer(source,"InitialOffset");float offset=floating(source,"Offset");boolean running=bool(source,"Running");bool(source,"Waiting");
        if(initial<0||offset>Integer.MAX_VALUE-1.0||offset<0&&(offset!=-1||running))throw invalid("Unrepresentable original elevator geometry");
        if(source.contains("SequencedOffsetLimit")&&number(source,"SequencedOffsetLimit")<0)throw invalid("Negative original elevator movement limit");
        if(source.contains("ScrollValue"))throw invalid("Actual original elevator overrides addBehaviours and has no movement-mode scroll behaviour");
        if(source.contains("MirrorParent")){
            result.store("OriginalMirrorParent",BlockPos.CODEC,pos(compound(source,"MirrorParent")));result.remove("MirrorParent");result.putFloat("Offset",0);
        }
        if(source.contains("MirrorChildren")){
            var positions=new LinkedHashSet<BlockPos>();for(Tag value:list(source,"MirrorChildren")){
                if(!(value instanceof CompoundTag row))throw invalid("Malformed original elevator mirror child");keys(row,"elevator mirror child","Pos");positions.add(pos(compound(row,"Pos")));
            }result.store("OriginalMirrorChildren",BlockPos.CODEC.listOf(),List.copyOf(positions));result.remove("MirrorChildren");
        }
        if(source.contains("LastException")){
            var old=compound(source,"LastException");keys(old,"elevator assembly exception","Component","Position");
            var fixed=NeoForgeTextComponentImport.flattened(StringTag.valueOf(string(old,"Component")),version,registries,child(scope,"assembly_exception_text",0));
            var ops=registries.createSerializationContext(NbtOps.INSTANCE);var text=ComponentSerialization.CODEC.parse(ops,fixed).getOrThrow();var next=new CompoundTag();
            next.putString("Component",ComponentSerialization.CODEC.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE),text).getOrThrow().toString());
            if(old.contains("Position")){if(!(old.get("Position") instanceof LongTag))throw invalid("Original assembly exception position must be its actual packed long");next.put("Position",old.get("Position").copy());}
            var wrapper=new CompoundTag();wrapper.put("LastException",next);result.put("AssemblyError",wrapper);result.remove("LastException");
        }
    }
    private static void deployer(CompoundTag source,CompoundTag result,int version,HolderLookup.Provider registries,NeoForgeMigrationScope scope){
        if(!Set.of("USE","PUNCH").contains(string(source,"Mode"))||!Set.of("WAITING","EXPANDING","RETRACTING","DUMPING").contains(string(source,"State")))throw invalid("Invalid original Deployer mode/state");
        integer(source,"Timer");bool(source,"Powered");if(source.contains("Owner"))uuid(source,"Owner");
        var held=ItemStack.EMPTY;var inventory=new ArrayList<DeployerInventory.Entry>();var occupied=new HashSet<Integer>();var ops=registries.createSerializationContext(NbtOps.INSTANCE);
        if(source.contains("Inventory"))for(Tag tag:list(source,"Inventory")){
            if(!(tag instanceof CompoundTag row))throw invalid("Malformed original player inventory row");keys(row,"player inventory row","Slot","id","count","components");
            if(!(row.get("Slot") instanceof ByteTag value))throw invalid("Original player inventory address must be its actual byte");int original=value.byteValue()&255;
            int slot=DeployerInventory.originalSlot(original);if(!occupied.add(slot))throw invalid("Duplicate original Deployer inventory slot");
            var stack=row.copy();stack.remove("Slot");var converted=item(stack,version,registries,child(scope,"player_inventory",original));var decoded=ItemStack.OPTIONAL_CODEC.parse(ops,converted).getOrThrow();
            if(decoded.isEmpty())throw invalid("Original sparse player inventory row cannot be empty");if(slot==0)held=decoded;else inventory.add(new DeployerInventory.Entry(slot,decoded));
        }
        if(source.contains("HeldItem")){
            // An active original actor removes captured Inventory after loading its real player.
            // Data.HeldItem is authoritative there; stale captured HeldItem never becomes extra stock.
            item(compound(source,"HeldItem"),version,registries,child(scope,"held_item_mirror",0));
        }
        result.put("HeldItem",ItemStack.OPTIONAL_CODEC.encodeStart(ops,held).getOrThrow());result.putInt("SelectedSlot",0);result.put("PlayerInventory",DeployerInventory.CODEC.encodeStart(ops,DeployerInventory.checked(inventory,0)).getOrThrow());result.remove("Inventory");
        if(source.contains("Overflow")){
            var converted=new ListTag();int index=0;for(Tag tag:list(source,"Overflow")){
                if(!(tag instanceof CompoundTag stack))throw invalid("Malformed original Deployer overflow stack");var fixed=item(stack,version,registries,child(scope,"overflow",index++));if(!fixed.isEmpty())converted.add(fixed);
            }result.put("Overflow",converted);
        }
    }
    private static NeoForgeMigrationScope child(NeoForgeMigrationScope scope,String role,int index){return scope==null?null:scope.item(role,index);}
    private static void check(int version,HolderLookup.Provider registries){if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original complex-machine version");Objects.requireNonNull(registries,"registries");}
    private NeoForgeComplexMachineBlockEntityImport(){}
}
