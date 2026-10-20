package com.simibubi.create.content.redstone.diodes;

import com.simibubi.create.*;
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

/** Source short-click inversion and held timing board, server owns all state mutations. */
public final class BrassDiodeSettings {
    public static BiConsumer<BlockPos,Direction> CLIENT_START=(pos,face)->{};
    public record ValueSetting(int row,int value){
        public ValueSetting{if(row<0||row>2||value<0||value>60)throw new IllegalArgumentException("Invalid diode board coordinate");}
        public int ticks(){return Math.max(2,Math.max(1,value)*(row==0?1:row==1?20:1200));}
        public static ValueSetting fromTicks(int ticks){return ticks>1200?new ValueSetting(2,Math.clamp(ticks/1200,0,60)):ticks>60?new ValueSetting(1,Math.clamp(ticks/20,0,60)):new ValueSetting(0,Math.clamp(ticks,0,60));}
        public String format(){int value=Math.max(1,this.value);return row==0?Math.max(2,value)+"t":row==1?"0:"+(value<10?"0":"")+value:value+":00";}
    }
    public record SetTiming(BlockPos pos,int ticks)implements CustomPacketPayload{
        public static final Type<SetTiming> TYPE=new Type<>(Create.asResource("diode_timing"));
        public static final StreamCodec<RegistryFriendlyByteBuf,SetTiming> CODEC=CustomPacketPayload.codec((packet,buffer)->{buffer.writeBlockPos(packet.pos);buffer.writeVarInt(packet.ticks);},buffer->new SetTiming(buffer.readBlockPos(),buffer.readVarInt()));
        public SetTiming{pos=pos.immutable();if(ticks<2||ticks>72000)throw new IllegalArgumentException("Diode timing out of range");}
        @Override public Type<SetTiming> type(){return TYPE;}
    }
    public record Toggle(BlockPos pos)implements CustomPacketPayload{
        public static final Type<Toggle> TYPE=new Type<>(Create.asResource("diode_invert"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Toggle> CODEC=CustomPacketPayload.codec((packet,buffer)->buffer.writeBlockPos(packet.pos),buffer->new Toggle(buffer.readBlockPos()));
        public Toggle{pos=pos.immutable();}
        @Override public Type<Toggle> type(){return TYPE;}
    }
    public static boolean testHit(BlockState state,BlockHitResult hit){return state.getBlock()instanceof BrassDiodeBlock&&hit.getDirection()==Direction.UP&&hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos())).distanceTo(new Vec3(.5,2.6/16,.5))<.25;}
    private static BrassDiodeBlockEntity owner(Player player,BlockPos pos){
        var level=player.level();if(player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(pos,1)||!level.mayInteract(player,pos)||!LoadedLevelAccess.isLoaded(level,pos))return null;
        return LoadedLevelAccess.blockEntity(level,pos)instanceof BrassDiodeBlockEntity diode?diode:null;
    }
    public static boolean apply(Player player,SetTiming packet){var diode=owner(player,packet.pos);if(diode==null)return false;diode.setMaxState(packet.ticks);return true;}
    public static boolean apply(Player player,Toggle packet){var diode=owner(player,packet.pos);return diode!=null&&((BrassDiodeBlock)diode.getBlockState().getBlock()).toggle(player.level(),packet.pos,diode.getBlockState(),player).consumesAction();}
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(SetTiming.TYPE,SetTiming.CODEC);PayloadTypeRegistry.serverboundPlay().register(Toggle.TYPE,Toggle.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SetTiming.TYPE,(packet,context)->apply(context.player(),packet));ServerPlayNetworking.registerGlobalReceiver(Toggle.TYPE,(packet,context)->apply(context.player(),packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
            if(hand!=InteractionHand.MAIN_HAND||player.isSpectator()||!player.mayBuild()||player.isShiftKeyDown()||player.getItemInHand(hand).is(AllItems.WRENCH)||!level.mayInteract(player,hit.getBlockPos())||!testHit(LoadedLevelAccess.blockState(level,hit.getBlockPos()),hit))return InteractionResult.PASS;
            if(level.isClientSide())CLIENT_START.accept(hit.getBlockPos().immutable(),hit.getDirection());return InteractionResult.SUCCESS;
        });
    }
    private BrassDiodeSettings(){}
}
