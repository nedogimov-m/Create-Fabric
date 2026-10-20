package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.*;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class ToolboxBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements ExtendedMenuProvider<ToolboxMenu.Opening>,Nameable {
    public final ToolboxInventory inventory=new ToolboxInventory();
    private UUID uniqueId;
    private Component customName;
    private final Map<Integer,WeakHashMap<Player,Integer>> connectedPlayers=new HashMap<>();
    private final Set<Player> viewers=Collections.newSetFromMap(new WeakHashMap<>());
    private int openCount;
    private float oldLid,lid,oldDrawers,drawers;
    public ToolboxBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.TOOLBOX,pos,state);inventory.onChange(this::notifyUpdate);}
    @Override public void setLevel(Level level){super.setLevel(level);inventory.setClientReplica(level.isClientSide());}
    public DyeColor getColor(){return getBlockState().getBlock() instanceof ToolboxBlock toolbox?toolbox.getColor():DyeColor.BROWN;}
    public UUID getUniqueId(){return uniqueId;}
    public boolean isFullyInitialized(){return uniqueId!=null;}
    public void setUniqueId(UUID owner){if(owner!=null){uniqueId=owner;setChanged();}}
    public void readInventory(ToolboxInventory stock){if(stock!=null)inventory.copyFrom(stock);}
    public void setCustomName(Component name){customName=name;notifyUpdate();}
    @Override public Component getCustomName(){return customName;}
    @Override public Component getName(){return getDisplayName();}
    @Override public Component getDisplayName(){return customName!=null?customName:getBlockState().getBlock().getName();}
    public float lid(float partial){return oldLid+(lid-oldLid)*partial;}
    public float drawers(float partial){return oldDrawers+(drawers-oldDrawers)*partial;}
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide()){level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());}}
    public void startOpen(Player player){if(level!=null&&!level.isClientSide()&&viewers.add(player)){openCount=viewers.size();notifyUpdate();}}
    public void stopOpen(Player player){if(level!=null&&!level.isClientSide()&&viewers.remove(player)){openCount=viewers.size();notifyUpdate();}}
    public boolean isOpenInContainer(Player player){return player.containerMenu instanceof ToolboxMenu menu&&menu.contentHolder==this;}
    public void connectPlayer(int compartment,Player player,int hotbar){if(level==null||level.isClientSide()||compartment<0||compartment>=8||hotbar<0||hotbar>8)return;var players=connectedPlayers.computeIfAbsent(compartment,key->new WeakHashMap<>());Integer previous=players.get(player);if(previous!=null&&previous!=hotbar)ToolboxHandler.unequip(player,previous,false);players.put(player,hotbar);}
    public void disconnectPlayer(int compartment,Player player){var players=connectedPlayers.get(compartment);if(players!=null)players.remove(player);}
    public void unequipTracked(){unequipTracked(false);}
    private void unequipTracked(boolean keepItems){for(var row:new ArrayList<>(connectedPlayers.values()))for(var entry:new ArrayList<>(row.entrySet()))ToolboxHandler.unequip(entry.getKey(),entry.getValue(),keepItems);connectedPlayers.clear();}
    private void tickPlayers(){for(var compartment:new ArrayList<>(connectedPlayers.entrySet()))for(var entry:new ArrayList<>(compartment.getValue().entrySet())){Player player=entry.getKey();int hotbar=entry.getValue(),slot=compartment.getKey();var binding=ToolboxBindings.get(player,hotbar);if(player.isRemoved()||!ToolboxBindings.matches(player,binding,this)||binding.compartment()!=slot){compartment.getValue().remove(player);continue;}ItemStack reference=inventory.getFilter(slot),held=player.getInventory().getItem(hotbar);if(reference.isEmpty()||!held.isEmpty()&&!ToolboxInventory.canItemsShareCompartment(held,reference)){ToolboxHandler.unequip(player,hotbar,true);continue;}if(!ToolboxHandler.withinRange(player,this))continue;int target=(reference.getMaxStackSize()+1)/2;if(held.getCount()<target){if(isOpenInContainer(player)&&ToolboxHandler.replenish(player,this,hotbar,slot,target-held.getCount(),true)>0){ToolboxHandler.unequip(player,hotbar,false);continue;}ToolboxHandler.replenish(player,this,hotbar,slot,target-held.getCount(),false);}else if(held.getCount()>target){if(isOpenInContainer(player)&&ToolboxHandler.returnItems(player,this,hotbar,slot,held.getCount()-target,true)>0){ToolboxHandler.unequip(player,hotbar,true);continue;}ToolboxHandler.returnItems(player,this,hotbar,slot,held.getCount()-target,false);}}}
    public static void tick(Level level,BlockPos pos,BlockState state,ToolboxBlockEntity box){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, box)) {
            if (!createTick.shouldTick()) return;
ToolboxHandler.onLoad(box);if(!level.isClientSide()){if(box.uniqueId==null){box.uniqueId=UUID.randomUUID();box.notifyUpdate();}box.tickPlayers();if(level.getGameTime()%10==0){int before=box.openCount;box.viewers.removeIf(player->player.isRemoved()||!box.isOpenInContainer(player));box.openCount=box.viewers.size();if(before!=box.openCount)box.notifyUpdate();}}else box.tickAudio();box.oldLid=box.lid;box.oldDrawers=box.drawers;float target=box.openCount>0?1:0;box.lid+=Math.clamp(target-box.lid,-.2f,.2f);box.drawers+=(target-box.drawers)*.2f;if(Math.abs(target-box.drawers)<.001f)box.drawers=target;
        }
    }
    private void tickAudio(){boolean open=openCount>0;if(open&&lid==0){sound(SoundEvents.IRON_DOOR_OPEN,.25f,1.2f);sound(SoundEvents.CHEST_OPEN,.1f,1.1f);}if(!open&&lid==1)sound(SoundEvents.CHEST_CLOSE,.1f,1.1f);if(!open&&lid>1/16f&&Math.max(0,lid-.2f)<1/16f)sound(SoundEvents.IRON_DOOR_CLOSE,.25f,1.2f);}
    private void sound(SoundEvent event,float volume,float pitch){level.playLocalSound(worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,event,SoundSource.BLOCKS,volume,pitch+level.getRandom().nextFloat()*.1f,true);}
    @Override public AbstractContainerMenu createMenu(int id,Inventory playerInventory,Player player){if(uniqueId==null&&!player.level().isClientSide()){uniqueId=UUID.randomUUID();notifyUpdate();}return new ToolboxMenu(id,playerInventory,this);}
    @Override public ToolboxMenu.Opening getScreenOpeningData(ServerPlayer player){if(uniqueId==null){uniqueId=UUID.randomUUID();notifyUpdate();}return new ToolboxMenu.Opening(worldPosition,uniqueId,inventory.frozenCopy());}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);if(uniqueId==null&&(level==null||!level.isClientSide()))uniqueId=UUID.randomUUID();out.store("Inventory",ToolboxInventory.CODEC,inventory);if(uniqueId!=null)out.store("UniqueId",UUIDUtil.CODEC,uniqueId);if(customName!=null)out.store("CustomName",ComponentSerialization.CODEC,customName);}
    @Override protected void read(ValueInput in, boolean clientPacket){in.read("Inventory",ToolboxInventory.SAVED_INVENTORY_CODEC).ifPresent(inventory::copyFrom);uniqueId=in.read("UniqueId",UUIDUtil.CODEC).orElse(null);customName=in.read("CustomName",ComponentSerialization.CODEC).orElse(null);openCount=Math.max(0,in.getIntOr("OpenCount",0));
        super.read(in, clientPacket);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);tag.putInt("OpenCount",openCount);return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override protected void applyImplicitComponents(DataComponentGetter components){super.applyImplicitComponents(components);setUniqueId(components.get(AllDataComponents.TOOLBOX_UUID));readInventory(components.get(AllDataComponents.TOOLBOX_INVENTORY));customName=components.get(DataComponents.CUSTOM_NAME);}
    @Override protected void collectImplicitComponents(DataComponentMap.Builder components){super.collectImplicitComponents(components);if(uniqueId==null&&(level==null||!level.isClientSide()))uniqueId=UUID.randomUUID();if(uniqueId!=null)components.set(AllDataComponents.TOOLBOX_UUID,uniqueId);components.set(AllDataComponents.TOOLBOX_INVENTORY,inventory.frozenCopy());if(customName!=null)components.set(DataComponents.CUSTOM_NAME,customName);}
    @Override public void invalidate(){ToolboxHandler.onUnload(this);super.invalidate();}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState replacement){if(!(replacement.getBlock() instanceof ToolboxBlock))unequipTracked(true);super.preRemoveSideEffects(pos,replacement);}
}
