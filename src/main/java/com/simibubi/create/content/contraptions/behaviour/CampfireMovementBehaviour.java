package com.simibubi.create.content.contraptions.behaviour;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.CampfireBlock;

public final class CampfireMovementBehaviour implements MovementBehaviour {
    @Override public void tick(MovementContext context) {
        if(!context.world.isClientSide()||context.position==null||context.disabled||!context.state.getValue(CampfireBlock.LIT))return;
        var r=context.world.getRandom();if(r.nextFloat()>=.11f)return;
        for(int i=r.nextInt(2)+2;i>0;i--)context.world.addAlwaysVisibleParticle(context.state.getValue(CampfireBlock.SIGNAL_FIRE)?ParticleTypes.CAMPFIRE_SIGNAL_SMOKE:ParticleTypes.CAMPFIRE_COSY_SMOKE,true,context.position.x+r.nextDouble()/(r.nextBoolean()?3d:-3d),context.position.y+r.nextDouble()+r.nextDouble(),context.position.z+r.nextDouble()/(r.nextBoolean()?3d:-3d),0,.07,0);
    }
}
