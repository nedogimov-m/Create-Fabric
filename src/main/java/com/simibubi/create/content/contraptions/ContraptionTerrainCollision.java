package com.simibubi.create.content.contraptions;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import com.simibubi.create.content.contraptions.piston.PistonContraption;
import com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.phys.Vec3;

/** Source block-grid terrain collision, distinct from OBB collision against riding entities. */
public final class ContraptionTerrainCollision {
    public static boolean isCollidingWithWorld(Level level,TranslatingContraption structure,BlockPos anchor,Direction direction)throws AssemblyException{
        var view=ContraptionWorld.loadedWorld(level);
        // Availability for the complete leading set is checked before invoking addon actor rules.
        for(var local:structure.getOrCreateColliders(level,direction)){
            var target=local.offset(anchor);if(!level.isInValidBounds(target))return true;
            if(!LoadedLevelAccess.isLoaded(level,target))throw AssemblyException.entitiesNotReady(target);
            LoadedEntityAccess.requireReady(level,target);
            for(var side:Direction.values())if(level.isInValidBounds(target.relative(side))&&!LoadedLevelAccess.isLoaded(level,target.relative(side)))throw AssemblyException.entitiesNotReady(target.relative(side));
        }
        for(var local:structure.getOrCreateColliders(level,direction)){
            var target=local.offset(anchor);var state=LoadedLevelAccess.blockState(level,target);
            if(structure instanceof PistonContraption piston&&target.equals(piston.getControllerPos())&&state.getBlock() instanceof com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock)continue;
            if(state.getBlock() instanceof CocoaBlock)continue;
            boolean empty=state.getCollisionShape(view,target).isEmpty();var moving=structure.getBlocks().get(local).state();var behaviour=MovementBehaviour.REGISTRY.get(moving);
            if(behaviour instanceof BlockBreakingMovementBehaviour breaker){if(!breaker.canBreak(level,target,state)&&!empty)return true;continue;}
            if(behaviour instanceof HarvesterMovementBehaviour harvester){if(!harvester.isValidCrop(level,target,state)&&!harvester.isValidOther(level,target,state)&&!empty)return true;continue;}
            if(!state.canBeReplaced()&&!empty)return true;
        }return false;
    }
    public static boolean collideBlocks(AbstractContraptionEntity owner,Vec3 movement)throws AssemblyException{return collideBlocksAt(owner,owner.position().add(movement),movement);}
    public static boolean collideBlocksAt(AbstractContraptionEntity owner,Vec3 collisionPosition,Vec3 movement)throws AssemblyException{
        if(!owner.hasOwnedStructure()||!(owner.getContraption() instanceof TranslatingContraption structure)||movement.lengthSqr()==0)return false;
        var direction=Direction.getApproximateNearest(movement);var anchor=BlockPos.containing(collisionPosition);
        if(direction.getAxisDirection()==Direction.AxisDirection.POSITIVE)anchor=anchor.relative(direction);
        if(isCollidingWithWorld(owner.level(),structure,anchor,direction))return true;
        var bounds=owner.getBoundingBox().expandTowards(movement).inflate(1);
        for(var other:owner.level().getEntitiesOfClass(AbstractContraptionEntity.class,bounds,entity->entity!=owner&&entity.isAlive())){
            if(!other.hasOwnedStructure()||!(other.getContraption() instanceof TranslatingContraption translated))continue;
            if(!owner.getBoundingBox().move(movement).intersects(other.getBoundingBox().move(other.getDeltaMovement())))continue;
            for(var local:structure.getOrCreateColliders(owner.level(),direction))if(translated.getBlocks().containsKey(local.offset(anchor).subtract(other.blockPosition())))return true;
        }return false;
    }
    private ContraptionTerrainCollision(){}
}
