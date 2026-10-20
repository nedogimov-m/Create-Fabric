package com.simibubi.create.content.fluids.tank;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import java.util.Locale;

public final class FluidTankBlock extends BaseEntityBlock {
    public static final BooleanProperty TOP = BooleanProperty.create("top"), BOTTOM = BooleanProperty.create("bottom");
    public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    private static final VoxelShape SMOKE_CLIP = box(0, 4, 0, 16, 16, 16);
    public final boolean creative;
    public FluidTankBlock(Properties properties, boolean creative) {
        super(properties.lightLevel(state -> state.getValue(LIGHT))); this.creative = creative;
        registerDefaultState(stateDefinition.any().setValue(TOP, true).setValue(BOTTOM, true).setValue(SHAPE, Shape.WINDOW).setValue(LIGHT, 0));
    }
    @Override public void setPlacedBy(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof FluidTankBlockEntity device)device.sourceAdvancements.place(placer);}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(TOP, BOTTOM, SHAPE, LIGHT); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return context == CollisionContext.empty() ? SMOKE_CLIP : Shapes.block(); }
    @Override protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.block(); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return creative ? new CreativeFluidTankBlockEntity(pos, state) : new FluidTankBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return creative ? createTickerHelper(type, AllBlockEntityTypes.CREATIVE_FLUID_TANK, FluidTankBlockEntity::tick) : createTickerHelper(type, AllBlockEntityTypes.FLUID_TANK, FluidTankBlockEntity::tick); }
    @Override protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        if (state.getBlock() != oldState.getBlock() && level.getBlockEntity(pos) instanceof FluidTankBlockEntity be) be.requestConnectivityUpdate();
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block neighbor,net.minecraft.world.level.redstone.Orientation orientation,boolean moved){
        if(!level.isClientSide()&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos) instanceof FluidTankBlockEntity tank){tank.updateBoilerState();tank.updateBoilerTemperature();}
    }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction side) {
        var master = level.getBlockEntity(pos) instanceof FluidTankBlockEntity part ? part.getControllerBE() : null;
        float fill = master == null ? 0 : master.getFillState(); return fill == 0 ? 0 : Math.min(15, 1 + (int) (fill * 14));
    }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.mayBuild() || !level.mayInteract(player, pos) || !creative && !player.isCreative()) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof FluidTankBlockEntity part) || part.getControllerBE() == null) return InteractionResult.PASS;
        var context = ContainerItemContext.forPlayerInteraction(player, hand); var item = context.find(FluidStorage.ITEM);
        if (item == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var master = part.getControllerBE(); FluidStack content = FluidStack.EMPTY;
        if (creative) for (var view : item) if (!view.isResourceBlank() && view.getAmount() > 0) { content = new FluidStack(view.getResource(), view.getAmount()); break; }
        if (FluidStorageUtil.interactWithFluidStorage(part.storage(), player, hand)) {
            if (creative && master.getTankInventory() instanceof CreativeFluidTankBlockEntity.CreativeSmartFluidTank tank) tank.setContainedFluid(content);
            master.sendDataImmediately();
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) {
        var shape = state.getValue(SHAPE);
        for (int i = 0; i < rotation.ordinal(); i++) shape = switch (shape) { case WINDOW_NE -> Shape.WINDOW_SE; case WINDOW_SE -> Shape.WINDOW_SW; case WINDOW_SW -> Shape.WINDOW_NW; case WINDOW_NW -> Shape.WINDOW_NE; default -> shape; };
        return state.setValue(SHAPE, shape);
    }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) return state;
        boolean x = mirror == Mirror.FRONT_BACK;
        return state.setValue(SHAPE, switch (state.getValue(SHAPE)) { case WINDOW_NE -> x ? Shape.WINDOW_NW : Shape.WINDOW_SE; case WINDOW_NW -> x ? Shape.WINDOW_NE : Shape.WINDOW_SW; case WINDOW_SE -> x ? Shape.WINDOW_SW : Shape.WINDOW_NE; case WINDOW_SW -> x ? Shape.WINDOW_SE : Shape.WINDOW_NW; default -> state.getValue(SHAPE); });
    }
    public enum Shape implements StringRepresentable { PLAIN, WINDOW, WINDOW_NW, WINDOW_SW, WINDOW_NE, WINDOW_SE; @Override public String getSerializedName() { return name().toLowerCase(Locale.ROOT); } }
}
