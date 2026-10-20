package com.simibubi.create.content.logistics.itemHatch;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringSettings;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
/** Source forced block interaction while sneaking, without bypassing the actual filter/protection path. */
public final class ItemHatchHandler {
 public static void register(){net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB,com.simibubi.create.Create.asResource("base"))).register(output->{output.accept(com.simibubi.create.AllBlocks.ITEM_HATCH);output.accept(com.simibubi.create.AllItems.CARDBOARD_SWORD);});com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{var pos=hit.getBlockPos();var state=LoadedLevelAccess.blockState(level,pos);if(!(state.getBlock() instanceof ItemHatchBlock hatch))return InteractionResult.PASS;var filtering=FilteringSettings.interact(level,player,hand,hit);return filtering!=InteractionResult.PASS?filtering:hatch.deposit(player.getItemInHand(hand),state,level,pos,player);});}
 private ItemHatchHandler(){}
}
