package com.simibubi.create.foundation.blockEntity.behaviour;

import java.util.function.Function;

import org.apache.commons.lang3.tuple.Pair;

import org.joml.Matrix4f;



import net.createmod.catnip.math.AngleHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public abstract class ValueBoxTransform {

	protected float scale = getScale();

	public abstract Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state);

	public abstract void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms);

	public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
		Vec3 offset = getLocalOffset(level, pos, state);
		if (offset == null)
			return false;
		return localHit.distanceTo(offset) < scale / 2;
	}

	public void transform(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
		Vec3 position = getLocalOffset(level, pos, state);
		if (position == null)
			return;
		ms.translate((float)position.x, (float)position.y, (float)position.z);
		rotate(level, pos, state, ms);
		ms.scale(scale, scale, scale);
	}

	public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
		return !state.isAir() && getLocalOffset(level, pos, state) != null;
	}

	public int getOverrideColor() {
		return -1;
	}

	protected Vec3 rotateHorizontally(BlockState state, Vec3 vec) {
		float yRot = 0;
		if (state.hasProperty(BlockStateProperties.FACING))
			yRot = AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.FACING));
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
			yRot = AngleHelper.horizontalAngle(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
		return rotateCentered(vec, yRot, Axis.Y);
	}

	public float getScale() {
		return .5f;
	}

	public float getFontScale() {
		return 1 / 64f;
	}

    /** Same source vector math without resolving client-only Catnip utility classes. */
    protected static Vec3 rotateCentered(Vec3 vector,double degrees,Axis axis) {
        if(degrees==0)return vector;
        var shifted=vector.subtract(.5,.5,.5);float radians=(float)(degrees/180f*Math.PI);
        double sin=net.minecraft.util.Mth.sin(radians),cos=net.minecraft.util.Mth.cos(radians);
        double x=shifted.x,y=shifted.y,z=shifted.z;
        return (switch(axis) {
            case X -> new Vec3(x,y*cos-z*sin,z*cos+y*sin);
            case Y -> new Vec3(x*cos+z*sin,y,z*cos-x*sin);
            case Z -> new Vec3(x*cos-y*sin,y*cos+x*sin,z);
        }).add(.5,.5,.5);
    }

	public static abstract class Dual extends ValueBoxTransform {

		protected boolean first;

		public Dual(boolean first) {
			this.first = first;
		}

		public boolean isFirst() {
			return first;
		}

		public static Pair<ValueBoxTransform, ValueBoxTransform> makeSlots(Function<Boolean, ? extends Dual> factory) {
			return Pair.of(factory.apply(true), factory.apply(false));
		}

		@Override
		public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
			Vec3 offset = getLocalOffset(level, pos, state);
			if (offset == null)
				return false;
			return localHit.distanceTo(offset) < scale / 3.5f;
		}

	}

	public static abstract class Sided extends ValueBoxTransform {

		protected Direction direction = Direction.UP;

		public Sided fromSide(Direction direction) {
			this.direction = direction;
			return this;
		}

		@Override
		public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
			Vec3 location = getSouthLocation();
			location = rotateCentered(location, AngleHelper.horizontalAngle(getSide()), Axis.Y);
			location = rotateCentered(location, AngleHelper.verticalAngle(getSide()), Axis.X);
			return location;
		}

		protected abstract Vec3 getSouthLocation();

		@Override
		public void rotate(LevelAccessor level, BlockPos pos, BlockState state, Matrix4f ms) {
			float yRot = AngleHelper.horizontalAngle(getSide()) + 180;
			float xRot = getSide() == Direction.UP ? 90 : getSide() == Direction.DOWN ? 270 : 0;
            ms.rotateY(AngleHelper.rad(yRot)).rotateX(AngleHelper.rad(xRot));
		}

		@Override
		public boolean shouldRender(LevelAccessor level, BlockPos pos, BlockState state) {
			return super.shouldRender(level, pos, state) && isSideActive(state, getSide());
		}

		@Override
		public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 localHit) {
			return isSideActive(state, getSide()) && super.testHit(level, pos, state, localHit);
		}

		protected boolean isSideActive(BlockState state, Direction direction) {
			return true;
		}

		public Direction getSide() {
			return direction;
		}

	}

}
