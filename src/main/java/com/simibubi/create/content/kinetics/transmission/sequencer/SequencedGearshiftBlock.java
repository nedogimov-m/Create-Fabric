package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

public final class SequencedGearshiftBlock extends HorizontalAxisKineticBlock implements TransformableBlock {
    public static final BooleanProperty VERTICAL=BooleanProperty.create("vertical");
    public static final IntegerProperty STATE=IntegerProperty.create("state",0,5);
    public SequencedGearshiftBlock(Properties properties){super(properties.noOcclusion().isRedstoneConductor((state,level,pos)->false));registerDefaultState(defaultBlockState().setValue(VERTICAL,false).setValue(STATE,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(VERTICAL,STATE);}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(VERTICAL)?Direction.Axis.Y:state.getValue(HORIZONTAL_AXIS);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new SequencedGearshiftBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.SEQUENCED_GEARSHIFT,SequencedGearshiftBlockEntity::tick);}
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved){super.onPlace(state,level,pos,previous,moved);if(level instanceof ServerLevel server&&!previous.is(this))server.scheduleTick(pos,this,1);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,neighbor,orientation,moved);if(level instanceof ServerLevel server)server.scheduleTick(pos,this,1);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(LoadedLevelAccess.blockEntity(level,pos)instanceof SequencedGearshiftBlockEntity entity)entity.onRedstoneUpdate(LoadedLevelAccess.getBestNeighborSignal(level,pos)>0,state.getValue(STATE)!=0);}
    @Override protected boolean hasAnalogOutputSignal(BlockState state){return true;}
    @Override protected int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos,Direction side){return state.getValue(STATE);}
    private InteractionResult open(Level level,BlockPos pos,Player player){if(player.isSpectator()||!player.mayBuild()||!level.mayInteract(player,pos))return InteractionResult.PASS;if(LoadedLevelAccess.blockEntity(level,pos)instanceof SequencedGearshiftBlockEntity entity)SequencedGearshiftMenu.open(player,entity);return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return open(level,pos,player);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(stack.is(AllItems.WRENCH)||stack.getItem()instanceof BlockItem item&&item.getBlock()instanceof KineticBlock&&hasShaftTowards(level,pos,state,hit.getDirection()))return InteractionResult.PASS;
        return open(level,pos,player);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        Direction.Axis preferred=null;
        for(var direction:Direction.values()){
            var pos=context.getClickedPos().relative(direction);var neighbor=LoadedLevelAccess.blockState(context.getLevel(),pos);
            if(neighbor.getBlock()instanceof IRotate rotate&&rotate.hasShaftTowards(context.getLevel(),pos,neighbor,direction.getOpposite())){
                if(preferred!=null&&preferred!=direction.getAxis()){preferred=null;break;}preferred=direction.getAxis();
            }
        }
        var axis=preferred!=null&&(context.getPlayer()==null||!context.getPlayer().isShiftKeyDown())?preferred:context.getNearestLookingDirection().getAxis();
        return defaultBlockState().setValue(VERTICAL,axis==Direction.Axis.Y).setValue(HORIZONTAL_AXIS,axis==Direction.Axis.Y?context.getHorizontalDirection().getAxis():axis);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(HORIZONTAL_AXIS,rotation.rotate(Direction.get(Direction.AxisDirection.POSITIVE,state.getValue(HORIZONTAL_AXIS))).getAxis());}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state;}
    @Override public BlockState transform(BlockState state,StructureTransform transform){
        if(transform.mirror!=null)state=state.mirror(transform.mirror);
        if(transform.rotationAxis==Direction.Axis.Y)return state.rotate(transform.rotation);
        if(transform.rotation.ordinal()%2==1){if(transform.rotationAxis!=state.getValue(HORIZONTAL_AXIS))return state.cycle(VERTICAL);if(state.getValue(VERTICAL))return state.cycle(VERTICAL).cycle(HORIZONTAL_AXIS);}
        return state;
    }
    public InteractionResult onWrenched(BlockState state,UseOnContext context){
        if(context.getLevel() instanceof ServerLevel server){
            var axis=context.getClickedFace().getAxis();var rotated=state;
            if(axis==Direction.Axis.Y)rotated=state.cycle(HORIZONTAL_AXIS);
            else if(state.getValue(HORIZONTAL_AXIS)!=axis)rotated=state.cycle(VERTICAL);
            server.setBlockAndUpdate(context.getClickedPos(),rotated);
        }
        return InteractionResult.SUCCESS;
    }
}
