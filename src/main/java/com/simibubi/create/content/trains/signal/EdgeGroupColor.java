package com.simibubi.create.content.trains.signal;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Original nine-color graph coloring and collision mask, independent of a client renderer. */
public enum EdgeGroupColor {
    YELLOW(0xEBC255),GREEN(0x51C054),BLUE(0x5391E1),ORANGE(0xE36E36),LAVENDER(0xCB92BA),RED(0xA43538),CYAN(0x6EDAD9),BROWN(0xA17C58),WHITE(0xE5E1DC);
    public static final Codec<EdgeGroupColor> CODEC=Codec.STRING.comapFlatMap(name->{try{return com.mojang.serialization.DataResult.success(valueOf(name));}catch(IllegalArgumentException invalid){return com.mojang.serialization.DataResult.error(()->"Unknown railway group color "+name);}},Enum::name);
    public static final StreamCodec<ByteBuf,EdgeGroupColor> STREAM_CODEC=StreamCodec.of((buffer,value)->buffer.writeByte(value.ordinal()),buffer->{int index=buffer.readUnsignedByte();if(index>=values().length)throw new io.netty.handler.codec.DecoderException("Invalid railway group color");return values()[index];});
    private final int color,mask;
    EdgeGroupColor(int color){this.color=color;mask=1<<ordinal();}
    public int strikeFrom(int mask){return this==WHITE?mask:mask|this.mask;}
    public int rgb(){return color;}
    public static EdgeGroupColor getDefault(){return YELLOW;}
    public static EdgeGroupColor findNextAvailable(int mask){for(var color:values()){if((mask&1)==0)return color;mask>>=1;}return WHITE;}
}
