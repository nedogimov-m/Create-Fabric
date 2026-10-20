package com.simibubi.create.content.logistics.factoryBoard;

import com.simibubi.create.Create;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Each ingredient keeps its own success pulse when several results arrive in one tick. */
public record FactoryPanelEffectPacket(FactoryPanelPosition from,FactoryPanelPosition to,boolean success) implements CustomPacketPayload {
    public static final Type<FactoryPanelEffectPacket> TYPE=new Type<>(Create.asResource("factory_panel_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FactoryPanelEffectPacket> CODEC=CustomPacketPayload.codec((packet,buf)->{
        FactoryPanelPosition.STREAM_CODEC.encode(buf,packet.from);
        FactoryPanelPosition.STREAM_CODEC.encode(buf,packet.to);
        buf.writeBoolean(packet.success);
    },buf->new FactoryPanelEffectPacket(FactoryPanelPosition.STREAM_CODEC.decode(buf),FactoryPanelPosition.STREAM_CODEC.decode(buf),buf.readBoolean()));
    @Override public Type<FactoryPanelEffectPacket> type() { return TYPE; }
}
