package com.simibubi.create.content.equipment.extendoGrip;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Original bounded long-range action; the native entity API merges specific/general interaction. */
public record ExtendoGripInteractionPacket(@Nullable InteractionHand hand,int target,@Nullable Vec3 point) implements CustomPacketPayload {
    public static final Type<ExtendoGripInteractionPacket> TYPE=new Type<>(Create.asResource("extendo_interact"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ExtendoGripInteractionPacket> STREAM_CODEC=StreamCodec.of((buffer,value)->{buffer.writeBoolean(value.hand!=null);if(value.hand!=null)buffer.writeEnum(value.hand);buffer.writeInt(value.target);buffer.writeBoolean(value.point!=null);if(value.point!=null)Vec3.STREAM_CODEC.encode(buffer,value.point);},buffer->{var hand=buffer.readBoolean()?buffer.readEnum(InteractionHand.class):null;int target=buffer.readInt();var point=buffer.readBoolean()?Vec3.STREAM_CODEC.decode(buffer):null;return new ExtendoGripInteractionPacket(hand,target,point);});
    public ExtendoGripInteractionPacket(Entity target){this(target,null);}
    public ExtendoGripInteractionPacket(Entity target,InteractionHand hand){this(target,hand,null);}
    public ExtendoGripInteractionPacket(Entity target,InteractionHand hand,Vec3 point){this(hand,target.getId(),point);}
    @Override public Type<ExtendoGripInteractionPacket> type(){return TYPE;}
    public boolean handle(ServerPlayer sender){
        if(sender==null||sender.isSpectator()||!sender.isAlive()||!ExtendoGripItem.isHoldingExtendoGrip(sender))return false;
        // Derive the actual held state now; no packet can retain an old range after a hand change.
        ExtendoGripItem.holdingExtendoGripIncreasesRange(sender);
        Entity entity=sender.level().getEntity(target);if(entity==null||entity==sender||!entity.isAlive()||entity.isRemoved()||entity.level()!=sender.level()||!sender.level().hasChunkAt(entity.blockPosition()))return false;
        double range=sender.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE),distance=sender.distanceToSqr(entity);
        if(!Double.isFinite(range)||range<0||distance>range*range)return false;
        // Native LOS reads blocks. Check the bounded intervening chunks without creating load tickets.
        var from=sender.blockPosition();var to=entity.blockPosition();
        for(int x=Math.min(from.getX(),to.getX())>>4;x<=(Math.max(from.getX(),to.getX())>>4);x++)for(int z=Math.min(from.getZ(),to.getZ())>>4;z<=(Math.max(from.getZ(),to.getZ())>>4);z++)if(sender.level().getChunkSource().getChunkNow(x,z)==null)return false;
        if(!sender.hasLineOfSight(entity))range-=3;
        if(range<0||distance>range*range)return false;
        if(point!=null&&(!Double.isFinite(point.x)||!Double.isFinite(point.y)||!Double.isFinite(point.z)||!entity.getBoundingBox().inflate(.01).contains(point.add(entity.position()))))return false;
        if(hand==null){if(point!=null||!entity.isAttackable()||entity instanceof net.minecraft.world.entity.player.Player other&&!sender.canHarmPlayer(other))return false;sender.attack(entity);}
        else sender.interactOn(entity,hand,point==null?Vec3.ZERO:point);
        return true;
    }
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.handle(context.player()));}
}
