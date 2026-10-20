package com.simibubi.create.content.contraptions.bearing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** One source counterrotating top, extracted before submission; no Flywheel/Sodium dependency. */
public final class StabilizedBearingActorRenderer implements ActorRenderer<StabilizedBearingActorRenderer.State> {
    public record State(PartialModelRenderState top, Quaternionf orientation, float counterRotation, int light) {}
    public static Quaternionf blockOrientation(Direction facing) {
        float horizontal = facing.getAxis().isVertical() ? 0 : facing.getOpposite().toYRot();
        if (facing.getAxis() == Direction.Axis.X) horizontal = -horizontal;
        float vertical = facing == Direction.UP ? -90 : facing == Direction.DOWN ? 90 : 0;
        return new Quaternionf().rotateY((float) Math.toRadians(horizontal)).rotateX((float) Math.toRadians(-90 - vertical));
    }
    @Override public State extract(MovementContext context, VirtualRenderWorld world, float partial, Vec3 camera) {
        var facing = context.state.getValue(BearingBlock.FACING);
        float counter = StabilizedBearingMovementBehaviour.getCounterRotationAngle(context, facing, partial) * facing.getAxisDirection().getStep();
        var rotation = new Quaternionf().rotationAxis((float) Math.toRadians(counter), facing.getStepX(), facing.getStepY(), facing.getStepZ()).mul(blockOrientation(facing));
        var top = new PartialModelRenderState(); AllPartialModels.extract(AllPartialModels.BEARING_TOP, top);
        return new State(top, rotation, counter, LightCoordsUtil.getLightCoords(world, context.localPos));
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose(); pose.translate(.5, .5, .5); pose.rotate(state.orientation()); pose.translate(-.5, -.5, -.5);
        state.top().submit(pose, collector, state.light(), OverlayTexture.NO_OVERLAY, 0); pose.popPose();
    }
}
