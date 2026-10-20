package com.simibubi.create.content.redstone.displayLink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.TagValueOutput;

/** Real source two-click selection/placement with native item persistence and ordinary BlockItem debit. */
public abstract class ClickToLinkBlockItem extends BlockItem {
    protected ClickToLinkBlockItem(Block block,Properties properties){super(block,properties);}
    public record ClickToLinkData(BlockPos selectedPos,Identifier selectedDim){
        public static final Codec<ClickToLinkData> CODEC=RecordCodecBuilder.create(instance->instance.group(BlockPos.CODEC.fieldOf("selected_pos").forGetter(ClickToLinkData::selectedPos),Identifier.CODEC.fieldOf("selected_dim").forGetter(ClickToLinkData::selectedDim)).apply(instance,ClickToLinkData::new));
        public static final StreamCodec<ByteBuf,ClickToLinkData> STREAM_CODEC=StreamCodec.composite(BlockPos.STREAM_CODEC,ClickToLinkData::selectedPos,Identifier.STREAM_CODEC,ClickToLinkData::selectedDim,ClickToLinkData::new);
        public ClickToLinkData{selectedPos=selectedPos.immutable();}
    }
    public abstract int getMaxDistanceFromSelection();
    public abstract String getMessageTranslationKey();
    public boolean isValidTarget(LevelAccessor level,BlockPos pos){return true;}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();var pos=context.getClickedPos();var stack=context.getItemInHand();if(player==null||player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos)||!LoadedLevelAccess.isLoaded(level,pos))return InteractionResult.FAIL;
        var selection=stack.get(AllDataComponents.CLICK_TO_LINK_DATA);if(player.isShiftKeyDown()&&selection!=null){if(!level.isClientSide()){stack.remove(AllDataComponents.CLICK_TO_LINK_DATA);stack.remove(DataComponents.BLOCK_ENTITY_DATA);player.sendOverlayMessage(Component.translatable("create."+getMessageTranslationKey()+".clear"));}return InteractionResult.SUCCESS;}
        if(selection==null){if(!isValidTarget(level,pos))return InteractionResult.FAIL;if(!level.isClientSide()){stack.set(AllDataComponents.CLICK_TO_LINK_DATA,new ClickToLinkData(pos,level.dimension().identifier()));player.sendOverlayMessage(Component.translatable("create."+getMessageTranslationKey()+".set"));}return InteractionResult.SUCCESS;}
        var placement=new BlockPlaceContext(context);var placed=placement.getClickedPos();if(!LoadedLevelAccess.isLoaded(level,placed)||!level.mayInteract(player,placed)||!selection.selectedDim.equals(level.dimension().identifier())||!selection.selectedPos.closerThan(placed,getMaxDistanceFromSelection())){if(!level.isClientSide())player.sendOverlayMessage(Component.translatable("create."+getMessageTranslationKey()+".too_far"));return InteractionResult.FAIL;}
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,level.registryAccess());output.store("TargetOffset",BlockPos.CODEC,selection.selectedPos.subtract(placed));output.putString("TargetDimension",selection.selectedDim.toString());BlockItem.setBlockEntityData(stack,com.simibubi.create.AllBlockEntityTypes.DISPLAY_LINK,output);var result=super.useOn(context);
        if(!level.isClientSide()&&result.consumesAction()){var remaining=player.getItemInHand(context.getHand());if(!remaining.isEmpty()){remaining.remove(AllDataComponents.CLICK_TO_LINK_DATA);remaining.remove(DataComponents.BLOCK_ENTITY_DATA);}player.sendOverlayMessage(Component.translatable("create."+getMessageTranslationKey()+".success"));}return result;
    }
    private static boolean registered;
    public static void register(){if(registered)return;registered=true;com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{var stack=player.getItemInHand(hand);if(!(stack.getItem()instanceof ClickToLinkBlockItem item)||LoadedLevelAccess.blockState(level,hit.getBlockPos()).is(item.getBlock()))return InteractionResult.PASS;return item.useOn(new UseOnContext(player,hand,hit));});}
}
