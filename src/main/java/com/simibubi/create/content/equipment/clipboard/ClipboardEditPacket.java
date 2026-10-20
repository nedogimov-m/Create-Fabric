package com.simibubi.create.content.equipment.clipboard;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.SchematicNbt;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Source editor payload, applied only to the actual selected clipboard or actual reachable placed owner. */
public record ClipboardEditPacket(int hotbarSlot,@Nullable ClipboardContent clipboardContent,@Nullable BlockPos targetedBlock) implements CustomPacketPayload {
    public ClipboardEditPacket{if(targetedBlock!=null)targetedBlock=targetedBlock.immutable();if(clipboardContent!=null)clipboardContent=copy(clipboardContent);}
    public static final Type<ClipboardEditPacket> TYPE=new Type<>(Create.asResource("clipboard_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ClipboardEditPacket> STREAM_CODEC=StreamCodec.of((buffer,packet)->{buffer.writeVarInt(packet.hotbarSlot);buffer.writeBoolean(packet.clipboardContent!=null);if(packet.clipboardContent!=null)ClipboardContent.STREAM_CODEC.encode(buffer,packet.clipboardContent);buffer.writeBoolean(packet.targetedBlock!=null);if(packet.targetedBlock!=null)buffer.writeBlockPos(packet.targetedBlock);},buffer->new ClipboardEditPacket(buffer.readVarInt(),buffer.readBoolean()?ClipboardContent.STREAM_CODEC.decode(buffer):null,buffer.readBoolean()?buffer.readBlockPos():null));
    public boolean handle(ServerPlayer player){
        if(player.level().getServer().getPlayerList().getPlayer(player.getUUID())!=player||player.isSpectator()||!player.mayBuild()||hotbarSlot<0||hotbarSlot>8)return false;
        var processed=clipboardProcessor(clipboardContent);if(clipboardContent!=null&&processed==null)return false;
        if(targetedBlock!=null){if(!LoadedLevelAccess.isLoaded(player.level(),targetedBlock)||!player.isWithinBlockInteractionRange(targetedBlock,20)||!player.level().mayInteract(player,targetedBlock)||!(com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(player.level(),targetedBlock) instanceof ClipboardBlockEntity owner))return false;var old=owner.components().get(AllDataComponents.CLIPBOARD_CONTENT);processed=validated(old,processed);if(clipboardContent!=null&&processed==null||old!=null&&old.readOnly()&&processed==null)return false;var map=new PatchedDataComponentMap(owner.components());if(processed==null)map.remove(AllDataComponents.CLIPBOARD_CONTENT);else map.set(AllDataComponents.CLIPBOARD_CONTENT,processed);owner.setComponents(map);owner.onEditedBy(player);return true;}
        if(player.getInventory().getSelectedSlot()!=hotbarSlot)return false;var stack=player.getInventory().getItem(hotbarSlot);if(!stack.is(AllBlocks.CLIPBOARD.asItem()))return false;var old=stack.get(AllDataComponents.CLIPBOARD_CONTENT);processed=validated(old,processed);if(clipboardContent!=null&&processed==null||old!=null&&old.readOnly()&&processed==null)return false;if(processed==null)stack.remove(AllDataComponents.CLIPBOARD_CONTENT);else stack.set(AllDataComponents.CLIPBOARD_CONTENT,processed);player.getInventory().setChanged();return true;
    }
    private static ClipboardContent validated(ClipboardContent old,ClipboardContent next){
        if(next==null)return null;
        if(old!=null&&old.readOnly()){
            if(!next.readOnly()||next.type()!=old.type()||next.pages().size()!=old.pages().size())return null;
            for(int page=0;page<old.pages().size();page++){var a=old.pages().get(page);var b=next.pages().get(page);if(a.size()!=b.size())return null;for(int entry=0;entry<a.size();entry++){var x=a.get(entry);var y=b.get(entry);if(!x.text.equals(y.text)||!ItemStack.matches(x.icon,y.icon)||x.itemAmount!=y.itemAmount)return null;}}
        }
        return new ClipboardContent(next.type(),next.pages(),old!=null&&old.readOnly(),Math.clamp(next.previouslyOpenedPage(),0,Math.max(0,next.pages().size()-1)),old==null?Optional.empty():old.copiedValues().map(net.minecraft.nbt.CompoundTag::copy));
    }
    @Nullable public static ClipboardContent clipboardProcessor(@Nullable ClipboardContent content){if(content==null)return null;for(var page:content.pages())for(var entry:page)if(SchematicNbt.hasClick(entry.text))return null;return copy(content);}
    private static ClipboardContent copy(ClipboardContent content){return new ClipboardContent(content.type(),content.pages().stream().map(page->page.stream().map(entry->new ClipboardEntry(entry.checked,entry.text.copy()).displayItem(entry.icon.copy(),entry.itemAmount)).toList()).toList(),content.readOnly(),content.previouslyOpenedPage(),content.copiedValues().map(net.minecraft.nbt.CompoundTag::copy));}
    @Override public Type<ClipboardEditPacket> type(){return TYPE;}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(TYPE,STREAM_CODEC);ServerPlayNetworking.registerGlobalReceiver(TYPE,(packet,context)->packet.handle(context.player()));}
}
