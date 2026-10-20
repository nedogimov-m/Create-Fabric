package com.simibubi.create.content.decoration.slidingDoor;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DoorBlock;

/** Server-validated ray selection is supplied by AbstractContraptionEntity. */
public final class SlidingDoorMovingInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player,InteractionHand hand,BlockPos pos,AbstractContraptionEntity owner){
        var structure=owner.getContraption();if(structure==null||owner.level().isClientSide())return false;
        var info=structure.getBlocks().get(pos);if(info==null||!(info.state().getBlock() instanceof SlidingDoorBlock))return false;
        boolean open=!info.state().getValue(DoorBlock.OPEN);SlidingDoorMovementBehaviour.setDoorOpen(structure,pos,open,true);
        var otherPos=SlidingDoorBlock.pairedPos(pos,info.state());var other=structure.getBlocks().get(otherPos);
        if(other!=null&&SlidingDoorBlock.isDoubleDoor(info.state().setValue(DoorBlock.OPEN,open),info.state().getValue(DoorBlock.HINGE),info.state().getValue(DoorBlock.FACING),other.state()))SlidingDoorMovementBehaviour.setDoorOpen(structure,otherPos,open,true);
        if(open)owner.level().playSound(null,BlockPos.containing(owner.toGlobalVector(net.minecraft.world.phys.Vec3.atCenterOf(pos),1)),SoundEvents.IRON_DOOR_OPEN,SoundSource.BLOCKS,1,owner.level().getRandom().nextFloat()*.1f+.9f);
        return true;
    }
}
