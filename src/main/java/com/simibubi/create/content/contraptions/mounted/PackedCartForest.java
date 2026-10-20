package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.StabilizedContraption;
import com.simibubi.create.content.contraptions.data.ContraptionPickupLimiting;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Full source-owned forest/base-cart value. Decoded proposals remain off-world, unbound and unowned. */
public final class PackedCartForest {
    public static final int MAX_OWNERS=1024,MAX_DEPTH=64;
    public record Node(int parent,CompoundTag structure,Direction initial,float yaw,float pitch,StabilizedSubContraptions.Location location){
        public Node{structure=structure.copy();Objects.requireNonNull(initial);if(!Float.isFinite(yaw)||!Float.isFinite(pitch))throw new IllegalArgumentException("Nonfinite packed source pose");}
        @Override public CompoundTag structure(){return structure.copy();}
    }
    public record Base(NativeCartKind kind,NativeCartState state,Vec3 localOffset){
        public Base{Objects.requireNonNull(kind);Objects.requireNonNull(state);if(!Double.isFinite(localOffset.x)||!Double.isFinite(localOffset.y)||!Double.isFinite(localOffset.z))throw new IllegalArgumentException("Nonfinite real base-cart offset");}
    }
    private final UUID receipt;
    private final List<Node> nodes;
    private final List<Base> bases;
    private final float structuralLength;
    public PackedCartForest(UUID receipt,List<Node> nodes,List<Base> bases,float structuralLength){
        this.receipt=Objects.requireNonNull(receipt);this.nodes=List.copyOf(nodes);this.bases=List.copyOf(bases);this.structuralLength=structuralLength;
        if(nodes.isEmpty()||nodes.size()>MAX_OWNERS||bases.isEmpty()||bases.size()>2||!bases.getFirst().localOffset.equals(Vec3.ZERO))throw new IllegalArgumentException("Invalid actual packed native forest/base extent");
        if(bases.size()==2&&(!Float.isFinite(structuralLength)||structuralLength<2)||bases.size()==1&&structuralLength!=0)throw new IllegalArgumentException("Invalid packed actual structural cart link");
        var children=new HashSet<String>();
        for(int index=0;index<nodes.size();index++){
            var node=nodes.get(index);if(index==0?(node.parent!=-1||node.location!=null):(node.parent<0||node.parent>=index||node.location==null))throw new IllegalArgumentException("Packed forest must retain one actual root and ordered actual child locations");
            int depth=0;for(int parent=node.parent;parent>=0;parent=nodes.get(parent).parent)if(++depth>MAX_DEPTH)throw new IllegalArgumentException("Packed stabilized forest exceeds source depth limit");
            if(index>0&&!children.add(node.parent+":"+node.location))throw new IllegalArgumentException("Duplicate actual stabilized child location");
            if(!node.structure.getListOrEmpty("SubContraptions").isEmpty()||node.structure.getBooleanOr("GraphRecovering",false)||node.structure.contains("GraphRestorationUpdates"))throw new IllegalArgumentException("An item proposal cannot retain a second saved native owner or an unfinished recovery");
        }
    }
    public UUID receipt(){return receipt;}
    public List<Node> nodes(){return nodes;}
    public List<Base> bases(){return bases;}
    public float structuralLength(){return structuralLength;}
    public CompoundTag encode(){
        var data=new CompoundTag();data.putInt("CartItemSchema",1);data.store("CustodyReceipt",UUIDUtil.CODEC,receipt);
        var allNodes=new ListTag();for(var node:nodes){var tag=new CompoundTag();tag.putInt("Parent",node.parent);tag.put("Contraption",node.structure.copy());tag.putString("InitialOrientation",node.initial.name());tag.putFloat("Yaw",node.yaw);tag.putFloat("Pitch",node.pitch);if(node.location!=null)tag.put("Bearing",node.location.write());allNodes.add(tag);}data.put("Forest",allNodes);
        var allBases=new ListTag();for(var base:bases){var tag=new CompoundTag();tag.putString("Kind",base.kind.name());tag.put("NativeState",base.state.copyTag());tag.store("LocalOffset",Vec3.CODEC,base.localOffset);allBases.add(tag);}data.put("BaseCarts",allBases);data.putFloat("StructuralLength",structuralLength);return data;
    }
    public ItemStack createInactiveItem(ServerLevel world,net.minecraft.network.chat.Component customName)throws AssemblyException{
        var stack=new ItemStack(bases.getFirst().kind.item());stack.set(AllDataComponents.MINECRAFT_CONTRAPTION_DATA,encode());if(customName!=null)stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,customName);
        var encoded=ItemStack.CODEC.encodeStart(world.registryAccess().createSerializationContext(NbtOps.INSTANCE),stack).getOrThrow();
        if(ContraptionPickupLimiting.isTooLargeForPickup(encoded))throw new AssemblyException(net.minecraft.network.chat.Component.translatable("create.contraption.minecart_contraption_too_big"));return stack;
    }
    public static PackedCartForest parse(ItemStack actual){
        var data=actual.get(AllDataComponents.MINECRAFT_CONTRAPTION_DATA);var receipt=PackedCartData.receiptId(actual);
        if(data==null||data.getIntOr("CartItemSchema",0)!=1)throw new IllegalArgumentException("A captured native forest requires actual schema1 cart snapshots");
        var nodes=new ArrayList<Node>();for(var value:data.getListOrEmpty("Forest")){
            if(!(value instanceof CompoundTag tag)||!(tag.get("Contraption") instanceof CompoundTag structure))throw new IllegalArgumentException("Missing actual packed source structure");
            var initial=Direction.valueOf(tag.getStringOr("InitialOrientation","").toUpperCase(Locale.ROOT));
            nodes.add(new Node(tag.getInt("Parent").orElseThrow(),structure,initial,tag.getFloat("Yaw").orElseThrow(),tag.getFloat("Pitch").orElseThrow(),tag.contains("Bearing")?StabilizedSubContraptions.Location.read(tag.getCompoundOrEmpty("Bearing")):null));
            if(nodes.size()>MAX_OWNERS)throw new IllegalArgumentException("Too many actual packed forest nodes");
        }
        var bases=new ArrayList<Base>();for(var value:data.getListOrEmpty("BaseCarts")){
            if(!(value instanceof CompoundTag tag)||!(tag.get("NativeState") instanceof CompoundTag nativeState))throw new IllegalArgumentException("Missing actual native base-cart state");
            bases.add(new Base(NativeCartKind.read(tag.getStringOr("Kind","")),new NativeCartState(nativeState),tag.read("LocalOffset",Vec3.CODEC).orElseThrow()));
            if(bases.size()>2)throw new IllegalArgumentException("More base carts than the actual source mounted assembly supports");
        }
        if(bases.isEmpty()||actual.getItem()!=bases.getFirst().kind.item())throw new IllegalArgumentException("Packed base-cart type disagrees with the actual original item");
        return new PackedCartForest(receipt,nodes,bases,data.getFloat("StructuralLength").orElseThrow());
    }
    /** A migrated original plan obtains base state only from the actual primary that owns its exact item. */
    public static PackedCartForest forDeployment(ItemStack actual,net.minecraft.world.entity.vehicle.minecart.AbstractMinecart actualPrimary){
        return LegacyPackedCartPlan.isPlan(actual)?LegacyPackedCartPlan.parse(actual).realize(actual,actualPrimary):parse(actual);
    }
    /** This never binds a proposal to an accepted native entity or makes its storage available. */
    public List<Contraption> decodeInactiveStructures(ServerLevel world){
        var result=new ArrayList<Contraption>();
        for(int index=0;index<nodes.size();index++){
            var structure=Contraption.fromPackedItemNBT(world,nodes.get(index).structure());
            if(index==0?!(structure instanceof MountedContraption):!(structure instanceof StabilizedContraption))throw new IllegalArgumentException("Actual cart root/child source type mismatch");
            for(var actor:structure.getActors())if(actor.right!=null){actor.right.position=null;actor.right.motion=Vec3.ZERO;actor.right.relativeMotion=Vec3.ZERO;actor.right.firstMovement=true;}
            result.add(structure);
        }
        return List.copyOf(result);
    }
}
