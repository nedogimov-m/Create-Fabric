package com.simibubi.create.content.equipment.clipboard;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Native source copy/paste interactions, including installed source frequency and timing providers. */
public final class ClipboardValueSettingsHandler {
    public static List<ClipboardCloneable> cloneables(BlockEntity owner){
        var result=new ArrayList<ClipboardCloneable>();if(owner instanceof ClipboardCloneable cloneable)result.add(cloneable);
        var link=BlockEntityBehaviour.get(owner,com.simibubi.create.content.redstone.link.LinkBehaviour.TYPE);if(link!=null)result.add(link);
        if(owner instanceof com.simibubi.create.content.redstone.diodes.BrassDiodeBlockEntity diode)result.add(new ClipboardCloneable(){
            @Override public String getClipboardKey(){return "Timings";}
            @Override public boolean writeToClipboard(HolderLookup.Provider registries,CompoundTag tag,Direction side){var settings=com.simibubi.create.content.redstone.diodes.BrassDiodeSettings.ValueSetting.fromTicks(diode.getMaxState());tag.putInt("Row",settings.row());tag.putInt("Value",settings.value());return true;}
            @Override public boolean readFromClipboard(HolderLookup.Provider registries,CompoundTag tag,Player player,Direction side,boolean simulate){int row=tag.getIntOr("Row",-1),value=tag.getIntOr("Value",-1);if(row<0||row>2||value<0||value>60)return false;if(!simulate)diode.setMaxState(new com.simibubi.create.content.redstone.diodes.BrassDiodeSettings.ValueSetting(row,value).ticks());return true;}
        });
        if(owner instanceof com.simibubi.create.foundation.blockEntity.SmartBlockEntity smart)
            for(var behaviour:smart.getAllBehaviours())
                if(behaviour instanceof ClipboardCloneable cloneable && !result.contains(cloneable)) result.add(cloneable);
        ClipboardSettingsAdapters.add(owner,result);
        return result;
    }
    public static InteractionResult interact(Player player,InteractionHand hand,BlockPos pos,Direction side,boolean paste){
        var level=player.level();var held=player.getItemInHand(hand);if(hand!=InteractionHand.MAIN_HAND||!held.is(AllBlocks.CLIPBOARD.asItem())||player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||!LoadedLevelAccess.isLoaded(level,pos)||!player.isWithinBlockInteractionRange(pos,1)||!level.mayInteract(player,pos))return InteractionResult.PASS;
        var owner=com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,pos);if(owner==null)return InteractionResult.PASS;var content=held.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT,ClipboardContent.EMPTY);
        if(owner instanceof ClipboardBlockEntity clipboard){if(!level.isClientSide()){
            var pages=ClipboardEntry.readAll(content);var names=new HashSet<String>();for(var page:pages)for(var entry:page)names.add(entry.text.getString());boolean added=false;
            for(var page:ClipboardEntry.readAll(clipboard.components()))for(var entry:page)if(names.add(entry.text.getString())){List<ClipboardEntry> destination=null;for(var candidate:pages)if(candidate.size()<12){destination=candidate;break;}if(destination==null){destination=new ArrayList<>();pages.add(destination);}destination.add(new ClipboardEntry(entry.checked,entry.text.copy()));added=true;}
            held.set(AllDataComponents.CLIPBOARD_CONTENT,content.setPages(pages).setType(added?ClipboardType.WRITTEN:content.type()));player.getInventory().setChanged();player.sendOverlayMessage(Component.translatable("create.clipboard.copied_from_clipboard",owner.getBlockState().getBlock().getName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN));
        }return InteractionResult.SUCCESS;}
        if(!paste&&owner instanceof com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity port){
            if(!level.isClientSide()&&!port.addressFilter.isBlank()){
                var pages=ClipboardEntry.readAll(content);var text="#"+port.addressFilter;boolean exists=pages.stream().flatMap(List::stream).anyMatch(entry->entry.text.getString().equals(text)||entry.text.getString().equals("# "+port.addressFilter));
                if(!exists){var page=pages.stream().filter(candidate->candidate.size()<12).findFirst().orElseGet(()->{var added=new ArrayList<ClipboardEntry>();pages.add(added);return added;});page.add(new ClipboardEntry(false,Component.literal(text)));held.set(AllDataComponents.CLIPBOARD_CONTENT,content.setPages(pages).setType(ClipboardType.WRITTEN));player.getInventory().setChanged();player.sendOverlayMessage(Component.translatable("create.clipboard.address_added",port.addressFilter));}
            }
            return InteractionResult.SUCCESS;
        }
        var providers=cloneables(owner);if(providers.isEmpty())return InteractionResult.PASS;var values=paste?content.copiedValues().map(CompoundTag::copy).orElse(null):new CompoundTag();if(values==null)return InteractionResult.PASS;boolean success=false;
        for(var provider:providers){if(paste)success|=provider.readFromClipboard(level.registryAccess(),values.getCompoundOrEmpty(provider.getClipboardKey()),player,side,level.isClientSide());else{var tag=new CompoundTag();if(provider.writeToClipboard(level.registryAccess(),tag,side)){success=true;values.put(provider.getClipboardKey(),tag);}}}
        if(!level.isClientSide()&&success){if(!paste){held.set(AllDataComponents.CLIPBOARD_CONTENT,content.setType(ClipboardType.WRITTEN).setCopiedValues(values));player.getInventory().setChanged();}player.sendOverlayMessage(Component.translatable(paste?"create.clipboard.pasted_to":"create.clipboard.copied_from",owner.getBlockState().getBlock().getName().copy().withStyle(ChatFormatting.WHITE)).withStyle(ChatFormatting.GREEN));}return InteractionResult.SUCCESS;
    }
    public static void register(){com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->interact(player,hand,hit.getBlockPos(),hit.getDirection(),false));AttackBlockCallback.EVENT.register((player,level,hand,pos,side)->interact(player,hand,pos,side,true));}
    private ClipboardValueSettingsHandler(){}
}
