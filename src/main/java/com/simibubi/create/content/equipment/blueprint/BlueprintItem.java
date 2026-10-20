package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Native hanging entity placement for source wall/floor/ceiling blueprints. */
public final class BlueprintItem extends Item {
 public BlueprintItem(Properties properties){super(properties);}
 @Override public InteractionResult useOn(UseOnContext context){
  var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var face=context.getClickedFace();var pos=context.getClickedPos().relative(face);
  if(player!=null&&!player.mayUseItemAt(pos,face,stack))return InteractionResult.FAIL;
  if(!LoadedEntityAccess.isReady(level,pos)||!level.isInValidBounds(pos)||!level.getWorldBorder().isWithinBounds(pos))return InteractionResult.FAIL;
  var blueprint=new BlueprintEntity(level,pos,face,face.getAxis().isHorizontal()?Direction.DOWN:context.getHorizontalDirection());
  var data=stack.get(DataComponents.ENTITY_DATA);if(data!=null)EntityType.updateCustomEntityTag(level,player,blueprint,data);else{var legacy=stack.get(DataComponents.CUSTOM_DATA);if(legacy!=null)EntityType.updateCustomEntityTag(level,player,blueprint,net.minecraft.world.item.component.TypedEntityData.of(com.simibubi.create.AllEntityTypes.CRAFTING_BLUEPRINT,legacy.copyTag()));}
  if(!blueprint.survives())return InteractionResult.CONSUME;
  if(!level.isClientSide()){
   boolean admitted=false;try{admitted=level.addFreshEntity(blueprint);if(!admitted)return InteractionResult.FAIL;blueprint.playPlacementSound();}finally{if(admitted||level.getEntity(blueprint.getUUID())==blueprint)stack.shrink(1);}
  }
  return InteractionResult.SUCCESS;
 }
}
