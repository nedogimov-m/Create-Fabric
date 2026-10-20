package com.simibubi.create.content.contraptions.behaviour;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.equipment.bell.AbstractBellBlock;
import com.simibubi.create.content.redstone.deskBell.DeskBellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.*;
import net.minecraft.world.phys.Vec3;

public class BellMovementBehaviour implements MovementBehaviour {
    @Override public void onSpeedChanged(MovementContext context,Vec3 previous,Vec3 motion) { if(previous.dot(motion)<=0&&context.relativeMotion.lengthSqr()!=0||context.firstMovement)playSound(context); }
    @Override public void stopMoving(MovementContext context) { if(context.position!=null&&isActive(context))playSound(context); }
    public static void playSound(MovementContext context) {
        if(context.world.isClientSide()||context.position==null)return;var pos=BlockPos.containing(context.position);
        if(context.state.getBlock() instanceof AbstractBellBlock bell)bell.playSound(context.world,pos);
        else if(context.state.getBlock() instanceof DeskBellBlock bell)bell.playSound(null,context.world,pos);
        else context.world.playSound(null,pos,SoundEvents.BELL_BLOCK,SoundSource.BLOCKS,2,1);
    }
}
