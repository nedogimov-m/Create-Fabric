package com.simibubi.create.content.logistics.filter;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class AttributeFilterNetworking {
    public record Sync(int menu,List<ItemAttribute> available,List<ItemAttributeEntry> selected,int revision) implements CustomPacketPayload {
        public static final Type<Sync> TYPE=new Type<>(Create.asResource("attribute_filter_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Sync> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Sync::menu,ItemAttribute.STREAM_CODEC.apply(ByteBufCodecs.list()),Sync::available,ItemAttributeEntry.STREAM_CODEC.apply(ByteBufCodecs.list()),Sync::selected,ByteBufCodecs.VAR_INT,Sync::revision,Sync::new);
        @Override public Type<Sync> type(){return TYPE;}
    }
    public record Add(int menu,int index,boolean inverted,int revision) implements CustomPacketPayload {
        public static final Type<Add> TYPE=new Type<>(Create.asResource("attribute_filter_add"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Add> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Add::menu,ByteBufCodecs.VAR_INT,Add::index,ByteBufCodecs.BOOL,Add::inverted,ByteBufCodecs.VAR_INT,Add::revision,Add::new);
        @Override public Type<Add> type(){return TYPE;}
    }
    public static void register(){PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE,Sync.CODEC);PayloadTypeRegistry.serverboundPlay().register(Add.TYPE,Add.CODEC);ServerPlayNetworking.registerGlobalReceiver(Add.TYPE,(packet,context)->{if(context.player().containerMenu instanceof AttributeFilterMenu menu&&menu.containerId==packet.menu)menu.addAttribute(context.player(),packet.index,packet.inverted,packet.revision);});}
    private AttributeFilterNetworking(){}
}
