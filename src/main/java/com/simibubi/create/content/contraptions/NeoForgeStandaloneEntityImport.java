package com.simibubi.create.content.contraptions;

import static com.simibubi.create.foundation.utility.NeoForgeSaveCodecs.*;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.*;
import com.simibubi.create.content.contraptions.minecart.capability.NeoForgeMinecartImport;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockEntity.CartMovementMode;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.level.block.state.BlockState;

/** Pure complete saved mixed source trees, before actual native factory dispatch. No replacement entity UUIDs. */
public final class NeoForgeStandaloneEntityImport {
    private static final Set<String> BASE=Set.of("id","UUID","Pos","Motion","Rotation","FallDistance","Fire","Air","OnGround","Invulnerable","PortalCooldown",
        "CustomName","CustomNameVisible","Silent","NoGravity","Glowing","HasVisualFire","Tags","TicksFrozen","NeoForgeData","neoforge:attachments");
    private static final Set<String> COMMON_BODY=Set.of("Type","Blocks","CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions","Anchor","Stalled","BottomlessSupply","BoundsFront","items","fluids");
    private record Node(CompoundTag source,Node parent,int depth){}
    private record Context(int version,HolderLookup.Provider registries,Map<UUID,Node> nodes,Map<UUID,CompoundTag> output,NeoForgePlayerVehicleContext playerVehicles,boolean inertRegionSeats){}
    public static ListTag convertRoots(ListTag originalRoots,int version,HolderLookup.Provider registries){return convertRootsInternal(originalRoots,version,registries,null,false);}
    /** Source region seat maps retain UUID references; they never instantiate a missing passenger. */
    static ListTag convertRegionRoots(ListTag originalRoots,int version,HolderLookup.Provider registries){return convertRootsInternal(originalRoots,version,registries,null,true);}
    public static ListTag convertRoots(ListTag originalRoots,int version,HolderLookup.Provider registries,NeoForgePlayerVehicleContext actualPlayerVehicles,Identifier actualDimension){
        Objects.requireNonNull(actualPlayerVehicles).requireDimension(actualDimension);return convertRootsInternal(originalRoots,version,registries,actualPlayerVehicles,false);
    }
    /** Raw complete player import owns player inventory/DFU; this only converts that real saved RootVehicle tree. */
    public static CompoundTag playerRootVehicle(CompoundTag actualCompleteOriginalPlayer,int version,HolderLookup.Provider registries){
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown actual player RootVehicle version");
        var dimension=Identifier.parse(string(actualCompleteOriginalPlayer,"Dimension"));var players=new ListTag();players.add(actualCompleteOriginalPlayer);
        var context=NeoForgePlayerVehicleContext.fromOriginalPlayers(players,dimension);var result=context.rootVehicle(uuid(actualCompleteOriginalPlayer,"UUID"));var roots=new ListTag();roots.add(compound(result,"Entity"));
        result.put("Entity",convertRoots(roots,version,registries,context,dimension).getFirst());return result;
    }
    private static ListTag convertRootsInternal(ListTag originalRoots,int version,HolderLookup.Provider registries,NeoForgePlayerVehicleContext playerVehicles,boolean inertRegionSeats){
        if(version!=NeoForgeSavedDataImport.SOURCE_DATA_VERSION)throw invalid("Unknown original complete saved entity-tree version");
        var nodes=new LinkedHashMap<UUID,Node>();var roots=new ArrayList<Node>();var queue=new ArrayDeque<Node>();
        for(var entry:originalRoots){if(!(entry instanceof CompoundTag tag))throw invalid("Malformed original saved root entity");var node=new Node(tag,null,0);roots.add(node);queue.add(node);}
        while(!queue.isEmpty()){
            var node=queue.removeFirst();if(node.depth>64||nodes.size()>=1024)throw invalid("Original complete entity forest exceeds supported bounds");
            if(nodes.putIfAbsent(uuid(node.source,"UUID"),node)!=null)throw invalid("Original entity UUID has two actual forest owners");
            if(node.source.contains("Passengers"))for(var value:list(node.source,"Passengers")){
                if(!(value instanceof CompoundTag child))throw invalid("Malformed actual source passenger");queue.add(new Node(child,node,node.depth+1));
            }
        }
        if(playerVehicles!=null)playerVehicles.requireNoPlayerBodies(nodes.keySet());
        var output=new LinkedHashMap<UUID,CompoundTag>();var context=new Context(version,registries,nodes,output,playerVehicles,inertRegionSeats);
        for(var node:nodes.values())output.put(uuid(node.source,"UUID"),convertNode(node,context));
        // Only after every complete original node validated are actual parent/child rows installed.
        for(var node:nodes.values())if(node.source.contains("Passengers")){
            var children=new ListTag();for(var child:list(node.source,"Passengers"))children.add(output.get(uuid((CompoundTag)child,"UUID")));
            output.get(uuid(node.source,"UUID")).put("Passengers",children);
        }
        var result=new ListTag();for(var root:roots)result.add(output.get(uuid(root.source,"UUID")));return result;
    }
    public static CompoundTag convertTree(CompoundTag original,int version,HolderLookup.Provider registries){var roots=new ListTag();roots.add(original);return (CompoundTag)convertRoots(roots,version,registries).getFirst();}
    private static CompoundTag convertNode(Node node,Context context){
        var source=node.source;var singleton=source.copy();singleton.remove("Passengers");var type=Identifier.parse(string(source,"id"));
        if(type.getNamespace().equals("minecraft")){
            CompoundTag convertedAttachment=null;
            if(singleton.contains("neoforge:attachments")&&!compound(singleton,"neoforge:attachments").isEmpty()){
                if(!Set.of("minecart","chest_minecart","furnace_minecart","hopper_minecart","tnt_minecart","command_block_minecart","spawner_minecart").contains(type.getPath()))
                    throw invalid("Original noncart extension owner needs its reviewed source bridge");
                var attachments=compound(singleton,"neoforge:attachments");keys(attachments,"original minecart attachments","create:minecart_controller");
                convertedAttachment=NeoForgeMinecartImport.convertSource(compound(attachments,"create:minecart_controller"),uuid(source,"UUID"),context.registries);
                singleton.remove("neoforge:attachments");
            }
            var target=NeoForgeSavedVanillaEntityImport.convert(singleton,context.version,context.registries);
            if(convertedAttachment!=null){var attachments=new CompoundTag();attachments.put("create:minecart_controller",convertedAttachment);target.put("fabric:attachments",attachments);}
            return target;
        }
        if(!type.getNamespace().equals("create"))throw invalid("Original addon saved entity needs its actual typed owner converter: "+type);
        return switch(type.getPath()){
            case "contraption","stationary_contraption","gantry_contraption"->contraption(node,context);
            case "seat"->{checkKeys(singleton,Set.of());yield base(singleton,context);}
            case "super_glue"->{
                checkKeys(singleton,Set.of("From","To"));var from=vector(list(singleton,"From"));var to=vector(list(singleton,"To"));
                for(int axis=0;axis<3;axis++){double low=((DoubleTag)from.get(axis)).doubleValue(),high=((DoubleTag)to.get(axis)).doubleValue();if(high<low||high-low>64)throw invalid("Original glue extent cannot reach its actual native finite64 bound");}
                yield base(singleton,context);
            }
            case "crafting_blueprint","potato_projectile","package"->{
                checkKeys(singleton,NeoForgeAdditionalEntityImport.sourceFields(type.getPath()));
                NeoForgeAdditionalEntityImport.validateOwnPersistentData(type.getPath(),singleton);
                var genuineBase=new CompoundTag();for(var key:BASE)if(!key.equals("NeoForgeData")&&singleton.contains(key))genuineBase.put(key,singleton.get(key).copy());
                yield NeoForgeAdditionalEntityImport.convert(type.getPath(),singleton,base(genuineBase,context),context.version,context.registries);
            }
            case "carriage_contraption"->throw invalid("A globally owned original railway body must use its coordinated saved Train/carriage source bridge");
            default->throw invalid("Original Create saved entity needs its complete typed additional/base schema: "+type);
        };
    }
    private static CompoundTag base(CompoundTag source,Context context){
        for(String field:Set.of("Pos","Motion","Rotation","FallDistance","Fire","Air","OnGround","Invulnerable","PortalCooldown"))if(!source.contains(field))throw invalid("Complete original custom entity lost actual base state "+field);
        NeoForgeVanillaEntityImport.validateBase(source);var actualId=uuid(source,"UUID");
        for(String extension:Set.of("NeoForgeData","neoforge:attachments"))if(source.contains(extension)&&!compound(source,extension).isEmpty())throw invalid("Original custom entity extension needs its actual typed owner bridge");
        var result=NeoForgeContraptionGeometryImport.fixed(source,References.ENTITY,context.version);
        if(!actualId.equals(uuid(result,"UUID")))throw invalid("Historical conversion changed a genuine custom entity UUID");
        result.putDouble("fall_distance",floating(source,"FallDistance"));result.remove("FallDistance");
        if(source.contains("CustomName"))result.put("CustomName",NeoForgeTextComponentImport.flattened((StringTag)source.get("CustomName"),context.version,context.registries,NeoForgeMigrationScope.entity(actualId).item("customname",0)));
        return result;
    }
    private static void checkKeys(CompoundTag source,Set<String> additional){for(String key:source.keySet())if(!BASE.contains(key)&&!additional.contains(key))throw invalid("Unknown original custom entity field "+key);}
    private static CompoundTag contraption(Node node,Context context){
        var source=node.source;var singleton=source.copy();singleton.remove("Passengers");String id=string(source,"id");var extra=new HashSet<String>(Set.of("Contraption","Stalled","Initialized"));
        if(id.equals("create:stationary_contraption"))extra.addAll(Set.of("ControllerRelative","Axis","Angle"));
        else if(id.equals("create:gantry_contraption"))extra.addAll(Set.of("GantryAxis","SequencedOffsetLimit"));
        else extra.addAll(Set.of("InitialOrientation","CachedMotion","ForceYaw","Placed","Yaw","Pitch","OnCoupling"));
        checkKeys(singleton,extra);bool(source,"Stalled");bool(source,"Initialized");
        var body=compound(source,"Contraption");String subtype=string(body,"Type");var allowed=new HashSet<String>(COMMON_BODY);
        switch(subtype){
            case "create:mounted"->allowed.add("RotationMode");
            case "create:bearing"->allowed.addAll(Set.of("Sails","Facing"));
            case "create:stabilized","create:gantry"->allowed.add("Facing");
            case "create:clockwork"->allowed.addAll(Set.of("facing","offset","HandType"));
            case "create:piston"->allowed.addAll(Set.of("InitialLength","ExtensionLength","Orientation"));
            case "create:pulley"->allowed.add("InitialOffset");
            case "create:elevator"->allowed.addAll(Set.of("InitialOffset","Arrived","Column","ContactY","MaxContactY","MinContactY"));
            default->throw invalid("Unreviewed actual standalone contraption subtype "+subtype);
        }
        for(String key:body.keySet())if(!allowed.contains(key))throw invalid("Unknown original standalone body field "+key);
        var actualOwner=uuid(source,"UUID");var geometry=NeoForgeContraptionGeometryImport.convert(body,context.version,context.registries,NeoForgeMigrationScope.entity(actualOwner));
        var target=base(singleton,context);var converted=geometry.tag();
        if(id.equals("create:stationary_contraption")){
            if(!Set.of("create:bearing","create:clockwork","create:piston","create:pulley","create:elevator").contains(subtype))throw invalid("Original controlled entity has an incompatible actual body subtype");
            var relative=pos(compound(source,"ControllerRelative"));var originalPosition=vector(list(source,"Pos"));var block=blockPosition(originalPosition);
            var actualController=new BlockPos(Math.addExact(block.getX(),relative.getX()),Math.addExact(block.getY(),relative.getY()),Math.addExact(block.getZ(),relative.getZ()));
            target.store("ControllerRelative",BlockPos.CODEC,actualController.subtract(block));
            float angle=floating(source,"Angle");if(source.contains("Axis"))target.putInt("Axis",Direction.Axis.valueOf(string(source,"Axis")).ordinal());else if(angle!=0)throw invalid("Original axis-less translation cannot acquire a native rotation");
            target.putString("id","create:controlled_contraption");
            if(subtype.equals("create:piston")){
                var direction=direction(body,"Orientation");var anchor=pos(compound(body,"Anchor"));
                if(!actualController.relative(direction).equals(anchor))throw invalid("Actual source piston controller and captured assembly anchor disagree");converted.store("PistonController",BlockPos.CODEC,actualController);
            }
        }else if(id.equals("create:gantry_contraption")){
            if(!subtype.equals("create:gantry"))throw invalid("Original gantry entity needs its genuine gantry carriage body");
            Direction.valueOf(string(source,"GantryAxis"));if(source.contains("SequencedOffsetLimit")&&number(source,"SequencedOffsetLimit")<0)throw invalid("Original saved gantry sequence limit is negative");
        }else{
            if(!Set.of("create:mounted","create:stabilized").contains(subtype))throw invalid("Original oriented entity has an incompatible actual body");
            floating(source,"Yaw");floating(source,"Pitch");bool(source,"Placed");if(source.contains("InitialOrientation"))Direction.valueOf(string(source,"InitialOrientation"));
            if(source.contains("ForceYaw"))target.putFloat("Yaw",floating(source,"ForceYaw"));

            if(source.contains("CachedMotion")){var zero=new ListTag();for(int axis=0;axis<3;axis++)zero.add(DoubleTag.valueOf(0));target.put("Motion",zero);}
            target.putString("id","create:oriented_contraption");
            if(subtype.equals("create:mounted")){
                CartMovementMode.valueOf(string(body,"RotationMode"));
                if(node.parent==null||!Set.of("minecraft:minecart","minecraft:chest_minecart","minecraft:furnace_minecart").contains(string(node.parent.source,"id")))throw invalid("Original mounted root has no actual containing saved native base cart");
                target.store("MountedCart",UUIDUtil.CODEC,uuid(node.parent.source,"UUID"));
                var actualConvertedCart=context.output.get(uuid(node.parent.source,"UUID"));
                if(actualConvertedCart==null)throw invalid("Actual saved cart parent must finish pure conversion before its real mounted child");
                NeoForgeMountedContinuationImport.convert(node.parent.source,source,actualConvertedCart,target,context.version,context.registries);
                if(source.contains("OnCoupling"))uuid(source,"OnCoupling");
            }else{
                if(node.parent==null||!compound(node.parent.source,"Contraption").contains("SubContraptions"))throw invalid("Original stabilized body lacks its actual source parent");
                var location=childLocation(node.parent.source,actualOwner);if(location.facing()!=direction(body,"Facing"))throw invalid("Original stabilized body and genuine parent bearing facing disagree");
                target.store("ParentContraption",UUIDUtil.CODEC,uuid(node.parent.source,"UUID"));target.put("ParentBearing",location.write());
                NeoForgeStabilizedContinuationImport.convert(node.parent.source,source,target,context.version,context.registries);
                if(source.contains("OnCoupling"))throw invalid("Original stabilized child cannot also own a native cart coupling");
            }
        }
        subtype(body,converted,subtype);topology(node,geometry,context,converted);
        var nativeType=Identifier.parse(string(target,"id"));if(!BuiltInRegistries.ENTITY_TYPE.containsKey(nativeType))throw invalid("Actual native custom type is not registered");
        target.remove("Initialized");target.remove("Placed");target.remove("ForceYaw");target.remove("CachedMotion");target.put("Contraption",converted);return target;
    }
    private static BlockPos blockPosition(ListTag source){double[] coordinates=new double[3];for(int i=0;i<3;i++){coordinates[i]=((DoubleTag)source.get(i)).doubleValue();if(coordinates[i]<-30000000||coordinates[i]>30000000)throw invalid("Original entity position exceeds genuine native finite coordinate bounds");}return BlockPos.containing(coordinates[0],coordinates[1],coordinates[2]);}
    private static Direction direction(CompoundTag source,String field){int value=integer(source,field);if(value<0||value>5)throw invalid("Original source direction is not an actual3D enum value");return Direction.from3DDataValue(value);}
    private static void subtype(CompoundTag source,CompoundTag target,String subtype){
        switch(subtype){
            case "create:bearing"->{if(integer(source,"Sails")<0)throw invalid("Negative original sail count");direction(source,"Facing");}
            case "create:stabilized","create:gantry"->direction(source,"Facing");
            case "create:clockwork"->{direction(source,"facing");int offset=integer(source,"offset");if(offset<0||offset>15||!Set.of("HOUR","MINUTE").contains(string(source,"HandType")))throw invalid("Invalid original clockwork hand/offset");}
            case "create:piston"->{int length=integer(source,"ExtensionLength"),initial=integer(source,"InitialLength");if(length<1||initial<0||initial>length)throw invalid("Invalid original piston saved extent");direction(source,"Orientation");}
            case "create:pulley","create:elevator"->{
                if(integer(source,"InitialOffset")<0)throw invalid("Negative original pulley offset");
                if(subtype.equals("create:elevator")){bool(source,"Arrived");int min=integer(source,"MinContactY"),max=integer(source,"MaxContactY");integer(source,"ContactY");if(min>max)throw invalid("Invalid original elevator contact range");var column=compound(source,"Column");keys(column,"elevator column","X","Z","Side");integer(column,"X");integer(column,"Z");if(!Direction.valueOf(string(column,"Side")).getAxis().isHorizontal())throw invalid("Original elevator contact side is vertical");}
            }
            default->{}
        }
    }
    private static StabilizedSubContraptions.Location childLocation(CompoundTag parent,UUID child){
        StabilizedSubContraptions.Location found=null;for(var value:list(compound(parent,"Contraption"),"SubContraptions")){
            if(!(value instanceof CompoundTag row))throw invalid("Malformed original child body reference");keys(row,"stabilized child reference","Id","Location");
            var id=uuid(row,"Id");var location=compound(row,"Location");keys(location,"stabilized child location","Pos","Face");
            var actual=new StabilizedSubContraptions.Location(pos(compound(location,"Pos")),Direction.valueOf(string(location,"Face")));
            if(id.equals(child)){if(found!=null)throw invalid("Original child UUID has two bearing references");found=actual;}
        }if(found==null)throw invalid("Actual source child has no original parent bearing reference");return found;
    }
    private static void topology(Node node,NeoForgeContraptionGeometryImport.Geometry geometry,Context context,CompoundTag target){
        var body=compound(node.source,"Contraption");var direct=new HashSet<UUID>();if(node.source.contains("Passengers"))for(var child:list(node.source,"Passengers"))direct.add(uuid((CompoundTag)child,"UUID"));
        var occupied=new HashSet<Integer>();var classified=new HashSet<UUID>();var unmaterializedReferences=new HashSet<UUID>();
        for(var value:list(body,"Passengers")){
            if(!(value instanceof CompoundTag seat))throw invalid("Malformed original seat passenger reference");keys(seat,"seat passenger reference","Id","Seat");var id=uuid(seat,"Id");int index=integer(seat,"Seat");
            boolean genuineExternalPlayer=context.playerVehicles!=null&&context.playerVehicles.allowsSeat(id,node.source);
            boolean inertRegionReference=context.inertRegionSeats&&!context.nodes.containsKey(id);
            if((genuineExternalPlayer||inertRegionReference)&&!direct.contains(id))unmaterializedReferences.add(id);
            if(index<0||index>=geometry.seats().size()||!occupied.add(index)||!direct.contains(id)&&!genuineExternalPlayer&&!inertRegionReference||!classified.add(id))throw invalid("Original seat reference has no unique actual saved passenger or complete saved player RootVehicle owner");
        }
        var children=new ListTag();var locations=new HashSet<StabilizedSubContraptions.Location>();
        for(var value:list(body,"SubContraptions")){
            var child=(CompoundTag)value;var id=uuid(child,"Id");var location=childLocation(node.source,id);var actual=context.nodes.get(id);
            var state=geometry.blocks().get(location.bearing());
            if(!direct.contains(id)||!classified.add(id)||!locations.add(location)||actual==null||!string(actual.source,"id").equals("create:contraption")||!string(compound(actual.source,"Contraption"),"Type").equals("create:stabilized")
                ||state==null||!state.is(AllBlocks.MECHANICAL_BEARING)||state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)!=location.facing())throw invalid("Original stabilized child has no unique actual saved body and matching captured bearing");
            var next=new CompoundTag();next.store("Id",UUIDUtil.CODEC,id);next.put("Location",location.write());children.add(next);
        }
        classified.removeAll(unmaterializedReferences);if(!classified.equals(direct))throw invalid("Original body has an unclassified actual saved passenger owner");target.put("SubContraptions",children);
    }
    private NeoForgeStandaloneEntityImport(){}
}
