package com.simibubi.create.content.kinetics.base;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.foundation.advancement.AdvancementBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

public abstract class KineticBlock extends BaseEntityBlock implements IRotate {
    protected KineticBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return AllBlockEntityTypes.createSimpleKineticOwner(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == AllBlockEntityTypes.KINETIC) return createTickerHelper(type, AllBlockEntityTypes.KINETIC, KineticBlockEntity::tick);
        return createTickerHelper(type, AllBlockEntityTypes.simpleType(state), KineticBlockEntity::tick);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        if (level instanceof ServerLevel serverLevel) {
            KineticNetworks.markDirty(serverLevel);
        }
        // A state-only change can retain exactly the same kinetic connections.
        if (level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic) {
            kinetic.preventSpeedUpdate = 0;
            if (oldState.getBlock() != state.getBlock()) return;
            if (state.hasBlockEntity() != oldState.hasBlockEntity()) return;
            if (!areStatesKineticallyEquivalent(oldState, state)) return;
            kinetic.preventSpeedUpdate = 2;
        }
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    protected boolean areStatesKineticallyEquivalent(BlockState oldState, BlockState newState) {
        if (oldState.getBlock() != newState.getBlock()) return false;
        return getRotationAxis(newState) == getRotationAxis(oldState);
    }

    @Override
    public void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int count) {
        if (level.isClientSide()) return;
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic)) return;
        if (kinetic.preventSpeedUpdate > 0) return;
        kinetic.warnOfMovement();
        kinetic.clearKineticInformation();
        kinetic.updateSpeed = true;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        AdvancementBehaviour.setPlacedBy(level, pos, placer);
    }

    public float getParticleTargetRadius() {
        return .65f;
    }

    public float getParticleInitialRadius() {
        return .75f;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   net.minecraft.world.level.block.Block neighbor, Orientation orientation, boolean moved) {
        if (level instanceof ServerLevel serverLevel) {
            KineticNetworks.markDirty(serverLevel);
        }
    }
}
