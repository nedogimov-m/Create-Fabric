package com.simibubi.create.content.decoration.steamWhistle;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.Locale;

public final class WhistleBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WALL=BooleanProperty.create("wall"),POWERED=BlockStateProperties.POWERED;
    public static final EnumProperty<WhistleSize> SIZE=EnumProperty.create("size",WhistleSize.class);
    public enum WhistleSize implements StringRepresentable {SMALL,MEDIUM,LARGE;@Override public String getSerializedName(){return name().toLowerCase(Locale.ROOT);}}
    public WhistleBlock(Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(POWERED,false).setValue(WALL,false).setValue(SIZE,WhistleSize.MEDIUM));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED,WALL,SIZE);}
    public static Direction getAttachedDirection(BlockState state){return state.getValue(WALL)?state.getValue(FACING):Direction.DOWN;}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return LoadedLevelAccess.blockState(level,pos.relative(getAttachedDirection(state))).getBlock() instanceof FluidTankBlock;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var face=context.getClickedFace();boolean wall=!face.getAxis().isVertical();if(!wall)face=context.getHorizontalDirection().getOpposite();
        var state=defaultBlockState().setValue(FACING,face.getOpposite()).setValue(WALL,wall).setValue(POWERED,context.getLevel().hasNeighborSignal(context.getClickedPos()));
        return canSurvive(state,context.getLevel(),context.getClickedPos())?state:null;
    }
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new WhistleBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.STEAM_WHISTLE,WhistleBlockEntity::tick);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!stack.is(AllBlocks.STEAM_WHISTLE.asItem())||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(!level.isClientSide())incrementSize(level,pos);return InteractionResult.SUCCESS;
    }
    public static void incrementSize(LevelAccessor level,BlockPos pos){
        var base=LoadedLevelAccess.blockState(level,pos);if(!base.hasProperty(SIZE))return;var size=base.getValue(SIZE);
        for(int i=1;i<=6;i++){
            var next=pos.above(i);if(level instanceof Level world&&!world.isLoaded(next))return;var state=LoadedLevelAccess.blockState(level,next);
            int semitones=i*2-1;
            if(state.is(AllBlocks.STEAM_WHISTLE_EXTENSION)){
                if(state.getValue(WhistleExtenderBlock.SHAPE)!=WhistleExtenderBlock.WhistleExtenderShape.SINGLE)continue;
                level.setBlock(next,state.setValue(WhistleExtenderBlock.SHAPE,WhistleExtenderBlock.WhistleExtenderShape.DOUBLE),3);semitones++;
            }else{
                if(!state.canBeReplaced())return;level.setBlock(next,AllBlocks.STEAM_WHISTLE_EXTENSION.defaultBlockState().setValue(SIZE,size),3);
            }
            var sound=base.getSoundType();float volume=(sound.getVolume()+1)/2,pitch=(float)Math.pow(2,-semitones/12.0);
            level.playSound(null,next,SoundEvents.NOTE_BLOCK_XYLOPHONE.value(),SoundSource.BLOCKS,volume/4,pitch);level.playSound(null,next,sound.getHitSound(),SoundSource.BLOCKS,volume,pitch);return;
        }
    }
    public static void queuePitchUpdate(LevelAccessor level,BlockPos pos){if(pos==null||level.isClientSide())return;var state=LoadedLevelAccess.blockState(level,pos);if(state.getBlock() instanceof WhistleBlock whistle&&!level.getBlockTicks().hasScheduledTick(pos,whistle))level.scheduleTick(pos,whistle,1);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(LoadedLevelAccess.blockEntity(level,pos) instanceof WhistleBlockEntity whistle)whistle.updatePitch();}
    static void updateBoiler(Level level,BlockPos pos,BlockState state){var tank=pos.relative(getAttachedDirection(state));if(level.isLoaded(tank)&&LoadedLevelAccess.blockEntity(level,tank) instanceof FluidTankBlockEntity part)part.updateBoilerState();}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){if(level.isClientSide())return;updateBoiler(level,pos,state);if(!old.is(this)||old.getValue(SIZE)!=state.getValue(SIZE))queuePitchUpdate(level,pos);}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){updateBoiler(level,pos,state);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(!level.isClientSide()&&state.getValue(POWERED)!=level.hasNeighborSignal(pos))level.setBlock(pos,state.cycle(POWERED),2);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){return side==getAttachedDirection(state)&&!state.canSurvive(level,pos)?Blocks.AIR.defaultBlockState():state;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected boolean isPathfindable(BlockState state,net.minecraft.world.level.pathfinder.PathComputationType type){return false;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        int inset=4-state.getValue(SIZE).ordinal();var body=box(inset,3,inset,16-inset,16,16-inset);
        if(!state.getValue(WALL))return Shapes.or(body,box(1,0,1,15,3,15),box(5,0,5,11,8,11));
        var base=switch(state.getValue(FACING)){case NORTH->Shapes.or(box(1,1,0,15,15,3),box(5,5,0,11,11,8));case SOUTH->Shapes.or(box(1,1,13,15,15,16),box(5,5,8,11,11,16));case WEST->Shapes.or(box(0,1,1,3,15,15),box(0,5,5,8,11,11));case EAST->Shapes.or(box(13,1,1,16,15,15),box(8,5,5,16,11,11));default->Shapes.empty();};return Shapes.or(body,base);
    }
}
