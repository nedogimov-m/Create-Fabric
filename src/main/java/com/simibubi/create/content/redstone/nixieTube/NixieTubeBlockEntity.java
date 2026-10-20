package com.simibubi.create.content.redstone.nixieTube;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.DynamicComponent;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Concrete source redstone/custom text state. DisplayLink/CC/signal integration is not replaced by dummy behaviours. */
public final class NixieTubeBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    public static final class ComputerSignal {
        public static final class TubeDisplay {
            public static final int ENCODED_SIZE=7;
            public byte r=63,g=63,b=63,blinkPeriod=0,blinkOffTime=0,glowWidth=1,glowHeight=1;
            public void decode(byte[] data,int at){r=data[at];g=data[at+1];b=data[at+2];blinkPeriod=data[at+3];blinkOffTime=data[at+4];glowWidth=data[at+5];glowHeight=data[at+6];}
            public void encode(byte[] data,int at){data[at]=r;data[at+1]=g;data[at+2]=b;data[at+3]=blinkPeriod;data[at+4]=blinkOffTime;data[at+5]=glowWidth;data[at+6]=glowHeight;}
        }
        public TubeDisplay first=new TubeDisplay(),second=new TubeDisplay();
        public void decode(byte[] data){if(data.length!=TubeDisplay.ENCODED_SIZE*2)throw new IllegalArgumentException("Invalid original nixie computer signal size");first.decode(data,0);second.decode(data,TubeDisplay.ENCODED_SIZE);}
        public byte[] encode(){var data=new byte[TubeDisplay.ENCODED_SIZE*2];first.encode(data,0);second.encode(data,TubeDisplay.ENCODED_SIZE);return data;}
    }
    public ComputerSignal computerSignal;
    public record DisplayedStrings(String first,String second){}
    private int redstoneStrength,nixieIndex;
    private DynamicComponent customText;
    private DisplayedStrings displayedStrings=new DisplayedStrings("0","0");
    private boolean initializedInput;
    public NixieTubeBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.NIXIE_TUBE,pos,state);}
    public boolean reactsToRedstone(){return customText==null;}
    public DisplayedStrings getDisplayedStrings(){return displayedStrings;}
    public MutableComponent getFullText(){return customText==null?Component.literal(Integer.toString(redstoneStrength)):customText.get();}
    public int getRedstoneStrength(){return redstoneStrength;}
    public int getCustomTextIndex(){return nixieIndex;}
    public void updateRedstoneStrength(int strength){clearCustomText();redstoneStrength=Math.clamp(strength,0,15);updateDisplayedStrings();if(level!=null)com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level,worldPosition);notifyUpdate();}
    public void displayCustomText(String json,int index){
        if(json==null)return;if(customText!=null&&customText.sameAs(json)&&nixieIndex==index)return;
        var text=new DynamicComponent();text.displayCustomText(level,worldPosition,json);if(!text.isValid())return;customText=text;nixieIndex=Math.clamp(index,0,Integer.MAX_VALUE/2-1);updateDisplayedStrings();if(level!=null)com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock.notifyGatherers(level,worldPosition);notifyUpdate();
    }
    public void displayEmptyText(int index){displayCustomText("\"\"",index);}
    public void clearCustomText(){customText=null;nixieIndex=0;updateDisplayedStrings();}
    public void updateDisplayedStrings(){String text=customText==null?null:customText.resolve();displayedStrings=text==null?new DisplayedStrings(redstoneStrength<10?"0":"1",Integer.toString(redstoneStrength%10)):new DisplayedStrings(charOrEmpty(text,nixieIndex*2),charOrEmpty(text,nixieIndex*2+1));}
    private static String charOrEmpty(String text,int index){return index<0||index>=text.length()?" ":text.substring(index,index+1);}
    public void copyDisplayFrom(NixieTubeBlockEntity other){redstoneStrength=other.redstoneStrength;nixieIndex=other.nixieIndex;customText=other.customText==null?null:other.customText.copy();updateDisplayedStrings();notifyUpdate();}
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    public static void tick(Level level,BlockPos pos,BlockState state,NixieTubeBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;
if(!level.isClientSide()&&!entity.initializedInput){entity.initializedInput=true;NixieTubeBlock.updateDisplayedRedstoneValue(entity,state,false);}
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);if(customText==null)out.putInt("RedstoneStrength",redstoneStrength);else{out.putInt("CustomTextIndex",nixieIndex);customText.write(out);}if(clientPacket&&computerSignal!=null)out.store("ComputerSignal",com.mojang.serialization.Codec.BYTE_BUFFER,java.nio.ByteBuffer.wrap(computerSignal.encode()));}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);customText=null;if(in.getString("CustomText").isPresent()){var text=new DynamicComponent();text.read(worldPosition,in);if(text.isValid())customText=text;}nixieIndex=customText==null?0:Math.clamp(in.getIntOr("CustomTextIndex",0),0,Integer.MAX_VALUE/2-1);redstoneStrength=Math.clamp(in.getIntOr("RedstoneStrength",0),0,15);initializedInput=false;updateDisplayedStrings();
        if(clientPacket){var encoded=in.read("ComputerSignal",com.mojang.serialization.Codec.BYTE_BUFFER);if(encoded.isEmpty())computerSignal=null;else{var buffer=encoded.get().duplicate();var bytes=new byte[buffer.remaining()];buffer.get(bytes);if(computerSignal==null)computerSignal=new ComputerSignal();computerSignal.decode(bytes);}}
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
