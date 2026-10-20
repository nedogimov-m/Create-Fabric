package com.simibubi.create.content.decoration.steamWhistle;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class WhistleBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){var names=com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("generic.notes").getString().split(";");tooltip.add(com.simibubi.create.content.equipment.goggles.GoggleTooltip.translate("generic.pitch",names[Math.floorMod(pitch,names.length)]));return true;}
    public static java.util.function.Consumer<WhistleBlockEntity> CLIENT_TICK=whistle->{};
    private int pitch;
    private float animation,previousAnimation;
    private boolean sounding;
    public WhistleBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.STEAM_WHISTLE,pos,state);}
    public int getPitch(){return pitch;}
    public int getPitchId(){return pitch+100*getOctave().ordinal();}
    public WhistleBlock.WhistleSize getOctave(){return getBlockState().getValue(WhistleBlock.SIZE);}
    public boolean isPowered(){return getBlockState().getValue(WhistleBlock.POWERED);}
    public boolean isSounding(){return sounding;}
    public float getAnimation(float partial){return previousAnimation+(animation-previousAnimation)*partial;}
    public FluidTankBlockEntity getTank(){if(level==null)return null;var pos=worldPosition.relative(WhistleBlock.getAttachedDirection(getBlockState()));return level.isLoaded(pos)&&LoadedLevelAccess.blockEntity(level,pos) instanceof FluidTankBlockEntity tank?tank.getControllerBE():null;}
    public void updatePitch(){
        if(level==null)return;int next;
        for(next=0;next<=24;next+=2){var pos=worldPosition.above(1+next/2);if(!level.isLoaded(pos))return;var state=LoadedLevelAccess.blockState(level,pos);if(!state.is(AllBlocks.STEAM_WHISTLE_EXTENSION))break;if(state.getValue(WhistleExtenderBlock.SHAPE)==WhistleExtenderBlock.WhistleExtenderShape.SINGLE){next++;break;}}
        if(next!=pitch){pitch=next;setChanged();sync();var tank=getTank();if(tank!=null)tank.boiler.checkPipeOrganAdvancement(tank);}
    }
    public static void tick(Level level,BlockPos pos,BlockState state,WhistleBlockEntity whistle){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, whistle)) {
            if (!createTick.shouldTick()) return;

        var tank=whistle.getTank();whistle.sounding=whistle.isPowered()&&(tank!=null&&tank.boiler.isActive()&&(tank.boiler.passiveHeat||tank.boiler.activeHeat>0)&&tank.structureLoaded()&&level.shouldTickBlocksAt(tank.getBlockPos())||whistle.isVirtual());
        if(!level.isClientSide()){if(whistle.isPowered())whistle.award(com.simibubi.create.foundation.advancement.AllAdvancements.STEAM_WHISTLE);return;}
        whistle.previousAnimation=whistle.animation;whistle.animation=whistle.sounding?whistle.animation+(1-whistle.animation)*0.5f:Math.max(0,whistle.animation-0.4f);CLIENT_TICK.accept(whistle);
    
        }
    }
    private void sync(){if(level!=null)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("Pitch",pitch);}
    @Override protected void read(ValueInput in, boolean clientPacket){pitch=Math.clamp(in.getIntOr("Pitch",0),0,26);
        super.read(in, clientPacket);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("steam_whistle");
    }
}
