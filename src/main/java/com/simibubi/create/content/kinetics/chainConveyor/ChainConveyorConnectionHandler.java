package com.simibubi.create.content.kinetics.chainConveyor;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Vanilla block-use packets select endpoints; all inventory and geometry checks run on the server. */
public final class ChainConveyorConnectionHandler {
    public record Feedback(BlockPos pos,boolean selected) implements CustomPacketPayload{
        public static final Type<Feedback> TYPE=new Type<>(Create.asResource("chain_connection_selection"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Feedback> CODEC=StreamCodec.of((buf,value)->{buf.writeBlockPos(value.pos);buf.writeBoolean(value.selected);},buf->new Feedback(buf.readBlockPos(),buf.readBoolean()));
        @Override public Type<Feedback> type(){return TYPE;}
    }
    public record Cancel() implements CustomPacketPayload{
        public static final Type<Cancel> TYPE=new Type<>(Create.asResource("cancel_chain_connection"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Cancel> CODEC=StreamCodec.of((buf,value)->{},buf->new Cancel());
        @Override public Type<Cancel> type(){return TYPE;}
    }
    private record Selection(ResourceKey<Level> dimension, BlockPos position) {}
    private static final WeakHashMap<Player,Selection> SELECTED = new WeakHashMap<>();
    public static void use(Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return;
        if (player.isShiftKeyDown()) { cancel(player); return; }
        if(LoadedLevelAccess.blockEntity(level,pos) instanceof ChainConveyorBlockEntity conveyor&&conveyor.getConnections().size()>=ChainConveyorBlockEntity.maxConnections()){message(player,"cannot_add_more_connections");return;}
        var previous = SELECTED.get(player);
        if (previous == null || previous.dimension() != level.dimension() || !(LoadedLevelAccess.blockEntity(level,previous.position()) instanceof ChainConveyorBlockEntity)) {
            SELECTED.put(player,new Selection(level.dimension(),pos.immutable()));feedback(player,pos);message(player,"select_second"); return;
        }
        if (previous.position().equals(pos)) { message(player,"select_second"); return; }
        SELECTED.remove(player);
        feedback(player,null);
        if (!(LoadedLevelAccess.blockEntity(level,previous.position()) instanceof ChainConveyorBlockEntity from)
                || !(LoadedLevelAccess.blockEntity(level,pos) instanceof ChainConveyorBlockEntity to)) { message(player,"blocks_invalid"); return; }
        var offset = pos.subtract(previous.position());
        double length=net.minecraft.world.phys.Vec3.atLowerCornerOf(offset).length(),horizontal=Math.hypot(offset.getX(),offset.getZ())-1.5;
        if(length>=ChainConveyorBlockEntity.maxLength()){message(player,"too_far");return;}
        if(length<2.5){message(player,"too_close");return;}
        if(horizontal<=0){message(player,"cannot_connect_vertically");return;}
        if(Math.abs(offset.getY())>horizontal){message(player,"too_steep");return;}
        if(from.getConnections().contains(offset)||to.getConnections().contains(offset.multiply(-1))){message(player,"already_connected");return;}
        if(from.getConnections().size()>=ChainConveyorBlockEntity.maxConnections()||to.getConnections().size()>=ChainConveyorBlockEntity.maxConnections()){message(player,"cannot_add_more_connections");return;}
        if (!from.connectTo(to,player)) { message(player,"not_enough_chains"); return; }
        level.playSound(null,pos,SoundEvents.CHAIN_PLACE,SoundSource.BLOCKS,1,.8f);
        player.sendOverlayMessage(Component.empty());
    }
    public static String validate(Level level,BlockPos from,BlockPos to,Player player){
        if(from.equals(to))return "select_second";
        if(!(LoadedLevelAccess.blockEntity(level,from) instanceof ChainConveyorBlockEntity a)||!(LoadedLevelAccess.blockEntity(level,to) instanceof ChainConveyorBlockEntity b))return "blocks_invalid";
        var offset=to.subtract(from);double length=net.minecraft.world.phys.Vec3.atLowerCornerOf(offset).length(),horizontal=Math.hypot(offset.getX(),offset.getZ())-1.5;
        if(length>=ChainConveyorBlockEntity.maxLength())return "too_far";if(length<2.5)return "too_close";if(horizontal<=0)return "cannot_connect_vertically";if(Math.abs(offset.getY())>horizontal)return "too_steep";
        if(a.getConnections().contains(offset)||b.getConnections().contains(offset.multiply(-1)))return "already_connected";
        if(a.getConnections().size()>=ChainConveyorBlockEntity.maxConnections()||b.getConnections().size()>=ChainConveyorBlockEntity.maxConnections())return "cannot_add_more_connections";
        if(!player.isCreative()&&!ChainConveyorBlockEntity.getChainsFromInventory(player,ChainConveyorBlockEntity.getChainCost(offset),true))return "not_enough_chains";return null;
    }
    public static void cancel(Player player){SELECTED.remove(player);feedback(player,null);message(player,"selection_cleared");}
    private static void feedback(Player player,BlockPos pos){if(player instanceof ServerPlayer server)ServerPlayNetworking.send(server,new Feedback(pos==null?BlockPos.ZERO:pos,pos!=null));}
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(Feedback.TYPE,Feedback.CODEC);PayloadTypeRegistry.serverboundPlay().register(Cancel.TYPE,Cancel.CODEC);ServerPlayNetworking.registerGlobalReceiver(Cancel.TYPE,(packet,context)->cancel(context.player()));}
    private static void message(Player player,String key) { player.sendOverlayMessage(Component.translatable("create.chain_conveyor."+key)); }
}
