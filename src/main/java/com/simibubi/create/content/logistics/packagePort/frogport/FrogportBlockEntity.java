package com.simibubi.create.content.logistics.packagePort.frogport;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.content.logistics.funnel.FunnelTransfer;
import com.simibubi.create.content.logistics.packagePort.*;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.foundation.item.LoadedItemStorage;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** An in-flight animation is an owned slot, saved until its actual handoff. */
public final class FrogportBlockEntity extends PackagePortBlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"frogport");
    public static final int CYCLE=10;
    public ItemStack animatedPackage=ItemStack.EMPTY,displayPackage=ItemStack.EMPTY;
    public boolean currentlyDepositing,goggles,failedLastExport;
    public float passiveYaw;
    private boolean animating;
    private int animationTicks,anticipationTicks,openCount;
    private float previousOpen,manualOpen;
    private record Animation(ItemStack owned,ItemStack display,boolean active,boolean deposit,int ticks){}
    private final SnapshotParticipant<Animation> animationSnapshot=new SnapshotParticipant<>(){
        @Override protected Animation createSnapshot(){return new Animation(animatedPackage.copy(),displayPackage.copy(),animating,currentlyDepositing,animationTicks);}
        @Override protected void readSnapshot(Animation snapshot){animatedPackage=snapshot.owned;displayPackage=snapshot.display;animating=snapshot.active;currentlyDepositing=snapshot.deposit;animationTicks=snapshot.ticks;}
        @Override protected void onFinalCommit(){animationStarted();}
    };
    public FrogportBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.PACKAGE_FROGPORT,pos,state);}
    public boolean isAnimationInProgress(){return animating;}
    public int getAnimationTicks(){return animationTicks;}
    public float animationProgress(float partial){return animating?Mth.clamp((animationTicks-1+partial)/CYCLE,0,1):0;}
    public float anticipationProgress(float partial){return anticipationTicks<=0?0:Mth.clamp((9-anticipationTicks+partial)/10,0,1);}
    public float manualOpenProgress(float partial){return Mth.lerp(partial,previousOpen,manualOpen);}
    public void startOpen(){if(openCount++==0&&level!=null&&!level.isClientSide())level.playSound(null,worldPosition,AllSoundEvents.FROGPORT_OPEN,SoundSource.BLOCKS,.5f,1);notifyUpdate();}
    public void stopOpen(){if(openCount==0)return;if(--openCount==0&&level!=null&&!level.isClientSide())level.playSound(null,worldPosition,AllSoundEvents.FROGPORT_CLOSE,SoundSource.BLOCKS,1,1.25f);notifyUpdate();}
    public void sendAnticipate(){if(!animating&&!isBackedUp()&&anticipationTicks==0){anticipationTicks=10;notifyUpdate();}}
    public boolean startAnimation(ItemStack box,boolean deposit){
        if(animating||!PackageItem.isPackage(box)||deposit&&(target==null||!target.export(this,box,true)))return false;
        begin(box,deposit);animationStarted();return true;
    }
    private void begin(ItemStack box,boolean deposit){animatedPackage=box.copyWithCount(1);displayPackage=animatedPackage.copy();currentlyDepositing=deposit;animationTicks=0;animating=true;}
    public boolean tryReceive(ItemStack box){return !isBackedUp()&&!animating&&startAnimation(box,false);}
    private void animationStarted(){
        notifyUpdate();if(level!=null&&!level.isClientSide()){
            if(!currentlyDepositing)sourceAdvancements.award("frogport");
            level.playSound(null,worldPosition,AllSoundEvents.FROGPORT_OPEN,SoundSource.BLOCKS,.5f,1);
            level.playSound(null,worldPosition,currentlyDepositing?AllSoundEvents.FROGPORT_DEPOSIT:AllSoundEvents.FROGPORT_CATCH,SoundSource.BLOCKS,1,1);
        }
    }
    public void tick(){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;

        
        previousOpen=manualOpen;manualOpen=Mth.clamp(manualOpen+(openCount>0?.35f:-.35f),0,1);
        if(anticipationTicks>0)anticipationTicks--;
        if(level.isClientSide()){
            if(isVirtual())refreshTarget();
            if(animating){
                animationTicks++;
                if(isVirtual()&&currentlyDepositing&&animationTicks>=6&&!animatedPackage.isEmpty()){
                    if(target==null||!target.export(this,animatedPackage,false))drop(animatedPackage);
                    animatedPackage=ItemStack.EMPTY;notifyUpdate();
                }
                if(currentlyDepositing&&animationTicks>=8)displayPackage=ItemStack.EMPTY;
                if(animationTicks>=CYCLE){animating=false;animatedPackage=displayPackage=ItemStack.EMPTY;}
            }
            return;
        }
        refreshTarget();
        if(animating){
            animationTicks++;setChanged();
            if(currentlyDepositing&&animationTicks>=8)displayPackage=ItemStack.EMPTY;
            if(currentlyDepositing&&animationTicks>=6&&!animatedPackage.isEmpty()){
                if(target==null||!target.export(this,animatedPackage,false))drop(animatedPackage);
                animatedPackage=ItemStack.EMPTY;notifyUpdate();
            }
            if(animationTicks>=CYCLE){
                if(!currentlyDepositing&&!animatedPackage.isEmpty()){
                    boolean inserted;try(var tx=Transaction.openOuter()){inserted=inventory.insert(ItemVariant.of(animatedPackage),1,tx)==1;if(inserted)tx.commit();}
                    if(!inserted)drop(animatedPackage);
                }
                animatedPackage=displayPackage=ItemStack.EMPTY;animating=false;anticipationTicks=0;
                level.playSound(null,worldPosition,AllSoundEvents.FROGPORT_CLOSE,SoundSource.BLOCKS,1,1.25f+level.getRandom().nextFloat()*.25f);notifyUpdate();
            }
        }
        if(!animating&&level.getGameTime()%10==0){tryPushingToAdjacentInventories();tryPullingFromOwnAndAdjacentInventories();}
    
        }
    }
    private Storage<ItemVariant> adjacent(){if(LoadedLevelAccess.blockEntity(level,worldPosition.below()) instanceof PackagePortBlockEntity)return null;return LoadedItemStorage.find(level,worldPosition.below(),Direction.UP);}
    public void tryPushingToAdjacentInventories(){
        var below=adjacent();boolean previous=failedLastExport;failedLastExport=false;
        if(below!=null){for(int i=0;i<18;i++)try(var tx=Transaction.openOuter()){var moved=FunnelTransfer.move(itemHandler,below,PackageItem::isPackage,1,true,true,tx);if(moved.isEmpty())break;tx.commit();}
            String filter=getFilterString();if(filter!=null)for(int i=0;i<18;i++)if(!inventory.getItem(i).isEmpty()&&PackageItem.matchAddress(inventory.getItem(i),filter))failedLastExport=true;
        }
        if(previous!=failedLastExport)notifyUpdate();
    }
    public void tryPullingFromOwnAndAdjacentInventories(){if(animating||target==null||!target.export(this,PackageStyles.getDefaultBox(),true))return;if(tryPullingFrom(inventory.storage))return;var below=adjacent();if(below!=null)tryPullingFrom(below);}
    public boolean tryPullingFrom(Storage<ItemVariant> source){
        if(animating||target==null||!target.export(this,PackageStyles.getDefaultBox(),true))return false;
        try(var tx=Transaction.openOuter()){
            String filter=getFilterString();var box=FunnelTransfer.extract(source,item->PackageItem.isPackage(item)&&(filter==null||source instanceof PackagerItemHandler||!PackageItem.matchAddress(item,filter)),1,true,tx);
            if(box.isEmpty())return false;animationSnapshot.updateSnapshots(tx);begin(box,true);tx.commit();return true;
        }
    }
    public boolean use(Player player){
        if(player==null||player.isShiftKeyDown())return false;
        if(!goggles&&player.getMainHandItem().is(AllItems.GOGGLES)){goggles=true;notifyUpdate();return true;}
        if(player instanceof com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer)return false;
        if(!level.isClientSide())PackagePortMenu.open(player,this);return true;
    }
    public float getYaw(){if(target==null)return passiveYaw;var point=target.getExactTargetLocation(this);if(point.equals(net.minecraft.world.phys.Vec3.ZERO))return passiveYaw;var diff=point.subtract(net.minecraft.world.phys.Vec3.atCenterOf(worldPosition));return (float)Math.toDegrees(Math.atan2(diff.x,diff.z))+180;}
    @Override public boolean addToTooltip(List<Component> lines,boolean sneaking){if(!failedLastExport)return false;lines.add(Component.translatable("create.hint.blocked_frogport.title"));lines.add(Component.translatable("create.hint.blocked_frogport"));return true;}
    @Override public void clearContent(){super.clearContent();animatedPackage=displayPackage=ItemStack.EMPTY;animating=false;}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){if(level!=null&&!level.isClientSide()){drop(animatedPackage);animatedPackage=displayPackage=ItemStack.EMPTY;animating=false;}super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putFloat("PlacedYaw",passiveYaw);out.putBoolean("Goggles",goggles);out.putBoolean("FailedLastExport",failedLastExport);out.putBoolean("Animating",animating);out.putInt("AnimationTicks",animationTicks);out.putBoolean("Deposit",currentlyDepositing);out.putInt("AnticipateTicks",anticipationTicks);out.putInt("OpenCount",openCount);if(!animatedPackage.isEmpty())out.store("AnimatedPackage",ItemStack.CODEC,animatedPackage);if(!displayPackage.isEmpty())out.store("AnimationDisplay",ItemStack.CODEC,displayPackage);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);passiveYaw=in.getFloatOr("PlacedYaw",0);goggles=in.getBooleanOr("Goggles",false);failedLastExport=in.getBooleanOr("FailedLastExport",false);animatedPackage=in.read("AnimatedPackage",ItemStack.CODEC).orElse(ItemStack.EMPTY);displayPackage=in.read("AnimationDisplay",ItemStack.CODEC).orElse(animatedPackage.copy());animating=in.getBooleanOr("Animating",!animatedPackage.isEmpty());animationTicks=Math.clamp(in.getIntOr("AnimationTicks",0),0,CYCLE);currentlyDepositing=in.getBooleanOr("Deposit",false);anticipationTicks=Math.clamp(in.getIntOr("AnticipateTicks",0),0,10);openCount=clientPacket?in.getIntOr("OpenCount",0):0;}
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
