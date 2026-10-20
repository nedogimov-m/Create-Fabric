package com.simibubi.create.content.equipment.clipboard;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.equipment.clipboard.ClipboardOverrides.ClipboardType;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class ClipboardBlockItem extends BlockItem implements com.simibubi.create.foundation.recipe.ItemCopyingRecipe.SupportsItemCopying {
    public static java.util.function.BiConsumer<Player,net.minecraft.core.BlockPos> CLIENT_OPEN;
    public ClipboardBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context){var player=context.getPlayer();if(player==null)return InteractionResult.PASS;return player.isShiftKeyDown()?super.useOn(context):use(context.getLevel(),player,context.getHand());}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;var stack=player.getItemInHand(hand);player.getCooldowns().addCooldown(stack,10);var content=stack.getOrDefault(AllDataComponents.CLIPBOARD_CONTENT,ClipboardContent.EMPTY);if(level.isClientSide()&&CLIENT_OPEN!=null)CLIENT_OPEN.accept(player,null);if(!content.readOnly())stack.set(AllDataComponents.CLIPBOARD_CONTENT,content.setType(ClipboardType.EDITING));return InteractionResult.SUCCESS;}
    @Override public net.minecraft.core.component.DataComponentType<?> getComponentType(){return AllDataComponents.CLIPBOARD_CONTENT;}
}
