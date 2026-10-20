package com.simibubi.create.content.contraptions.actors.psi;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

public final class PortableStorageInterfaceActorRenderer implements ActorRenderer<PortableStorageInterfaceActorRenderer.State> {
    public record State(PortableStorageInterfaceRenderer renderer, PortableStorageInterfaceRenderer.State state) {}
    @Override public State extract(MovementContext context, VirtualRenderWorld world, float partial, Vec3 camera) {
        if (!(world.getBlockEntity(context.localPos) instanceof PortableStorageInterfaceBlockEntity entity)) return null;
        net.minecraft.client.renderer.blockentity.BlockEntityRenderer<?, ?> found = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
        if (!(found instanceof PortableStorageInterfaceRenderer renderer)) return null;
        var state = renderer.createRenderState(); renderer.extractActorRenderState(entity, state, context, partial, camera);
        return new State(renderer, state);
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        state.renderer.submit(state.state, pose, collector, camera);
    }
}
