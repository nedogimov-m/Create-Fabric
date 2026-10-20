package com.simibubi.create.content.redstone.analogLever;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

public final class AnalogLeverBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public AnalogLeverBlock(Properties properties){super(properties.noOcclusion());registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(FACE,AttachFace.WALL));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,FACE);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new AnalogLeverBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.ANALOG_LEVER?(world,pos,block,entity)->AnalogLeverBlockEntity.tick(world,pos,block,(AnalogLeverBlockEntity)entity):null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.isClientSide()){addParticles(state,level,pos,1);return InteractionResult.SUCCESS;}
        if(LoadedLevelAccess.blockEntity(level,pos)instanceof AnalogLeverBlockEntity lever){lever.changeState(player.isShiftKeyDown());level.playSound(null,pos,SoundEvents.LEVER_CLICK,SoundSource.BLOCKS,.2f,.25f+(lever.getState()+5)/15f*.5f);return InteractionResult.SUCCESS;}return InteractionResult.PASS;
    }
    @Override protected boolean isSignalSource(BlockState state){return true;}
    @Override protected int ownSignal(BlockState state,BlockGetter level,BlockPos pos){return getSignal(state,level,pos,Direction.UP);}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return LoadedLevelAccess.blockEntity(level,pos)instanceof AnalogLeverBlockEntity lever?lever.getState():0;}
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getConnectedDirection(state)==side?getSignal(state,level,pos,side):0;}
    @Override public void animateTick(BlockState state,Level level,BlockPos pos,RandomSource random){if(LoadedLevelAccess.blockEntity(level,pos)instanceof AnalogLeverBlockEntity lever&&lever.getState()!=0&&random.nextFloat()<.25f)addParticles(state,level,pos,.5f);}
    private static void addParticles(BlockState state,Level level,BlockPos pos,float size){var facing=state.getValue(FACING).getOpposite();var support=getConnectedDirection(state).getOpposite();level.addParticle(new DustParticleOptions(0xFF0000,size),pos.getX()+.5+.1*facing.getStepX()+.2*support.getStepX(),pos.getY()+.5+.1*facing.getStepY()+.2*support.getStepY(),pos.getZ()+.5+.1*facing.getStepZ()+.2*support.getStepZ(),0,0,0);}
    public static void updateNeighbors(BlockState state,Level level,BlockPos pos){notifyAt(level,pos,state.getBlock());notifyAt(level,pos.relative(getConnectedDirection(state).getOpposite()),state.getBlock());}
    private static void notifyAt(Level level,BlockPos pos,Block block){for(var side:Direction.values()){var next=pos.relative(side);if(LoadedLevelAccess.isLoaded(level,next))level.neighborChanged(LoadedLevelAccess.blockState(level,next),next,block,null,false);}}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){if(!moved)updateNeighbors(state,level,pos);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACING,state.getValue(FACING)).setValue(LeverBlock.FACE,state.getValue(FACE)).getShape(level,pos,context);}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
}
