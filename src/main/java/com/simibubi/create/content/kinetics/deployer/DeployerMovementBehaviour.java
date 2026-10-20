package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity.Mode;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Source moving hand, shared native interactions and separately owned fake-player inventory. */
public class DeployerMovementBehaviour implements MovementBehaviour {
    @Override public Vec3 getActiveAreaOffset(MovementContext context){return context.state.getValue(DeployerBlock.FACING).getUnitVec3().scale(2);}
    public static Mode getMode(MovementContext context){String mode=context.blockEntityData.getStringOr("Mode","USE");if(context.world.isClientSide())mode=context.data.getStringOr("Mode",mode);try{return Mode.valueOf(mode);}catch(IllegalArgumentException ignored){return Mode.USE;}}
    public static ItemStack getHeldItem(MovementContext context){return input(context,context.data).read("HeldItem",ItemStack.OPTIONAL_CODEC).orElseGet(()->input(context,context.blockEntityData).read("HeldItem",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));}
    private static ValueInput input(MovementContext context,CompoundTag data){return TagValueInput.create(ProblemReporter.DISCARDING,context.world.registryAccess(),data);}
    private static BlockPos position(CompoundTag tag,String key){if(tag.get(key) instanceof CompoundTag old)return new BlockPos(old.getIntOr("X",0),old.getIntOr("Y",0),old.getIntOr("Z",0));return tag.read(key,BlockPos.CODEC).orElse(BlockPos.ZERO);}
    public DeployerFakePlayer getPlayer(MovementContext context){
        if(!(context.world instanceof ServerLevel level))return null;
        if(context.temporaryData instanceof DeployerFakePlayer existing)return existing;
        var saved=input(context,context.blockEntityData);var player=new DeployerFakePlayer(level,saved.read("Owner",UUIDUtil.CODEC).orElse(null));
        var held=getHeldItem(context);int selected=saved.getIntOr("SelectedSlot",0);
        var entries=new ArrayList<>(saved.read("PlayerInventory",DeployerInventory.CODEC).orElse(List.of()));
        if(saved.childrenList("Inventory").isPresent()){
            if(saved.read("PlayerInventory",DeployerInventory.CODEC).isPresent())throw new IllegalArgumentException("Two moving Deployer inventory owners");
            selected=0;var legacyHand=ItemStack.EMPTY;var occupied=new HashSet<Integer>();
            for(var slot:saved.childrenListOrEmpty("Inventory")){
                var stack=slot.read(ItemStack.MAP_CODEC).orElseThrow();int index=DeployerInventory.originalSlot(slot.getByteOr("Slot",(byte)-1)&255);
                if(!occupied.add(index))throw new IllegalArgumentException("Duplicate original moving Deployer slot");
                if(index==0)legacyHand=stack;else if(!stack.isEmpty())entries.add(new DeployerInventory.Entry(index,stack));
            }
            // Source actor Data.HeldItem supersedes the captured Inventory hand, never extra stock.
            if(!context.data.contains("HeldItem"))held=legacyHand;
        }
        var overflow=saved.read("Overflow",ItemStack.CODEC.listOf()).orElse(List.of()).stream().map(ItemStack::copy).toList();
        DeployerInventory.hydrate(player,entries,selected,held);player.movingOverflow.addAll(overflow);
        for(var key:new String[]{"Inventory","PlayerInventory","SelectedSlot","Overflow"})context.blockEntityData.remove(key);
        context.temporaryData=player;
        if(context.data.contains("BreakingPos")){float progress=context.data.getFloatOr("BreakingProgress",0);if(Float.isFinite(progress)&&progress>0&&progress<1)player.blockBreakingProgress=org.apache.commons.lang3.tuple.Pair.of(position(context.data,"BreakingPos"),progress);else{context.data.remove("BreakingPos");context.data.remove("BreakingProgress");}}
        return player;
    }
    @Override public void visitNewPosition(MovementContext context,BlockPos pos){
        if(context.world.isClientSide())return;
        if(!LoadedLevelAccess.isLoaded(context.world,pos)){context.data.store("PendingPos",BlockPos.CODEC,pos);context.stall=true;return;}
        var player=getPlayer(context);if(player==null)return;tryGrabbingItem(context,player);
        if(getMode(context)==Mode.USE&&!DeployerHandler.shouldActivate(player.getMainHandItem(),context.world,pos,null)){sync(context);return;}
        activate(context,pos,player,getMode(context));dispose(context,player);context.stall=player.blockBreakingProgress!=null||DeployerSchematicPrinter.hasPending(context);sync(context);
    }
    public void activate(MovementContext context,BlockPos pos,DeployerFakePlayer player,Mode mode){
        if(!(context.world instanceof ServerLevel level)||context.position==null||!LoadedLevelAccess.isLoaded(level,pos))return;
        var normal=context.rotation.apply(context.state.getValue(DeployerBlock.FACING).getUnitVec3()).normalize();var center=context.position.subtract(normal.scale(2));var yaw=normal;
        if(Math.abs(normal.y)>.999){yaw=context.rotation.apply(new Vec3(0,0,1));if(yaw.horizontalDistanceSqr()<1e-8)yaw=context.rotation.apply(new Vec3(1,0,0));}
        player.setYRot((float)Math.toDegrees(Math.atan2(-yaw.x,yaw.z)));player.setXRot((float)-Math.toDegrees(Math.asin(Math.clamp(normal.y,-1,1))));
        player.placedTracks=false;
        var drops=DeployerDropCapture.run(level,pos,()->{try(var source=DeployerCartItemSource.moving(context,player)){if(!DeployerSchematicPrinter.activate(context,pos,player))DeployerHandler.activate(player,center,pos,normal,mode);}});for(var drop:drops)collectOrDropItem(context,drop);
        if(player.placedTracks&&(context.contraption instanceof com.simibubi.create.content.contraptions.mounted.MountedContraption||context.contraption instanceof com.simibubi.create.content.trains.entity.CarriageContraption))com.simibubi.create.foundation.advancement.GameplayAdvancements.awardOwner(level,input(context,context.blockEntityData).read("Owner",UUIDUtil.CODEC).orElse(null),"self_deploying");
    }
    private void tryGrabbingItem(MovementContext context,DeployerFakePlayer player){
        if(!player.getMainHandItem().isEmpty())return;var filter=context.getFilterFromBE();
        ItemStack grabbed=ItemStack.EMPTY;try(var transaction=Transaction.openOuter()){
            for(var view:context.contraption.getStorage().getAllItems()){if(view.isResourceBlank())continue;var variant=view.getResource();var stack=variant.toStack();if(!filter.test(context.world,stack)||view.extract(variant,1,transaction)!=1)continue;grabbed=stack.copyWithCount(1);transaction.commit();break;}
        }
        if(!grabbed.isEmpty())player.setItemInHand(InteractionHand.MAIN_HAND,grabbed);
    }
    private void dispose(MovementContext context,DeployerFakePlayer player){
        if(context.position==null)return;var inventory=player.getInventory();var filter=context.getFilterFromBE();for(int i=0;i<inventory.getContainerSize();i++){var stack=inventory.getItem(i);if(stack.isEmpty()||i==inventory.getSelectedSlot()&&filter.test(context.world,stack))continue;DeployerOutputTransfer.inventory(context,player,i);}
        var actualOverflow=player.movingOverflow;for(int i=0;i<actualOverflow.size();i++)DeployerOutputTransfer.overflow(context,player,actualOverflow,i);actualOverflow.removeIf(ItemStack::isEmpty);
        // A filtered-out hand can no longer advance its partial punch.
        if(player.getMainHandItem().isEmpty())DeployerHandler.clearBreaking(player);
    }
    @Override public void tick(MovementContext context){
        if(context.world.isClientSide()||!context.stall&&!DeployerSchematicPrinter.hasPending(context))return;
        if(DeployerSchematicPrinter.hasPending(context)){
            var player=getPlayer(context);if(player==null)return;player.placedTracks=false;
            DeployerSchematicPrinter.retry(context,player);
            if(player.placedTracks&&(context.contraption instanceof com.simibubi.create.content.contraptions.mounted.MountedContraption||context.contraption instanceof com.simibubi.create.content.trains.entity.CarriageContraption))com.simibubi.create.foundation.advancement.GameplayAdvancements.awardOwner(context.world,input(context,context.blockEntityData).read("Owner",UUIDUtil.CODEC).orElse(null),"self_deploying");
            dispose(context,player);context.stall=DeployerSchematicPrinter.hasPending(context)||player.blockBreakingProgress!=null;sync(context);return;
        }
        if(context.data.contains("PendingPos")){var pos=position(context.data,"PendingPos");if(!LoadedLevelAccess.isLoaded(context.world,pos))return;context.data.remove("PendingPos");context.stall=false;visitNewPosition(context,pos);return;}
        var player=getPlayer(context);if(player==null)return;if(player.getMainHandItem().isEmpty()&&player.blockBreakingProgress!=null){DeployerHandler.clearBreaking(player);context.stall=false;context.data.remove("Timer");sync(context);return;}if(player.blockBreakingProgress!=null){var pos=player.blockBreakingProgress.getKey();if(!LoadedLevelAccess.isLoaded(context.world,pos))return;int timer=context.data.getIntOr("Timer",0);if(timer<20){context.data.putInt("Timer",timer+1);return;}context.data.remove("Timer");activate(context,pos,player,getMode(context));dispose(context,player);}
        context.stall=player.blockBreakingProgress!=null||DeployerSchematicPrinter.hasPending(context);sync(context);
    }
    @Override public void cancelStall(MovementContext context){
        if(context.world.isClientSide())return;context.stall=false;for(var key:new String[]{"PendingPos","Timer","BreakingPos","BreakingProgress"})context.data.remove(key);
        if(context.temporaryData instanceof DeployerFakePlayer player)DeployerHandler.clearBreaking(player);
    }
    @Override public void stopMoving(MovementContext context){
        if(context.world.isClientSide())return;var player=getPlayer(context);if(player!=null)DeployerSchematicPrinter.stop(context,player);cancelStall(context);writeExtraData(context);if(player!=null)player.discard();
    }
    @Override public void writeExtraData(MovementContext context){
        if(context.world.isClientSide())return;DeployerSchematicPrinter.write(context);if(context.contraption.phase()!=Contraption.Phase.REMOVED)return;
        var player=getPlayer(context);if(player==null)return;var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,context.world.registryAccess());output.store("HeldItem",ItemStack.OPTIONAL_CODEC,player.getMainHandItem());
        output.store("PlayerInventory",DeployerInventory.CODEC,DeployerInventory.capture(player));output.putInt("SelectedSlot",player.getInventory().getSelectedSlot());
        output.store("Overflow",ItemStack.CODEC.listOf(),player.movingOverflow.stream().filter(stack->!stack.isEmpty()).map(ItemStack::copy).toList());var saved=output.buildResult();
        context.data.put("HeldItem",saved.get("HeldItem").copy());context.data.putString("Mode",getMode(context).name());
        for(var key:new String[]{"HeldItem","PlayerInventory","SelectedSlot","Overflow"})context.blockEntityData.put(key,saved.get(key).copy());context.blockEntityData.remove("Inventory");
        if(player.blockBreakingProgress!=null){context.data.store("BreakingPos",BlockPos.CODEC,player.blockBreakingProgress.getKey());context.data.putFloat("BreakingProgress",player.blockBreakingProgress.getValue());}else{context.data.remove("BreakingPos");context.data.remove("BreakingProgress");}
        // A captured stationary cycle must not restore an unrelated, pre-move breaking overlay.
        context.blockEntityData.remove("BreakingPos");context.blockEntityData.remove("BreakingProgress");
    }
    public void sync(MovementContext context){writeExtraData(context);if(context.contraption.entity!=null)ContraptionNetworking.syncActorData(context);}
    @Override public boolean disableBlockEntityRendering(){return true;}
}
