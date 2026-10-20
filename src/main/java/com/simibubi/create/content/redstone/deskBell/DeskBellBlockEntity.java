package com.simibubi.create.content.redstone.deskBell;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class DeskBellBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    private int pressTimer;
    private long dingSequence,animationTick=Long.MIN_VALUE;
    private boolean receivedDing;
    private boolean initializedClientAnimation;
    private boolean originalDingPending;
    private float previousAnimation,animation,animationOffset;
    public DeskBellBlockEntity(BlockPos pos,BlockState state) { super(AllBlockEntityTypes.DESK_BELL,pos,state); }
    @Override public void setLevel(Level level) { super.setLevel(level);if(level.isClientSide()&&!initializedClientAnimation&&pressTimer>0){previousAnimation=animation=pressTimer/20f;animationOffset=level.getRandom().nextFloat()*(float)Math.PI*2;initializedClientAnimation=true;}consumeOriginalDing(); }
    public int getPressTimer() { return pressTimer; }
    public float getAnimation(float partial) { return previousAnimation+(animation-previousAnimation)*partial; }
    public float getAnimationOffset() { return animationOffset; }
    private void consumeOriginalDing(){if(originalDingPending&&level!=null&&level.isClientSide()){originalDingPending=false;ding();}}
    public void ding() {
        if(level==null)return;
        if(level.isClientSide()){previousAnimation=animation=1;animationOffset=level.getRandom().nextFloat()*(float)Math.PI*2;initializedClientAnimation=true;return;}
        pressTimer=20;dingSequence++;setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2);
    }
    public void tickClientAnimation() { if(level==null||!level.isClientSide()||animationTick==level.getGameTime())return;animationTick=level.getGameTime();previousAnimation=animation;animation=Math.max(0,animation-.05f); }
    public static void tick(Level level,BlockPos pos,BlockState state,DeskBellBlockEntity bell) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, bell)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide()){bell.tickClientAnimation();return;}
        if(bell.pressTimer>0){bell.pressTimer--;bell.setChanged();}
        if(bell.pressTimer==0&&state.getValue(DeskBellBlock.POWERED))((DeskBellBlock)state.getBlock()).unPress(state,level,pos);
    
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket) { super.write(out, clientPacket);out.putInt("PressTimer",pressTimer);out.putLong("DingSequence",dingSequence);if(clientPacket&&originalDingPending){out.putBoolean("Ding",true);originalDingPending=false;} }
    @Override protected void read(ValueInput in, boolean clientPacket) {
        super.read(in, clientPacket);int timer=Math.clamp(in.getIntOr("PressTimer",0),0,20);long next=in.getLongOr("DingSequence",0);
        if(level!=null&&level.isClientSide()&&timer>0&&(!receivedDing||next!=dingSequence)){previousAnimation=animation=receivedDing?1:timer/20f;animationOffset=level.getRandom().nextFloat()*(float)Math.PI*2;initializedClientAnimation=true;}
        receivedDing=true;pressTimer=timer;dingSequence=next;
        originalDingPending=clientPacket&&in.getBooleanOr("Ding",false);consumeOriginalDing();
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return super.getUpdateTag(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
