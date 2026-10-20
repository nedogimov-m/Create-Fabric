package com.simibubi.create.content.decoration.steamWhistle;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock.WhistleSize;
import com.simibubi.create.content.kinetics.steamEngine.SteamJetParticleData;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class WhistleEffects {
    private static final Map<WhistleBlockEntity,WhistleSoundInstance> SOUNDS=new WeakHashMap<>();
    private WhistleEffects(){}
    public static WhistleSoundInstance getSound(WhistleBlockEntity whistle){return SOUNDS.get(whistle);}
    public static void clearSounds(){SOUNDS.values().forEach(WhistleSoundInstance::fadeOut);SOUNDS.clear();}
    public static void tick(WhistleBlockEntity whistle){
        var sound=SOUNDS.get(whistle);
        if(!whistle.isSounding()){if(sound!=null){sound.fadeOut();SOUNDS.remove(whistle);}return;}
        var client=Minecraft.getInstance();var level=whistle.getLevel();var pos=whistle.getBlockPos();var size=whistle.getOctave();float pitch=(float)Math.pow(2,-whistle.getPitch()/12.0);
        boolean particle=level.getGameTime()%8==0;
        if(sound==null||sound.isStopped()||sound.getOctave()!=size){
            if(sound!=null)sound.fadeOut();sound=new WhistleSoundInstance(size,pos);SOUNDS.put(whistle,sound);client.getSoundManager().play(sound);
            var eye=client.gameRenderer.mainCamera().position();float maxVolume=(float)Math.clamp((64-eye.distanceTo(Vec3.atCenterOf(pos)))/64,0,1);
            level.playLocalSound(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+0.5,AllSoundEvents.WHISTLE_CHIFF,SoundSource.RECORDS,maxVolume*0.175f,size==WhistleSize.SMALL?pitch+0.75f:pitch,false);particle=true;
        }
        sound.keepAlive();sound.setPitch(pitch);if(!particle)return;
        var facing=whistle.getBlockState().getValue(WhistleBlock.FACING);float angle=(float)Math.toRadians(180+switch(facing){case NORTH->180;case WEST->-90;case EAST->-270;default->0;});
        var sizeOffset=new org.joml.Vector3f(0,-0.4f,size.ordinal()/16f).rotateY(angle);var offset=new org.joml.Vector3f(0,1,0.75f).rotateY(angle);
        level.addParticle(new SteamJetParticleData(1),pos.getX()+0.5+offset.x*0.45+sizeOffset.x,pos.getY()+0.5+offset.y*0.45+sizeOffset.y,pos.getZ()+0.5+offset.z*0.45+sizeOffset.z,offset.x-facing.getStepX()*0.75,offset.y,offset.z-facing.getStepZ()*0.75);
    }
}
