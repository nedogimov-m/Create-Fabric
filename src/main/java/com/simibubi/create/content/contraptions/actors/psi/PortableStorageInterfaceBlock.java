package com.simibubi.create.content.contraptions.actors.psi;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.shapes.*;

/** Original six-way item/fluid endpoint. All sides expose the same connected storage. */
public final class PortableStorageInterfaceBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public final boolean fluids;

    public static PortableStorageInterfaceBlock forItems(Properties properties) { return new PortableStorageInterfaceBlock(properties, false); }
    public static PortableStorageInterfaceBlock forFluids(Properties properties) { return new PortableStorageInterfaceBlock(properties, true); }

    private PortableStorageInterfaceBlock(Properties properties, boolean fluids) {
        super(properties.strength(fluids ? 3 : 1.5f, 6).requiresCorrectToolForDrops().sound(fluids ? SoundType.COPPER : SoundType.STONE).mapColor(fluids ? MapColor.TERRACOTTA_LIGHT_GRAY : MapColor.PODZOL).noOcclusion());
        this.fluids = fluids;
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var direction = context.getNearestLookingDirection();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) direction = direction.getOpposite();
        return defaultBlockState().setValue(FACING, direction.getOpposite());
    }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> box(0, 0, 0, 16, 14, 16);
            case DOWN -> box(0, 2, 0, 16, 16, 16);
            case NORTH -> box(0, 0, 2, 16, 16, 16);
            case SOUTH -> box(0, 0, 0, 16, 16, 14);
            case WEST -> box(2, 0, 0, 16, 16, 16);
            case EAST -> box(0, 0, 0, 14, 16, 16);
        };
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(placer instanceof net.minecraft.server.level.ServerPlayer player&&LoadedLevelAccess.blockEntity(level,pos) instanceof PortableStorageInterfaceBlockEntity endpoint)endpoint.placedBy(player);}
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return fluids ? new PortableFluidInterfaceBlockEntity(pos, state) : new PortableItemInterfaceBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return fluids ? createTickerHelper(type, AllBlockEntityTypes.PORTABLE_FLUID_INTERFACE, PortableStorageInterfaceBlockEntity::tick)
            : createTickerHelper(type, AllBlockEntityTypes.PORTABLE_STORAGE_INTERFACE, PortableStorageInterfaceBlockEntity::tick);
    }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, Orientation orientation, boolean moving) {
        if (LoadedLevelAccess.blockEntity(level, pos) instanceof PortableStorageInterfaceBlockEntity endpoint) endpoint.neighbourChanged();
    }
    @Override protected boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction side) {
        return LoadedLevelAccess.blockEntity(level, pos) instanceof PortableStorageInterfaceBlockEntity endpoint && endpoint.isConnected() ? 15 : 0;
    }
    @Override protected BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(FACING, rotation.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(FACING))); }
}
