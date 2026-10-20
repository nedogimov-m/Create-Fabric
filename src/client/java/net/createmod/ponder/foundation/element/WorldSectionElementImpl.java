// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WorldSectionElementImpl extends AnimatedSceneElementBase implements WorldSectionElement {
  private final net.createmod.ponder.foundation.render.PonderSectionRenderer nativeRenderer =
      new net.createmod.ponder.foundation.render.PonderSectionRenderer();

	@Nullable
	List<BlockEntity> renderedBlockEntities;
	@Nullable
	List<Pair<BlockEntity, Consumer<Level>>> tickableBlockEntities;
	@Nullable
	Selection section;
	boolean redraw;

	Vec3 prevAnimatedOffset = Vec3.ZERO;
	Vec3 animatedOffset = Vec3.ZERO;
	Vec3 prevAnimatedRotation = Vec3.ZERO;
	Vec3 animatedRotation = Vec3.ZERO;
	Vec3 centerOfRotation = Vec3.ZERO;
	@Nullable
	Vec3 stabilizationAnchor = null;

	@Nullable
	BlockPos selectedBlock;

	public WorldSectionElementImpl() {
	}

	public WorldSectionElementImpl(Selection section) {
		this.section = section.copy();
		centerOfRotation = section.getCenter();
	}

	@Override
	public void mergeOnto(WorldSectionElement other) {
		setVisible(false);
		if (other.isEmpty())
			other.set(section);
		else
			other.add(section);
	}

	@Override
	public void set(Selection selection) {
		applyNewSelection(selection.copy());
	}

	@Override
	public void add(Selection toAdd) {
		applyNewSelection(this.section.add(toAdd));
	}

	@Override
	public void erase(Selection toErase) {
		applyNewSelection(this.section.substract(toErase));
	}

	private void applyNewSelection(Selection selection) {
		this.section = selection;
		queueRedraw();
	}

	@Override
	public void setCenterOfRotation(Vec3 center) {
		centerOfRotation = center;
	}

	@Override
	public void stabilizeRotation(Vec3 anchor) {
		stabilizationAnchor = anchor;
	}

	@Override
	public void reset(PonderScene scene) {
		super.reset(scene);
		resetAnimatedTransform();
		resetSelectedBlock();
	}

	@Override
	public void selectBlock(BlockPos pos) {
		selectedBlock = pos;
	}

	@Override
	public void resetSelectedBlock() {
		selectedBlock = null;
	}

	public void resetAnimatedTransform() {
		prevAnimatedOffset = Vec3.ZERO;
		animatedOffset = Vec3.ZERO;
		prevAnimatedRotation = Vec3.ZERO;
		animatedRotation = Vec3.ZERO;
	}

	@Override
	public void queueRedraw() {
		redraw = true;
    nativeRenderer.invalidate();
	}

	@Override
	public boolean isEmpty() {
		return section == null;
	}

	@Override
	public void setEmpty() {
		section = null;
	}

	@Override
	public void setAnimatedRotation(Vec3 eulerAngles, boolean force) {
		this.animatedRotation = eulerAngles;
		if (force)
			prevAnimatedRotation = animatedRotation;
	}

	@Override
	public Vec3 getAnimatedRotation() {
		return animatedRotation;
	}

	@Override
	public void setAnimatedOffset(Vec3 offset, boolean force) {
		this.animatedOffset = offset;
		if (force)
			prevAnimatedOffset = animatedOffset;
	}

	@Override
	public Vec3 getAnimatedOffset() {
		return animatedOffset;
	}

	@Override
	public boolean isVisible() {
		return super.isVisible() && !isEmpty();
	}

	@Override
	public Pair<Vec3, BlockHitResult> rayTrace(PonderLevel world, Vec3 source, Vec3 target) {
		world.setMask(this.section);
		Vec3 transformedTarget = reverseTransformVec(target, world.scenePartialTicks());
        BlockHitResult rayTraceBlocks;
        try {
            rayTraceBlocks = world.clip(new ClipContext(reverseTransformVec(source, world.scenePartialTicks()), transformedTarget,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, CollisionContext.empty()));
        } finally { world.clearMask(); }

		double t = rayTraceBlocks.getLocation()
			.subtract(transformedTarget)
			.lengthSqr()
			/ source.subtract(target)
			.lengthSqr();
		Vec3 actualHit = VecHelper.lerp((float) t, target, source);
		return Pair.of(actualHit, rayTraceBlocks);
	}

	private Vec3 reverseTransformVec(Vec3 in, float pt) {
		in = in.subtract(VecHelper.lerp(pt, prevAnimatedOffset, animatedOffset));
		if (!animatedRotation.equals(Vec3.ZERO) || !prevAnimatedRotation.equals(Vec3.ZERO)) {
			double rotX = Mth.lerp(pt, prevAnimatedRotation.x, animatedRotation.x);
			double rotZ = Mth.lerp(pt, prevAnimatedRotation.z, animatedRotation.z);
			double rotY = Mth.lerp(pt, prevAnimatedRotation.y, animatedRotation.y);
			in = in.subtract(centerOfRotation);
			in = VecHelper.rotate(in, -rotX, Axis.X);
			in = VecHelper.rotate(in, -rotZ, Axis.Z);
			in = VecHelper.rotate(in, -rotY, Axis.Y);
			in = in.add(centerOfRotation);
			if (stabilizationAnchor != null) {
				in = in.subtract(stabilizationAnchor);
				in = VecHelper.rotate(in, rotX, Axis.X);
				in = VecHelper.rotate(in, rotZ, Axis.Z);
				in = VecHelper.rotate(in, rotY, Axis.Y);
				in = in.add(stabilizationAnchor);
			}
		}
		return in;
	}

	public void transformMS(PoseStack ms, float pt) {

		Vec3 vec = VecHelper.lerp(pt, prevAnimatedOffset, animatedOffset);
		ms.translate(vec.x, vec.y, vec.z);
		if (!animatedRotation.equals(Vec3.ZERO) || !prevAnimatedRotation.equals(Vec3.ZERO)) {
			double rotX = Mth.lerp(pt, prevAnimatedRotation.x, animatedRotation.x);
			double rotZ = Mth.lerp(pt, prevAnimatedRotation.z, animatedRotation.z);
			double rotY = Mth.lerp(pt, prevAnimatedRotation.y, animatedRotation.y);

			ms.translate(centerOfRotation.x, centerOfRotation.y, centerOfRotation.z);
			ms.rotateDegrees(com.mojang.math.Axis.XP, (float) rotX);
			ms.rotateDegrees(com.mojang.math.Axis.YP, (float) rotY);
			ms.rotateDegrees(com.mojang.math.Axis.ZP, (float) rotZ);
			ms.translate(-centerOfRotation.x, -centerOfRotation.y, -centerOfRotation.z);

			if (stabilizationAnchor != null) {
				ms.translate(stabilizationAnchor.x, stabilizationAnchor.y, stabilizationAnchor.z);
				ms.rotateDegrees(com.mojang.math.Axis.XP, (float) -rotX);
				ms.rotateDegrees(com.mojang.math.Axis.YP, (float) -rotY);
				ms.rotateDegrees(com.mojang.math.Axis.ZP, (float) -rotZ);
				ms.translate(-stabilizationAnchor.x, -stabilizationAnchor.y, -stabilizationAnchor.z);
			}
		}
	}

	@Override
	public void tick(PonderScene scene) {
		prevAnimatedOffset = animatedOffset;
		prevAnimatedRotation = animatedRotation;
		if (!isVisible())
			return;
		if (redraw) { renderedBlockEntities = null; tickableBlockEntities = null; redraw = false; }
		loadBEsIfMissing(scene.getWorld());
		renderedBlockEntities.removeIf(be -> scene.getWorld()
			.getBlockEntity(be.getBlockPos()) != be);
		tickableBlockEntities.removeIf(be -> scene.getWorld()
			.getBlockEntity(be.getFirst()
				.getBlockPos()) != be.getFirst());
		tickableBlockEntities.forEach(be -> be.getSecond()
			.accept(scene.getWorld()));
	}

	@Override
	public void whileSkipping(PonderScene scene) {
		if (redraw) {
			renderedBlockEntities = null;
			tickableBlockEntities = null;
		}
		redraw = false;
	}

	protected void loadBEsIfMissing(PonderLevel world) {
		if (renderedBlockEntities != null)
			return;
		tickableBlockEntities = new ArrayList<>();
		renderedBlockEntities = new ArrayList<>();
		section.forEach(pos -> {
			BlockEntity blockEntity = world.getBlockEntity(pos);
			BlockState blockState = world.getBlockState(pos);
			Block block = blockState.getBlock();
			if (blockEntity == null)
				return;
			if (!(block instanceof EntityBlock))
				return;
			blockEntity.setBlockState(world.getBlockState(pos));
			BlockEntityTicker<?> ticker = ((EntityBlock) block).getTicker(world, blockState, blockEntity.getType());
			if (ticker != null)
				addTicker(blockEntity, ticker);
			renderedBlockEntities.add(blockEntity);
		});
	}

	@SuppressWarnings("unchecked")
	private <T extends BlockEntity> void addTicker(T blockEntity, BlockEntityTicker<?> ticker) {
		tickableBlockEntities.add(Pair.of(blockEntity, w -> ((BlockEntityTicker<T>) ticker).tick(w,
			blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity)));
	}

  /** Native world-fluid pass captured with this same real section mask/fade/transform. */
  public net.createmod.ponder.foundation.render.PonderFluidRenderState extractFluids(PonderLevel world, float partial) {
    var pose = new PoseStack();
    applyFade(pose, partial);
    transformMS(pose, partial);
    if (section == null)
      return new net.createmod.ponder.foundation.render.PonderFluidRenderState(pose.last().pose(), List.of());
    return net.createmod.ponder.foundation.render.PonderFluidRenderState.extract(world, section, pose.last().pose());
  }

  /** Capture native breaking meshes under this actual section's neighbor mask. */
  public net.createmod.ponder.foundation.render.PonderBreakingRenderState extractBreaking(PonderLevel world, float partial) {
    var pose = new PoseStack();
    applyFade(pose, partial);
    transformMS(pose, partial);
    if (section == null)
      return new net.createmod.ponder.foundation.render.PonderBreakingRenderState(pose.last().pose(), List.of());
    return net.createmod.ponder.foundation.render.PonderBreakingRenderState.extract(world, section, pose.last().pose());
  }

  /** Real native meshes/BE states frozen during extraction; no world reads during submit. */
  public net.createmod.ponder.foundation.render.PonderSceneFrame.Section extract(
      PonderLevel world, float partial, Vec3 camera) {
    var pose = new PoseStack();
    float currentFade = applyFade(pose, partial);
    transformMS(pose, partial);
    int lightLevel = currentFade == 1 ? -1 : (int) Mth.lerp(currentFade, 5, 15);
    return nativeRenderer.extract(world, section, pose.last().pose(), partial, camera,
        lightCoordsFromFade(currentFade), lightLevel, selectedBlock);
  }

}
