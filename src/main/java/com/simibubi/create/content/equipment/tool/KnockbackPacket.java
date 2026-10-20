package com.simibubi.create.content.equipment.tool;
import com.simibubi.create.Create;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Genuine source client-player knockback payload, never an authority/request packet. */
public record KnockbackPacket(float yRot,float strength) implements CustomPacketPayload {
 public static final Type<KnockbackPacket> TYPE=new Type<>(Create.asResource("knockback"));
 public static final StreamCodec<RegistryFriendlyByteBuf,KnockbackPacket> CODEC=StreamCodec.composite(ByteBufCodecs.FLOAT,KnockbackPacket::yRot,ByteBufCodecs.FLOAT,KnockbackPacket::strength,KnockbackPacket::new);
 @Override public Type<KnockbackPacket> type(){return TYPE;}
}
