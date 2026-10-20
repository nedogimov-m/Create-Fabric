package com.simibubi.create.content.kinetics.press;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Source pressing cycle and actual native transactional impact share this one installed owner. */
public class PressingBehaviour extends BeltProcessingBehaviour {
    public static final int CYCLE=240,ENTITY_SCAN=10;
    public List<ItemStack> particleItems=new ArrayList<>();
    public PressingBehaviourSpecifics specifics;
    public int prevRunningTicks,runningTicks;
    public boolean running,finished;
    public Mode mode;
    int entityScanCooldown;
    // This is the paid impact receipt, not a second animation/processing clock.
    private boolean impactApplied;

    public interface PressingBehaviourSpecifics {
        boolean tryProcessInBasin(boolean simulate);
        boolean tryProcessOnBelt(TransportedItemStack input,List<ItemStack> outputList,boolean simulate);
        boolean tryProcessInWorld(ItemEntity itemEntity,boolean simulate);
        boolean canProcessInBulk();
        void onPressingCompleted();
        int getParticleAmount();
        float getKineticSpeed();
    }
    /** Typed adaptation of the existing native depot/belt/basin transaction path. */
    public interface NativePressingBehaviourSpecifics extends PressingBehaviourSpecifics {
        boolean tryStartNativeCycle();
        boolean tryApplyNativeImpact();
        boolean isNativeCycleBlocked();
    }
    public <T extends SmartBlockEntity & PressingBehaviourSpecifics> PressingBehaviour(T be){
        super(be);specifics=be;mode=Mode.WORLD;entityScanCooldown=ENTITY_SCAN;
        whenItemEnters((stack,inventory)->BeltPressingCallbacks.onItemReceived(stack,inventory,this));
        whileItemHeld((stack,inventory)->BeltPressingCallbacks.whenItemHeld(stack,inventory,this));
    }
    public boolean isCurrentOwner(){
        return !blockEntity.isRemoved()&&(!blockEntity.hasLevel()||blockEntity.getBehaviour(getType())==this
            &&(blockEntity.isVirtual()||LoadedLevelAccess.blockEntity(getWorld(),getPos())==blockEntity));
    }
    @Override public void read(ValueInput input,boolean clientPacket){
        running=input.getBooleanOr("Running",false);finished=input.getBooleanOr("Finished",false);
        int ordinal=input.getIntOr("Mode",input.getBooleanOr("OnDepot",false)?Mode.BELT.ordinal():Mode.WORLD.ordinal());
        mode=Mode.values()[Math.clamp(ordinal,0,Mode.values().length-1)];
        prevRunningTicks=runningTicks=input.getIntOr("Ticks",input.getIntOr("PressTicks",0));
        impactApplied=input.getBooleanOr("Pressed",runningTicks>CYCLE/2);
        super.read(input,clientPacket);
        if(clientPacket){particleItems.clear();particleItems.addAll(input.read("ParticleItems",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));spawnParticles();}
    }
    @Override public void write(ValueOutput output,boolean clientPacket){
        output.putBoolean("Running",running);output.putBoolean("Finished",finished);output.putInt("Mode",mode.ordinal());output.putInt("Ticks",runningTicks);
        output.putBoolean("Pressed",impactApplied);
        // Legacy aliases are derived from this same real source cycle.
        output.putInt("PressTicks",runningTicks);output.putBoolean("OnDepot",mode!=Mode.WORLD);
        super.write(output,clientPacket);
        if(clientPacket){output.store("ParticleItems",ItemStack.OPTIONAL_CODEC.listOf(),particleItems);particleItems.clear();}
    }
    public float getRenderedHeadOffset(float partialTicks){
        if(!running)return 0;
        int ticks=Math.abs(runningTicks);float interpolated=Mth.lerp(partialTicks,prevRunningTicks,ticks);
        return ticks<CYCLE*2/3?(float)Math.clamp(Math.pow(interpolated/CYCLE*2,3),0,1):Mth.clamp((CYCLE-interpolated)/CYCLE*3,0,1);
    }
    private void publish(){if(!blockEntity.isVirtual()){blockEntity.setChanged();blockEntity.sendData();}}
    public void start(Mode mode){
        if(!isCurrentOwner())return;
        this.mode=java.util.Objects.requireNonNull(mode);running=true;finished=false;prevRunningTicks=runningTicks=0;impactApplied=false;
        particleItems.clear();publish();
    }
    public boolean inWorld(){return mode==Mode.WORLD;}
    public boolean onBasin(){return mode==Mode.BASIN;}
    public boolean hasAppliedImpact(){return impactApplied;}
    void markImpactApplied(){if(!isCurrentOwner())return;impactApplied=true;publish();}
    /** The paid world output commit must survive serialization inside native onCreated. */
    void markWorldImpactAppliedPure(){if(running&&mode==Mode.WORLD)impactApplied=true;}
    public void cancel(){running=false;finished=false;prevRunningTicks=runningTicks=0;impactApplied=false;particleItems.clear();}
    @Override public void tick(){
        super.tick();var level=getWorld();if(level==null||!isCurrentOwner())return;
        if(level.isClientSide()&&!particleItems.isEmpty())spawnParticles();
        if(specifics instanceof NativePressingBehaviourSpecifics nativePress&&nativePress.isNativeCycleBlocked())return;
        if(!running){
            if(level.isClientSide()||blockEntity.isVirtual()||specifics.getKineticSpeed()==0)return;
            if(--entityScanCooldown>0)return;entityScanCooldown=ENTITY_SCAN;
            if(specifics instanceof NativePressingBehaviourSpecifics nativePress){if(nativePress.tryStartNativeCycle())start(mode);return;}
            if(BlockEntityBehaviour.get(level,getPos().below(2),TransportedItemStackHandlerBehaviour.TYPE)!=null
                    ||LoadedLevelAccess.blockEntity(level,getPos().below(2)) instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity)return;
            for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(getPos().below()).deflate(.125f)))
                if(item.isAlive()&&item.onGround()&&specifics.tryProcessInWorld(item,true)){start(Mode.WORLD);return;}
            return;
        }
        if(specifics.getKineticSpeed()==0){prevRunningTicks=Math.abs(runningTicks);return;}
        if(level.isClientSide()&&!blockEntity.isVirtual()&&runningTicks==-CYCLE/2){prevRunningTicks=CYCLE/2;return;}
        if(runningTicks>=CYCLE/2&&!impactApplied){
            if(level.isClientSide()&&!blockEntity.isVirtual()){prevRunningTicks=CYCLE/2;runningTicks=-CYCLE/2;return;}
            if(blockEntity.isVirtual())markImpactApplied();
            else if(specifics instanceof NativePressingBehaviourSpecifics){if(!applyNativeImpact()){if(isCurrentOwner()){cancel();blockEntity.sendData();}return;}}
            else {if(inWorld())applyInWorld();if(onBasin())applyOnBasin();impactApplied=true;}
            if(!isCurrentOwner())return;
        }
        if(runningTicks>CYCLE){
            finished=true;running=false;particleItems.clear();
            specifics.onPressingCompleted();if(isCurrentOwner()){publish();}return;
        }
        prevRunningTicks=runningTicks;runningTicks+=getRunningTickSpeed();
        if(prevRunningTicks<CYCLE/2&&runningTicks>=CYCLE/2)runningTicks=level.isClientSide()&&!blockEntity.isVirtual()?-CYCLE/2:CYCLE/2;
        if(!level.isClientSide()&&!blockEntity.isVirtual())blockEntity.setChanged();
    }
    boolean applyNativeImpact(){
        if(impactApplied)return true;
        if(!isCurrentOwner()||getWorld()==null||getWorld().isClientSide()||blockEntity.isVirtual()
                ||!(specifics instanceof NativePressingBehaviourSpecifics nativePress))return false;
        particleItems.clear();
        if(!nativePress.tryApplyNativeImpact())return false;
        if(!isCurrentOwner())return false;
        markImpactApplied();return true;
    }
    protected void applyOnBasin(){
        if(getWorld()==null||getWorld().isClientSide()||blockEntity.isVirtual())return;
        particleItems.clear();if(specifics.tryProcessInBasin(false))blockEntity.sendData();
    }
    protected void applyInWorld(){
        var level=getWorld();if(level==null||level.isClientSide()||blockEntity.isVirtual())return;
        particleItems.clear();for(var item:level.getEntitiesOfClass(ItemEntity.class,new AABB(getPos().below()))){
            if(!isCurrentOwner())return;if(!item.isAlive()||!item.onGround())continue;entityScanCooldown=0;
            if(specifics.tryProcessInWorld(item,false))blockEntity.sendData();if(!specifics.canProcessInBulk())break;
        }
    }
    public int getRunningTickSpeed(){float speed=specifics.getKineticSpeed();return speed==0?0:(int)Mth.lerp(Mth.clamp(Math.abs(speed)/512f,0,1),1,60);}
    protected void spawnParticles(){
        if(getWorld()==null||!getWorld().isClientSide()||!isCurrentOwner())return;
        var center=Vec3.atCenterOf(getPos().below(mode==Mode.WORLD?1:2));
        for(var stack:particleItems){if(mode==Mode.BASIN)makeCompactingParticleEffect(center,stack);else makePressingParticleEffect(center.add(0,mode==Mode.BELT?8/16f:-1/4f,0),stack);}
        particleItems.clear();
    }
    public void makePressingParticleEffect(Vec3 pos,ItemStack stack){makePressingParticleEffect(pos,stack,specifics.getParticleAmount());}
    public void makePressingParticleEffect(Vec3 pos,ItemStack stack,int amount){
        var level=getWorld();if(level==null||!level.isClientSide()||!isCurrentOwner()||stack.isEmpty())return;
        for(int i=0;i<amount;i++){var random=level.getRandom();var motion=new Vec3((random.nextFloat()-.5f)*.25f,(random.nextFloat()-.5f)*.25f,(random.nextFloat()-.5f)*.25f).multiply(1,0,1);
            level.addParticle(new ItemParticleOption(ParticleTypes.ITEM,net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1))),pos.x,pos.y-.25,pos.z,motion.x,amount!=1?.125:1/16d,motion.z);}
    }
    public void makeCompactingParticleEffect(Vec3 pos,ItemStack stack){
        var level=getWorld();if(level==null||!level.isClientSide()||!isCurrentOwner()||stack.isEmpty())return;
        for(int i=0;i<20;i++){var random=level.getRandom();var motion=new Vec3((random.nextFloat()-.5f)*.35f,(random.nextFloat()-.5f)*.35f,(random.nextFloat()-.5f)*.35f).multiply(1,0,1);
            level.addParticle(new ItemParticleOption(ParticleTypes.ITEM,net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1))),pos.x,pos.y,pos.z,motion.x,.25,motion.z);}
    }
    public enum Mode {WORLD(1),BELT(19f/16f),BASIN(22f/16f);public float headOffset;Mode(float headOffset){this.headOffset=headOffset;}}
}
