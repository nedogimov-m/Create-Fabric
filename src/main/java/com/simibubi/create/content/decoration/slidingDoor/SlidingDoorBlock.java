package com.simibubi.create.content.decoration.slidingDoor;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.contraptions.ContraptionWorld;
import com.simibubi.create.foundation.block.IHaveBigOutline;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;

/** Two-part Create door. The lower block owns the animation; collision follows the source shapes. */
public class SlidingDoorBlock extends DoorBlock implements EntityBlock,IHaveBigOutline {
    public static final BooleanProperty VISIBLE=BooleanProperty.create("visible");
    public static final BlockSetType TRAIN_SET_TYPE=type("train",SoundType.NETHERITE_BLOCK),GLASS_SET_TYPE=type("glass",SoundType.GLASS),STONE_SET_TYPE=type("stone",SoundType.STONE);
    private final boolean folds;
    private static BlockSetType type(String name,SoundType sound) {
        return new BlockSetType("create:"+name,true,true,true,BlockSetType.PressurePlateSensitivity.EVERYTHING,sound,SoundEvents.IRON_DOOR_CLOSE,SoundEvents.IRON_DOOR_OPEN,SoundEvents.IRON_TRAPDOOR_CLOSE,SoundEvents.IRON_TRAPDOOR_OPEN,SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,SoundEvents.STONE_BUTTON_CLICK_OFF,SoundEvents.STONE_BUTTON_CLICK_ON);
    }
    public static SlidingDoorBlock metal(Properties properties,boolean folds){return new SlidingDoorBlock(properties,TRAIN_SET_TYPE,folds);}
    public static SlidingDoorBlock glass(Properties properties,boolean folds){return new SlidingDoorBlock(properties,GLASS_SET_TYPE,folds);}
    public static SlidingDoorBlock stone(Properties properties,boolean folds){return new SlidingDoorBlock(properties,STONE_SET_TYPE,folds);}
    public SlidingDoorBlock(Properties properties,BlockSetType type,boolean folds){super(type,properties.strength(3,6).requiresCorrectToolForDrops().noOcclusion().dynamicShape());this.folds=folds;registerDefaultState(defaultBlockState().setValue(VISIBLE,true));}
    public boolean isFoldingDoor(){return folds;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder.add(VISIBLE));}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        if(!state.getValue(OPEN)&&(state.getValue(VISIBLE)||level instanceof ContraptionWorld))return super.getShape(state,level,pos,context);
        return SlidingDoorShapes.get(state.getValue(FACING),state.getValue(HINGE)==DoorHingeSide.RIGHT,folds);
    }
    @Override protected VoxelShape getInteractionShape(BlockState state,BlockGetter level,BlockPos pos){return getShape(state,level,pos,CollisionContext.empty());}
    @Override protected boolean canSurvive(BlockState state,LevelReader level,BlockPos pos){return state.getValue(HALF)==DoubleBlockHalf.LOWER||LoadedLevelAccess.blockState(level,pos.below()).is(this);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){var state=super.getStateForPlacement(context);return state==null?null:state.setValue(OPEN,false).setValue(POWERED,false).setValue(VISIBLE,true);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){if(!old.is(this)){var lower=state.getValue(HALF)==DoubleBlockHalf.LOWER?pos:pos.below();if(LoadedLevelAccess.blockEntity(level,lower) instanceof SlidingDoorBlockEntity door)door.deferUpdate();}}
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){
        // Vanilla's lower-door floor check intentionally does not apply to Create's hanging doors.
        if(side.getAxis()==Direction.Axis.Y&&(state.getValue(HALF)==DoubleBlockHalf.LOWER)==(side==Direction.UP))
            return other.is(this)&&other.getValue(HALF)!=state.getValue(HALF)?state.setValue(FACING,other.getValue(FACING)).setValue(OPEN,other.getValue(OPEN)).setValue(HINGE,other.getValue(HINGE)).setValue(POWERED,other.getValue(POWERED)).setValue(VISIBLE,other.getValue(VISIBLE)):Blocks.AIR.defaultBlockState();
        return state;
    }
    @Override public void setOpen(Entity entity,Level level,BlockState state,BlockPos pos,boolean open){
        changeOpen(entity,level,state,pos,open,true);
    }
    private void changeOpen(Entity entity,Level level,BlockState state,BlockPos pos,boolean open,boolean playClosingSound){
        if(!state.is(this)||state.getValue(OPEN)==open)return;
        setDoorState(level,pos,state,open,null);
        var otherPos=pairedPos(pos,state);var other=LoadedLevelAccess.blockState(level,otherPos);
        if(isDoubleDoor(state.setValue(OPEN,open),state.getValue(HINGE),state.getValue(FACING),other))setDoorState(level,otherPos,other,open,null);
        if(open||playClosingSound)playDoorSound(entity,level,pos,open);level.gameEvent(entity,open?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,pos);
    }
    /** Explicitly updates both loaded halves, including callers using the upper half. */
    private void setDoorState(Level level,BlockPos pos,BlockState state,boolean open,Boolean powered){
        var next=state.setValue(OPEN,open);if(open)next=next.setValue(VISIBLE,false);if(powered!=null)next=next.setValue(POWERED,powered);
        if(LoadedLevelAccess.isLoaded(level,pos))level.setBlock(pos,next,UPDATE_CLIENTS|UPDATE_IMMEDIATE);
        var otherPos=state.getValue(HALF)==DoubleBlockHalf.LOWER?pos.above():pos.below();var other=LoadedLevelAccess.blockState(level,otherPos);
        if(other.is(this)&&other.getValue(HALF)!=state.getValue(HALF)){var upper=other.setValue(OPEN,open).setValue(POWERED,next.getValue(POWERED)).setValue(VISIBLE,next.getValue(VISIBLE));level.setBlock(otherPos,upper,UPDATE_CLIENTS|UPDATE_IMMEDIATE);}
    }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block changed,Orientation orientation,boolean moving){
        if(changed==this||level.isClientSide())return;
        var lower=state.getValue(HALF)==DoubleBlockHalf.LOWER?pos:pos.below();if(LoadedLevelAccess.blockEntity(level,lower) instanceof SlidingDoorBlockEntity door&&door.isUpdateDeferred())return;
        boolean powered=isDoorPowered(level,pos,state);if(powered==state.getValue(POWERED))return;
        setDoorState(level,pos,state,powered,powered);
        if(powered!=state.getValue(OPEN)){
            var otherPos=pairedPos(pos,state);var other=LoadedLevelAccess.blockState(level,otherPos);
            if(isDoubleDoor(state.setValue(OPEN,powered),state.getValue(HINGE),state.getValue(FACING),other))setDoorState(level,otherPos,other,powered,powered);
            playDoorSound(null,level,pos,powered);level.gameEvent(null,powered?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,pos);
        }
    }
    public static boolean isDoorPowered(Level level,BlockPos pos,BlockState state){
        if(hasPower(level,pos,state))return true;
        var otherPos=pairedPos(pos,state);var other=LoadedLevelAccess.blockState(level,otherPos);
        return isDoubleDoor(state.cycle(OPEN),state.getValue(HINGE),state.getValue(FACING),other)&&hasPower(level,otherPos,other);
    }
    private static boolean hasPower(Level level,BlockPos pos,BlockState state){return LoadedLevelAccess.getBestNeighborSignal(level,pos)>0||LoadedLevelAccess.getBestNeighborSignal(level,state.getValue(HALF)==DoubleBlockHalf.LOWER?pos.above():pos.below())>0;}
    public static BlockPos pairedPos(BlockPos pos,BlockState state){var facing=state.getValue(FACING);return pos.relative(state.getValue(HINGE)==DoorHingeSide.LEFT?facing.getClockWise():facing.getCounterClockWise());}
    public static boolean isDoubleDoor(BlockState state,DoorHingeSide hinge,Direction facing,BlockState other){return other.getBlock()==state.getBlock()&&other.getValue(HINGE)!=hinge&&other.getValue(FACING)==facing&&other.getValue(OPEN)!=state.getValue(OPEN)&&other.getValue(HALF)==state.getValue(HALF);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return stack.is(AllItems.WRENCH)?InteractionResult.PASS:useWithoutItem(state,level,pos,player,hit);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){changeOpen(player,level,state,pos,!state.getValue(OPEN),false);return InteractionResult.SUCCESS;}
    public InteractionResult onWrenched(BlockState state,UseOnContext context){
        var level=context.getLevel();var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        var lower=state.getValue(HALF)==DoubleBlockHalf.LOWER?context.getClickedPos():context.getClickedPos().below();var lowerState=LoadedLevelAccess.blockState(level,lower);
        if(!lowerState.is(this)||!level.mayInteract(player,lower)||!level.mayInteract(player,lower.above()))return InteractionResult.FAIL;
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(player.isShiftKeyDown()){
            if(!(level instanceof ServerLevel server))return InteractionResult.PASS;
            var be=LoadedLevelAccess.blockEntity(level,lower);if(!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level,player,lower,lowerState,be))return InteractionResult.FAIL;
            if(!player.isCreative())Block.getDrops(lowerState,server,lower,be,player,context.getItemInHand()).forEach(stack->player.getInventory().placeItemBackInInventory(stack,Prediction.SERVER_ONLY));
            level.destroyBlock(lower,false);PlayerBlockBreakEvents.AFTER.invoker().afterBlockBreak(level,player,lower,lowerState,be);level.playSound(null,lower,AllSoundEvents.WRENCH_REMOVE,SoundSource.BLOCKS,1,.75f);return InteractionResult.SUCCESS;
        }
        if(context.getClickedFace().getAxis()!=Direction.Axis.Y)return InteractionResult.SUCCESS;
        var next=lowerState.setValue(FACING,lowerState.getValue(FACING).getClockWise());level.setBlock(lower,next,UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE);
        var upper=LoadedLevelAccess.blockState(level,lower.above());if(upper.is(this))level.setBlock(lower.above(),upper.setValue(FACING,next.getValue(FACING)),UPDATE_CLIENTS|UPDATE_KNOWN_SHAPE);
        level.playSound(null,lower,AllSoundEvents.WRENCH_ROTATE,SoundSource.BLOCKS,.25f,1.25f);return InteractionResult.SUCCESS;
    }
    @Override protected RenderShape getRenderShape(BlockState state){return state.getValue(VISIBLE)?RenderShape.MODEL:RenderShape.INVISIBLE;}
    private void playDoorSound(Entity entity,Level level,BlockPos pos,boolean open){level.playSound(entity,pos,open?SoundEvents.IRON_DOOR_OPEN:SoundEvents.IRON_DOOR_CLOSE,SoundSource.BLOCKS,1,level.getRandom().nextFloat()*.1f+.9f);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return state.getValue(HALF)==DoubleBlockHalf.UPPER?null:new SlidingDoorBlockEntity(pos,state);}
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.SLIDING_DOOR?(world,pos,block,entity)->SlidingDoorBlockEntity.tick(world,pos,block,(SlidingDoorBlockEntity)entity):null;}
}
