package com.simibubi.create.content.decoration.slidingDoor;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.*;

/** Source ordinal order is part of saved station/elevator settings. */
public enum DoorControl {
    ALL,NORTH,EAST,SOUTH,WEST,NONE;
    public static final StreamCodec<ByteBuf,DoorControl> STREAM_CODEC=ByteBufCodecs.VAR_INT.map(DoorControl::byId,Enum::ordinal);
    public static DoorControl byId(int id){if(id<0||id>=values().length)throw new IllegalArgumentException("Unknown door control "+id);return values()[id];}
    public boolean matches(Direction direction){return switch(this){case ALL->true;case NORTH->direction==Direction.NORTH;case EAST->direction==Direction.EAST;case SOUTH->direction==Direction.SOUTH;case WEST->direction==Direction.WEST;case NONE->false;};}
}
