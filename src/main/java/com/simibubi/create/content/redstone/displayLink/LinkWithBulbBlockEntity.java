package com.simibubi.create.content.redstone.displayLink;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Real source pulse/glow state on a native block entity; no SmartBlockEntity facade. */
public abstract class LinkWithBulbBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    private float glow,previousGlow;
    private boolean sendPulse;
    protected LinkWithBulbBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state){super(type,pos,state);}
    protected void tickGlow(){previousGlow=glow;glow*=.5f;if(glow<.001f)glow=0;}
    public float getGlow(float partial){return previousGlow+(glow-previousGlow)*partial;}
    public void sendPulseNextSync(){sendPulse=true;}
    public void pulse(){glow=2;}
    public Vec3 getBulbOffset(BlockState state){return Vec3.ZERO;}
    public Direction getBulbFacing(BlockState state){return state.getValue(DisplayLinkBlock.FACING);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);if(sendPulse){tag.putBoolean("Pulse",true);sendPulse=false;}return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);if(level!=null&&level.isClientSide()&&in.getBooleanOr("Pulse",false))pulse();}
}
