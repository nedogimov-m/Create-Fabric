package com.simibubi.create.content.kinetics.motor;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.speedController.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Shared motor/controller settings; only the server changes kinetic state. */
public final class KineticSpeedSettings {
    public static BiConsumer<BlockPos,Direction> CLIENT_START=(pos,face)->{};
    public record SetSpeed(BlockPos pos,int speed) implements CustomPacketPayload {
        public static final Type<SetSpeed> TYPE=new Type<>(Create.asResource("kinetic_speed"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetSpeed> CODEC=CustomPacketPayload.codec((packet,buffer)->{buffer.writeBlockPos(packet.pos);buffer.writeVarInt(packet.speed);},buffer->new SetSpeed(buffer.readBlockPos(),buffer.readVarInt()));
        @Override public Type<SetSpeed> type(){return TYPE;}
    }
    public static boolean supports(BlockState state){return state.is(AllBlocks.CREATIVE_MOTOR)||state.is(AllBlocks.ROTATION_SPEED_CONTROLLER);}
    public static int value(KineticBlockEntity entity){return entity instanceof SpeedControllerBlockEntity controller?controller.getTargetSpeed():entity.getGeneratedSpeedSetting();}
    public static boolean activeSide(BlockState state,Direction side){
        if(state.is(AllBlocks.CREATIVE_MOTOR))return new CreativeMotorBlockEntity.MotorValueBox().fromSide(side).shouldRender(null,BlockPos.ZERO,state);
        return state.is(AllBlocks.ROTATION_SPEED_CONTROLLER)&&side.getAxis().isHorizontal()&&side.getAxis()!=state.getValue(SpeedControllerBlock.HORIZONTAL_AXIS);
    }
    public static Vec3 offset(BlockState state,Direction side){
        if(state.is(AllBlocks.CREATIVE_MOTOR))return new CreativeMotorBlockEntity.MotorValueBox().fromSide(side).getLocalOffset(null,BlockPos.ZERO,state);
        return new Vec3(.5+side.getStepX()*7.5/16,11d/16,.5+side.getStepZ()*7.5/16);
    }
    public static boolean testHit(BlockState state,BlockHitResult hit){return activeSide(state,hit.getDirection())&&hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())).distanceTo(offset(state,hit.getDirection()))<0.25;}
    public static InteractionResult interact(Level level,BlockPos pos,Player player,Direction face){
        var state=LoadedLevelAccess.blockState(level,pos);
        if(!supports(state)||!activeSide(state,face)||!player.mayBuild()||player.isSpectator()||player.isShiftKeyDown()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(LoadedLevelAccess.blockEntity(level,pos) instanceof CreativeMotorBlockEntity motor){
            var settings=motor.generatedSpeed;
            if(!settings.isActive()||!settings.mayInteract(player)||settings.bypassesInput(player.getMainHandItem()))return InteractionResult.PASS;
            var slot=settings.getSlotPositioning();if(slot instanceof com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided sided)sided.fromSide(face);
            if(!slot.shouldRender(level,pos,state))return InteractionResult.PASS;
        }
        if(level.isClientSide())CLIENT_START.accept(pos.immutable(),face);return InteractionResult.SUCCESS;
    }
    public static boolean apply(Level level,Player player,SetSpeed packet){
        if(level.isClientSide()||player.level()!=level||player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(packet.pos,1)||!level.mayInteract(player,packet.pos)||!level.isLoaded(packet.pos)||packet.speed<-com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get()||packet.speed>com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get())return false;
        var entity=LoadedLevelAccess.blockEntity(level,packet.pos);if(!(entity instanceof KineticBlockEntity kinetic)||!supports(entity.getBlockState())
                ||entity.getBlockState().is(AllBlocks.CREATIVE_MOTOR)&&!(entity instanceof CreativeMotorBlockEntity))return false;
        if(entity instanceof CreativeMotorBlockEntity motor){
            var settings=motor.generatedSpeed;
            if(!settings.isActive()||!settings.mayInteract(player)
                    ||motor.generatedSpeed!=settings||motor.getBehaviour(com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour.TYPE)!=settings)return false;
        }
        if(kinetic.isRemoved()||kinetic.isVirtual()||LoadedLevelAccess.blockEntity(level,packet.pos)!=kinetic)return false;
        if(entity instanceof SpeedControllerBlockEntity controller)controller.setTargetSpeed(packet.speed);else kinetic.setGeneratedSpeed(packet.speed);
        return true;
    }
    public static void register(){
        com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket.register();
        com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsInputHandler.register();
        PayloadTypeRegistry.serverboundPlay().register(SetSpeed.TYPE,SetSpeed.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetSpeed.TYPE,(packet,context)->apply(context.player().level(),context.player(),packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
            if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;var state=LoadedLevelAccess.blockState(level,hit.getBlockPos());
            if(!supports(state)||!testHit(state,hit))return InteractionResult.PASS;
            return interact(level,hit.getBlockPos(),player,hit.getDirection());
        });
    }
    private KineticSpeedSettings(){}
}
