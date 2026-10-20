package com.simibubi.create.content.decoration.girder;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.chute.AbstractChuteBlock;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.content.decoration.placard.PlacardBlock;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.content.trains.display.FlapDisplayBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Real source pole/beam/cross and native neighbor rules for vanilla, girders, chute and actual girdable tracks. */
public final class GirderBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty X=BooleanProperty.create("x"),Z=BooleanProperty.create("z"),TOP=BooleanProperty.create("top"),BOTTOM=BooleanProperty.create("bottom"),WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Direction.Axis> AXIS=BlockStateProperties.AXIS;
    public static final TagKey<Block> GIRDABLE_TRACKS=TagKey.create(Registries.BLOCK,Create.asResource("girdable_tracks"));
    public GirderBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(AXIS,Direction.Axis.Y).setValue(X,false).setValue(Z,false).setValue(TOP,false).setValue(BOTTOM,false).setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(AXIS,X,Z,TOP,BOTTOM,WATERLOGGED);}
    public static boolean available(BlockGetter level,BlockPos pos){return !(level instanceof Level world)||LoadedLevelAccess.isLoaded(world,pos);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(X)?state.getValue(Z)?GirderShapes.CROSS:GirderShapes.X:state.getValue(Z)?GirderShapes.Z:GirderShapes.POLE;}
    @Override protected VoxelShape getBlockSupportShape(BlockState state,BlockGetter level,BlockPos pos){return Shapes.or(getShape(state,level,pos,CollisionContext.empty()),GirderShapes.POLE);}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var pos=context.getClickedPos();var level=context.getLevel();var axis=context.getClickedFace().getAxis();var state=defaultBlockState().setValue(AXIS,axis).setValue(X,axis==Direction.Axis.X).setValue(Z,axis==Direction.Axis.Z);
        for(var side:Direction.values())state=updateState(level,pos,state,side);return state.setValue(WATERLOGGED,LoadedLevelAccess.blockState(level,pos).getFluidState().getType()==Fluids.WATER);
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction direction,BlockPos next,BlockState neighbor,RandomSource random){
        if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));var axis=direction.getAxis();
        if(!available(level,next))return state;
        if(axis!=Direction.Axis.Y&&state.getValue(AXIS)!=axis){if(available(level,pos.relative(direction.getOpposite()))&&!isConnected(level,pos,state,direction)&&!isConnected(level,pos,state,direction.getOpposite()))state=state.setValue(axis==Direction.Axis.X?X:Z,false);}
        else if(axis==Direction.Axis.Y&&state.getValue(AXIS)!=Direction.Axis.Y){
            if(available(level,pos.above())&&LoadedLevelAccess.blockState(level,pos.above()).getBlockSupportShape(level,pos.above()).isEmpty())state=state.setValue(TOP,false);
            if(available(level,pos.below())&&LoadedLevelAccess.blockState(level,pos.below()).getBlockSupportShape(level,pos.below()).isEmpty())state=state.setValue(BOTTOM,false);
        }
        for(var side:Direction.values())if(side.getAxis()==axis)state=updateState(level,pos,state,side);return state;
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(available(level,pos))Block.updateOrDestroy(state,Block.updateFromNeighbourShapes(state,level,pos),level,pos,Block.UPDATE_ALL);}
    public static BlockState updateState(LevelReader level,BlockPos pos,BlockState state,Direction side){
        var next=pos.relative(side);if(!available(level,next))return state;var axis=side.getAxis();var property=axis==Direction.Axis.X?X:axis==Direction.Axis.Z?Z:side==Direction.UP?TOP:BOTTOM;var neighbor=LoadedLevelAccess.blockState(level,next);
        if(axis==Direction.Axis.Y)return updateVerticalProperty(level,pos,state,property,neighbor,side);
        if(state.getValue(AXIS)==axis||neighbor.getBlock()instanceof GirderEncasedShaftBlock&&neighbor.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS)!=axis||neighbor.getBlock()==state.getBlock()&&neighbor.getValue(property)||neighbor.getBlock()instanceof NixieTubeBlock&&NixieTubeBlock.getFacing(neighbor)==side||neighbor.getBlock()instanceof PlacardBlock&&PlacardBlock.connectedDirection(neighbor)==side)state=state.setValue(property,true);
        for(var cross:Direction.Plane.HORIZONTAL)if(cross.getAxis()!=axis){var trackPos=pos.above().relative(cross);if(!available(level,trackPos))continue;var track=LoadedLevelAccess.blockState(level,trackPos);if(track.is(GIRDABLE_TRACKS)&&track.hasProperty(TrackBlock.SHAPE)&&track.getValue(TrackBlock.SHAPE)==(axis==Direction.Axis.X?TrackShape.XO:TrackShape.ZO))state=state.setValue(property,true);}
        return state;
    }
    public static BlockState updateVerticalProperty(LevelReader level,BlockPos pos,BlockState state,Property<Boolean> property,BlockState neighbor,Direction side){
        boolean attach=state.hasProperty(AXIS)&&state.getValue(AXIS)==Direction.Axis.Y||isGirder(neighbor)||neighbor.hasProperty(WallBlock.UP)&&neighbor.getValue(WallBlock.UP)
                ||neighbor.getBlock()instanceof NixieTubeBlock&&NixieTubeBlock.getFacing(neighbor)==side
                ||neighbor.getBlock()instanceof FlapDisplayBlock
                ||neighbor.getBlock()instanceof LanternBlock&&(side==Direction.DOWN)==neighbor.getValue(LanternBlock.HANGING)
                ||neighbor.getBlock()instanceof ChainBlock&&neighbor.getValue(BlockStateProperties.AXIS)==Direction.Axis.Y;
        var face=BlockStateProperties.ATTACH_FACE;if(!attach&&neighbor.hasProperty(face))attach=neighbor.getValue(face)==AttachFace.CEILING&&side==Direction.DOWN||neighbor.getValue(face)==AttachFace.FLOOR&&side==Direction.UP;
        return attach?state.setValue(property,true):state;
    }
    public static boolean isGirder(BlockState state){return state.getBlock()instanceof GirderBlock||state.getBlock()instanceof GirderEncasedShaftBlock;}
    public static boolean isXGirder(BlockState state){return state.getBlock()instanceof GirderBlock&&state.getValue(X)||state.getBlock()instanceof GirderEncasedShaftBlock&&state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS)==Direction.Axis.Z;}
    public static boolean isZGirder(BlockState state){return state.getBlock()instanceof GirderBlock&&state.getValue(Z)||state.getBlock()instanceof GirderEncasedShaftBlock&&state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS)==Direction.Axis.X;}
    public static boolean isConnected(BlockGetter level,BlockPos pos,BlockState state,Direction side){
        var axis=side.getAxis();if(axis==Direction.Axis.Y)return false;if(state.getBlock()instanceof GirderBlock&&!state.getValue(axis==Direction.Axis.X?X:Z)||state.getBlock()instanceof GirderEncasedShaftBlock&&state.getValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS)==axis)return false;
        var next=pos.relative(side);if(!available(level,next))return false;var other=LoadedLevelAccess.blockState(level,next);if(other.isAir())return false;if(other.getBlock()instanceof NixieTubeBlock&&NixieTubeBlock.getFacing(other)==side||other.getBlock()instanceof PlacardBlock&&PlacardBlock.connectedDirection(other)==side)return true;var shape=other.getShape(level,next);
        return !shape.isEmpty()&&(Block.isFaceFull(shape,side.getOpposite())&&other.isSolid()||AbstractChuteBlock.getChuteFacing(other)==Direction.DOWN);
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(stack.is(AllBlocks.SHAFT.asItem())){
            if(!level.isClientSide()){
                var next=AllBlocks.METAL_GIRDER_ENCASED_SHAFT.defaultBlockState().setValue(WATERLOGGED,state.getValue(WATERLOGGED)).setValue(TOP,state.getValue(TOP)).setValue(BOTTOM,state.getValue(BOTTOM)).setValue(GirderEncasedShaftBlock.HORIZONTAL_AXIS,state.getValue(X)||hit.getDirection().getAxis()==Direction.Axis.Z?Direction.Axis.Z:Direction.Axis.X);
                if(!level.setBlock(pos,next,Block.UPDATE_ALL))return InteractionResult.FAIL;level.playSound(null,pos,SoundEvents.NETHERITE_BLOCK_HIT,SoundSource.BLOCKS,.5f,1.25f);if(!player.isCreative())stack.shrink(1);
            }return InteractionResult.SUCCESS;
        }
        if(stack.is(AllItems.WRENCH)&&!player.isShiftKeyDown())return GirderWrenchBehavior.handleClick(level,pos,state,hit)?InteractionResult.SUCCESS:InteractionResult.FAIL;
        if(stack.is(AllBlocks.METAL_GIRDER.asItem()))return GirderPlacementHelper.place(player,hand,level,state,pos,hit);
        return InteractionResult.PASS;
    }
    public InteractionResult onWrenched(BlockState state,UseOnContext context){return InteractionResult.PASS;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){
        state=state.setValue(AXIS,rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE,state.getValue(AXIS))).getAxis());
        if(rotation.rotate(Direction.EAST).getAxis()==Direction.Axis.X)return state;
        // Pinned6.0.10 source sets X from Z, then retains Z. Preserve this quirk explicitly.
        return state.setValue(X,state.getValue(Z)).setValue(Z,state.getValue(Z));
    }
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state;}
}
