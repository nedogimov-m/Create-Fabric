package com.simibubi.create.content.equipment.bell;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Ring sound is an event; render snapshots never replay it or consume a pending marker. */
public abstract class AbstractBellBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    public static final int RING_DURATION = 74;
    public boolean isRinging;
    public int ringingTicks;
    public Direction ringDirection = Direction.NORTH;
    private long ringSequence, animationTick = Long.MIN_VALUE;
    protected AbstractBellBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state) { super(type,pos,state); }
    public boolean ring(Level level,BlockPos pos,Direction direction) {
        isRinging=true;ringingTicks=0;ringDirection=direction;ringSequence++;setChanged();sync();return true;
    }
    protected void advanceRing() { if (isRinging && ++ringingTicks>=RING_DURATION) {isRinging=false;ringingTicks=0;} }
    /** Also used by virtual render BEs; no server/world effects execute here. */
    public void tickClientAnimation() { if(level==null||!level.isClientSide()||animationTick==level.getGameTime())return;animationTick=level.getGameTime();advanceRing(); }
    public static void tick(Level level,BlockPos pos,BlockState state,AbstractBellBlockEntity bell) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, bell)) {
            if (!createTick.shouldTick()) return;

        if(level.isClientSide())bell.tickClientAnimation();else bell.advanceRing();
        if(bell instanceof HauntedBellBlockEntity haunted)haunted.tickEffect();
    
        }
    }
    protected void sync() { if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),2); }
    @Override protected void write(ValueOutput out, boolean clientPacket) { super.write(out, clientPacket);out.putLong("RingSequence",ringSequence); }
    @Override protected void read(ValueInput in, boolean clientPacket) { super.read(in, clientPacket);ringSequence=in.getLongOr("RingSequence",0);isRinging=in.getBooleanOr("Ringing",false);ringingTicks=Math.clamp(in.getIntOr("RingingTicks",0),0,RING_DURATION-1);ringDirection=in.read("RingDirection",Direction.CODEC).orElse(Direction.NORTH); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { var tag=super.getUpdateTag(registries);tag.putBoolean("Ringing",isRinging);tag.putInt("RingingTicks",ringingTicks);tag.store("RingDirection",Direction.CODEC,ringDirection);return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
