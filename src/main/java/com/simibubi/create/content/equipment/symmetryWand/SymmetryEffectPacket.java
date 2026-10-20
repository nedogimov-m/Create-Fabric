package com.simibubi.create.content.equipment.symmetryWand;

import com.simibubi.create.Create;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SymmetryEffectPacket(BlockPos mirror,List<BlockPos> positions) implements CustomPacketPayload {
    public SymmetryEffectPacket{if(positions.size()>8)throw new IllegalArgumentException("Too many symmetry targets");mirror=mirror.immutable();positions=positions.stream().map(BlockPos::immutable).toList();}
    public static final Type<SymmetryEffectPacket> TYPE=new Type<>(Create.asResource("symmetry_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SymmetryEffectPacket> STREAM_CODEC=StreamCodec.of((buffer,value)->{buffer.writeBlockPos(value.mirror);buffer.writeVarInt(value.positions.size());value.positions.forEach(buffer::writeBlockPos);},buffer->{var mirror=buffer.readBlockPos();int size=buffer.readVarInt();if(size<0||size>8)throw new io.netty.handler.codec.DecoderException("Invalid symmetry targets");var positions=new java.util.ArrayList<BlockPos>(size);for(int i=0;i<size;i++)positions.add(buffer.readBlockPos());return new SymmetryEffectPacket(mirror,positions);});
    @Override public Type<SymmetryEffectPacket> type(){return TYPE;}
}
