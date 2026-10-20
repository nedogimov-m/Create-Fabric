package com.simibubi.create.content.contraptions;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.mounted.PackedCartData;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Private pure source validation across serialization callbacks; these retained views never become a second owner. */
final class OwnedCartStructureSnapshot {
    private record View(StorageView<?> actual,Object expected,long amount,long capacity){
        boolean matches(){return actual.getAmount()==amount&&actual.getCapacity()==capacity&&Objects.equals(actual.getResource(),expected);}
    }
    private record Stack(java.util.function.Supplier<ItemStack> current,ItemStack actual,ItemStack expected){boolean matches(){return current.get()==actual&&ItemStack.matches(actual,expected);}}
    private record Actor(MovementContext actual,Object runtime,CompoundTag data,CompoundTag blockEntityData,List<Stack> liveHand,int selected,int overflow){
        boolean matches(){return actual.temporaryData==runtime&&actual.data.equals(data)&&actual.blockEntityData.equals(blockEntityData)&&(!(runtime instanceof DeployerFakePlayer player)||player.getInventory().getSelectedSlot()==selected&&player.movingOverflow.size()==overflow)&&liveHand.stream().allMatch(Stack::matches);}
    }
    private final OrientedContraptionEntity owner;
    private final Contraption structure;
    private final Vec3 position;
    private final float yaw,pitch;
    private final UUID base,coupling,parent;
    private final Map<UUID,StabilizedSubContraptions.Location> children;
    private final Map<BlockPos,Object> items,fluids;
    private final Map<BlockPos,CompoundTag> blockData;
    private final Map<BlockPos,net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo> blocks;
    private final List<View> views;
    private final List<Actor> actors;
    private OwnedCartStructureSnapshot(OrientedContraptionEntity owner){
        this.owner=owner;structure=owner.getContraption();position=owner.position();yaw=owner.getViewYRot(1);pitch=owner.getViewXRot(1);base=owner.getMountedCartId();coupling=owner.getCouplingId();parent=owner.getParentId();
        children=new LinkedHashMap<>(structure.getStabilizedSubContraptions().children());items=new LinkedHashMap<>(structure.getStorage().getAllItemStorages());fluids=new LinkedHashMap<>(structure.getStorage().getFluids().storages);
        views=new ArrayList<>();for(var storage:structure.getStorage().getAllItemStorages().values())captureViews(storage);for(var storage:structure.getStorage().getFluids().storages.values())captureViews(storage);
        blocks=new LinkedHashMap<>(structure.getBlocks());blockData=new LinkedHashMap<>();structure.getBlocks().forEach((pos,info)->{if(info.nbt()!=null)blockData.put(pos,info.nbt().copy());});
        actors=new ArrayList<>();for(var actor:structure.getActors())if(actor.right!=null){
            var context=actor.right;var hand=new ArrayList<Stack>();
            int selected=-1,overflow=-1;
            if(context.temporaryData instanceof DeployerFakePlayer player){
                var inventory=player.getInventory();selected=inventory.getSelectedSlot();overflow=player.movingOverflow.size();
                for(int index=0;index<inventory.getContainerSize();index++){int slot=index;var stack=inventory.getItem(slot);hand.add(new Stack(()->inventory.getItem(slot),stack,PackedCartData.detachedItemCopy(stack)));}
                for(int index=0;index<player.movingOverflow.size();index++){int slot=index;var stack=player.movingOverflow.get(slot);hand.add(new Stack(()->slot<player.movingOverflow.size()?player.movingOverflow.get(slot):ItemStack.EMPTY,stack,PackedCartData.detachedItemCopy(stack)));}
            }
            actors.add(new Actor(context,context.temporaryData,context.data.copy(),context.blockEntityData.copy(),List.copyOf(hand),selected,overflow));
        }
    }
    private void captureViews(Storage<?> storage){
        for(var view:storage){Object expected=view.getResource();if(expected instanceof ItemVariant item)expected=ItemVariant.of(PackedCartData.detachedItemCopy(item.toStack()));views.add(new View(view,expected,view.getAmount(),view.getCapacity()));}
    }
    static OwnedCartStructureSnapshot capture(OrientedContraptionEntity owner){return new OwnedCartStructureSnapshot(owner);}
    private static boolean sameStorage(Map<BlockPos,?> actual,Map<BlockPos,Object> expected){if(actual.size()!=expected.size())return false;for(var entry:expected.entrySet())if(actual.get(entry.getKey())!=entry.getValue())return false;return true;}
    boolean matchesRaw(){
        if(owner.getContraption()!=structure||!owner.hasOwnedStructure()||!owner.position().equals(position)||Float.compare(owner.getViewYRot(1),yaw)!=0||Float.compare(owner.getViewXRot(1),pitch)!=0||!Objects.equals(owner.getMountedCartId(),base)||!Objects.equals(owner.getCouplingId(),coupling)||!Objects.equals(owner.getParentId(),parent)||!structure.getStabilizedSubContraptions().children().equals(children)||!sameStorage(structure.getStorage().getAllItemStorages(),items)||!sameStorage(structure.getStorage().getFluids().storages,fluids))return false;
        if(structure.getBlocks().size()!=blocks.size())return false;for(var entry:blocks.entrySet())if(structure.getBlocks().get(entry.getKey())!=entry.getValue())return false;
        for(var entry:blockData.entrySet()){var info=structure.getBlocks().get(entry.getKey());if(info==null||!Objects.equals(info.nbt(),entry.getValue()))return false;}
        if(structure.getActors().stream().filter(actor->actor.right!=null).count()!=actors.size())return false;
        for(var actor:actors)if(structure.getActors().stream().noneMatch(entry->entry.right==actor.actual)||!actor.matches())return false;
        return views.stream().allMatch(View::matches);
    }
}
