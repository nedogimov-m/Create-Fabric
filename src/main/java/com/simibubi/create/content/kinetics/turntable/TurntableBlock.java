package com.simibubi.create.content.kinetics.turntable;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;

public final class TurntableBlock extends KineticBlock {
    private static final VoxelShape SHAPE = Shapes.or(box(1,4,1,15,8,15),box(5,0,5,11,4,11));
    public TurntableBlock(Properties properties) { super(properties); }
    @Override protected boolean isPathfindable(BlockState state,net.minecraft.world.level.pathfinder.PathComputationType type) { return false; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context) { return SHAPE; }
    @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
    @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction face) { return face == Direction.DOWN; }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new TurntableBlockEntity(pos,state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) { return createTickerHelper(type,AllBlockEntityTypes.TURNTABLE,com.simibubi.create.content.kinetics.base.KineticBlockEntity::tick); }
    @Override protected void entityInside(BlockState state,Level level,BlockPos pos,Entity entity,InsideBlockEffectApplier effects,boolean wasInside) {
        if (!entity.onGround() || entity.getDeltaMovement().y > 0 || entity.getY() < pos.getY()+.5) return;
        if (!(LoadedLevelAccess.blockEntity(level,pos) instanceof TurntableBlockEntity turntable)) return;
        float speed = turntable.getSpeed()*.3f;
        if (speed == 0) return;
        if (level.isClientSide() && entity instanceof Player && LoadedLevelAccess.blockState(level,entity.blockPosition()) != state) {
            var center = Vec3.atCenterOf(pos); var offset = entity.position().subtract(center);
            var rotated = offset.yRot((float)Math.toRadians(-Math.clamp(speed,-16,16)));
            entity.setDeltaMovement(entity.getDeltaMovement().add(center.add(rotated).subtract(entity.position())));
            entity.syncVelocity = true;
        }
        if (entity instanceof Player || level.isClientSide()) return;
        if (entity instanceof LivingEntity living) {
            float angle = entity.getYHeadRot()-speed; living.setNoActionTime(20);
            entity.setYBodyRot(angle); entity.setYHeadRot(angle); entity.setOnGround(false); entity.syncVelocity = true;
        }
        entity.setYRot(entity.getYRot()-speed);
    }
}
