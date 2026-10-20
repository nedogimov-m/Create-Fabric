package com.simibubi.create.content.decoration.placard;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** The original displayed item remains a real owned stack, with the source19tick confirmation pulse. */
public final class PlacardBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    private ItemStack heldItem=ItemStack.EMPTY;
    private int poweredTicks;
    public PlacardBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.PLACARD,pos,state);}
    public ItemStack getHeldItem(){return heldItem;}
    public int getPoweredTicks(){return poweredTicks;}
    public void setHeldItem(ItemStack item){heldItem=item.copy();notifyUpdate();if(level!=null)com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level,worldPosition);}
    public void startPulse(){poweredTicks=19;notifyUpdate();}
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    public static void tick(Level level,BlockPos pos,BlockState state,PlacardBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide()||entity.poweredTicks==0)return;entity.poweredTicks--;entity.setChanged();if(entity.poweredTicks>0)return;
        if(level.setBlock(pos,state.setValue(PlacardBlock.POWERED,false),Block.UPDATE_ALL))PlacardBlock.updateNeighbours(state,level,pos);
    
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){var item=heldItem;heldItem=ItemStack.EMPTY;if(!item.isEmpty())Block.popResource(level,pos,item);super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("PoweredTicks",poweredTicks);out.store("Item",ItemStack.OPTIONAL_CODEC,heldItem);}
    @Override protected void read(ValueInput in, boolean clientPacket){int previous=poweredTicks;poweredTicks=Math.clamp(in.getIntOr("PoweredTicks",0),0,19);heldItem=in.read("Item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);if(level!=null&&level.isClientSide()&&previous<poweredTicks)spawnParticles();
        super.read(in, clientPacket);
    }
    private void spawnParticles(){
        var normal=Vec3.ZERO.relative(PlacardBlock.connectedDirection(getBlockState()),1);var plane=new Vec3(1-Math.abs(normal.x),1-Math.abs(normal.y),1-Math.abs(normal.z));var center=Vec3.atCenterOf(worldPosition);var random=level.getRandom();
        for(int i=0;i<10;i++){var point=new Vec3(random.nextDouble()-.5,random.nextDouble()-.5,random.nextDouble()-.5).multiply(plane).normalize().scale(.45).add(normal.scale(-.45)).add(center);level.addParticle(new DustParticleOptions(0xFF3300,1),point.x,point.y,point.z,0,0,0);}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
