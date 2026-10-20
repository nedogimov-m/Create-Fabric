package com.simibubi.create.content.kinetics.steamEngine;

import java.util.*;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

public final class SteamEngineEffects {
    private static final Map<SteamEngineBlockEntity,Float> PREVIOUS=new WeakHashMap<>();
    private SteamEngineEffects(){}
    public static void tick(SteamEngineBlockEntity engine){
        var shaft=engine.getShaft();var tank=engine.getTank();if(shaft==null||tank==null||!shaft.isPoweredBy(engine.getBlockPos())||shaft.engineEfficiency==0||shaft.getSpeed()==0)return;
        var facing=SteamEngineBlock.getFacing(engine.getBlockState());var facingAxis=facing.getAxis();var axis=shaft.getBlockState().getValue(PoweredShaftBlock.AXIS);
        float angle=shaft.getAngle(0);int parity=(axis==Direction.Axis.X?0:shaft.getBlockPos().getX())+(axis==Direction.Axis.Y?0:shaft.getBlockPos().getY())+(axis==Direction.Axis.Z?0:shaft.getBlockPos().getZ());angle+=parity%2==0?22.5f:0;
        if(axis.isHorizontal()&&(facingAxis==Direction.Axis.X^(facing.getAxisDirection()==Direction.AxisDirection.POSITIVE)))angle*=-1;
        if(axis==Direction.Axis.X&&facing==Direction.DOWN)angle*=-1;angle=(angle+(angle<0?-105:285))%360;
        float previous=PREVIOUS.getOrDefault(engine,0f);PREVIOUS.put(engine,angle);
        if(angle>=0&&!(previous>180&&angle<180)||angle<0&&!(previous< -180&&angle> -180))return;
        tank.boiler.queueSoundOnSide(engine.getBlockPos(),facing);
        var random=engine.getLevel().getRandom();var jitter=new Vector3f(random.nextFloat()*2-1,random.nextFloat()*2-1,0);if(jitter.lengthSquared()>0)jitter.normalize(0.5f);jitter.add(0,0,1);
        float yaw=switch(facing){case NORTH->180;case WEST->-90;case EAST->-270;default->0;};float pitch=facing==Direction.UP?-90:facing==Direction.DOWN?90:0;
        jitter.rotateX((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));var pos=engine.getBlockPos();
        engine.getLevel().addParticle(new SteamJetParticleData(1),pos.getX()+0.5+jitter.x*0.5,pos.getY()+0.5+jitter.y*0.5,pos.getZ()+0.5+jitter.z*0.5,jitter.x-facing.getStepX()*0.75,jitter.y-facing.getStepY()*0.75,jitter.z-facing.getStepZ()*0.75);
    }
}
