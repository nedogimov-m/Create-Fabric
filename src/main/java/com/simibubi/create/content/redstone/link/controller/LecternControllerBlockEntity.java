package com.simibubi.create.content.redstone.link.controller;

import com.simibubi.create.*;
import java.util.UUID;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

public final class LecternControllerBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity{
    public static java.util.function.Consumer<LecternControllerBlockEntity> CLIENT_SESSION=entity->{};
    private ItemContainerContents controllerData=ItemContainerContents.EMPTY;
    private UUID user,previousClientUser;private boolean deactivatedThisTick,containsController=true;
    public LecternControllerBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.LECTERN_CONTROLLER,pos,state);}
    public ItemStack getController(){if(!containsController)return ItemStack.EMPTY;var stack=new ItemStack(AllItems.LINKED_CONTROLLER);stack.set(AllDataComponents.LINKED_CONTROLLER_ITEMS,controllerData);return stack;}
    public boolean hasUser(){return user!=null;}public UUID getUser(){return user;}
    public boolean isUsedBy(Player player){return user!=null&&user.equals(player.getUUID());}
    private BlockEntity publishedEntity(){
        if(level==null)return null;var chunk=level.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
        return chunk==null?null:chunk.getBlockEntities().get(worldPosition);
    }
    public boolean isPublished(){return publishedEntity()==this;}
    public static boolean playerInRange(Player player,Level level,BlockPos pos){double reach=.4*player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);return player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))<reach*reach;}
    public boolean tryStartUsing(Player player){
        if(!(level instanceof ServerLevel server)||deactivatedThisTick||user!=null||!containsController||player.isSpectator()
            ||player.level()!=level||!isPublished()||!playerInRange(player,level,worldPosition))return false;
        if(!LinkedControllerServerHandler.claimSession(server,player.getUUID(),worldPosition))return false;
        user=player.getUUID();changed();return true;
    }
    public void tryStopUsing(Player player){if(isUsedBy(player))stopUsing();}
    public void stopUsing(){if(user==null)return;var previous=user;user=null;deactivatedThisTick=true;
        if(level instanceof ServerLevel server&&LinkedControllerServerHandler.ownsSession(server,previous,worldPosition)
            &&(publishedEntity()==null||publishedEntity()==this)){
            LinkedControllerServerHandler.releasePlayer(server.getServer(),previous);
        }changed();}
    public void setController(ItemStack controller){
        controllerData=controller.getOrDefault(AllDataComponents.LINKED_CONTROLLER_ITEMS,ItemContainerContents.EMPTY);containsController=true;changed();
        if(level!=null)level.playSound(null,worldPosition,AllSoundEvents.CONTROLLER_PUT,SoundSource.BLOCKS,1,1);
    }
    public void swapControllers(ItemStack held,Player player,InteractionHand hand){
        if(!(level instanceof ServerLevel)||held.isEmpty())return;
        var previous=getController();var replacement=held.copyWithCount(1);stopUsing();setController(replacement);
        held.shrink(1);
        if(held.isEmpty())player.setItemInHand(hand,previous);
        else QueuedControllerDrops.dropOrQueue((ServerLevel)level,worldPosition,previous);
    }
    public void dropController(){
        if(!(level instanceof ServerLevel server)||!containsController)return;
        stopUsing();var stack=getController();QueuedControllerDrops.dropOrQueue(server,worldPosition,stack);
        containsController=false;controllerData=ItemContainerContents.EMPTY;changed();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,LecternControllerBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide()){
            if(!java.util.Objects.equals(entity.user,entity.previousClientUser)){
                entity.previousClientUser=entity.user;CLIENT_SESSION.accept(entity);
            }return;
        }
        if(!(level instanceof ServerLevel server))return;entity.deactivatedThisTick=false;
        if(entity.user==null)return;var player=server.getServer().getPlayerList().getPlayer(entity.user);
        if(player==null||player.isSpectator()||player.level()!=level||!playerInRange(player,level,pos)
            ||!LinkedControllerServerHandler.ownsSession(server,entity.user,pos))entity.stopUsing();
    
        }
    }
    @Override public void invalidate(){stopUsing();super.invalidate();}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){dropController();super.preRemoveSideEffects(pos,state);}
    private void changed(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.store("ControllerData",ItemContainerContents.CODEC,controllerData);out.putBoolean("HasController",containsController);if(user!=null)out.store("User",UUIDUtil.CODEC,user);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);controllerData=in.read("ControllerData",ItemContainerContents.CODEC).orElse(ItemContainerContents.EMPTY);containsController=in.getBooleanOr("HasController",true);user=in.read("User",UUIDUtil.CODEC).orElse(null);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
