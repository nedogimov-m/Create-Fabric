package com.simibubi.create.content.kinetics.speedController;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class SpeedControllerBlock extends HorizontalAxisKineticBlock {
    private static final VoxelShape SHAPE=Shapes.or(box(0,0,0,16,4,16),box(1,1,1,15,13,15),box(0,8,0,16,14,16));
    public SpeedControllerBlock(Properties properties){super(properties);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var above=LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos().above());
        if(above.getBlock() instanceof CogWheelBlock cog&&cog.isLargeCog()&&cog.getRotationAxis(above).isHorizontal())return defaultBlockState().setValue(HORIZONTAL_AXIS,cog.getRotationAxis(above)==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X);
        return super.getStateForPlacement(context);
    }
    public static boolean connectsToCog(BlockState controller,BlockState cog){return controller.is(AllBlocks.ROTATION_SPEED_CONTROLLER)&&cog.getBlock() instanceof CogWheelBlock wheel&&wheel.isLargeCog()&&wheel.getRotationAxis(cog).isHorizontal()&&wheel.getRotationAxis(cog)!=controller.getValue(HORIZONTAL_AXIS);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SpeedControllerBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.ROTATION_SPEED_CONTROLLER,com.simibubi.create.content.kinetics.base.KineticBlockEntity::tick);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return SHAPE;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;
        if(!stack.is(AllBlocks.LARGE_COGWHEEL.asItem()))return InteractionResult.TRY_WITH_EMPTY_HAND;
        var above=pos.above();if(!LoadedLevelAccess.blockState(level,above).canBeReplaced())return InteractionResult.PASS;
        var axis=getRotationAxis(state)==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X;
        var context=new BlockPlaceContext(new UseOnContext(player,hand,hit)){
            @Override public BlockPos getClickedPos(){return above;}
            @Override public Direction getClickedFace(){return Direction.get(Direction.AxisDirection.POSITIVE,axis);}
            @Override public boolean replacingClickedOnBlock(){return true;}
        };
        return ((BlockItem)stack.getItem()).place(context);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.testHit(state,hit)?com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.interact(level,pos,player,hit.getDirection()):InteractionResult.PASS;}
}
