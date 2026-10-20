package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class ToolboxNetworking {
    public record Range(int value)implements CustomPacketPayload{
        public static final Type<Range> TYPE=new Type<>(Create.asResource("toolbox_range"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Range> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Range::value,Range::new);
        @Override public Type<Range> type(){return TYPE;}
    }
    public static void register(){ToolboxConfig.reload();ToolboxBindings.register();ToolboxMenu.register();ToolboxDyeingRecipe.register();ToolboxHandler.register();PayloadTypeRegistry.serverboundPlay().register(ToolboxEquipPacket.TYPE,ToolboxEquipPacket.STREAM_CODEC);PayloadTypeRegistry.serverboundPlay().register(ToolboxDisposeAllPacket.TYPE,ToolboxDisposeAllPacket.STREAM_CODEC);PayloadTypeRegistry.clientboundPlay().register(Range.TYPE,Range.CODEC);ServerPlayNetworking.registerGlobalReceiver(ToolboxEquipPacket.TYPE,(packet,context)->packet.handle(context.player()));ServerPlayNetworking.registerGlobalReceiver(ToolboxDisposeAllPacket.TYPE,(packet,context)->packet.handle(context.player()));ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->sender.sendPacket(new Range(ToolboxConfig.server())));}
    private ToolboxNetworking(){}
}
