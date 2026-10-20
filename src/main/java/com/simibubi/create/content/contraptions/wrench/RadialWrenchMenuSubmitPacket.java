package com.simibubi.create.content.contraptions.wrench;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/** One source-permitted property; exact observed state prevents stale or fabricated whole-state edits. */
public record RadialWrenchMenuSubmitPacket(BlockPos blockPos,BlockState expectedState,String property,String value) implements CustomPacketPayload {
    public static final Type<RadialWrenchMenuSubmitPacket> TYPE=new Type<>(Create.asResource("radial_wrench_menu_submit"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RadialWrenchMenuSubmitPacket> STREAM_CODEC=StreamCodec.of(
        (buffer,packet)->{buffer.writeBlockPos(packet.blockPos);buffer.writeVarInt(Block.getId(packet.expectedState));buffer.writeUtf(packet.property,64);buffer.writeUtf(packet.value,64);},
        buffer->{var pos=buffer.readBlockPos();int id=buffer.readVarInt();var state=Block.stateById(id);if(id<0||Block.getId(state)!=id)throw new DecoderException("Unknown radial wrench source block state");return new RadialWrenchMenuSubmitPacket(pos,state,buffer.readUtf(64),buffer.readUtf(64));});
    public RadialWrenchMenuSubmitPacket {blockPos=blockPos.immutable();java.util.Objects.requireNonNull(expectedState);java.util.Objects.requireNonNull(property);java.util.Objects.requireNonNull(value);}
    @Override public Type<RadialWrenchMenuSubmitPacket> type(){return TYPE;}
    public boolean apply(ServerPlayer player){
        var level=player.level();
        if(!player.isAlive()||player.isSpectator()||!player.mayBuild()||!player.getMainHandItem().is(AllItems.WRENCH)
                ||level.getServer().getPlayerList().getPlayer(player.getUUID())!=player||level.getEntity(player.getUUID())!=player
                ||!level.isInValidBounds(blockPos)||!level.getWorldBorder().isWithinBounds(blockPos)
                ||!player.isWithinBlockInteractionRange(blockPos,0)||!level.mayInteract(player,blockPos)||!player.mayInteract(level,blockPos)
                ||!LoadedLevelAccess.isLoaded(level,blockPos)||LoadedLevelAccess.blockState(level,blockPos)!=expectedState)return false;
        var selected=RadialWrenchRotation.select(expectedState,property,value).orElse(null);
        if(selected==null||selected==expectedState)return false;
        var view=new RadialWrenchWorld(level);
        var hit=view.nativeRay(player);
        if(hit==null||hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(blockPos))return false;
        var updated=view.updateFromNeighbourShapes(selected,blockPos);
        if(!view.known()||!updated.is(expectedState.getBlock())||!updated.canSurvive(view,blockPos)||!view.known())return false;
        // Native UPDATE_ALL owns the real block entity/cargo and native kinetic graph invalidation.
        KineticBlockEntity.switchToBlockState(level,blockPos,updated);
        if(LoadedLevelAccess.blockState(level,blockPos)!=updated)return false;
        view.commitTicks();
        level.playSound(null,blockPos,AllSoundEvents.WRENCH_ROTATE,SoundSource.BLOCKS,.25f,1.25f);
        return true;
    }
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.apply(context.player()));}
}
