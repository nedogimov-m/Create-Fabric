package com.simibubi.create.content.contraptions.actors.contraptionControls;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Source global-to-specific inversion, applied atomically before one native actor snapshot. */
public class ContraptionControlsMovingInteraction extends MovingInteractionBehaviour {
    @Override public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos, AbstractContraptionEntity owner) {
        var contraption = owner.getContraption();
        if (contraption == null) return false;
        var actor = contraption.getActorAt(localPos);
        if (actor == null || actor.right == null || !(MovementBehaviour.REGISTRY.get(actor.left.state()) instanceof ContraptionControlsMovement)) return false;
        if(contraption instanceof com.simibubi.create.content.contraptions.elevator.ElevatorContraption){
            if(owner.level().isClientSide()){
                if(!(actor.right.temporaryData instanceof ContraptionControlsMovement.ElevatorFloorSelection selection))return false;if(selection.targetYEqualsSelection)return true;com.simibubi.create.content.contraptions.elevator.ElevatorNetworking.selectFromControls(owner,localPos);if(contraption.getBlockEntityClientSide(localPos) instanceof ContraptionControlsBlockEntity controls)controls.pressButton();return true;
            }
            var global=BlockPos.containing(owner.toGlobalVector(Vec3.atCenterOf(localPos),1));owner.level().playSound(null,global,AllSoundEvents.CONTROLLER_CLICK,SoundSource.BLOCKS,1,1.5f);owner.level().playSound(null,global,AllSoundEvents.CONTRAPTION_ASSEMBLE,SoundSource.BLOCKS,.75f,.8f);return true;
        }
        if (owner.level().isClientSide()) {
            if (contraption.getBlockEntityClientSide(localPos) instanceof ContraptionControlsBlockEntity controls) controls.pressButton();
            return true;
        }
        var filter = ContraptionControlsMovement.getFilter(actor.right);
        if (filter == null) return false;
        boolean disabled = toggle(contraption, filter);
        ContraptionControlsBlockEntity.sendStatus(player, filter, !disabled);
        var global = BlockPos.containing(owner.toGlobalVector(Vec3.atCenterOf(localPos), 1));
        owner.level().playSound(null, global, AllSoundEvents.CONTROLLER_CLICK, SoundSource.BLOCKS, 1, disabled ? .8f : 1.5f);
        ContraptionNetworking.syncActors(owner);
        return true;
    }
    /** The empty stack is the source global sentinel; every other filter compares components. */
    public static boolean toggle(Contraption contraption, ItemStack filter) {
        boolean disable = !ContraptionControlsMovement.isDisabled(contraption, filter);
        ContraptionControlsMovement.setActorTypeActive(contraption, filter, !disable);
        return disable;
    }
}
