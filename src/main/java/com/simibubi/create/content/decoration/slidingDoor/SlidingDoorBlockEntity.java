package com.simibubi.create.content.decoration.slidingDoor;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public class SlidingDoorBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    private float animation,previousAnimation;
    private int bridgeTicks;
    private boolean deferUpdate=true;
    private long animationTick=Long.MIN_VALUE;
    public SlidingDoorBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.SLIDING_DOOR,pos,state);animation=previousAnimation=isOpen(state)?1:0;}
    public void deferUpdate(){deferUpdate=true;}
    public boolean isUpdateDeferred(){return deferUpdate;}
    public float getAnimation(float partial){return previousAnimation+(animation-previousAnimation)*partial;}
    public boolean shouldRenderSpecial(BlockState state){return !isVisible(state)||bridgeTicks!=0;}
    public static boolean isOpen(BlockState state){return state.getOptionalValue(DoorBlock.OPEN).orElse(false);}
    public static boolean isVisible(BlockState state){return state.getOptionalValue(SlidingDoorBlock.VISIBLE).orElse(true);}
    /** Called for mounted doors by their actor; the world BE ticker calls it for placed doors. */
    public boolean tickAnimation(boolean open){
        if(level!=null&&animationTick==level.getGameTime())return false;if(level!=null)animationTick=level.getGameTime();
        previousAnimation=animation;animation=open?Math.min(1,animation+.15f):Math.max(0,animation-.15f);
        if(bridgeTicks<2&&open)bridgeTicks++;else if(bridgeTicks>0&&!open&&isVisible(getBlockState()))bridgeTicks--;
        return previousAnimation>0&&animation==0;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,SlidingDoorBlockEntity door){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, door)) {
            if (!createTick.shouldTick()) return;

        if(!level.isClientSide()&&door.deferUpdate){door.deferUpdate=false;level.neighborChanged(state,pos,Blocks.AIR,null,false);state=door.getBlockState();}
        boolean closed=door.tickAnimation(isOpen(state));
        if(!level.isClientSide()&&!isOpen(state)&&!isVisible(state)&&(closed||door.animation==0)){
            level.setBlock(pos,state.setValue(SlidingDoorBlock.VISIBLE,true),Block.UPDATE_ALL);
            var upper=LoadedLevelAccess.blockState(level,pos.above());if(upper.is(state.getBlock()))level.setBlock(pos.above(),upper.setValue(SlidingDoorBlock.VISIBLE,true),Block.UPDATE_ALL);
            level.playSound(null,pos,SoundEvents.IRON_DOOR_CLOSE,SoundSource.BLOCKS,.5f,1);
        }
    
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putBoolean("DeferredDoorUpdate",deferUpdate);}
    @Override protected void read(ValueInput input, boolean clientPacket){super.read(input, clientPacket);deferUpdate=input.getBooleanOr("DeferredDoorUpdate",true);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
