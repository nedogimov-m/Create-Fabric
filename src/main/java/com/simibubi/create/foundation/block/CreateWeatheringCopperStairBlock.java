package com.simibubi.create.foundation.block;
import net.minecraft.world.level.block.*;
/** Actual vanilla copper random ticks/aging, retaining source stair blast resistance. */
public class CreateWeatheringCopperStairBlock extends WeatheringCopperStairBlock {
 public CreateWeatheringCopperStairBlock(WeatheringCopper.WeatherState age,Properties properties){super(age,Blocks.AIR.defaultBlockState(),properties);}
 @Override public float getExplosionResistance(){return explosionResistance;}
}
