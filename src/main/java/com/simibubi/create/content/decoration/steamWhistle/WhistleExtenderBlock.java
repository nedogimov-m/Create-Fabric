package com.simibubi.create.content.decoration.steamWhistle;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.Locale;

public final class WhistleExtenderBlock extends Block implements com.simibubi.create.api.equipment.goggles.IProxyHoveringInformation {
    @Override public BlockPos getInformationSource(Level level,BlockPos pos,BlockState state){var root=findRoot(level,pos);return root==null?pos:root;}
    public enum WhistleExtenderShape implements StringRepresentable {SINGLE,DOUBLE,DOUBLE_CONNECTED;@Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}}
    public static final EnumProperty<WhistleExtenderShape> SHAPE=EnumProperty.create("shape",WhistleExtenderShape.class);
    public static final EnumProperty<WhistleBlock.WhistleSize> SIZE=WhistleBlock.SIZE;
    public WhistleExtenderBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(SHAPE,WhistleExtenderShape.SINGLE).setValue(SIZE,WhistleBlock.WhistleSize.MEDIUM));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(SHAPE,SIZE);}
    public static BlockPos findRoot(LevelAccessor level,BlockPos pos){
        for(var current=pos.below();current.getY()>=level.getMinY();current=current.below()){
            if(level instanceof Level world&&!world.isLoaded(current))return null;
            if(!LoadedLevelAccess.blockState(level,current).is(AllBlocks.STEAM_WHISTLE_EXTENSION))return current;
        }return null;
    }
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){var below=LoadedLevelAccess.blockState(level,pos.below());return below.is(AllBlocks.STEAM_WHISTLE)||below.is(this)&&below.getValue(SHAPE)!=WhistleExtenderShape.SINGLE;}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){
        if(side==Direction.UP){boolean connected=state.getValue(SHAPE)==WhistleExtenderShape.DOUBLE_CONNECTED,above=other.is(this);if(!connected&&above)return state.setValue(SHAPE,WhistleExtenderShape.DOUBLE_CONNECTED);if(connected&&!above)return state.setValue(SHAPE,WhistleExtenderShape.DOUBLE);}
        if(side==Direction.DOWN)return !state.canSurvive(level,pos)?Blocks.AIR.defaultBlockState():state.setValue(SIZE,other.getValue(SIZE));return state;
    }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){if(!level.isClientSide()&&(!old.is(this)||old.getValue(SHAPE)!=state.getValue(SHAPE)))WhistleBlock.queuePitchUpdate(level,findRoot(level,pos));}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){WhistleBlock.queuePitchUpdate(level,findRoot(level,pos));}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!stack.is(AllBlocks.STEAM_WHISTLE.asItem())||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        var root=findRoot(level,pos);if(root==null||!LoadedLevelAccess.blockState(level,root).is(AllBlocks.STEAM_WHISTLE))return InteractionResult.PASS;if(!level.isClientSide())WhistleBlock.incrementSize(level,root);return InteractionResult.SUCCESS;
    }
    @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean data){return new ItemStack(AllBlocks.STEAM_WHISTLE);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){int inset=4-state.getValue(SIZE).ordinal();int height=switch(state.getValue(SHAPE)){case SINGLE->10;case DOUBLE->18;case DOUBLE_CONNECTED->16;};return box(inset,0,inset,16-inset,height,16-inset);}
}
