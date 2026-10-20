package com.simibubi.create.content.contraptions.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.render.BlockEntityRenderHelper.FailureState;
import com.simibubi.create.infrastructure.config.AllConfigs;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.*;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.RenderShape;

/** Extracts native block/BE states first; submit reads no entity, model or live world. */
public final class ContraptionEntityRenderer extends EntityRenderer<AbstractContraptionEntity,ContraptionEntityRenderer.State> {
    private final BlockModelResolver models;
    private final BlockEntityRenderDispatcher blockEntities;
    private final BlockDisplayContext display = BlockDisplayContext.create();
    public static final class State extends EntityRenderState {
        public record Block(BlockPos pos, BlockModelRenderState model,int light) {}
        public record BlockEntity(BlockPos pos,BlockEntityRenderState state,BlockEntityRenderer renderer,
                                  FailureState failure,boolean explainErrors) {}
        public record Actor(BlockPos pos,Object state,ActorRenderer renderer) {}
        public final List<Block> blocks=new ArrayList<>(); public final List<BlockEntity> blockEntities=new ArrayList<>();
        public final List<Actor> actors=new ArrayList<>();
        public float angle; public Direction.Axis axis; public int entityId;
        public org.joml.Quaternionf orientation=new org.joml.Quaternionf(); public net.minecraft.world.phys.Vec3 anchorOffset=net.minecraft.world.phys.Vec3.ZERO;
    }
    public ContraptionEntityRenderer(EntityRendererProvider.Context context) { super(context); models=context.getBlockModelResolver(); blockEntities=Minecraft.getInstance().getBlockEntityRenderDispatcher(); }
    @Override protected net.minecraft.world.phys.AABB getBoundingBoxForCulling(AbstractContraptionEntity entity,float partial){
        var box=super.getBoundingBoxForCulling(entity,partial);var structure=entity.getContraption();
        return structure!=null&&structure.getActors().stream().anyMatch(actor->actor.left.state().is(com.simibubi.create.AllBlocks.MECHANICAL_ROLLER))?box.inflate(2):box;
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(AbstractContraptionEntity entity,State state,float partial) {
        super.extractRenderState(entity,state,partial); state.blocks.clear();state.blockEntities.clear();state.actors.clear();state.entityId=entity.getId();
        state.orientation=entity.getOrientationQuaternion(partial); state.anchorOffset=entity.getAnchorVec().subtract(entity.position());
        if(entity instanceof ControlledContraptionEntity controlled){state.angle=controlled.getAngle(partial);state.axis=controlled.getRotationAxis();}else{state.angle=0;state.axis=Direction.Axis.Y;}
        if(entity instanceof OrientedContraptionEntity child&&entity.getVehicle() instanceof AbstractContraptionEntity parent&&parent.getContraption()!=null){var expected=parent.getContraption().getStabilizedSubContraptions().passengerPosition(child,partial);if(expected!=null)state.anchorOffset=state.anchorOffset.add(expected.subtract(state.x,state.y,state.z));}
        // Both native cart behaviors render between physics positions. The real mounted forest
        // follows that same extracted offset, including independent stabilized descendants.
        state.anchorOffset=state.anchorOffset.add(com.simibubi.create.content.contraptions.minecart.NativeCartRenderPose.adjustmentForAncestor(entity,partial));
        var contraption=entity.getContraption();if(contraption==null||contraption.disassembled)return;
        var view=entity.getClientRenderState() instanceof VirtualRenderWorld existing?existing:new VirtualRenderWorld(entity);entity.setClientRenderState(view);
        for(var info:contraption.getBlocks().values())if(info.state().getRenderShape()==RenderShape.MODEL){var model=view.models.extract(view,info.pos(),info.state());state.blocks.add(new State.Block(info.pos(),model,LightCoordsUtil.getLightCoords(view,info.pos())));}
        for(var be:view.blockEntities()){
            var actor=contraption.getActorAt(be.getBlockPos());if(actor!=null){var behaviour=com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(actor.left.state());if(behaviour!=null&&behaviour.disableBlockEntityRendering())continue;}
            var failure=view.renderFailure(be);if(failure.hasFailed())continue;
            boolean explain=AllConfigs.client().explainRenderErrors.get();
            try {
                BlockEntityRenderer renderer=blockEntities.getRenderer(be);
                if(renderer==null){failure.skip();continue;}
                var camera=entity.toLocalVector(entityRenderDispatcher.camera.position(),partial);
                if(!renderer.shouldRender(be,camera))continue;
                BlockEntityRenderState extracted=renderer.createRenderState();renderer.extractRenderState(be,extracted,partial,camera,null);
                state.blockEntities.add(new State.BlockEntity(be.getBlockPos(),extracted,renderer,failure,explain));
            } catch(Exception exception){failure.report(exception,explain);}
        }
        var camera=entity.toLocalVector(entityRenderDispatcher.camera.position(),partial);
        for(var actor:contraption.getActors()){var renderer=ActorRenderer.REGISTRY.get(actor.left.state());if(renderer==null||actor.right==null)continue;var extracted=renderer.extract(actor.right,view,partial,camera);if(extracted!=null)state.actors.add(new State.Actor(actor.left.pos(),extracted,renderer));}
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        pose.pushPose();pose.translate(state.anchorOffset.x,state.anchorOffset.y,state.anchorOffset.z);pose.translate(.5,.5,.5);pose.rotate(state.orientation);pose.translate(-.5,-.5,-.5);
        for(var block:state.blocks){pose.pushPose();pose.translate(block.pos.getX(),block.pos.getY(),block.pos.getZ());block.model.submit(pose,collector,block.light,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();}
        for(var block:state.blockEntities){
            if(block.failure.hasFailed())continue;
            pose.pushPose();
            try{pose.translate(block.pos.getX(),block.pos.getY(),block.pos.getZ());block.renderer.submit(block.state,pose,collector,camera);}
            catch(Exception exception){block.failure.report(exception,block.explainErrors);}
            finally{pose.popPose();}
        }
        for(var actor:state.actors){pose.pushPose();pose.translate(actor.pos.getX(),actor.pos.getY(),actor.pos.getZ());actor.renderer.submit(actor.state,pose,collector,camera);pose.popPose();}
        pose.popPose();super.submit(state,pose,collector,camera);
    }
}
