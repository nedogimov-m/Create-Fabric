package com.simibubi.create.content.materials;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class ExperienceBlock extends Block {
    public ExperienceBlock(Properties properties){super(properties);}
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){
        if(random.nextInt(5)!=0)return;
        double x=pos.getX()+.5+Math.clamp((random.nextFloat()-.5)*1.5,-.55,.55),
                y=pos.getY()+.5+Math.clamp((random.nextFloat()-.5)*1.5,-.55,.55),z=pos.getZ()+.5+Math.clamp((random.nextFloat()-.5)*1.5,-.55,.55);
        level.addParticle(ParticleTypes.END_ROD,x,y,z,random.nextGaussian()*.005,random.nextGaussian()*.005,random.nextGaussian()*.005);
    }
}
