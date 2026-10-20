package com.simibubi.create.content.processing.burner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.ActorRenderer;
import com.simibubi.create.content.contraptions.render.VirtualRenderWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Source moving-burner visuals; all camera access stays in the client source set. */
public final class BlazeBurnerActorRenderer implements ActorRenderer<BlazeBurnerRenderer.State> {
    public static void registerTick() { BlazeBurnerMovementBehaviour.CLIENT_TICK = BlazeBurnerActorRenderer::tick; }
    private static void tick(MovementContext context) {
        if (context.position == null || !BlazeBurnerMovementBehaviour.shouldRender(context)) return;
        var random = context.world.getRandom();
        if (random.nextInt(3) == 0 && context.motion.lengthSqr() < 1 / (64d * 64)) {
            var p = context.position;
            context.world.addParticle(ParticleTypes.LARGE_SMOKE, p.x + (random.nextDouble() - .5) * .25, p.y, p.z + (random.nextDouble() - .5) * .25, 0, 0, 0);
        }
        var camera = Minecraft.getInstance().getCameraEntity();
        float target = targetAngle(context, camera == null || camera.isInvisible() ? null : camera.position());
        BlazeBurnerMovementBehaviour.headAngle(context, target).tick(target);
    }
    public static float targetAngle(MovementContext context, Vec3 camera) {
        if (camera == null || context.position == null || context.contraption.entity == null) return 0;
        var local = context.contraption.entity.reverseRotation(camera.subtract(context.position), 1);
        return (float) -Math.toDegrees(Math.atan2(local.z, local.x)) - 90;
    }
    @Override public BlazeBurnerRenderer.State extract(MovementContext context, VirtualRenderWorld world, float partial, Vec3 camera) {
        if (!BlazeBurnerMovementBehaviour.shouldRender(context) || !(world.getBlockEntity(context.localPos) instanceof BlazeBurnerBlockEntity burner)) return null;
        var worldCamera = context.contraption.entity.toGlobalVector(camera, partial);
        return BlazeBurnerRenderer.inContraption(burner, partial, BlazeBurnerMovementBehaviour.headAngle(context, targetAngle(context, worldCamera)).getValue(partial));
    }
    @Override public void submit(BlazeBurnerRenderer.State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) { BlazeBurnerRenderer.draw(state, pose, collector); }
}
