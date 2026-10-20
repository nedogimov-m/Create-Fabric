package com.simibubi.create.content.trains.entity;

import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.ContraptionRelocationPacket;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.track.BezierTrackPointLocation;
import com.simibubi.create.content.trains.track.UnloadedTrackException;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Actual source wrench relocation request; native server custody remains authoritative. */
public record TrainRelocationPacket(UUID trainId, BlockPos pos, Vec3 lookAngle, int entityId,
        boolean direction, BezierTrackPointLocation hoveredBezier, UUID entityUuid) implements CustomPacketPayload {
    public TrainRelocationPacket {
        pos=pos.immutable();
        if(!Double.isFinite(lookAngle.lengthSqr())||lookAngle.lengthSqr()<1e-12)
            throw new IllegalArgumentException("Invalid actual relocation look vector");
    }
    public TrainRelocationPacket(UUID trainId,BlockPos pos,Vec3 lookAngle,int entityId,
            boolean direction,BezierTrackPointLocation hoveredBezier) {
        this(trainId,pos,lookAngle,entityId,direction,hoveredBezier,null);
    }
    public static final Type<TrainRelocationPacket> TYPE=new Type<>(Create.asResource("relocate_train"));
    public static final StreamCodec<RegistryFriendlyByteBuf,TrainRelocationPacket> CODEC=StreamCodec.of((b,p)->{
        b.writeUUID(p.trainId);b.writeBlockPos(p.pos);b.writeDouble(p.lookAngle.x);b.writeDouble(p.lookAngle.y);b.writeDouble(p.lookAngle.z);
        b.writeInt(p.entityId);b.writeBoolean(p.direction);b.writeBoolean(p.hoveredBezier!=null);
        if(p.hoveredBezier!=null)BezierTrackPointLocation.STREAM_CODEC.encode(b,p.hoveredBezier);
        b.writeBoolean(p.entityUuid!=null);if(p.entityUuid!=null)b.writeUUID(p.entityUuid);
    },b->{
        var train=b.readUUID();var pos=b.readBlockPos();var look=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());
        int id=b.readInt();boolean direction=b.readBoolean();var curve=b.readBoolean()?BezierTrackPointLocation.STREAM_CODEC.decode(b):null;
        var uuid=b.readBoolean()?b.readUUID():null;return new TrainRelocationPacket(train,pos,look,id,direction,curve,uuid);
    });
    public static final StreamCodec<RegistryFriendlyByteBuf,TrainRelocationPacket> STREAM_CODEC=CODEC;
    @Override public Type<TrainRelocationPacket> type(){return TYPE;}

    public static boolean apply(ServerPlayer sender,TrainRelocationPacket packet) {
        if(sender.isSpectator()||!sender.isAlive()||!sender.mayBuild()||sender.isPassenger()
                ||!sender.getMainHandItem().is(AllItems.WRENCH))return false;
        var level=sender.level();var manager=TrainManager.get(level);
        var train=manager.get(packet.trainId);var entity=level.getEntity(packet.entityId);
        if(train==null||train.isRestoring()||!(entity instanceof CarriageContraptionEntity carriage)
                ||packet.entityUuid!=null&&!packet.entityUuid.equals(entity.getUUID())
                ||carriage.getCarriage()==null||carriage.getCarriage().train!=train
                ||!train.carriages.contains(carriage.getCarriage()))return false;
        double extraRange=AllConfigs.server().trains.maxTrackPlacementLength.get()*2d;
        if(!sender.isWithinBlockInteractionRange(packet.pos,extraRange)
                ||!sender.isWithinEntityInteractionRange(carriage,extraRange)
                ||!level.mayInteract(sender,packet.pos)||!level.isLoaded(packet.pos))return false;
        try {
            if(!TrainRelocator.relocate(train,level,packet.pos,packet.hoveredBezier,
                    packet.direction,packet.lookAngle,false))return false;
        } catch(UnloadedTrackException pending){return false;}
        sender.sendOverlayMessage(Component.translatable("create.train.relocate.success").withStyle(ChatFormatting.GREEN));
        for(var row:train.carriages)row.forEachPresentEntity(front->{
            front.nonDamageTicks=10;
            for(var player:PlayerLookup.tracking(front))ServerPlayNetworking.send(player,new ContraptionRelocationPacket(front.getId()));
        });
        return true;
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(TYPE,CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ContraptionRelocationPacket.TYPE,ContraptionRelocationPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->apply(context.player(),packet));
    }
}
