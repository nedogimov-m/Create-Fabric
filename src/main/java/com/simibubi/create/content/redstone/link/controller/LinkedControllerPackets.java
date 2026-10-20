package com.simibubi.create.content.redstone.link.controller;

import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

public final class LinkedControllerPackets{
    public record Bind(int button,BlockPos link)implements CustomPacketPayload{
        public static final Type<Bind> TYPE=new Type<>(Create.asResource("linked_controller_bind"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Bind> CODEC=CustomPacketPayload.codec((packet,buffer)->{buffer.writeVarInt(packet.button);buffer.writeBlockPos(packet.link);},buffer->new Bind(buffer.readVarInt(),buffer.readBlockPos()));
        @Override public Type<Bind>type(){return TYPE;}
    }
    public record Input(List<Integer> buttons,boolean pressed,BlockPos lectern)implements CustomPacketPayload{
        public static final Type<Input> TYPE=new Type<>(Create.asResource("linked_controller_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Input> CODEC=CustomPacketPayload.codec((packet,buffer)->{
            if(packet.buttons.size()>6)throw new IllegalArgumentException("Too many controller buttons");buffer.writeVarInt(packet.buttons.size());for(int button:packet.buttons)buffer.writeVarInt(button);buffer.writeBoolean(packet.pressed);buffer.writeBoolean(packet.lectern!=null);if(packet.lectern!=null)buffer.writeBlockPos(packet.lectern);
        },buffer->{int count=buffer.readVarInt();if(count<0||count>6)throw new IllegalArgumentException("Invalid controller button count");var buttons=new ArrayList<Integer>(count);for(int i=0;i<count;i++){int button=buffer.readVarInt();if(button<0||button>=6||buttons.contains(button))throw new IllegalArgumentException("Invalid controller button");buttons.add(button);}return new Input(List.copyOf(buttons),buffer.readBoolean(),buffer.readBoolean()?buffer.readBlockPos():null);});
        public Input{buttons=List.copyOf(buttons);}
        @Override public Type<Input>type(){return TYPE;}
    }
    public record Stop(BlockPos lectern)implements CustomPacketPayload{
        public static final Type<Stop> TYPE=new Type<>(Create.asResource("linked_controller_stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Stop> CODEC=CustomPacketPayload.codec((packet,buffer)->buffer.writeBlockPos(packet.lectern),buffer->new Stop(buffer.readBlockPos()));
        @Override public Type<Stop>type(){return TYPE;}
    }
    public static boolean bind(ServerPlayer player,Bind packet){
        if(player.isSpectator()||!player.mayBuild()||packet.button<0||packet.button>=6||!player.isWithinBlockInteractionRange(packet.link,1)
            ||!player.level().mayInteract(player,packet.link)||!LoadedLevelAccess.isLoaded(player.level(),packet.link))return false;
        var controller=LinkedControllerItem.heldController(player);if(controller.isEmpty())return false;
        var link=BlockEntityBehaviour.get(player.level(),packet.link,LinkBehaviour.TYPE);if(link==null)return false;
        var samples=LinkedControllerItem.getFrequencyItems(controller);var key=link.getNetworkKey();samples.set(packet.button*2,key.getFirst().getStack());samples.set(packet.button*2+1,key.getSecond().getStack());LinkedControllerItem.setFrequencyItems(controller,samples);player.getInventory().setChanged();return true;
    }
    private static LecternControllerBlockEntity ownedLectern(ServerPlayer player,BlockPos pos){
        if(pos==null||!LoadedLevelAccess.isLoaded(player.level(),pos)||!LecternControllerBlockEntity.playerInRange(player,player.level(),pos))return null;
        return LoadedLevelAccess.blockEntity(player.level(),pos)instanceof LecternControllerBlockEntity lectern&&lectern.isUsedBy(player)
            &&LinkedControllerServerHandler.ownsSession(player.level(),player.getUUID(),pos)?lectern:null;
    }
    public static boolean input(ServerPlayer player,Input packet){
        if(packet.buttons.size()>6||packet.buttons.stream().anyMatch(button->button==null||button<0||button>=6)||new HashSet<>(packet.buttons).size()!=packet.buttons.size()||player.isSpectator()&&packet.pressed)return false;
        ItemStack controller;
        if(packet.lectern!=null){var lectern=ownedLectern(player,packet.lectern);if(lectern==null)return false;controller=lectern.getController();}
        else controller=LinkedControllerItem.heldController(player);
        if(controller.isEmpty())return false;
        LinkedControllerServerHandler.receivePressed(player.level(),player.blockPosition(),player.getUUID(),packet.buttons.stream().map(button->LinkedControllerItem.toFrequency(controller,button)).toList(),packet.pressed);return true;
    }
    public static void stop(ServerPlayer player,Stop packet){var lectern=ownedLectern(player,packet.lectern);if(lectern!=null)lectern.tryStopUsing(player);}
    private static boolean registered;
    public static void register(){if(registered)return;registered=true;
        PayloadTypeRegistry.serverboundPlay().register(Bind.TYPE,Bind.CODEC);PayloadTypeRegistry.serverboundPlay().register(Input.TYPE,Input.CODEC);PayloadTypeRegistry.serverboundPlay().register(Stop.TYPE,Stop.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Bind.TYPE,(packet,context)->bind(context.player(),packet));ServerPlayNetworking.registerGlobalReceiver(Input.TYPE,(packet,context)->input(context.player(),packet));ServerPlayNetworking.registerGlobalReceiver(Stop.TYPE,(packet,context)->stop(context.player(),packet));
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->player.getItemInHand(hand).getItem()instanceof LinkedControllerItem item?item.useBlockFirst(player,level,hand,hit):InteractionResult.PASS);
    }
    private LinkedControllerPackets(){}
}
