package com.simibubi.create.content.legacy;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** Native ItemEntity tick seam for the source magical material item extension. */
public class NoGravMagicalDohickyItem extends Item {
 public NoGravMagicalDohickyItem(Properties properties){super(properties);}
 public boolean onEntityItemUpdate(ItemStack stack,ItemEntity entity){
  var world=entity.level();var pos=entity.position();var data=LegacyItemData.of(entity);
  if(world.isClientSide()){
   if(world.getRandom().nextFloat()<getIdleParticleChance(entity)){var at=LegacyRandom.offset(pos,world.getRandom(),.5f);world.addParticle(ParticleTypes.END_ROD,at.x,pos.y,at.z,0,-.1f,0);}
   if(entity.isSilent()&&!data.getBooleanOr("PlayEffects",false)){world.addParticle(ColorParticleOption.create(ParticleTypes.FLASH,0xffffffff),pos.x,pos.y,pos.z,0,0,0);for(int i=0;i<20;i++){var motion=LegacyRandom.offset(new Vec3(0,1,0),world.getRandom(),1);world.addParticle(ParticleTypes.WITCH,pos.x,pos.y,pos.z,motion.x,motion.y,motion.z);world.addParticle(ParticleTypes.END_ROD,pos.x,pos.y,pos.z,motion.x,motion.y,motion.z);}data.putBoolean("PlayEffects",true);}return false;
  }
  entity.setNoGravity(true);if(data.contains("JustCreated"))onCreated(entity,data);return false;
 }
 protected float getIdleParticleChance(ItemEntity entity){return Mth.clamp(entity.getItem().getCount()-10,5,100)/64f;}
 protected void onCreated(ItemEntity entity,CompoundTag data){
  // Native ItemEntity has the genuine fixed6000-tick source lifespan; its current age is retained.
  data.remove("JustCreated");entity.setSilent(true);
 }
}
