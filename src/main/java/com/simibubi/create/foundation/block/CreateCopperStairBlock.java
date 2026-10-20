package com.simibubi.create.foundation.block;
import net.minecraft.world.level.block.*;
/** Source stair uses AIR as its model base, and its real inherited copper resistance. */
public class CreateCopperStairBlock extends StairBlock {
 public CreateCopperStairBlock(Properties properties){super(Blocks.AIR.defaultBlockState(),properties);}
 @Override public float getExplosionResistance(){return explosionResistance;}
}
