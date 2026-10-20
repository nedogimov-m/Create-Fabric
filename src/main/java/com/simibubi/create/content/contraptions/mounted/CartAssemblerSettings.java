package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.ContraptionWorld;
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
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.*;

/** Pinned side/value-box centre, with actual loaded native ray/reach/protection validation. */
public final class CartAssemblerSettings {
    public static BiConsumer<BlockPos,Direction> CLIENT_START=(pos,side)->{};
    public record SetMode(BlockPos pos,int mode)implements CustomPacketPayload{
        public SetMode{pos=pos.immutable();}
        public static final Type<SetMode> TYPE=new Type<>(Create.asResource("cart_assembler_movement_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetMode> CODEC=CustomPacketPayload.codec((packet,buffer)->{buffer.writeBlockPos(packet.pos);buffer.writeVarInt(packet.mode);},buffer->new SetMode(buffer.readBlockPos(),buffer.readVarInt()));
        @Override public Type<SetMode> type(){return TYPE;}
    }
    public static boolean panelHit(BlockState state,BlockPos pos,BlockHitResult hit){
        if(!(state.getBlock() instanceof CartAssemblerBlock)||hit.getDirection().getAxis().isVertical())return false;
        var side=hit.getDirection();if((side.getAxis()==Direction.Axis.X)!=(state.getValue(CartAssemblerBlock.RAIL_SHAPE)==RailShape.NORTH_SOUTH))return false;
        var center=Vec3.atLowerCornerOf(pos).add(.5,7/16d,.5).add(side.getUnitVec3().scale(9.5/16d));
        return center.distanceTo(hit.getLocation())<.25;
    }
    public static boolean targetsPanel(Level level,Player player,BlockPos pos){
        var eye=player.getEyePosition();var end=eye.add(player.getViewVector(1).scale(player.blockInteractionRange()+1));
        var hit=ContraptionWorld.loadedWorld(level).clip(new ClipContext(eye,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos)&&panelHit(LoadedLevelAccess.blockState(level,pos),pos,hit);
    }
    public static boolean apply(Level level,Player player,SetMode packet){
        if(level.isClientSide()||packet.mode<0||packet.mode>=CartAssemblerBlockEntity.CartMovementMode.values().length||player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(packet.pos,1)||!level.mayInteract(player,packet.pos)||!targetsPanel(level,player,packet.pos)||!(LoadedLevelAccess.blockEntity(level,packet.pos) instanceof CartAssemblerBlockEntity assembler))return false;
        assembler.setMovementMode(CartAssemblerBlockEntity.CartMovementMode.values()[packet.mode]);return true;
    }
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(SetMode.TYPE,SetMode.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetMode.TYPE,(packet,context)->apply(context.player().level(),context.player(),packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
            if(hand!=InteractionHand.MAIN_HAND||player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,hit.getBlockPos())||!panelHit(LoadedLevelAccess.blockState(level,hit.getBlockPos()),hit.getBlockPos(),hit))return InteractionResult.PASS;
            if(level.isClientSide())CLIENT_START.accept(hit.getBlockPos().immutable(),hit.getDirection());return InteractionResult.SUCCESS;
        });
    }
    private CartAssemblerSettings(){}
}
