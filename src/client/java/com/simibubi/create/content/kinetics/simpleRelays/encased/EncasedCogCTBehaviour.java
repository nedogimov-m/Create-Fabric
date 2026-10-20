package com.simibubi.create.content.kinetics.simpleRelays.encased;

import com.simibubi.create.content.decoration.encasing.*;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

public final class EncasedCogCTBehaviour extends EncasedCTBehaviour {
    private final CTSpriteShiftEntry vertical,horizontal;
    private final boolean large;
    public EncasedCogCTBehaviour(CasingConnectivity connectivity,CTSpriteShiftEntry casing,CTSpriteShiftEntry vertical,CTSpriteShiftEntry horizontal) {
        super(connectivity,casing); this.vertical = vertical; this.horizontal = horizontal; large = vertical == null;
    }
    @Override public boolean connectsTo(BlockState state,BlockState other,BlockGetter reader,BlockPos pos,BlockPos target,Direction face) {
        var axis = state.getValue(EncasedCogwheelBlock.AXIS);
        if (large || axis == face.getAxis()) return super.connectsTo(state,other,reader,pos,target,face);
        if (state.getBlock() == other.getBlock() && axis == other.getValue(EncasedCogwheelBlock.AXIS)) return true;
        var out = LoadedLevelAccess.blockState(reader,target.relative(face));
        return ICogWheel.isLargeCog(out) && ((IRotate)out.getBlock()).getRotationAxis(out) == axis;
    }
    @Override protected boolean reverseUVs(BlockState state,Direction face) {
        return state.getValue(EncasedCogwheelBlock.AXIS).isHorizontal() && face.getAxis().isHorizontal()
                && face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
    }
    @Override protected boolean reverseUVsVertically(BlockState state,Direction face) {
        if (!large && state.getValue(EncasedCogwheelBlock.AXIS) == Direction.Axis.X && face.getAxis() == Direction.Axis.Z) return face != Direction.SOUTH;
        return super.reverseUVsVertically(state,face);
    }
    @Override protected boolean reverseUVsHorizontally(BlockState state,Direction face) {
        if (!large && (state.getValue(EncasedCogwheelBlock.AXIS).isVertical() && face.getAxis().isHorizontal()
                || state.getValue(EncasedCogwheelBlock.AXIS) == Direction.Axis.Z && face == Direction.DOWN)) return true;
        return super.reverseUVsHorizontally(state,face);
    }
    @Override public CTSpriteShiftEntry getShift(BlockState state,Direction face) {
        var axis = state.getValue(EncasedCogwheelBlock.AXIS);
        if (large || axis == face.getAxis()) {
            if (axis == face.getAxis() && state.getValue(face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? EncasedCogwheelBlock.TOP_SHAFT : EncasedCogwheelBlock.BOTTOM_SHAFT)) return null;
            return super.getShift(state,face);
        }
        return axis == Direction.Axis.X || axis == Direction.Axis.Z && face.getAxis() == Direction.Axis.X ? vertical : horizontal;
    }
}
