package com.simibubi.create.content.equipment.symmetryWand;

import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.symmetryWand.mirror.SymmetryMirror;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class SymmetryWandNetworking {
    public record Open(InteractionHand hand,SymmetryMirror mirror,UUID session) implements CustomPacketPayload {
        public Open{mirror=mirror.copy();}
        public static final Type<Open> TYPE=new Type<>(Create.asResource("open_symmetry_wand"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.of((buffer,value)->{buffer.writeBoolean(value.hand==InteractionHand.OFF_HAND);SymmetryMirror.STREAM_CODEC.encode(buffer,value.mirror);buffer.writeUUID(value.session);},buffer->new Open(buffer.readBoolean()?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,SymmetryMirror.STREAM_CODEC.decode(buffer),buffer.readUUID()));
        @Override public Type<Open> type(){return TYPE;}
    }
    private record Session(UUID token,ItemStack owner,ItemStack before,InteractionHand hand,int selected,long expires){}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    public static boolean actual(ServerPlayer player){return player.level().getServer().getPlayerList().getPlayer(player.getUUID())==player&&!player.isSpectator()&&player.mayBuild();}
    public static void invalidate(Player player){SESSIONS.remove(player.getUUID());}
    public static Open begin(ServerPlayer player,InteractionHand hand){
        if(!actual(player))return null;var stack=player.getItemInHand(hand);if(!(stack.getItem() instanceof SymmetryWandItem))return null;var token=UUID.randomUUID();SESSIONS.put(player.getUUID(),new Session(token,stack,stack.copy(),hand,player.getInventory().getSelectedSlot(),player.level().getGameTime()+1200));return new Open(hand,SymmetryWandItem.getMirror(stack),token);
    }
    public static void open(ServerPlayer player,InteractionHand hand){var packet=begin(player,hand);if(packet!=null)ServerPlayNetworking.send(player,packet);}
    public static boolean configure(ServerPlayer player,ConfigureSymmetryWandPacket packet){
        var session=SESSIONS.get(player.getUUID());if(session==null||!session.token.equals(packet.session())||session.hand!=packet.hand()||!actual(player)||session.expires<player.level().getGameTime())return false;
        var stack=player.getItemInHand(packet.hand());if(stack!=session.owner||!ItemStack.matches(stack,session.before)||packet.hand()==InteractionHand.MAIN_HAND&&player.getInventory().getSelectedSlot()!=session.selected)return false;
        var settings=packet.mirror().copy();if(settings instanceof com.simibubi.create.content.equipment.symmetryWand.mirror.EmptyMirror)return false;
        var original=SymmetryWandItem.getMirror(stack);settings.setPosition(original.getPosition());settings.enable=original.enable;
        SESSIONS.remove(player.getUUID());SymmetryWandItem.configureSettings(stack,settings);player.getInventory().setChanged();return true;
    }
    public static void register(){SymmetryWandConfig.reload();PayloadTypeRegistry.serverboundPlay().register(ConfigureSymmetryWandPacket.TYPE,ConfigureSymmetryWandPacket.STREAM_CODEC);PayloadTypeRegistry.clientboundPlay().register(Open.TYPE,Open.CODEC);PayloadTypeRegistry.clientboundPlay().register(SymmetryEffectPacket.TYPE,SymmetryEffectPacket.STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(ConfigureSymmetryWandPacket.TYPE,(packet,context)->packet.handle(context.player()));ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->invalidate(handler.getPlayer()));net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server->SESSIONS.clear());SymmetryHandler.register();}
    private SymmetryWandNetworking(){}
}
