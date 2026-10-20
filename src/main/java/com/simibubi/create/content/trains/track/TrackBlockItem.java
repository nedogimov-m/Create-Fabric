package com.simibubi.create.content.trains.track;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Real track selection component and native use dispatch; the server recalculates source geometry. */
public final class TrackBlockItem extends BlockItem {
    public TrackBlockItem(Block block,Properties properties){super(block,properties);}
    @Override public boolean isFoil(ItemStack stack){return stack.has(AllDataComponents.TRACK_CONNECTING_FROM);}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        var stack=player.getItemInHand(hand);if(player.isShiftKeyDown()&&isFoil(stack)){clearSelection(stack,level,player);return InteractionResult.SUCCESS;}return super.use(level,player,hand);
    }
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var player=context.getPlayer();var stack=context.getItemInHand();var pos=context.getClickedPos();
        if(player==null||context.getHand()!=InteractionHand.MAIN_HAND)return super.useOn(context);
        if(!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.FAIL;
        if(!LoadedLevelAccess.isLoaded(level,pos))return InteractionResult.FAIL;
        var state=LoadedLevelAccess.blockState(level,pos);
        if(!isFoil(stack)){
            if(state.getBlock() instanceof ITrackBlock track){
                var owner=TrackBlockEntityAccess.existing(level,pos);
                if(state.getOptionalValue(TrackBlock.HAS_BE).orElse(false)&&!(owner instanceof TrackBlockEntity))return InteractionResult.FAIL;
                if(track.getTrackAxes(level,pos,state).size()!=1||track.getTrackAxes(level,pos,state).getFirst().lengthSqr()<1e-12||state.getValue(TrackBlock.SHAPE).isPortal()||(owner instanceof TrackBlockEntity be&&be.isTilted()))return InteractionResult.FAIL;
                if(!level.isClientSide())select(level,pos,player.getLookAngle(),stack);
                level.playSound(player,pos,SoundEvents.ITEM_FRAME_ADD_ITEM,SoundSource.BLOCKS,.75f,1);return InteractionResult.SUCCESS;
            }
            return super.useOn(context);
        }
        if(player.isShiftKeyDown()){clearSelection(stack,level,player);return InteractionResult.SUCCESS;}
        if(!(state.getBlock() instanceof ITrackBlock)){
            var placement=updatePlacementContext(new BlockPlaceContext(context));if(placement==null)return InteractionResult.FAIL;
            pos=placement.getClickedPos();if(!LoadedLevelAccess.isLoaded(level,pos))return InteractionResult.FAIL;
            state=getPlacementState(placement);if(state==null)return InteractionResult.FAIL;
        }
        boolean girder=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(player.getOffhandItem().getItem()).equals(com.simibubi.create.Create.asResource("metal_girder"));
        boolean extend=player.isSprinting()||stack.getOrDefault(AllDataComponents.TRACK_EXTENDED_CURVE,false);
        stack.remove(AllDataComponents.TRACK_EXTENDED_CURVE);
        var info=TrackPlacement.tryConnect(level,player,pos,state,stack,girder,extend);
        if(!info.valid){if(!level.isClientSide()&&info.message!=null)player.sendOverlayMessage(Component.translatable(info.message));return InteractionResult.FAIL;}
        if(!level.isClientSide()){player.getMainHandItem().remove(AllDataComponents.TRACK_CONNECTING_FROM);level.playSound(null,pos,state.getSoundType().getPlaceSound(),SoundSource.BLOCKS,1,.8f);}
        return InteractionResult.SUCCESS;
    }
    public static boolean select(Level level,BlockPos pos,Vec3 look,ItemStack stack){
        if(!LoadedLevelAccess.isLoaded(level,pos))return false;var state=LoadedLevelAccess.blockState(level,pos);
        if(!(state.getBlock() instanceof ITrackBlock track)||track.getTrackAxes(level,pos,state).size()!=1||track.getTrackAxes(level,pos,state).getFirst().lengthSqr()<1e-12)return false;
        var owner=TrackBlockEntityAccess.existing(level,pos);
        if((state.getOptionalValue(TrackBlock.HAS_BE).orElse(false)&&!(owner instanceof TrackBlockEntity))||(owner instanceof TrackBlockEntity be&&be.isTilted()))return false;
        var nearest=track.getNearestTrackAxis(level,pos,state,look);var axis=nearest.getFirst().scale(nearest.getSecond()==AxisDirection.POSITIVE?-1:1);
        stack.set(AllDataComponents.TRACK_CONNECTING_FROM,new TrackPlacement.ConnectingFrom(pos.immutable(),axis,track.getUpNormal(level,pos,state).normalize(),track.getCurveStart(level,pos,state,axis),level.dimension()));return true;
    }
    private static void clearSelection(ItemStack stack,Level level,Player player){if(!level.isClientSide()){stack.remove(AllDataComponents.TRACK_CONNECTING_FROM);player.sendOverlayMessage(Component.translatable("create.track.selection_cleared"));}level.playSound(player,player.blockPosition(),SoundEvents.ITEM_FRAME_REMOVE_ITEM,SoundSource.BLOCKS,.75f,1);}
}
