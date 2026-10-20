package com.simibubi.create.content.logistics.packagePort.postbox;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.packagePort.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Actual source postbox inventory, station retained mail handoff, native menu and flag lifecycle. */
public final class PostboxBlockEntity extends PackagePortBlockEntity {
  private WeakReference<GlobalStation> station=new WeakReference<>(null);
  private float flag,previousFlag,flagTarget;
  private int openers;
  public boolean forceFlag;
  private boolean replacing;
  private boolean clientParticles;
  public PostboxBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.PACKAGE_POSTBOX,pos,state);}
  public List<ItemStack> items(){var items=new ArrayList<ItemStack>();for(int i=0;i<inventory.getContainerSize();i++)items.add(inventory.getItem(i).copy());return List.copyOf(items);}
  public boolean targets(GlobalStation actual){return level!=null&&level.dimension().equals(actual.getBlockEntityDimension())&&target instanceof PackagePortTarget.TrainStationFrogportTarget destination&&worldPosition.offset(destination.relativePos).equals(actual.getBlockEntityPos());}
  public boolean isBoundTo(GlobalStation actual){return station.get()==actual&&targets(actual);}
  public void bind(GlobalStation actual){if(!targets(actual))throw new IllegalArgumentException("Actual Postbox/station target required");station=new WeakReference<>(actual);}
  public void unbind(GlobalStation actual){if(station.get()==actual)station=new WeakReference<>(null);}
  public void replaceMailContents(List<ItemStack> actual){
    if(actual.size()!=18||actual.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Actual complete18-slot original mail handoff required");
    replacing=true;
    try{for(int slot=0;slot<18;slot++)inventory.setItem(slot,actual.get(slot));}finally{replacing=false;}
    inventory.setChanged();
  }
  @Override public void inventoryChanged(){if(replacing)return;super.inventoryChanged();}
  @Override public void setChanged(){
    super.setChanged();
    if(level==null||level.isClientSide()||replacing)return;
    var actual=station.get();if(actual!=null&&targets(actual))actual.savePostboxBuffer(this);
  }
  public float flag(float partial){return Mth.lerp(partial,previousFlag,flag);}
  public float flagTarget(){return flagTarget;}
  public boolean flagSettled(){return Math.abs(flag-flagTarget)<1e-6;}
  public void tick(){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;

    if(level==null)return;
    if(!level.isClientSide()){
      refreshTarget();
      if(openers==0&&getBlockState().getValue(PostboxBlock.OPEN))setOpen(false);
      return;
    }
    previousFlag=flag;float previousTarget=flagTarget;
    if(flagTarget==0||flagSettled()){
      float next=inventory.isEmpty()&&!forceFlag?0:1;
      if(next!=flagTarget){flagTarget=next;if(next==1)level.playLocalSound(worldPosition,AllSoundEvents.CONTRAPTION_ASSEMBLE,SoundSource.BLOCKS,1,2,true);}
    }
    boolean crossed=flag>.15f;flag=Mth.approach(flag,flagTarget,.1f);
    if(previousTarget==0&&crossed!=(flag>.15f))level.playLocalSound(worldPosition,AllSoundEvents.CONTRAPTION_DISASSEMBLE,SoundSource.BLOCKS,.75f,1.5f,true);
    if(clientParticles){clientParticles=false;BoneMealItem.addGrowthParticles(level,worldPosition,40);}
  
        }
    }
  public void spawnParticles(){if(level!=null&&!level.isClientSide())level.blockEvent(worldPosition,getBlockState().getBlock(),1,0);}
  @Override public boolean triggerEvent(int type,int data){if(type==1){if(level!=null&&level.isClientSide())BoneMealItem.addGrowthParticles(level,worldPosition,40);return true;}return super.triggerEvent(type,data);}
  @Override public void startOpen(){if(openers++==0)setOpen(true);}
  @Override public void stopOpen(){if(openers>0&&--openers==0)setOpen(false);}
  private void setOpen(boolean open){
    if(level==null||level.isClientSide())return;
    var current=LoadedLevelAccess.blockState(level,worldPosition);
    if(!(current.getBlock() instanceof PostboxBlock)||current.getValue(PostboxBlock.OPEN)==open)return;
    level.setBlockAndUpdate(worldPosition,current.setValue(PostboxBlock.OPEN,open));
    level.playSound(null,worldPosition,open?SoundEvents.BARREL_OPEN:SoundEvents.BARREL_CLOSE,SoundSource.BLOCKS);
  }
  @Override public boolean use(Player player){
    if(player==null||player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||player instanceof net.fabricmc.fabric.api.entity.FakePlayer)return false;
    if(!level.isClientSide())PackagePortMenu.open(player,this);return true;
  }
  @Override protected boolean retainTargetOnUnload(){return true;}
  @Override public void invalidate(){setChanged();super.invalidate();}
  @Override protected void write(ValueOutput output, boolean clientPacket){super.write(output,clientPacket);if(clientPacket)output.putInt("OpenCount",openers);if(clientPacket&&clientParticles){output.putBoolean("Particles",true);clientParticles=false;}}
  @Override protected void read(ValueInput input, boolean clientPacket){super.read(input, clientPacket);openers=clientPacket?input.getIntOr("OpenCount",0):0;clientParticles=clientPacket&&input.getBooleanOr("Particles",false);}
}
