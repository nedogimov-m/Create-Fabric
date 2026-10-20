package com.simibubi.create.content.trains.bogey;

import com.simibubi.create.AllBogeyStyles;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Native persistent source BogeyData/Style. Virtual wheel movement has no physical train authority. */
public abstract class AbstractBogeyBlockEntity extends com.simibubi.create.foundation.blockEntity.CachedRenderBBBlockEntity {
    public static final String BOGEY_STYLE_KEY="BogeyStyle",BOGEY_DATA_KEY="BogeyData",UPSIDE_DOWN_KEY="UpsideDown";
    private CompoundTag bogeyData;
    private float previousVirtualAngle,virtualAngle;
    protected AbstractBogeyBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    public abstract BogeyStyle getDefaultStyle();
    private CompoundTag createBogeyData(){var data=new CompoundTag();data.putString(BOGEY_STYLE_KEY,getDefaultStyle().id.toString());data.putBoolean(UPSIDE_DOWN_KEY,getBlockState().getBlock()instanceof AbstractBogeyBlock<?> block&&block.isUpsideDown(getBlockState()));return data;}
    public CompoundTag getBogeyData(){if(bogeyData==null)bogeyData=createBogeyData();if(!bogeyData.contains(BOGEY_STYLE_KEY))bogeyData.putString(BOGEY_STYLE_KEY,getDefaultStyle().id.toString());return bogeyData;}
    public void setBogeyData(CompoundTag data){bogeyData=data.copy();getBogeyData();markUpdated();}
    public void setBogeyStyle(BogeyStyle style){getBogeyData().putString(BOGEY_STYLE_KEY,style.id.toString());markUpdated();}
    public BogeyStyle getStyle(){var id=Identifier.tryParse(getBogeyData().getStringOr(BOGEY_STYLE_KEY,""));var style=id==null?null:AllBogeyStyles.BOGEY_STYLES.get(id);if(style!=null)return style;style=getDefaultStyle();setBogeyStyle(style);return style;}
    public float getVirtualAngle(float partial){return previousVirtualAngle+(virtualAngle-previousVirtualAngle)*partial;}
    public void animate(float distanceMoved){
        if(!Float.isFinite(distanceMoved))throw new IllegalArgumentException("Non-finite bogey movement");
        if(getBlockState().getBlock()instanceof AbstractBogeyBlock<?> block){previousVirtualAngle=virtualAngle;virtualAngle-=(float)(360*distanceMoved/(2*Math.PI*block.getWheelRadius()));}
    }
    private void markUpdated(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);}
    @Override protected void saveAdditional(ValueOutput out){super.saveAdditional(out);out.store(BOGEY_DATA_KEY,CompoundTag.CODEC,getBogeyData());}
    @Override protected void loadAdditional(ValueInput in){super.loadAdditional(in);bogeyData=in.read(BOGEY_DATA_KEY,CompoundTag.CODEC).map(CompoundTag::copy).orElseGet(this::createBogeyData);getBogeyData();}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
