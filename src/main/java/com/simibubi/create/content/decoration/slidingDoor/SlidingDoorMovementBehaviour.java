package com.simibubi.create.content.decoration.slidingDoor;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.lang.ref.WeakReference;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.sounds.*;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;

public class SlidingDoorMovementBehaviour implements MovementBehaviour {
    @Override public boolean mustTickWhileDisabled(){return true;}
    @Override public void tick(MovementContext context){
        var info=context.contraption.getBlocks().get(context.localPos);if(info==null)return;
        boolean open=SlidingDoorBlockEntity.isOpen(info.state());
        if(!context.world.isClientSide()){tickOpen(context,open);return;}
        if(context.contraption.getBlockEntityClientSide(context.localPos) instanceof SlidingDoorBlockEntity door&&door.tickAnimation(open)&&context.position!=null)
            context.world.playLocalSound(context.position.x,context.position.y,context.position.z,SoundEvents.IRON_DOOR_CLOSE,SoundSource.BLOCKS,.125f,1,false);
    }
    protected void tickOpen(MovementContext context,boolean currentlyOpen){
        boolean open=shouldOpen(context);if(!shouldUpdate(context,open)||currentlyOpen==open)return;
        setDoorOpen(context.contraption,context.localPos,open,true);
        if(context.position==null)return;
        var facing=getDoorFacing(context);var pos=BlockPos.containing(context.position).relative(facing);var state=LoadedLevelAccess.blockState(context.world,pos);
        if(state.getBlock() instanceof DoorBlock door&&state.getValue(DoorBlock.FACING).getAxis()==facing.getAxis())door.setOpen(null,context.world,state,pos,open);
        if(open)context.world.playSound(null,BlockPos.containing(context.position),SoundEvents.IRON_DOOR_OPEN,SoundSource.BLOCKS,.125f,1);
    }
    protected boolean shouldUpdate(MovementContext context,boolean open){
        if(context.firstMovement&&open)return false;
        boolean changed=!context.data.contains("Open")||context.data.getBooleanOr("Open",false)!=open;
        context.data.putBoolean("Open",open);return changed;
    }
    protected boolean shouldOpen(MovementContext context){
        if(context.disabled)return false;
        if(!canOpen(context)){context.temporaryData=null;return false;}
        if(context.temporaryData instanceof WeakReference<?> ref&&ref.get() instanceof DoorControlBehaviour control&&control.blockEntity!=null&&!control.blockEntity.isRemoved())return shouldOpenAt(control,context);
        context.temporaryData=null;var control=DoorControlBehaviour.resolve(context);if(control==null)return false;
        context.temporaryData=new WeakReference<>(control);return shouldOpenAt(control,context);
    }
    /** Elevator arrival can override this rule when its actual controller is ported. */
    protected boolean canOpen(MovementContext context){return context.contraption.entity!=null&&(context.motion.length()<1/128f&&!context.contraption.entity.isStalled()||context.contraption instanceof com.simibubi.create.content.contraptions.elevator.ElevatorContraption cabin&&cabin.arrived);}
    protected boolean shouldOpenAt(DoorControlBehaviour control,MovementContext context){return control.mode.matches(getDoorFacing(context));}
    protected Direction getDoorFacing(MovementContext context){
        var stateFacing=context.state.getValue(DoorBlock.FACING);var facing=Direction.get(Direction.AxisDirection.POSITIVE,stateFacing.getAxis());
        var center=context.contraption.bounds==null?Vec3.ZERO:context.contraption.bounds.getCenter();
        var diff=Vec3.atCenterOf(context.localPos).add(stateFacing.getUnitVec3().scale(-.45)).subtract(center);
        if(facing.getAxis().choose(diff.x,diff.y,diff.z)<0)facing=facing.getOpposite();
        return Direction.getApproximateNearest(context.rotation.apply(facing.getUnitVec3()));
    }
    /** Changes both real captured states and their actor views; collision queries read these states directly. */
    public static List<BlockPos> setDoorOpen(Contraption contraption,BlockPos pos,boolean open,boolean sync){
        var info=contraption.getBlocks().get(pos);if(info==null||!(info.state().getBlock() instanceof SlidingDoorBlock))return List.of();
        var positions=new ArrayList<BlockPos>(2);applyHalf(contraption,pos,open,positions);
        var other=info.state().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER?pos.above():pos.below();var mate=contraption.getBlocks().get(other);
        if(mate!=null&&mate.state().is(info.state().getBlock())&&mate.state().getValue(DoorBlock.HALF)!=info.state().getValue(DoorBlock.HALF))applyHalf(contraption,other,open,positions);
        var owner=contraption.entity;if(sync&&!positions.isEmpty()&&owner!=null&&!owner.level().isClientSide()){
            var packet=new DoorStatePacket(owner.getId(),owner.getUUID(),pos,open);for(var player:PlayerLookup.tracking(owner))ServerPlayNetworking.send(player,packet);
        }
        return positions;
    }
    private static void applyHalf(Contraption contraption,BlockPos pos,boolean open,List<BlockPos> positions){
        var old=contraption.getBlocks().get(pos);var state=old.state().setValue(DoorBlock.OPEN,open).setValue(SlidingDoorBlock.VISIBLE,false);if(state==old.state())return;
        var next=new StructureBlockInfo(old.pos(),state,old.nbt());contraption.getBlocks().put(pos,next);var actor=contraption.getActorAt(pos);if(actor!=null){actor.setLeft(next);if(actor.right!=null)actor.right.state=state;}
        if(contraption.getBlockEntityClientSide(pos) instanceof SlidingDoorBlockEntity door)door.setBlockState(state);positions.add(pos.immutable());
    }
}
