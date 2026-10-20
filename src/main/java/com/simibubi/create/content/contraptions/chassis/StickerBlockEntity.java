package com.simibubi.create.content.contraptions.chassis;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.glue.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persisted state is the real block latch; interpolation and feedback are native client state. */
public class StickerBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    private float previousPiston,piston,target;
    private long animationTick=Long.MIN_VALUE;
    public StickerBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.STICKER,pos,state);previousPiston=piston=target=extended(state)?1:0;}
    private static boolean extended(BlockState state){return state.getBlock() instanceof StickerBlock&&state.getValue(StickerBlock.EXTENDED);}
    public boolean isBlockStateExtended(){return extended(getBlockState());}
    public boolean isAttachedToBlock(){
        if(level==null||!(getBlockState().getBlock() instanceof StickerBlock))return false;var facing=getBlockState().getValue(StickerBlock.FACING);var next=worldPosition.relative(facing);
        return LoadedLevelAccess.isLoaded(level,next)&&SuperGlueEntity.isValidFace(level,next,facing.getOpposite());
    }
    public float getPiston(float partial){return previousPiston+(piston-previousPiston)*partial;}
    public void tickClientAnimation(){
        if(level==null||!level.isClientSide()||animationTick==level.getGameTime())return;animationTick=level.getGameTime();
        previousPiston=piston;piston=target>piston?Math.min(target,piston+.4f):Math.max(target,piston-.4f);
        if(piston==1&&previousPiston!=1&&isAttachedToBlock()){SuperGlueItem.spawnParticles(level,worldPosition,getBlockState().getValue(StickerBlock.FACING),true);playSound(true);}
        float next=isBlockStateExtended()?1:0;
        if(next!=target){if(next==0&&target==1&&isAttachedToBlock())playSound(false);target=next;}
    }
    private void playSound(boolean attach){level.playLocalSound(worldPosition,AllSoundEvents.SLIME_ADDED,SoundSource.BLOCKS,.35f,attach?.75f:.2f,false);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
