package com.simibubi.create.content.logistics.filter;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.util.StringRepresentable;

public enum AttributeFilterWhitelistMode implements StringRepresentable {
    WHITELIST_DISJ,WHITELIST_CONJ,BLACKLIST;
    public static final Codec<AttributeFilterWhitelistMode> CODEC=StringRepresentable.fromEnum(AttributeFilterWhitelistMode::values);
    public static final StreamCodec<ByteBuf,AttributeFilterWhitelistMode> STREAM_CODEC=ByteBufCodecs.fromCodec(CODEC);
    @Override public String getSerializedName(){return name().toLowerCase(java.util.Locale.ROOT);}
}
