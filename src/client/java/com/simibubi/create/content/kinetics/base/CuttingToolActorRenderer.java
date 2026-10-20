package com.simibubi.create.content.kinetics.base;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

public final class CuttingToolActorRenderer implements ActorRenderer<CuttingToolActorRenderer.State> {
    public record State(CuttingToolRenderer renderer,CuttingToolRenderer.State state){}
    @Override public State extract(MovementContext context,VirtualRenderWorld world,float partial,Vec3 camera){
        if(!(world.getBlockEntity(context.localPos) instanceof KineticBlockEntity entity))return null;
        net.minecraft.client.renderer.blockentity.BlockEntityRenderer<?,?> nativeRenderer=Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
        if(!(nativeRenderer instanceof CuttingToolRenderer renderer))return null;
        var state=renderer.createRenderState();renderer.extractActorRenderState(entity,state,context,partial,camera);return new State(renderer,state);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){state.renderer.submit(state.state,pose,collector,camera);}
}
