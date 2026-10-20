package com.simibubi.create.content.legacy;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.foundation.utility.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Source light is an optical provider; the later native destroy attempt is best effort, not payment. */
public final class LegacyLightSources {
 static boolean visibleLight(ItemEntity entity,Level world,Vec3 from,BlockPos pos,BlockState light){
  if(light.getLightEmission()==0||light.getDestroySpeed(world,pos)==-1||light.is(Blocks.BEACON)||!world.isInValidBounds(pos)||!world.getWorldBorder().isWithinBounds(pos))return false;
  int minX=Math.min(net.minecraft.util.Mth.floor(from.x),pos.getX())-1,maxX=Math.max(net.minecraft.util.Mth.floor(from.x),pos.getX())+1,minZ=Math.min(net.minecraft.util.Mth.floor(from.z),pos.getZ())-1,maxZ=Math.max(net.minecraft.util.Mth.floor(from.z),pos.getZ())+1;
  for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++)if(world.getChunkSource().getChunkNow(x,z)==null)return false;
  // Upstream compares only the final block position. In particular a collider MISS at lava is viable.
  return pos.equals(world.clip(new ClipContext(from.add(0,.5,0),Vec3.atCenterOf(pos),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity)).getBlockPos());
 }
 public static boolean consumePlaced(ChromaticCompoundItem item,ItemStack stack,ItemEntity entity,Level world,ItemStack original,Vec3 position,BlockPos pos,BlockState state){
  if(!(world instanceof ServerLevel level)||stack!=original||!LegacyConversions.actual(entity,stack)||!visibleLight(entity,world,position,pos,state)||LoadedLevelAccess.blockState(world,pos)!=state)return false;
  var source=new LegacyCompoundSource(entity,stack);var drops=LegacyMaterialDrops.get(level);var at=entity.position();var motion=entity.getDeltaMovement();LegacyMaterialDrops.Receipt receipt;
  try(var transaction=Transaction.openOuter()){
   var part=source.takeOne(transaction);part.set(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT,item.getLight(original)+1);receipt=drops.own(part,at,motion,10,transaction);transaction.commit();
  }
  // Source checkLight publishes before the caller's unconditional best-effort destroyBlock(false).
  // A refused native spawn retains this exact paid part in durable custody, without undoing payment.
  drops.emit(level,receipt);LegacyConversions.finishSource(entity,stack);level.destroyBlock(pos,false);return true;
 }
 private LegacyLightSources(){}
}
