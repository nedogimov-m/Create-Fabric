package com.simibubi.create.content.legacy;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Source light storage/bar and modern actual entity/owner conversion route. */
public final class ChromaticCompoundItem extends Item {
 public ChromaticCompoundItem(Properties properties){super(properties);}
 public int getLight(ItemStack stack){return stack.getOrDefault(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT,0);}
 @Override public boolean isBarVisible(ItemStack stack){return getLight(stack)>0;}
 @Override public int getBarWidth(ItemStack stack){return Math.round(13f*getLight(stack)/AllConfigs.server().recipes.lightSourceCountForRefinedRadiance.get());}
 @Override public int getBarColor(ItemStack stack){float weight=getLight(stack)/(float)AllConfigs.server().recipes.lightSourceCountForRefinedRadiance.get();return ((int)(0x41+(0xff-0x41)*weight)<<16)+((int)(0x3c+(0xff-0x3c)*weight)<<8)+(int)(0x69+(0xff-0x69)*weight);}
 public int getMaxStackSize(ItemStack stack){return isBarVisible(stack)?1:16;}
 public boolean onEntityItemUpdate(ItemStack stack,ItemEntity entity){
  var world=entity.level();if(world.isClientSide()){int light=getLight(entity.getItem());if(world.getRandom().nextInt(AllConfigs.server().recipes.lightSourceCountForRefinedRadiance.get()+20)<light){var from=LegacyRandom.offset(entity.position(),world.getRandom(),3);var motion=entity.position().subtract(from).normalize().scale(.2f);world.addParticle(ParticleTypes.END_ROD,from.x,from.y,from.z,motion.x,motion.y,motion.z);}return false;}
  LegacyConversions.update(this,stack,entity);return false;
 }
 public boolean checkLight(ItemStack stack,ItemEntity entity,Level world,ItemStack original,Vec3 position,BlockPos pos,BlockState state){return LegacyLightSources.consumePlaced(this,stack,entity,world,original,position,pos,state);}
}
