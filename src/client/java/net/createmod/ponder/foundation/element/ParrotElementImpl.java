// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.element;

import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.createmod.catnip.math.AngleHelper;
import net.createmod.ponder.api.element.ParrotElement;
import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.phys.Vec3;

public class ParrotElementImpl extends AnimatedSceneElementBase implements ParrotElement {

	protected Vec3 location;
	@Nullable
	protected Parrot entity;
	protected ParrotPose pose;
	protected Supplier<? extends ParrotPose> initialPose;

	public static ParrotElement create(Vec3 location, Supplier<? extends ParrotPose> pose) {
		return new ParrotElementImpl(location, pose);
	}

	protected ParrotElementImpl(Vec3 location, Supplier<? extends ParrotPose> pose) {
		this.location = location;
		initialPose = pose;
		this.pose = initialPose.get();
	}

	@Override
	public void reset(PonderScene scene) {
		super.reset(scene);
		if(entity==null) entity=pose.create(scene.getWorld());
		setPose(initialPose.get());
		entity.setPosRaw(0, 0, 0);
		entity.xo = 0;
		entity.yo = 0;
		entity.zo = 0;
		entity.xOld = 0;
		entity.yOld = 0;
		entity.zOld = 0;
		entity.setXRot(entity.xRotO = 0);
		entity.setYRot(entity.yRotO = 180);
	}

	@Override
	public void tick(PonderScene scene) {
		super.tick(scene);
		if (entity == null) {
			entity = pose.create(scene.getWorld());
			entity.setYRot(entity.yRotO = 180);
		}

		entity.tickCount++;
		entity.yHeadRotO = entity.yHeadRot;
		entity.oFlapSpeed = entity.flapSpeed;
		entity.oFlap = entity.flap;
		entity.setOnGround(true);

		entity.xo = entity.getX();
		entity.yo = entity.getY();
		entity.zo = entity.getZ();
		entity.yRotO = entity.getYRot();
		entity.xRotO = entity.getXRot();

		pose.tick(scene, entity, location);

		entity.xOld = entity.getX();
		entity.yOld = entity.getY();
		entity.zOld = entity.getZ();
	}

	@Override
	public void setPositionOffset(Vec3 position, boolean immediate) {
		if (entity == null)
			return;
		entity.setPos(position.x, position.y, position.z);
		if (!immediate)
			return;
		entity.xo = position.x;
		entity.yo = position.y;
		entity.zo = position.z;
	}

	@Override
	public void setRotation(Vec3 eulers, boolean immediate) {
		if (entity == null)
			return;
		entity.setXRot((float) eulers.x);
		entity.setYRot((float) eulers.y);
		if (!immediate)
			return;
		entity.xRotO = entity.getXRot();
		entity.yRotO = entity.getYRot();
	}

	@Override
	public Vec3 getPositionOffset() {
		return entity != null ? entity.position() : Vec3.ZERO;
	}

	@Override
	public Vec3 getRotation() {
		return entity != null ? new Vec3(entity.getXRot(), entity.getYRot(), 0) : Vec3.ZERO;
	}

	@Override
	public void setPose(ParrotPose pose) {
		this.pose = pose;
	}

	public net.minecraft.world.entity.Entity extractEntity(PonderScene scene) {
		if(entity==null) entity=pose.create(scene.getWorld());
		return entity;
	}
	public Vec3 extractLocation() { return location; }
	/** Source virtual render transform, captured before render submission. */
	public PoseStack extractTransform(PonderScene scene, float partial) {
		var current = extractEntity(scene);
		var transform = new PoseStack();
		applyFade(transform, partial);
		transform.translate(location);
		transform.translate(Mth.lerp(partial, current.xo, current.getX()),
			Mth.lerp(partial, current.yo, current.getY()), Mth.lerp(partial, current.zo, current.getZ()));
		transform.rotateDegrees(Axis.YP, AngleHelper.angleLerp(partial, current.yRotO, current.getYRot()));
		return transform;
	}

	/** Real source subclasses may capture native held model states alongside their entity. */
	public java.util.List<net.createmod.ponder.foundation.render.PonderSceneFrame.Item> extractAdditionalItems(
		PonderScene scene, float partial, PoseStack transform) {
		return java.util.List.of();
	}

}
