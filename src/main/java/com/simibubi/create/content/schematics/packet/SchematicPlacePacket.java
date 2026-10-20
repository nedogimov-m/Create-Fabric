package com.simibubi.create.content.schematics.packet;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Source creative print, processed through actual native targets in bounded server tick batches. */
public record SchematicPlacePacket(ItemStack stack) implements CustomPacketPayload {
    public SchematicPlacePacket{stack=stack.copy();}
    @Override public ItemStack stack(){return stack.copy();}
    private record Pending(ServerPlayer player,SchematicPrinter printer){}
    private static final Map<UUID,Pending> PENDING=new LinkedHashMap<>();
    public static final Type<SchematicPlacePacket> TYPE=new Type<>(Create.asResource("place_schematic"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SchematicPlacePacket> STREAM_CODEC=ItemStack.STREAM_CODEC.map(SchematicPlacePacket::new,SchematicPlacePacket::stack);
    public boolean handle(ServerPlayer player){
        if(!player.isCreative()||player.level().getServer().getPlayerList().getPlayer(player.getUUID())!=player||!stack.is(AllItems.SCHEMATIC)||!ItemStack.matches(stack,player.getMainHandItem())||PENDING.containsKey(player.getUUID()))return false;
        var printer=new SchematicPrinter();printer.loadSchematic(stack,player.level(),!player.canUseGameMasterBlocks());if(!printer.isLoaded()||printer.isErrored())return false;PENDING.put(player.getUUID(),new Pending(player,printer));return true;
    }
    private static void tick(net.minecraft.server.MinecraftServer server){for(var entry:List.copyOf(PENDING.entrySet())){
        var pending=entry.getValue();var player=pending.player;if(player.level().getServer()!=server)continue;if(server.getPlayerList().getPlayer(player.getUUID())!=player||!player.isCreative()||pending.printer.getBlockReader().original()!=player.level()){PENDING.remove(entry.getKey(),pending);continue;}
        try{for(int scanned=0;scanned<1000;scanned++){
            var printer=pending.printer;if(!printer.advanceCurrentPos()){printer.sendBlockUpdates(player.level());PENDING.remove(entry.getKey(),pending);break;}if(!printer.shouldPlaceCurrent(player.level()))continue;
            printer.handleCurrentTarget((pos,state,be)->{if(state.isAir()&&!SchematicConfig.get().creativePrintIncludesAir()||!player.level().mayInteract(player,pos))return;SchematicPlacement.placeBlock(player.level(),state,pos,ItemStack.EMPTY,SchematicNbt.prepare(player.level(),state,be));},(pos,entity)->{if(player.level().mayInteract(player,pos))player.level().addFreshEntity(entity);});
        }}catch(RuntimeException failure){PENDING.remove(entry.getKey(),pending);Create.LOGGER.error("Native creative schematic print failed while retaining the original held blueprint",failure);}
    }}
    @Override public Type<SchematicPlacePacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.handle(context.player()));ServerTickEvents.END_SERVER_TICK.register(SchematicPlacePacket::tick);ServerLifecycleEvents.SERVER_STOPPING.register(server->PENDING.entrySet().removeIf(entry->entry.getValue().player.level().getServer()==server));}
}
