package com.simibubi.create.content.contraptions.piston;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import com.simibubi.create.content.contraptions.IControlContraption.MovementMode;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Source allowed sides/offset, with authoritative native wrench/ray/protection checks. */
public final class PistonSettings {
    public static BiConsumer<BlockPos,Direction> CLIENT_START=(pos,side)->{};
    public record SetMode(BlockPos pos,int mode)implements CustomPacketPayload{
        public SetMode{pos=pos.immutable();}
        public static final Type<SetMode> TYPE=new Type<>(Create.asResource("piston_movement_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetMode> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeBlockPos(packet.pos);buf.writeVarInt(packet.mode);},buf->new SetMode(buf.readBlockPos(),buf.readVarInt()));
        @Override public Type<SetMode> type(){return TYPE;}
    }
    public static boolean panelHit(BlockState state,BlockPos pos,BlockHitResult hit){
        if(state.getBlock() instanceof com.simibubi.create.content.contraptions.pulley.PulleyBlock)return hit.getDirection()==Direction.UP&&Vec3.atCenterOf(pos).add(0,.5-1/32d,0).distanceTo(hit.getLocation())<.25;
        if(!(state.getBlock() instanceof MechanicalPistonBlock piston))return false;
        var side=hit.getDirection();var front=state.getValue(MechanicalPistonBlock.FACING);
        if(side.getAxis()==front.getAxis()||side.getAxis()==piston.getRotationAxis(state))return false;
        var centre=Vec3.atCenterOf(pos).add(side.getUnitVec3().scale(.5-1/32d)).add(front.getUnitVec3().scale(-2/16d));
        return centre.distanceTo(hit.getLocation())<.25;
    }
    public static boolean targetsPanel(Level level,Player player,BlockPos pos){
        var eye=player.getEyePosition();var ray=ContraptionWorld.loadedWorld(level).clip(new net.minecraft.world.level.ClipContext(eye,eye.add(player.getViewVector(1).scale(player.blockInteractionRange()+1)),net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,player));
        return ray.getType()==HitResult.Type.BLOCK&&ray.getBlockPos().equals(pos)&&panelHit(LoadedLevelAccess.blockState(level,pos),pos,ray);
    }
    public static boolean apply(Level level,Player player,SetMode packet){
        if(level.isClientSide()||packet.mode<0||packet.mode>=MovementMode.values().length||player.isSpectator()||!player.mayBuild()||!player.getMainHandItem().is(AllItems.WRENCH)||!player.isWithinBlockInteractionRange(packet.pos,1)||!level.mayInteract(player,packet.pos)||!targetsPanel(level,player,packet.pos)||!(LoadedLevelAccess.blockEntity(level,packet.pos) instanceof LinearActuatorBlockEntity piston))return false;
        piston.setMovementMode(MovementMode.values()[packet.mode]);return true;
    }
    public static void register(){
        PistonStructure.register();
        PayloadTypeRegistry.serverboundPlay().register(SetMode.TYPE,SetMode.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetMode.TYPE,(packet,context)->apply(context.player().level(),context.player(),packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
            if(hand!=InteractionHand.MAIN_HAND||player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||!player.getMainHandItem().is(AllItems.WRENCH)||!level.mayInteract(player,hit.getBlockPos())||!panelHit(LoadedLevelAccess.blockState(level,hit.getBlockPos()),hit.getBlockPos(),hit))return InteractionResult.PASS;
            if(level.isClientSide())CLIENT_START.accept(hit.getBlockPos().immutable(),hit.getDirection());return InteractionResult.SUCCESS;
        });
    }
    private PistonSettings(){}
}
