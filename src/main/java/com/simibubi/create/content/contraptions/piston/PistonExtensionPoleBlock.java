package com.simibubi.create.content.contraptions.piston;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class PistonExtensionPoleBlock extends DirectionalBlock implements SimpleWaterloggedBlock {
    private static Properties properties(Properties p,boolean head){p.strength(1.5f).sound(head?SoundType.STONE:SoundType.SCAFFOLDING).noOcclusion().pushReaction(PushReaction.PUSH_PULL);return head?p:p.forceSolidOn();}
    public PistonExtensionPoleBlock(Properties p){this(p,false);}
    protected PistonExtensionPoleBlock(Properties p,boolean head){super(properties(p,head));registerDefaultState(defaultBlockState().setValue(FACING,Direction.UP).setValue(BlockStateProperties.WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,BlockStateProperties.WATERLOGGED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace().getOpposite()).setValue(BlockStateProperties.WATERLOGGED,LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos()).getFluidState().getType()==Fluids.WATER);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return PistonShapes.pole(state.getValue(FACING).getAxis());}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(BlockStateProperties.WATERLOGGED)?Fluids.WATER.getSource(false):Fluids.EMPTY.defaultFluidState();}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){if(state.getValue(BlockStateProperties.WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isShiftKeyDown()||player.isSpectator()||!player.mayBuild()||!stack.is(com.simibubi.create.AllBlocks.PISTON_EXTENSION_POLE.asItem())||!(stack.getItem() instanceof BlockItem item))return InteractionResult.TRY_WITH_EMPTY_HAND;
        var axis=state.getValue(FACING).getAxis();var preferred=hit.getDirection();
        if(preferred.getAxis()!=axis){var offset=hit.getLocation().subtract(Vec3.atCenterOf(pos));preferred=Direction.get(axis.choose(offset.x,offset.y,offset.z)>=0?Direction.AxisDirection.POSITIVE:Direction.AxisDirection.NEGATIVE,axis);}
        for(var side:new Direction[]{preferred,preferred.getOpposite()})for(int distance=1;distance<=16;distance++){
            var target=pos.relative(side,distance);if(!LoadedLevelAccess.isLoaded(level,target))break;var sampled=LoadedLevelAccess.blockState(level,target);
            if(MechanicalPistonBlock.isExtensionPole(sampled)&&sampled.getValue(FACING).getAxis()==axis)continue;
            if(!sampled.canBeReplaced()||!level.mayInteract(player,target)||!player.mayUseItemAt(target,side,stack))break;
            var previous=target.relative(side.getOpposite());var actualHit=new BlockHitResult(Vec3.atCenterOf(previous).add(side.getUnitVec3().scale(.5)),side,previous,false);
            var result=item.place(new BlockPlaceContext(new UseOnContext(player,hand,actualHit)));
            if(result.consumesAction()&&!level.isClientSide()){
                var placed=LoadedLevelAccess.blockState(level,target);if(MechanicalPistonBlock.isExtensionPole(placed))level.setBlockAndUpdate(target,placed.setValue(FACING,state.getValue(FACING)));
            }return result;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){PistonStructure.destroyConnectedParts(level,pos,state,player);return super.playerWillDestroy(level,pos,state,player);}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.setValue(FACING,mirror.mirror(state.getValue(FACING)));}
}
