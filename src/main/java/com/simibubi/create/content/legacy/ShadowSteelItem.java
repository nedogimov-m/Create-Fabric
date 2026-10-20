package com.simibubi.create.content.legacy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;

public final class ShadowSteelItem extends NoGravMagicalDohickyItem {
 public ShadowSteelItem(Properties properties){super(properties);}
 @Override protected void onCreated(ItemEntity entity,CompoundTag data){super.onCreated(entity,data);entity.setDeltaMovement(0,(float)(entity.fallDistance+3)/50f,0);}
 @Override protected float getIdleParticleChance(ItemEntity entity){return (float)(Mth.clamp(entity.getItem().getCount()-10,Mth.clamp(entity.getDeltaMovement().y*20,5,20),100)/64f);}
}
