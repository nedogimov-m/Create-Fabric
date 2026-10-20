package com.simibubi.create.content.contraptions.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Native actor rendering API: extract live inputs once, submit only the detached state. */
public interface ActorRenderer<S> {
    SimpleRegistry<Block,ActorRenderer<?>> REGISTRY=SimpleRegistry.create();
    @Nullable S extract(MovementContext context,VirtualRenderWorld world,float partial,Vec3 camera);
    void submit(S state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera);
}
