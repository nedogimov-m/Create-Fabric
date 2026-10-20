package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.AllItems;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public class DeployerMovingInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player,InteractionHand hand,BlockPos pos,AbstractContraptionEntity owner){
        var pair=owner.getContraption().getActorAt(pos);if(pair==null||pair.right==null)return false;if(owner.level().isClientSide())return true;
        var context=pair.right;if(!(MovementBehaviour.REGISTRY.get(context.state) instanceof DeployerMovementBehaviour behaviour))return false;
        if(player.getItemInHand(hand).is(AllItems.WRENCH)){behaviour.cancelStall(context);context.blockEntityData.putString("Mode",DeployerMovementBehaviour.getMode(context)==DeployerBlockEntity.Mode.PUNCH?"USE":"PUNCH");}
        else{var fake=behaviour.getPlayer(context);if(fake==null)return false;behaviour.cancelStall(context);var held=player.getItemInHand(hand).copy();player.setItemInHand(hand,fake.getMainHandItem().copy());fake.setItemInHand(InteractionHand.MAIN_HAND,held);}
        behaviour.sync(context);return true;
    }
}
