package com.simibubi.create.content.equipment.zapper.terrainzapper;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.zapper.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
public class ConfigureWorldshaperPacket extends ConfigureZapperPacket {
    public static final Type<ConfigureWorldshaperPacket> TYPE=new Type<>(Create.asResource("configure_worldshaper"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ConfigureWorldshaperPacket> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeEnum(p.hand);b.writeEnum(p.pattern);b.writeEnum(p.brush);b.writeVarInt(p.brushParamX);b.writeVarInt(p.brushParamY);b.writeVarInt(p.brushParamZ);b.writeEnum(p.tool);b.writeEnum(p.placement);},b->new ConfigureWorldshaperPacket(b.readEnum(InteractionHand.class),b.readEnum(PlacementPatterns.class),b.readEnum(TerrainBrushes.class),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readEnum(TerrainTools.class),b.readEnum(PlacementOptions.class)));
    private final TerrainBrushes brush;private final int brushParamX,brushParamY,brushParamZ;private final TerrainTools tool;private final PlacementOptions placement;
    public ConfigureWorldshaperPacket(InteractionHand hand,PlacementPatterns pattern,TerrainBrushes brush,int x,int y,int z,TerrainTools tool,PlacementOptions placement){super(hand,pattern);if(!WorldshaperItem.validSettings(brush,new BlockPos(x,y,z),tool))throw new IllegalArgumentException("Invalid bounded native Worldshaper settings");this.brush=brush;brushParamX=x;brushParamY=y;brushParamZ=z;this.tool=tool;this.placement=java.util.Objects.requireNonNull(placement);}
    @Override public void configureZapper(ItemStack stack){WorldshaperItem.configureSettings(stack,pattern,brush,brushParamX,brushParamY,brushParamZ,tool,placement);}
    @Override public Type<ConfigureWorldshaperPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.handle(context.player()));}
}
