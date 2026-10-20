package com.simibubi.create.content.kinetics.simpleRelays;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class CogWheelBlock extends ShaftBlock implements ICogWheel {
    private final boolean large;

    public CogWheelBlock(Properties properties, boolean large) {
        super(properties);
        this.large = large;
    }

    @Override public boolean isDedicatedCogWheel() { return true; }
    @Override protected boolean canSurvive(BlockState state,net.minecraft.world.level.LevelReader level,BlockPos pos){
        return isValidCogwheelPosition(large,level,pos,state.getValue(AXIS));
    }
    protected net.minecraft.core.Direction.Axis getAxisForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){
        var assisted=CogwheelBlockItem.assistedAxis(context);if(assisted!=null)return assisted;
        if(context.getPlayer()!=null&&context.getPlayer().isShiftKeyDown())return context.getClickedFace().getAxis();
        var level=context.getLevel();var below=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,context.getClickedPos().below());
        if(large&&below.is(com.simibubi.create.AllBlocks.ROTATION_SPEED_CONTROLLER))return below.getValue(com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock.HORIZONTAL_AXIS)==net.minecraft.core.Direction.Axis.X?net.minecraft.core.Direction.Axis.Z:net.minecraft.core.Direction.Axis.X;
        var support=context.getClickedPos().relative(context.getClickedFace().getOpposite());var against=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,support);
        if(ICogWheel.isSmallCog(against))return ((com.simibubi.create.content.kinetics.base.IRotate)against.getBlock()).getRotationAxis(against);
        var preferred=com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock.getPreferredAxis(context);
        return preferred!=null?preferred:context.getClickedFace().getAxis();
    }
    @Override public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context){
        return defaultBlockState().setValue(AXIS,getAxisForPlacement(context))
                .setValue(WATERLOGGED,context.getLevel().getFluidState(context.getClickedPos()).getType()==net.minecraft.world.level.material.Fluids.WATER);
    }
    @Override public void setPlacedBy(net.minecraft.world.level.Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){
        super.setPlacedBy(level,pos,state,placer,stack);
        if(level.isClientSide()||!(placer instanceof net.minecraft.world.entity.player.Player player))return;
        var axis=state.getValue(AXIS);
        for(var first:net.minecraft.core.Direction.values())for(var second:net.minecraft.core.Direction.values()){
            if(first.getAxis()==axis||second.getAxis()==axis||first.getAxis()==second.getAxis())continue;
            var other=com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,pos.relative(first).relative(second));
            if(other.getBlock() instanceof CogWheelBlock wheel&&wheel.large!=large&&other.getValue(AXIS)==axis){com.simibubi.create.foundation.advancement.AllAdvancements.COGS.awardTo(player);return;}
        }
    }
    public static boolean isValidCogwheelPosition(boolean large, net.minecraft.world.level.LevelReader level,
            BlockPos pos, net.minecraft.core.Direction.Axis axis) {
        for (var side : net.minecraft.core.Direction.values()) {
            if (side.getAxis() == axis) continue;
            var state = com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level, pos.relative(side));
            if (state.hasProperty(AXIS) && state.getValue(AXIS) == side.getAxis()) continue;
            if (ICogWheel.isLargeCog(state) || large && ICogWheel.isSmallCog(state)) return false;
        }
        return true;
    }
    public boolean isLargeCog() {
        return large;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        double min = large ? 0 : 2;
        double max = large ? 16 : 14;
        VoxelShape gear = switch (getRotationAxis(state)) {
            case X -> box(6, min, min, 10, max, max);
            case Y -> box(min, 6, min, max, 10, max);
            case Z -> box(min, min, 6, max, max, 10);
        };
        return Shapes.or(super.getShape(state, level, pos, context), gear);
    }
}
