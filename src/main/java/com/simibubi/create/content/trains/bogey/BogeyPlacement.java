package com.simibubi.create.content.trains.bogey;

import com.simibubi.create.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/** Source station operation kernel. Call only AFTER a real station admits its assembly area and bogey offset.
 * This class registers no global casing-on-track callback and grants no station/assembly ownership.
 */
public final class BogeyPlacement {
    public static boolean placeAtAuthorizedTrack(Player player,InteractionHand hand,ITrackBlock track,BlockState state,BlockPos pos){
        var level=player.level();
        if(level.isClientSide()||player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(pos,1)||!level.mayInteract(player,pos)||!LoadedLevelAccess.isLoaded(level,pos)||LoadedLevelAccess.blockState(level,pos)!=state||state.getBlock()!=track)return false;
        var axes=track.getTrackAxes(level,pos,state);
        if(axes.size()!=1||!track.getUpNormal(level,pos,state).equals(new Vec3(0,1,0)))return false;
        var axis=axes.getFirst();if(axis.y!=0||Math.abs(axis.x)+Math.abs(axis.z)!=1)return false;
        if(state.hasProperty(TrackBlock.HAS_BE)&&state.getValue(TrackBlock.HAS_BE))return false;
        var anchor=track.getBogeyAnchor(level,pos,state);
        if(!(anchor.getBlock()instanceof AbstractBogeyBlock<?> bogey)||bogey.isOnIncompatibleTrack(track.getMaterial(),bogey.getDefaultStyle()))return false;
        var target=pos.above();if(!LoadedLevelAccess.isLoaded(level,target)||!level.mayInteract(player,target))return false;
        var previous=LoadedLevelAccess.blockState(level,target);if(previous.getBlock()instanceof AbstractBogeyBlock<?>||previous.getDestroySpeed(level,target)==-1)return false;
        var stack=player.getItemInHand(hand);if(!player.isCreative()&&!stack.is(AllBlocks.RAILWAY_CASING.asItem()))return false;
        anchor=anchor.setValue(AbstractBogeyBlock.WATERLOGGED,LoadedLevelAccess.blockState(level,pos).getFluidState().getType()==Fluids.WATER);
        if(!previous.isAir()&&!level.destroyBlock(target,true))return false;
        if(!level.setBlock(target,anchor,Block.UPDATE_ALL))return false;
        if(!(LoadedLevelAccess.blockEntity(level,target)instanceof AbstractBogeyBlockEntity))throw new IllegalStateException("Registered bogey block has no real bogey BE");
        player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("create.train_assembly.bogey_created"));
        var sound=anchor.getSoundType();level.playSound(null,pos,sound.getPlaceSound(),SoundSource.BLOCKS,(sound.getVolume()+1)/2,sound.getPitch()*.8f);
        if(!player.isCreative())stack.shrink(1);return true;
    }
    /** Station-owned existing-bogey size action; callers perform source +/-1 and spacing selection. */
    public static boolean cycleSize(Player player,BlockPos pos){
        var level=player.level();if(level.isClientSide()||player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(pos,1)||!level.mayInteract(player,pos))return false;
        var state=LoadedLevelAccess.blockState(level,pos);if(!(state.getBlock()instanceof AbstractBogeyBlock<?> block)||!(LoadedLevelAccess.blockEntity(level,pos)instanceof AbstractBogeyBlockEntity entity))return false;
        var next=block.getNextSize(entity);if(next==state)return false;
        if(!replace(level,pos,state,next,entity.getBogeyData()))return false;
        level.playSound(null,pos,AllSoundEvents.WRENCH_ROTATE,SoundSource.BLOCKS,1,1);return true;
    }
    /** Retains source data across actual registered block/BE replacement; no detached copied owner. */
    public static boolean replace(Level level,BlockPos pos,BlockState expected,BlockState replacement,CompoundTag data){
        if(level.isClientSide()||!(replacement.getBlock()instanceof AbstractBogeyBlock<?>)||!LoadedLevelAccess.isLoaded(level,pos)||LoadedLevelAccess.blockState(level,pos)!=expected)return false;
        var retained=data.copy();
        if(expected!=replacement&&!level.setBlock(pos,replacement,Block.UPDATE_ALL))return false;
        if(!(LoadedLevelAccess.blockEntity(level,pos)instanceof AbstractBogeyBlockEntity entity))return false;
        entity.setBogeyData(retained);return true;
    }
    private BogeyPlacement(){}
}
