package com.simibubi.create.content.redstone.nixieTube;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.BiConsumer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Real16-color source Nixie block. Clipboard text uses its original first entry; optional CC drivers remain an addon boundary. */
public final class NixieTubeBlock extends DoubleFaceAttachedBlock implements EntityBlock,SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED;
    private final DyeColor color;
    public NixieTubeBlock(Properties properties,DyeColor color){super(properties);this.color=color;registerDefaultState(defaultBlockState().setValue(FACE,DoubleAttachFace.FLOOR).setValue(FACING,Direction.NORTH).setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACE,FACING,WATERLOGGED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new NixieTubeBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.NIXIE_TUBE?(world,pos,block,entity)->NixieTubeBlockEntity.tick(world,pos,block,(NixieTubeBlockEntity)entity):null;}
    public DyeColor getColor(){return color;}
    public static DyeColor colorOf(BlockState state){return state.getBlock()instanceof NixieTubeBlock block?block.color:DyeColor.ORANGE;}
    public static Direction getFacing(BlockState state){return getConnectedDirection(state);}
    public static Direction getLeftNixieDirection(BlockState state){return switch(state.getValue(FACE)){case WALL->Direction.UP;case WALL_REVERSED->Direction.DOWN;default->state.getValue(FACING).getOpposite();};}
    public static Direction getRightNixieDirection(BlockState state){return getLeftNixieDirection(state).getOpposite();}
    public static boolean areNixieBlocksEqual(BlockState first,BlockState second){return first.getBlock()instanceof NixieTubeBlock&&second.getBlock()instanceof NixieTubeBlock&&first.getValue(FACE)==second.getValue(FACE)&&first.getValue(FACING)==second.getValue(FACING)&&first.getValue(WATERLOGGED)==second.getValue(WATERLOGGED);}
    public static BlockState withColor(BlockState state,DyeColor color){return AllBlocks.NIXIE_TUBES.get(color).defaultBlockState().setValue(FACING,state.getValue(FACING)).setValue(FACE,state.getValue(FACE)).setValue(WATERLOGGED,state.getValue(WATERLOGGED));}
    private static boolean available(BlockGetter level,BlockPos pos){return !(level instanceof Level world)||LoadedLevelAccess.isLoaded(world,pos);}
    /** Complete loaded-row preflight before any callback, rather than a partial recolor at an unknown chunk frontier. */
    public static boolean walkNixies(LevelAccessor level,BlockPos start,BiConsumer<BlockPos,Integer> callback){
        var state=LoadedLevelAccess.blockState(level,start);if(!(state.getBlock()instanceof NixieTubeBlock))return false;var left=getLeftNixieDirection(state);var first=start;
        while(true){var next=first.relative(left);if(!available(level,next))return false;if(!areNixieBlocksEqual(LoadedLevelAccess.blockState(level,next),state))break;first=next;}
        var positions=new ArrayList<BlockPos>();var current=first;while(true){positions.add(current.immutable());var next=current.relative(left.getOpposite());if(!available(level,next))return false;if(!areNixieBlocksEqual(LoadedLevelAccess.blockState(level,next),state))break;current=next;}
        if(callback!=null)for(int i=0;i<positions.size();i++)callback.accept(positions.get(i),i);return true;
    }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return interact(stack,state,level,pos,player);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return interact(ItemStack.EMPTY,state,level,pos,player);}
    private static InteractionResult interact(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player){
        if(player.isShiftKeyDown()||player.isSpectator()||!level.mayInteract(player,pos)||!(LoadedLevelAccess.blockEntity(level,pos)instanceof NixieTubeBlockEntity entity))return InteractionResult.PASS;
        if(stack.isEmpty()){if(entity.reactsToRedstone())return InteractionResult.PASS;if(!level.isClientSide()){entity.clearCustomText();updateDisplayedRedstoneValue(entity,state,false);}return InteractionResult.SUCCESS;}
        boolean clipboard=stack.is(AllBlocks.CLIPBOARD.asItem());boolean display=stack.is(Items.NAME_TAG)&&stack.has(DataComponents.CUSTOM_NAME)||clipboard;var dye=stack.get(DataComponents.DYE);if(!display&&dye==null)return InteractionResult.PASS;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        var component=stack.getOrDefault(DataComponents.CUSTOM_NAME,Component.empty());if(clipboard){var entries=com.simibubi.create.content.equipment.clipboard.ClipboardEntry.getLastViewedEntries(stack);if(!entries.isEmpty())component=entries.getFirst().text;}var json=ComponentSerialization.CODEC.encodeStart(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),component).getOrThrow().toString();
        boolean changed=walkNixies(level,pos,(current,index)->{
            if(!(LoadedLevelAccess.blockEntity(level,current)instanceof NixieTubeBlockEntity before))return;
            if(display)before.displayCustomText(json,index);
            if(dye!=null){var next=withColor(LoadedLevelAccess.blockState(level,current),dye);if(LoadedLevelAccess.blockState(level,current)!=next&&level.setBlock(current,next,Block.UPDATE_ALL)&&LoadedLevelAccess.blockEntity(level,current)instanceof NixieTubeBlockEntity after&&after!=before)after.copyDisplayFrom(before);}
        });return changed?InteractionResult.SUCCESS:InteractionResult.PASS;
    }
    @Override protected boolean shouldRedstoneWireConnectTo(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side!=null;}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){if(level instanceof ServerLevel&&!level.getBlockTicks().willTickThisTick(pos,this))level.scheduleTick(pos,this,1);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){if(LoadedLevelAccess.blockEntity(level,pos)instanceof NixieTubeBlockEntity entity)updateDisplayedRedstoneValue(entity,state,false);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved){if(!level.isClientSide()&&!(previous.getBlock()instanceof NixieTubeBlock)&&!moved)level.scheduleTick(pos,this,1);}
    public static void updateDisplayedRedstoneValue(NixieTubeBlockEntity entity,BlockState state,boolean force){var level=entity.getLevel();if(level==null||level.isClientSide()||!entity.reactsToRedstone()&&!force)return;var view=new SignalView(level);int power=0;for(var side:Direction.values())power=Math.max(power,view.getSignal(entity.getBlockPos().relative(side),side));for(var side:Direction.values())if(state.getValue(FACING).getOpposite()!=side)power=Math.max(power,view.getSignal(entity.getBlockPos().relative(side),Direction.UP));entity.updateRedstoneStrength(power);}
    private record SignalView(Level level)implements SignalGetter {
        @Override public BlockState getBlockState(BlockPos pos){return LoadedLevelAccess.blockState(level,pos);}
        @Override public BlockEntity getBlockEntity(BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos);}
        @Override public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
        @Override public int getHeight(){return level.getHeight();}
        @Override public int getMinY(){return level.getMinY();}
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=super.getStateForPlacement(context);if(state==null)return null;if(state.getValue(FACE)!=DoubleAttachFace.WALL&&state.getValue(FACE)!=DoubleAttachFace.WALL_REVERSED)state=state.setValue(FACING,state.getValue(FACING).getClockWise());return state.setValue(WATERLOGGED,LoadedLevelAccess.blockState(context.getLevel(),context.getClickedPos()).getFluidState().getType()==Fluids.WATER);}
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos next,BlockState neighbor,RandomSource random){if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));return state;}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        var face=state.getValue(FACE);if(face==DoubleAttachFace.WALL||face==DoubleAttachFace.WALL_REVERSED)return rotateY(Shapes.or(box(5,9,0,11,15,12),box(5,1,0,11,7,12)),switch(state.getValue(FACING)){case EAST->1;case NORTH->2;case WEST->3;default->0;});
        int min=face==DoubleAttachFace.CEILING?4:0,max=min+12;var shape=Shapes.or(box(9,min,5,15,max,11),box(1,min,5,7,max,11));return state.getValue(FACING).getAxis()==Direction.Axis.X?shape:rotateY(shape,1);
    }
    private static VoxelShape rotateY(VoxelShape shape,int count){for(int turn=0;turn<count;turn++){var result=new VoxelShape[]{Shapes.empty()};shape.forAllBoxes((x,y,z,a,b,c)->result[0]=Shapes.or(result[0],Shapes.box(z,y,1-a,c,b,1-x)));shape=result[0];}return shape;}
    @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){return new ItemStack(AllBlocks.ORANGE_NIXIE_TUBE);}
    @Override protected boolean isPathfindable(BlockState state,PathComputationType type){return false;}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){if(context.getClickedFace().getAxis()==Direction.Axis.Y&&!context.getLevel().isClientSide()){context.getLevel().setBlockAndUpdate(context.getClickedPos(),state.setValue(FACING,state.getValue(FACING).getClockWise()));context.getLevel().playSound(null,context.getClickedPos(),AllSoundEvents.WRENCH_ROTATE,SoundSource.BLOCKS,1,1);}return InteractionResult.SUCCESS;}
    @Override protected BlockState rotate(BlockState state,Rotation rotation){return state.setValue(FACING,rotation.rotate(state.getValue(FACING)));}
    @Override protected BlockState mirror(BlockState state,Mirror mirror){return state.rotate(mirror.getRotation(state.getValue(FACING)));}
}
