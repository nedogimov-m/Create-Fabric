package com.simibubi.create.content.redstone.link.controller;

import com.simibubi.create.*;
import com.simibubi.create.content.redstone.link.RedstoneLinkBlock;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.BiConsumer;
import net.createmod.catnip.data.Couple;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;

public final class LinkedControllerItem extends Item {
    public static final int BUTTONS=6,SLOTS=12;
    public enum ClientAction{TOGGLE,BIND}
    public static BiConsumer<ClientAction,BlockPos> CLIENT_ACTION=(action,pos)->{};
    public LinkedControllerItem(Properties properties){super(properties.stacksTo(1));}
    public static NonNullList<ItemStack> getFrequencyItems(ItemStack stack){
        if(!(stack.getItem()instanceof LinkedControllerItem))throw new IllegalArgumentException("Not a linked controller");
        var result=NonNullList.withSize(SLOTS,ItemStack.EMPTY);
        stack.getOrDefault(AllDataComponents.LINKED_CONTROLLER_ITEMS,ItemContainerContents.EMPTY).copyInto(result);
        for(int slot=0;slot<SLOTS;slot++)if(!result.get(slot).isEmpty())result.set(slot,result.get(slot).copyWithCount(1));
        return result;
    }
    public static Couple<Frequency> toFrequency(ItemStack controller,int button){
        if(button<0||button>=BUTTONS)throw new IllegalArgumentException("Invalid controller button");
        var samples=getFrequencyItems(controller);return Couple.create(Frequency.of(samples.get(button*2)),Frequency.of(samples.get(button*2+1)));
    }
    public static ItemStack heldController(Player player){
        return player.getMainHandItem().getItem()instanceof LinkedControllerItem?player.getMainHandItem():player.getOffhandItem().getItem()instanceof LinkedControllerItem?player.getOffhandItem():ItemStack.EMPTY;
    }
    public static void setFrequencyItems(ItemStack controller,List<ItemStack> items){
        if(items.size()!=SLOTS)throw new IllegalArgumentException("Linked controller requires twelve samples");
        controller.set(AllDataComponents.LINKED_CONTROLLER_ITEMS,ItemContainerContents.fromItems(items.stream().map(item->item.isEmpty()?ItemStack.EMPTY:item.copyWithCount(1)).toList()));
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);
        if(player.isShiftKeyDown()&&hand==InteractionHand.MAIN_HAND){
            if(player instanceof ServerPlayer server&&player.mayBuild())open(server,stack);
            return InteractionResult.SUCCESS;
        }
        if(!player.isShiftKeyDown()){
            if(hand==InteractionHand.OFF_HAND&&player.getMainHandItem().getItem()instanceof LinkedControllerItem)return InteractionResult.PASS;
            if(level.isClientSide())CLIENT_ACTION.accept(ClientAction.TOGGLE,null);
            player.getCooldowns().addCooldown(stack,2);
            return InteractionResult.SUCCESS;
        }return InteractionResult.PASS;
    }
    private static void open(ServerPlayer player,ItemStack stack){
        int selected=player.getInventory().getSelectedSlot();
        player.openMenu(new ExtendedMenuProvider<LinkedControllerMenu.Opening>(){
            @Override public Component getDisplayName(){return stack.getHoverName();}
            @Override public LinkedControllerMenu.Opening getScreenOpeningData(ServerPlayer ignored){return new LinkedControllerMenu.Opening(stack.copy(),selected);}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){return new LinkedControllerMenu(id,inventory,stack,selected);}
        });
    }
    public InteractionResult useBlockFirst(Player player,Level level,InteractionHand hand,BlockHitResult hit){
        if(player.isSpectator())return InteractionResult.PASS;
        var stack=player.getItemInHand(hand);var pos=hit.getBlockPos();var state=LoadedLevelAccess.blockState(level,pos);
        if(player.mayBuild()&&level.mayInteract(player,pos)){
            if(player.isShiftKeyDown()&&state.getBlock()instanceof LecternControllerBlock){
                if(!level.isClientSide()&&LoadedLevelAccess.blockEntity(level,pos)instanceof LecternControllerBlockEntity lectern)lectern.swapControllers(stack,player,hand);
                return InteractionResult.SUCCESS;
            }
            if(!player.isShiftKeyDown()){
                if(state.getBlock()instanceof RedstoneLinkBlock){
                    if(level.isClientSide())CLIENT_ACTION.accept(ClientAction.BIND,pos.immutable());
                    player.getCooldowns().addCooldown(stack,2);return InteractionResult.SUCCESS;
                }
                if(state.is(Blocks.LECTERN)&&!state.getValue(LecternBlock.HAS_BOOK)){
                    if(!level.isClientSide())LecternControllerBlock.install(level,pos,state,stack,player.isCreative());
                    return InteractionResult.SUCCESS;
                }
                if(state.getBlock()instanceof LecternControllerBlock)return InteractionResult.PASS;
            }
        }return use(level,player,hand);
    }
    @Override public InteractionResult useOn(UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:useBlockFirst(context.getPlayer(),context.getLevel(),context.getHand(),new BlockHitResult(context.getClickLocation(),context.getClickedFace(),context.getClickedPos(),context.isInside()));}
}
