package com.simibubi.create.content.logistics.factoryBoard;

import com.mojang.serialization.Codec;
import com.simibubi.create.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.core.*;
import net.minecraft.network.codec.*;
import net.minecraft.util.*;
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
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class FactoryPanelBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
        @Override public void setPlacedBy(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof FactoryPanelBlockEntity device)device.sourceAdvancements.place(placer);}

    public static final EnumProperty<AttachFace> FACE=BlockStateProperties.ATTACH_FACE;
    public static final EnumProperty<Direction> FACING=BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty WATERLOGGED=BlockStateProperties.WATERLOGGED,POWERED=BlockStateProperties.POWERED;
    public enum PanelSlot implements StringRepresentable {
        TOP_LEFT(1,1),TOP_RIGHT(0,1),BOTTOM_LEFT(1,0),BOTTOM_RIGHT(0,0);
        public static final Codec<PanelSlot> CODEC=StringRepresentable.fromValues(PanelSlot::values);
        public static final StreamCodec<ByteBuf,PanelSlot> STREAM_CODEC=ByteBufCodecs.VAR_INT.map(value->{if(value<0||value>=values().length)throw new IllegalArgumentException("Invalid panel slot");return values()[value];},PanelSlot::ordinal);
        public final int xOffset,yOffset;
        PanelSlot(int x,int y) { xOffset=x;yOffset=y; }
    @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
    }
    public enum PanelState { PASSIVE,ACTIVE }
    public enum PanelType { NETWORK,PACKAGER }
    public FactoryPanelBlock(Properties properties) { super(properties.strength(3,6).sound(SoundType.COPPER).noOcclusion().forceSolidOn().isRedstoneConductor((s,l,p)->false));registerDefaultState(defaultBlockState().setValue(FACE,AttachFace.FLOOR).setValue(FACING,Direction.NORTH).setValue(POWERED,false).setValue(WATERLOGGED,false)); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) { builder.add(FACE,FACING,POWERED,WATERLOGGED); }
    public static Direction connectedDirection(BlockState state) { return switch(state.getValue(FACE)){case FLOOR->Direction.UP;case CEILING->Direction.DOWN;case WALL->state.getValue(FACING);}; }
    public static float getXRot(BlockState state) { return switch(state.getValue(FACE)){case FLOOR->-Mth.PI/2;case CEILING->Mth.PI/2;case WALL->0;}; }
    public static float getYRot(BlockState state) { return (state.getValue(FACE)==AttachFace.CEILING?Mth.PI:0)+(float)Math.toRadians(switch(state.getValue(FACING)){case NORTH->180;case WEST->-90;case EAST->-270;default->0;}); }
    public static PanelSlot getTargetedSlot(BlockPos pos,BlockState state,Vec3 click) { var local=click.subtract(Vec3.atLowerCornerOf(pos));PanelSlot result=PanelSlot.BOTTOM_LEFT;double best=Double.MAX_VALUE;for(var slot:PanelSlot.values()){var center=FactoryPanelTransform.point(state,new Vec3(.25+slot.xOffset*.5,0,.25+slot.yOffset*.5));double distance=center.distanceToSqr(local);if(distance<=best){best=distance;result=slot;}}return result; }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { var level=context.getLevel();for(var direction:context.getNearestLookingDirections()){var state=defaultBlockState().setValue(FACE,direction==Direction.DOWN?AttachFace.FLOOR:direction==Direction.UP?AttachFace.CEILING:AttachFace.WALL).setValue(FACING,direction.getAxis().isHorizontal()?direction.getOpposite():context.getHorizontalDirection());if(direction==Direction.DOWN)state=state.setValue(FACING,state.getValue(FACING).getOpposite());if(canSurvive(state,level,context.getClickedPos()))return state.setValue(WATERLOGGED,level.getFluidState(context.getClickedPos()).is(Fluids.WATER));}return null; }
    @Override public boolean canSurvive(BlockState state,LevelReader level,BlockPos pos) { var support=pos.relative(connectedDirection(state).getOpposite());return !LoadedLevelAccess.blockState(level,support).getCollisionShape(level,support).isEmpty(); }
    @Override protected BlockState updateShape(BlockState state,LevelReader level,ScheduledTickAccess ticks,BlockPos pos,Direction side,BlockPos neighbor,BlockState neighborState,RandomSource random) { if(state.getValue(WATERLOGGED))ticks.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));if(side==connectedDirection(state).getOpposite()&&!canSurvive(state,level,pos))return Blocks.AIR.defaultBlockState();return state; }
    @Override public FluidState getFluidState(BlockState state) { return state.getValue(WATERLOGGED)?Fluids.WATER.getSource(false):super.getFluidState(state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new FactoryPanelBlockEntity(pos,state); }
    @Override public <T extends BlockEntity>BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,AllBlockEntityTypes.FACTORY_PANEL,FactoryPanelBlockEntity::tick); }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return LoadedLevelAccess.blockEntity(level,pos) instanceof FactoryPanelBlockEntity gauge?gauge.getShape():Shapes.block(); }
    @Override protected VoxelShape getCollisionShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return context instanceof EntityCollisionContext entity&&entity.getEntity()==null?getShape(state,level,pos,context):Shapes.empty(); }
    @Override protected boolean canBeReplaced(BlockState state,BlockPlaceContext context) { if(!context.getItemInHand().is(asItem())||!(LoadedLevelAccess.blockEntity(context.getLevel(),context.getClickedPos()) instanceof FactoryPanelBlockEntity gauge))return false;return !gauge.panels.get(getTargetedSlot(context.getClickedPos(),state,context.getClickLocation())).active; }
    @Override protected boolean isSignalSource(BlockState state) { return true; }
    @Override protected int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side) { return state.getValue(POWERED)?15:0; }
    @Override protected int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side) { return state.getValue(POWERED)&&connectedDirection(state)==side?15:0; }
    @Override protected InteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit) {
        if(!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,pos))return InteractionResult.PASS;var slot=getTargetedSlot(pos,state,hit.getLocation());if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof FactoryPanelBlockEntity gauge))return InteractionResult.PASS;var panel=gauge.panels.get(slot);
        if(stack.is(AllBlocks.FACTORY_GAUGE.asItem())&&!panel.active){if(!FactoryPanelBlockItem.isTuned(stack))return InteractionResult.FAIL;if(!level.isClientSide()&&gauge.addPanel(slot,LogisticallyLinkedBlockItem.networkFromStack(stack))&&!player.isCreative())stack.shrink(1);return InteractionResult.SUCCESS;}
        if(!panel.active)return InteractionResult.PASS;
        if(stack.is(AllItems.WRENCH))return InteractionResult.PASS;
        if(!level.isClientSide()&&!GlobalLogisticsManager.get(level).mayInteract(panel.network,player))return InteractionResult.SUCCESS;
        if(stack.getItem() instanceof LogisticallyLinkedBlockItem){if(!level.isClientSide())LogisticallyLinkedBlockItem.assignFrequency(stack,player,panel.network);return InteractionResult.SUCCESS;}
        if(!level.isClientSide()){
            if(panel.getFilter().isEmpty()&&!stack.isEmpty())panel.setFilter(stack);else FactoryPanelMenu.open(player,panel);
        }return InteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit) { return useItemOn(ItemStack.EMPTY,state,level,pos,player,InteractionHand.MAIN_HAND,hit); }
    public InteractionResult onWrenched(UseOnContext context) { var level=context.getLevel();var pos=context.getClickedPos();if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof FactoryPanelBlockEntity gauge))return InteractionResult.PASS;var slot=getTargetedSlot(pos,gauge.getBlockState(),context.getClickLocation());if(context.getPlayer().isShiftKeyDown()){if(!level.isClientSide()&&net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level,context.getPlayer(),pos,gauge.getBlockState(),gauge)&&gauge.removePanel(slot)){if(!context.getPlayer().isCreative())context.getPlayer().getInventory().placeItemBackInInventory(new ItemStack(this),Prediction.SERVER_ONLY);if(gauge.activePanels()==0)level.destroyBlock(pos,false);}return InteractionResult.SUCCESS;}if(!level.isClientSide()){var panel=gauge.panels.get(slot);for(var target:panel.targeting){var other=FactoryPanelBehaviour.at(level,target);if(other!=null){var c=other.targetedBy.get(panel.getPanelPosition());if(c!=null)c.arrowBendMode=(c.arrowBendMode+1)%4;other.panelBE().notifyUpdate();}}}return InteractionResult.SUCCESS; }
}
