package com.simibubi.create.content.redstone.link;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class RedstoneLinkBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING=BlockStateProperties.FACING;
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED,RECEIVER=BooleanProperty.create("receiver");
    public RedstoneLinkBlock(Properties properties){super(properties.isRedstoneConductor((state,level,pos)->false));
        registerDefaultState(defaultBlockState().setValue(POWERED,false).setValue(RECEIVER,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED,RECEIVER);}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(FACING,context.getClickedFace());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RedstoneLinkBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return createTickerHelper(type,AllBlockEntityTypes.REDSTONE_LINK,RedstoneLinkBlockEntity::tick);}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return !LoadedLevelAccess.blockState(level,pos.relative(state.getValue(FACING).getOpposite())).canBeReplaced();}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(level instanceof ServerLevel server)server.scheduleTick(pos,this,1);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){
        if(level instanceof ServerLevel server){if(!old.is(this))server.scheduleTick(pos,this,1);
            else if(old.getValue(RECEIVER)!=state.getValue(RECEIVER)||old.getValue(POWERED)!=state.getValue(POWERED))notifySelfAndSupport(level,pos,state);}
    }
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        var support=pos.relative(state.getValue(FACING).getOpposite());
        if(LoadedLevelAccess.isLoaded(level,support)&&!canSurvive(state,level,pos)){level.destroyBlock(pos,true);return;}
        updateTransmittedSignal(state,level,pos);if(!state.getValue(RECEIVER))notifySelfAndSupport(level,pos,state);
    }
    public void updateTransmittedSignal(BlockState state,Level level,BlockPos pos){
        if(level.isClientSide()||state.getValue(RECEIVER))return;
        if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof RedstoneLinkBlockEntity entity))return;
        var panels=entity.panelSupport.shouldBePoweredTristate();if(panels==null)return;
        var signals=new LoadedSignals(level);int power=panels?15:0;
        for(var side:Direction.values())power=Math.max(power,signals.getSignal(pos.relative(side),side));
        for(var side:Direction.values())if(side!=state.getValue(FACING).getOpposite())power=Math.max(power,signals.getSignal(pos.relative(side),Direction.UP));
        if(state.getValue(POWERED)!=(power>0))LoadedLevelAccess.setBlockQuiet(level,pos,state.setValue(POWERED,power>0));
        entity.transmit(power);
    }
    private record LoadedSignals(Level level)implements SignalGetter{
        @Override public BlockState getBlockState(BlockPos pos){return LoadedLevelAccess.blockState(level,pos);}
        @Override public BlockEntity getBlockEntity(BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos);}
        @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public int getHeight(){return level.getHeight();}@Override public int getMinY(){return level.getMinY();}
    }
    @Override protected boolean isSignalSource(BlockState state){return state.getValue(RECEIVER)&&state.getValue(POWERED);}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return state.getValue(RECEIVER)&&LoadedLevelAccess.blockEntity(level,pos)instanceof RedstoneLinkBlockEntity link?link.getReceivedSignal():0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side==state.getValue(FACING)?getSignal(state,level,pos,side):0;}
    @Override protected boolean shouldRedstoneWireConnectTo(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side!=null;}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){if(state.getValue(RECEIVER)&&state.getValue(POWERED))notifySelfAndSupport(level,pos,state);}
    public static void notifySelfAndSupport(Level level,BlockPos pos,BlockState state){
        for(var center:new BlockPos[]{pos,pos.relative(state.getValue(FACING).getOpposite())})for(var side:Direction.values()){
            var next=center.relative(side);if(LoadedLevelAccess.isLoaded(level,next))level.neighborChanged(LoadedLevelAccess.blockState(level,next),next,state.getBlock(),null,false);
        }
    }
    public InteractionResult toggleMode(BlockState state,Level level,BlockPos pos){
        if(level instanceof ServerLevel server){
            boolean receiver=!state.getValue(RECEIVER);
            LoadedLevelAccess.setBlockQuiet(level,pos,state.setValue(RECEIVER,receiver).setValue(POWERED,false));
            if(LoadedLevelAccess.blockEntity(level,pos)instanceof RedstoneLinkBlockEntity entity){entity.transmit(0);entity.refreshMode();}
            server.scheduleTick(pos,this,1);
        }return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return player.isShiftKeyDown()?toggleMode(state,level,pos):InteractionResult.PASS;}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){return toggleMode(state,context.getLevel(),context.getClickedPos());}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return RedstoneLinkFrequencySlot.shape(state.getValue(FACING));}
}
