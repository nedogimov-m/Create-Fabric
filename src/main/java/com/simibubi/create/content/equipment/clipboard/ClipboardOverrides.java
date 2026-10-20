package com.simibubi.create.content.equipment.clipboard;

import com.mojang.serialization.Codec;
import com.simibubi.create.Create;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import java.util.Locale;

/** Source clipboard model variants; native item model selection is registered on the client. */
public final class ClipboardOverrides {
    public enum ClipboardType implements StringRepresentable {
        EMPTY("empty_clipboard"),WRITTEN("clipboard"),EDITING("clipboard_and_quill");
        public static final Codec<ClipboardType> CODEC=StringRepresentable.fromValues(ClipboardType::values);
        public static final StreamCodec<ByteBuf,ClipboardType> STREAM_CODEC=StreamCodec.of((buf,value)->ByteBufCodecs.VAR_INT.encode(buf,value.ordinal()),buf->{int ordinal=ByteBufCodecs.VAR_INT.decode(buf);var values=values();if(ordinal<0||ordinal>=values.length)throw new io.netty.handler.codec.DecoderException("Invalid clipboard type");return values[ordinal];});
        public static final Identifier ID=Create.asResource("clipboard_type");
        public final String file;
        ClipboardType(String file){this.file=file;}
        @Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}
    }
    private ClipboardOverrides(){}
}
