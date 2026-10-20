package com.simibubi.create.content.contraptions.elevator;

import com.simibubi.create.*;
import com.simibubi.create.content.redstone.contact.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/** Source input POWERED is distinct from arrival output POWERING and pending-call CALLING. */
public class ElevatorContactBlock extends RedstoneContactBlock implements EntityBlock {
    public static final BooleanProperty CALLING=BooleanProperty.create("calling"),POWERING=BooleanProperty.create("powering");
    public ElevatorContactBlock(Properties properties){super(properties.lightLevel(state->getLight(state)));registerDefaultState(defaultBlockState().setValue(FACING,Direction.SOUTH).setValue(CALLING,false).setValue(POWERING,false));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(CALLING,POWERING);}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ElevatorContactBlockEntity(pos,state);}
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntityTypes.ELEVATOR_CONTACT?(world,pos,block,be)->ElevatorContactBlockEntity.tick(world,pos,block,(ElevatorContactBlockEntity)be):null;}
    public static ElevatorColumn.ColumnCoords getColumnCoords(Level level,BlockPos pos){var state=LoadedLevelAccess.blockState(level,pos);if(!(state.getBlock() instanceof RedstoneContactBlock)||!state.getValue(FACING).getAxis().isHorizontal())return null;return new ElevatorColumn.ColumnCoords(pos.getX(),pos.getZ(),state.getValue(FACING));}
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,Orientation orientation,boolean moved){
        if(!(level instanceof ServerLevel server))return;boolean powered=LoadedLevelAccess.getBestNeighborSignal(level,pos)>0;if(powered==state.getValue(POWERED))return;LoadedLevelAccess.setBlockQuiet(server,pos,state.setValue(POWERED,powered));if(!powered||state.getValue(CALLING))return;var coords=getColumnCoords(level,pos);if(coords!=null)callToContactAndUpdate(ElevatorColumn.getOrCreate(level,coords),LoadedLevelAccess.blockState(level,pos),server,pos,true);
    }
    public void callToContactAndUpdate(ElevatorColumn column,BlockState state,Level level,BlockPos pos,boolean powered){
        if(!(level instanceof ServerLevel server)||!LoadedLevelAccess.isLoaded(level,pos)||!column.coordinates().equals(getColumnCoords(level,pos)))return;
        for(var other:column.getContacts())if(!other.equals(pos)&&LoadedLevelAccess.blockState(level,other).getBlock() instanceof ElevatorContactBlock block){var old=LoadedLevelAccess.blockState(level,other);LoadedLevelAccess.setBlockQuiet(level,other,old.setValue(CALLING,false));block.scheduleActivation(level,other);}
        var actual=LoadedLevelAccess.blockState(server,pos);LoadedLevelAccess.setBlockQuiet(server,pos,actual.setValue(CALLING,true).setValue(POWERED,powered||actual.getValue(POWERED)));column.target(pos.getY());column.markDirty();notifyNeighbors(server,pos);
    }
    public void scheduleActivation(LevelAccessor level,BlockPos pos){if(!level.getBlockTicks().hasScheduledTick(pos,this))level.scheduleTick(pos,this,1);}
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moving){if(level instanceof ServerLevel&&(!previous.is(this)||previous.getValue(FACING)!=state.getValue(FACING)))scheduleActivation(level,pos);}
    @Override protected void tick(BlockState state,ServerLevel level,BlockPos pos,RandomSource random){
        boolean active=false;if(LoadedLevelAccess.blockEntity(level,pos) instanceof ElevatorContactBlockEntity contact){active=contact.activateBlock;contact.activateBlock=false;contact.setChanged();}
        active|=RedstoneContactBlock.hasValidContact(level,pos,state.getValue(FACING));
        if(active!=state.getValue(POWERING))LoadedLevelAccess.setBlockQuiet(level,pos,state.setValue(POWERING,active));notifyNeighbors(level,pos);
    }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos otherPos,BlockState other,RandomSource random){if(side==state.getValue(FACING)&&state.getValue(POWERING)!=RedstoneContactBlock.hasValidContact(level,pos,side))ticks.scheduleTick(pos,this,1);return state;}
    private void notifyNeighbors(ServerLevel level,BlockPos pos){RedstoneContactBlock.refreshWireConnections(level,pos);for(var side:Direction.values()){var adjacent=pos.relative(side);if(LoadedLevelAccess.isLoaded(level,adjacent))level.neighborChanged(LoadedLevelAccess.blockState(level,adjacent),adjacent,this,null,false);}}
    @Override protected void affectNeighborsAfterRemoval(BlockState state,ServerLevel level,BlockPos pos,boolean moving){if(state.getValue(POWERING))notifyNeighbors(level,pos);}
    @Override protected boolean isSignalSource(BlockState state){return state.getValue(POWERING);}
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){if(side==null)return 0;if(LoadedLevelAccess.blockState(level,pos.relative(side.getOpposite())).is(this))return 0;return state.getValue(POWERING)?15:0;}
    @Override protected boolean shouldRedstoneWireConnectTo(BlockState state,BlockGetter level,BlockPos pos,Direction side){return side==null||side!=state.getValue(FACING).getOpposite();}
    @Override protected ItemStack getCloneItemStack(LevelReader level,BlockPos pos,BlockState state,boolean includeData){return new ItemStack(AllBlocks.REDSTONE_CONTACT);}
    private InteractionResult open(Level level,BlockPos pos,Player player){if(player instanceof ServerPlayer server&&LoadedLevelAccess.blockEntity(level,pos) instanceof ElevatorContactBlockEntity contact)ElevatorNetworking.openContact(server,contact);return InteractionResult.SUCCESS;}
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(stack.isEmpty()||stack.is(AllItems.WRENCH))return InteractionResult.TRY_WITH_EMPTY_HAND;return open(level,pos,player);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){return open(level,pos,player);}
    @Override public InteractionResult onWrenched(BlockState state,UseOnContext context){var result=super.onWrenched(state,context);if(result!=InteractionResult.SUCCESS||!(context.getLevel() instanceof ServerLevel level))return result;var current=LoadedLevelAccess.blockState(level,context.getClickedPos());var coords=getColumnCoords(level,context.getClickedPos());if(coords==null||ElevatorColumn.get(level,coords)==null)level.setBlockAndUpdate(context.getClickedPos(),AllBlocks.REDSTONE_CONTACT.defaultBlockState().setValue(FACING,current.getValue(FACING)).setValue(POWERED,current.getValue(POWERED)));return result;}
    public static int getLight(BlockState state){return state.getValue(POWERING)?10:0;}
}
