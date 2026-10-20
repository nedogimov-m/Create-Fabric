package com.simibubi.create.content.contraptions.chassis;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;

/** A rising-edge toggle latch. POWERED tracks input, while EXTENDED survives the falling edge. */
public class StickerBlock extends DirectionalBlock implements EntityBlock {
    public static final BooleanProperty POWERED=BlockStateProperties.POWERED,EXTENDED=BlockStateProperties.EXTENDED;
    public StickerBlock(Properties properties){super(properties.strength(1.5f,6).requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion().isRedstoneConductor((state,level,pos)->false));registerDefaultState(defaultBlockState().setValue(FACING,Direction.NORTH).setValue(POWERED,false).setValue(EXTENDED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,POWERED,EXTENDED);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var facing=context.getNearestLookingDirection();if(context.getPlayer()==null||!context.getPlayer().isShiftKeyDown())facing=facing.getOpposite();return defaultBlockState().setValue(FACING,facing).setValue(POWERED,LoadedLevelAccess.getBestNeighborSignal(context.getLevel(),context.getClickedPos())>0);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block block,Orientation orientation,boolean moving){
        if(level.isClientSide())return;boolean powered=LoadedLevelAccess.getBestNeighborSignal(level,pos)>0;if(powered==state.getValue(POWERED))return;
        var next=state.setValue(POWERED,powered);if(powered)next=next.cycle(EXTENDED);LoadedLevelAccess.setBlockQuiet(level,pos,next);
    }
    public static boolean isUpright(BlockState state){return state.getBlock() instanceof StickerBlock&&state.getValue(FACING)==Direction.UP;}
    public static boolean attachedTowards(BlockState state,Level level,BlockPos pos,Direction side){return state.getBlock() instanceof StickerBlock&&state.getValue(EXTENDED)&&state.getValue(FACING)==side&&!BlockMovementChecks.isNotSupportive(LoadedLevelAccess.blockState(level,pos.relative(side)),side.getOpposite());}
    public static boolean notSupportive(BlockState state,Direction side){return state.getBlock() instanceof StickerBlock&&!state.getValue(EXTENDED)&&state.getValue(FACING)==side;}
    @Override public void fallOn(Level level,BlockState state,BlockPos pos,Entity entity,double distance){
        if(!isUpright(state)||entity.isSuppressingBounce())super.fallOn(level,state,pos,entity,distance);
        // Source Sticker deliberately retains ordinary fall damage, unlike vanilla SlimeBlock.
        entity.causeFallDamage(distance,1,level.damageSources().fall());
    }
    // Native restitution is selected per contacted state by StickerBouncinessMixin. Block.bounceOn
    // is only an after-effect callback in 26.3: writing velocity there would be overwritten.
    @Override public void stepOn(Level level,BlockPos pos,BlockState state,Entity entity){
        double vertical=Math.abs(entity.getDeltaMovement().y);if(isUpright(state)&&vertical<.1&&!entity.isSteppingCarefully())entity.setDeltaMovement(entity.getDeltaMovement().multiply(.4+vertical*.2,1,.4+vertical*.2));super.stepOn(level,pos,state,entity);
    }
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.setValue(FACING,mirror.mirror(state.getValue(FACING)));}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new StickerBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return level.isClientSide()&&type==AllBlockEntityTypes.STICKER?(world,pos,block,entity)->((StickerBlockEntity)entity).tickClientAnimation():null;}
}
