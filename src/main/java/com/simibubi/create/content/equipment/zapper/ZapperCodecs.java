package com.simibubi.create.content.equipment.zapper;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.*;
/** Actual bounded native ordinal wire codec; persistent names remain the original source enum IDs. */
public final class ZapperCodecs {
    public static <E extends Enum<E>>StreamCodec<ByteBuf,E> enumCodec(Class<E> type){var values=type.getEnumConstants();return ByteBufCodecs.idMapper(index->{if(index<0||index>=values.length)throw new DecoderException("Invalid "+type.getSimpleName()+" ordinal "+index);return values[index];},Enum::ordinal);}
    private ZapperCodecs(){}
}
