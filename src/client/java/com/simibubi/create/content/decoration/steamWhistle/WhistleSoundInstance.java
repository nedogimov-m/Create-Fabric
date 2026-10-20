package com.simibubi.create.content.decoration.steamWhistle;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock.WhistleSize;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;

public final class WhistleSoundInstance extends AbstractTickableSoundInstance {
    private boolean active=true;
    private int keepAlive=2;
    private final WhistleSize size;
    public WhistleSoundInstance(WhistleSize size,BlockPos pos){
        super(size==WhistleSize.SMALL?AllSoundEvents.WHISTLE_HIGH:size==WhistleSize.MEDIUM?AllSoundEvents.WHISTLE_MEDIUM:AllSoundEvents.WHISTLE_LOW,SoundSource.RECORDS,SoundInstance.createUnseededRandom());
        this.size=size;looping=true;volume=0.05f;delay=0;x=pos.getX()+0.5;y=pos.getY()+0.5;z=pos.getZ()+0.5;
    }
    public WhistleSize getOctave(){return size;}
    public void fadeOut(){active=false;}
    public void keepAlive(){keepAlive=2;}
    public void setPitch(float pitch){this.pitch=pitch;}
    @Override public boolean canStartSilent(){return true;}
    @Override public void tick(){if(active){volume=Math.min(1,volume+0.25f);if(--keepAlive==0)fadeOut();}else{volume=Math.max(0,volume-0.25f);if(volume==0)stop();}}
}
