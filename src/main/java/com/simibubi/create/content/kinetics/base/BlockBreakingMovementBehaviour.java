package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.utility.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Original breaking/stall/waiting lifecycle, adapted to native damage and transactional loot. */
public class BlockBreakingMovementBehaviour implements MovementBehaviour {
    @Override public void startMoving(MovementContext context){if(!context.world.isClientSide())context.data.putInt("BreakerId",BlockBreakingKineticBlockEntity.nextBreakerId());}
    private static BlockPos readPos(CompoundTag data,String key){if(data.get(key) instanceof CompoundTag old)return new BlockPos(old.getIntOr("X",0),old.getIntOr("Y",0),old.getIntOr("Z",0));return data.read(key,BlockPos.CODEC).orElse(BlockPos.ZERO);}
    @Override public void visitNewPosition(MovementContext context,BlockPos pos){
        if(!LoadedLevelAccess.isLoaded(context.world,pos))return;var state=LoadedLevelAccess.blockState(context.world,pos);
        if(!state.isRedstoneConductor(context.world,pos))damageEntities(context,pos,context.world);
        if(context.world.isClientSide()||!canBreak(context.world,pos,state))return;
        context.data.store("BreakingPos",BlockPos.CODEC,pos);context.stall=true;
    }
    public void damageEntities(MovementContext context,BlockPos pos,Level level){
        if(context.contraption.entity instanceof com.simibubi.create.content.contraptions.OrientedContraptionEntity oriented&&oriented.nonDamageTicks>0)return;
        var source=getDamageSource(level);if(source==null&&!throwsEntities(level))return;
        for(var entity:level.getEntitiesOfClass(Entity.class,new AABB(pos))){
            if(entity instanceof ItemEntity||entity instanceof AbstractContraptionEntity||context.contraption.entity!=null&&(entity.isPassengerOfSameVehicle(context.contraption.entity)||context.contraption.entity.hasIndirectPassenger(entity)))continue;
            if(source!=null&&level instanceof ServerLevel server)entity.hurtServer(server,source,(float)Mth.clamp(6*Math.pow(context.relativeMotion.length(),.4)+1,2,10));
            if(throwsEntities(level)&&level.isClientSide()==(entity instanceof Player))throwEntity(context,entity);
        }
    }
    protected void throwEntity(MovementContext context,Entity entity){var boost=context.motion.add(0,context.motion.length()/4,0);if(boost.length()>4)boost=boost.normalize().scale(4);entity.setDeltaMovement(entity.getDeltaMovement().add(boost));entity.syncVelocity=true;}
    protected DamageSource getDamageSource(Level level){return null;}
    protected boolean throwsEntities(Level level){return getDamageSource(level)!=null;}
    @Override public void cancelStall(MovementContext context){
        if(context.world.isClientSide())return;var data=context.data;
        if(data.contains("BreakingPos"))context.world.destroyBlockProgress(data.getIntOr("BreakerId",0),readPos(data,"BreakingPos"),-1);
        for(var key:new String[]{"Progress","TicksUntilNextProgress","BreakingPos","WaitingTicks","LastPos"})data.remove(key);context.stall=false;
    }
    @Override public void stopMoving(MovementContext context){cancelStall(context);}
    @Override public void tick(MovementContext context){
        if(context.world.isClientSide())return;tickBreaker(context);var data=context.data;if(!data.contains("WaitingTicks"))return;
        int ticks=data.getIntOr("WaitingTicks",0);if(ticks-->0){data.putInt("WaitingTicks",ticks);context.stall=true;return;}
        var pos=readPos(data,"LastPos");if(!LoadedLevelAccess.isLoaded(context.world,pos)){context.stall=true;return;}data.remove("WaitingTicks");data.remove("LastPos");context.stall=false;visitNewPosition(context,pos);
    }
    public void tickBreaker(MovementContext context){
        if(!(context.world instanceof ServerLevel level))return;var data=context.data;
        if(!data.contains("BreakingPos")||context.relativeMotion.equals(Vec3.ZERO)){context.stall=false;return;}
        var pos=readPos(data,"BreakingPos");if(!LoadedLevelAccess.isLoaded(level,pos)){context.stall=true;return;}
        int ticks=data.getIntOr("TicksUntilNextProgress",0);if(ticks-->0){data.putInt("TicksUntilNextProgress",ticks);return;}
        var state=LoadedLevelAccess.blockState(level,pos);int progress=data.getIntOr("Progress",0),id=data.getIntOr("BreakerId",0);
        if(!canBreak(level,pos,state)){cancelStall(context);return;}
        float hardness=state.getDestroySpeed(level,pos),speed=getBlockBreakingSpeed(context);
        progress+=Mth.clamp(hardness==0?10:(int)(speed/hardness),1,10-progress);level.playSound(null,pos,state.getSoundType().getHitSound(),SoundSource.NEUTRAL,.25f,1);
        if(progress>=10){
            // Do not break the lower block while an unknown/unloaded falling-block column continues above it.
            var original=pos;var above=pos.above();while(true){if(!LoadedLevelAccess.isLoaded(level,above)){context.stall=true;return;}var next=LoadedLevelAccess.blockState(level,above);if(!(next.getBlock() instanceof FallingBlock))break;pos=above;above=above.above();}
            state=LoadedLevelAccess.blockState(level,pos);level.destroyBlockProgress(id,original,-1);context.stall=false;
            if(shouldDestroyStartBlock(state))destroyBlock(context,pos);onBlockBroken(context,original,state);
            data.remove("Progress");data.remove("TicksUntilNextProgress");data.remove("BreakingPos");return;
        }
        level.destroyBlockProgress(id,pos,progress);data.putInt("Progress",progress);data.putInt("TicksUntilNextProgress",(int)(hardness/speed));
    }
    protected void destroyBlock(MovementContext context,BlockPos pos){if(context.world instanceof ServerLevel server)BlockHelper.destroyBlock(server,pos,1,stack->collectOrDropItem(context,stack));}
    protected float getBlockBreakingSpeed(MovementContext context){return Mth.clamp(Math.abs(context.getAnimationSpeed())/500f,1/128f,16);}
    protected boolean shouldDestroyStartBlock(BlockState state){return true;}
    public boolean canBreak(Level level,BlockPos pos,BlockState state){return BlockBreakingKineticBlockEntity.isBreakable(state,state.getDestroySpeed(level,pos));}
    protected void onBlockBroken(MovementContext context,BlockPos pos,BlockState state){if(state.getBlock() instanceof FallingBlock){context.data.putInt("WaitingTicks",10);context.data.store("LastPos",BlockPos.CODEC,pos);context.stall=true;}}
}
