package com.simibubi.create.content.trains.display;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.*;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

/** Actual display board column/cog/item and multiline Clipboard text interactions. */
public final class FlapDisplayBlock extends HorizontalKineticBlock implements ICogWheel,SimpleWaterloggedBlock {
    public static final BooleanProperty UP=BooleanProperty.create("up"),DOWN=BooleanProperty.create("down"),WATERLOGGED=BlockStateProperties.WATERLOGGED;
    public FlapDisplayBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING,Direction.NORTH).setValue(UP,false).setValue(DOWN,false).setValue(WATERLOGGED,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(UP,DOWN,WATERLOGGED);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FlapDisplayBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.FLAP_DISPLAY?(world,pos,block,entity)->FlapDisplayBlockEntity.tick(world,pos,block,(FlapDisplayBlockEntity)entity):null;}
    @Override protected RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(HORIZONTAL_FACING).getAxis();}
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction side){return false;}
    public static Direction.Axis getConnectionAxis(BlockState state){return state.getValue(HORIZONTAL_FACING).getClockWise().getAxis();}
    public static boolean canConnect(BlockState state,BlockState other){return state.getBlock()instanceof FlapDisplayBlock&&other.getBlock()==state.getBlock()&&state.getValue(HORIZONTAL_FACING)==other.getValue(HORIZONTAL_FACING);}
    public static boolean getConnection(BlockState state,Direction side){return side==Direction.UP?state.getValue(UP):side==Direction.DOWN&&state.getValue(DOWN);}
    public static BlockState setConnection(BlockState state,Direction side,boolean connected){return side==Direction.UP?state.setValue(UP,connected):side==Direction.DOWN?state.setValue(DOWN,connected):state;}
    private static boolean available(LevelReader level,BlockPos pos){return !(level instanceof Level world)||LoadedLevelAccess.isLoaded(world,pos);}
    /** Source compares whole horizontal row widths. At an unknown frontier preserve both old connection bits. */
    public static BlockState updateColumn(Level level,BlockPos pos,BlockState state){
        var result=state;for(var vertical:new Direction[]{Direction.DOWN,Direction.UP}){boolean connected=true;outer:for(var movement:Direction.values()){if(movement.getAxis()!=getConnectionAxis(state))continue;var current=pos;for(int i=0;i<1000;i++){var adjacent=current.relative(vertical);if(!available(level,current)||!available(level,adjacent))return state;var first=current.equals(pos)?state:LoadedLevelAccess.blockState(level,current);var second=LoadedLevelAccess.blockState(level,adjacent);boolean a=canConnect(state,first),b=canConnect(state,second);current=current.relative(movement);if(!a&&!b)break;if(a&&b)continue;connected=false;break outer;}}result=setConnection(result,vertical,connected);}return result;
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){
        var pos=context.getClickedPos();var level=context.getLevel();var support=LoadedLevelAccess.blockState(level,pos.relative(context.getClickedFace().getOpposite()));var state=support.getBlock()==this&&(context.getPlayer()==null||!context.getPlayer().isShiftKeyDown())?defaultBlockState().setValue(HORIZONTAL_FACING,support.getValue(HORIZONTAL_FACING)):super.getStateForPlacement(context);return updateColumn(level,pos,state.setValue(WATERLOGGED,LoadedLevelAccess.blockState(level,pos).getFluidState().getType()==Fluids.WATER));
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved){super.onPlace(state,level,pos,previous,moved);if(!level.isClientSide()&&(previous.getBlock()!=this||previous.getValue(HORIZONTAL_FACING)!=state.getValue(HORIZONTAL_FACING)))level.scheduleTick(pos,this,1);}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){super.neighborChanged(state,level,pos,neighbor,orientation,moved);if(!level.isClientSide()&&!level.getBlockTicks().willTickThisTick(pos,this))level.scheduleTick(pos,this,1);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        var first=pos.relative(Direction.get(Direction.AxisDirection.NEGATIVE,getConnectionAxis(state)));if(available(level,first)&&!canConnect(state,LoadedLevelAccess.blockState(level,first))){var next=updateColumn(level,pos,state);if(next!=state)level.setBlock(pos,next,Block.UPDATE_ALL);}if(LoadedLevelAccess.blockEntity(level,pos)instanceof FlapDisplayBlockEntity entity)entity.updateControllerStatus();
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos next,BlockState neighbor,RandomSource random){
        if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));if(!available(level,next))return state;if(!canConnect(state,neighbor))return setConnection(state,side,false);if(side.getAxis()==getConnectionAxis(state))return withPropertiesOf(neighbor).setValue(WATERLOGGED,state.getValue(WATERLOGGED));return setConnection(state,side,getConnection(neighbor,side.getOpposite()));
    }
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moved){
        super.affectNeighborsAfterRemoval(state,level,pos,moved);if(moved)return;for(var side:Direction.values())if(side.getAxis()==getConnectionAxis(state)){var next=pos.relative(side);if(!available(level,next))continue;var adjacent=LoadedLevelAccess.blockState(level,next);if(canConnect(state,adjacent)){var updated=updateColumn(level,next,adjacent);if(updated!=adjacent)level.setBlock(next,updated,Block.UPDATE_ALL);level.scheduleTick(next,this,1);}}
    }
    @Override protected FluidState getFluidState(BlockState state){return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state);}
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){return state.getValue(HORIZONTAL_FACING).getAxis()==Direction.Axis.X?box(3,0,0,13,16,16):box(0,0,3,16,16,13);}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){return interact(stack,state,level,pos,player,hand,hit);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return interact(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit);}
    private static InteractionResult interact(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(player.isShiftKeyDown()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.PASS;if(stack.is(AllBlocks.DISPLAY_BOARD.asItem()))return assistPlacement(player,hand,level,state,pos,hit);
        if(!(LoadedLevelAccess.blockEntity(level,pos)instanceof FlapDisplayBlockEntity part))return InteractionResult.PASS;var controller=part.getController();if(controller==null)return InteractionResult.PASS;int line=controller.getLineIndexAt(hit.getLocation().y-hit.getDirection().getStepY()*.125);if(!controller.validLine(line))return InteractionResult.PASS;
        if(stack.isEmpty()){if(!controller.isSpeedRequirementFulfilled())return InteractionResult.PASS;if(!level.isClientSide())controller.applyTextManually(line,null);return InteractionResult.SUCCESS;}
        if(stack.is(Items.GLOW_INK_SAC)){if(!level.isClientSide()){level.playSound(null,pos,SoundEvents.INK_SAC_USE,SoundSource.BLOCKS,1,1);controller.setGlowing(line);}return InteractionResult.SUCCESS;}
        boolean clipboard=stack.is(AllBlocks.CLIPBOARD.asItem());boolean display=stack.is(Items.NAME_TAG)&&stack.has(DataComponents.CUSTOM_NAME)||clipboard;var dye=stack.get(DataComponents.DYE);if(!display&&dye==null||dye==null&&!controller.isSpeedRequirementFulfilled())return InteractionResult.PASS;if(level.isClientSide())return InteractionResult.SUCCESS;
        if(clipboard){int current=line;for(var entry:com.simibubi.create.content.equipment.clipboard.ClipboardEntry.getLastViewedEntries(stack))for(var text:entry.text.getString().split("\\n"))controller.applyTextManually(current++,net.minecraft.network.chat.Component.literal(text));return InteractionResult.SUCCESS;}
        if(display)controller.applyTextManually(line,stack.get(DataComponents.CUSTOM_NAME));if(dye!=null){level.playSound(null,pos,SoundEvents.DYE_USE,SoundSource.BLOCKS,1,1);controller.setColour(line,dye);}return InteractionResult.SUCCESS;
    }
    /** Source one-step nearest edge helper; native BlockItem.place performs permissions, collision and survival debit. */
    private static InteractionResult assistPlacement(Player player,InteractionHand hand,Level level,BlockState state,BlockPos pos,BlockHitResult hit){
        if(!player.mayBuild()||!(player.getItemInHand(hand).getItem()instanceof BlockItem item))return InteractionResult.PASS;var choices=new ArrayList<Direction>();for(var side:Direction.values())if(side.getAxis()!=state.getValue(HORIZONTAL_FACING).getAxis()&&available(level,pos.relative(side))&&LoadedLevelAccess.blockState(level,pos.relative(side)).canBeReplaced())choices.add(side);choices.sort(Comparator.comparingDouble(side->Vec3.atCenterOf(pos).relative(side,.5).distanceToSqr(hit.getLocation())));if(choices.isEmpty())return InteractionResult.PASS;var side=choices.getFirst();var target=pos.relative(side);if(!level.mayInteract(player,target))return InteractionResult.FAIL;var nextHit=new BlockHitResult(Vec3.atCenterOf(pos).relative(side,.5),side,pos,false);return item.place(new BlockPlaceContext(new UseOnContext(player,hand,nextHit)));
    }
}
