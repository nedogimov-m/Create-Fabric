package com.simibubi.create.foundation.blockEntity.behaviour;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Source row/value/hand/hit/side/control/behaviour protocol on the actual Fabric receiver. */
public class ValueSettingsPacket implements CustomPacketPayload {
    public static final Type<ValueSettingsPacket> TYPE=new Type<>(Create.asResource("value_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ValueSettingsPacket> STREAM_CODEC=CustomPacketPayload.codec((packet,buffer)->{
        buffer.writeBlockPos(packet.pos);buffer.writeVarInt(packet.row);buffer.writeVarInt(packet.value);
        buffer.writeBoolean(packet.interactHand!=null);if(packet.interactHand!=null)buffer.writeEnum(packet.interactHand);
        buffer.writeBoolean(packet.hitResult!=null);if(packet.hitResult!=null)BlockHitResult.STREAM_CODEC.encode(buffer,packet.hitResult);
        buffer.writeEnum(packet.side);buffer.writeBoolean(packet.ctrlDown);buffer.writeVarInt(packet.behaviourIndex);
    },buffer->{
        var pos=buffer.readBlockPos();int row=buffer.readVarInt(),value=buffer.readVarInt();
        var hand=buffer.readBoolean()?buffer.readEnum(InteractionHand.class):null;
        var hit=buffer.readBoolean()?BlockHitResult.STREAM_CODEC.decode(buffer):null;
        return new ValueSettingsPacket(pos,row,value,hand,hit,buffer.readEnum(Direction.class),buffer.readBoolean(),buffer.readVarInt());
    });
    private final BlockPos pos;
    private final int row,value;
    private final InteractionHand interactHand;
    private final BlockHitResult hitResult;
    private final Direction side;
    private final boolean ctrlDown;
    private final int behaviourIndex;
    public ValueSettingsPacket(BlockPos pos,int row,int value,InteractionHand interactHand,BlockHitResult hitResult,Direction side,boolean ctrlDown,int behaviourIndex){
        this.pos=pos.immutable();this.row=row;this.value=value;this.interactHand=interactHand;
        this.hitResult=hitResult;this.side=java.util.Objects.requireNonNull(side);this.ctrlDown=ctrlDown;this.behaviourIndex=behaviourIndex;
    }
    @Override public Type<ValueSettingsPacket> type(){return TYPE;}
    public boolean apply(Level level,Player player){
        if(level.isClientSide()||player.level()!=level||player.isSpectator()||!player.mayBuild()
                ||!LoadedLevelAccess.isLoaded(level,pos)||!player.isWithinBlockInteractionRange(pos,1)||!level.mayInteract(player,pos))return false;
        var owner=LoadedLevelAccess.blockEntity(level,pos);
        if(!(owner instanceof SmartBlockEntity smart)||smart.isRemoved()||smart.isVirtual())return false;
        for(var behaviour:java.util.List.copyOf(smart.getAllBehaviours())){
            if(!(behaviour instanceof ValueSettingsBehaviour settings)||!settings.acceptsValueSettings()
                    ||settings.netId()!=behaviourIndex||!settings.isActive()||!settings.mayInteract(player))continue;
            if(settings.onlyVisibleWithWrench()&&!player.getMainHandItem().is(com.simibubi.create.AllItems.WRENCH)&&!player.getOffhandItem().is(com.simibubi.create.AllItems.WRENCH))return false;
            var slot=settings.getSlotPositioning();
            if(slot instanceof ValueBoxTransform.Sided sided)sided.fromSide(side);
            if(!slot.shouldRender(level,pos,owner.getBlockState()))return false;
            BlockHitResult hit=hitResult;
            if(hit!=null){
                var point=hit.getLocation();
                if(!hit.getBlockPos().equals(pos)||hit.getDirection()!=side||!Double.isFinite(point.x)||!Double.isFinite(point.y)||!Double.isFinite(point.z)||!settings.testHit(point))return false;
            }else{
                // Source ordinary board packets omit the hit. Side activation and range are still real owner checks.
                var offset=slot.getLocalOffset(level,pos,owner.getBlockState());if(offset==null)return false;
                hit=new BlockHitResult(Vec3.atLowerCornerOf(pos).add(offset),side,pos,false);
            }
            if(!current(level,pos,smart,behaviour))return false;
            if(interactHand!=null){settings.onShortInteract(player,interactHand,side,hit);return true;}
            var board=settings.createBoard(player,hit);
            if(row<0||row>=board.rows().size()||value<0||value>board.maxValue())return false;
            // Callbacks can replace the block or installed behaviour; never mutate a stale settings owner.
            if(!current(level,pos,smart,behaviour))return false;
            settings.setValueSettings(player,new ValueSettings(row,value),ctrlDown);
            return true;
        }
        return false;
    }
    private static boolean current(Level level,BlockPos pos,SmartBlockEntity owner,BlockEntityBehaviour behaviour){
        return !owner.isRemoved()&&!owner.isVirtual()&&LoadedLevelAccess.blockEntity(level,pos)==owner
            &&LoadedLevelAccess.blockState(level,pos)==owner.getBlockState()
            &&owner.getBehaviour(behaviour.getType())==behaviour;
    }
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.apply(context.player().level(),context.player()));
    }
}
