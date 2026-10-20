package com.simibubi.create.content.logistics.tunnel;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;

public final class TunnelSettings {
    public static Consumer<BlockPos> CLIENT_START=pos->{};
    public record SetMode(BlockPos pos,int mode) implements CustomPacketPayload {
        public static final Type<SetMode> TYPE=new Type<>(Create.asResource("tunnel_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetMode> CODEC=CustomPacketPayload.codec((packet,buffer)->{buffer.writeBlockPos(packet.pos);buffer.writeVarInt(packet.mode);},buffer->new SetMode(buffer.readBlockPos(),buffer.readVarInt()));
        @Override public Type<SetMode> type(){return TYPE;}
    }
    public static Vec3 filterPosition(Direction side){return new Vec3(.5+side.getStepX()*7.5/16,13f/16,.5+side.getStepZ()*7.5/16);}
    public static boolean modeHit(BlockHitResult hit){return hit.getDirection()==Direction.UP&&hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())).distanceTo(new Vec3(.5,1,.5))<.25;}
    public static InteractionResult interact(Level level,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,hit.getBlockPos())||player.getItemInHand(hand).is(AllItems.WRENCH)||!(LoadedLevelAccess.blockEntity(level,hit.getBlockPos()) instanceof BrassTunnelBlockEntity tunnel))return InteractionResult.PASS;
        if(modeHit(hit)&&tunnel.hasDistributionBehaviour()){if(level.isClientSide())CLIENT_START.accept(hit.getBlockPos().immutable());return InteractionResult.SUCCESS;}
        var side=hit.getDirection();var filter=tunnel.filtering.get(side);if(filter==null||side.getAxis().isVertical()||hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())).distanceTo(filterPosition(side))>=.25)return InteractionResult.PASS;
        if(!level.isClientSide())filter.applyPlayerSample(player,hand);return InteractionResult.SUCCESS;
    }
    public static boolean apply(Level level,Player player,SetMode packet){if(packet.mode<0||packet.mode>=BrassTunnelBlockEntity.SelectionMode.values().length||!player.mayBuild()||player.isSpectator()||!player.isWithinBlockInteractionRange(packet.pos,1)||!level.isLoaded(packet.pos)||!level.mayInteract(player,packet.pos)||!(LoadedLevelAccess.blockEntity(level,packet.pos) instanceof BrassTunnelBlockEntity tunnel)||!tunnel.hasDistributionBehaviour())return false;return tunnel.setSelectionMode(BrassTunnelBlockEntity.SelectionMode.values()[packet.mode]);}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(SetMode.TYPE,SetMode.CODEC);ServerPlayNetworking.registerGlobalReceiver(SetMode.TYPE,(packet,context)->apply(context.player().level(),context.player(),packet));}
    private TunnelSettings(){}
}
