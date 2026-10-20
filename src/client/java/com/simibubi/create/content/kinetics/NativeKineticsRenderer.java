package com.simibubi.create.content.kinetics;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.*;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import com.simibubi.create.content.kinetics.flywheel.FlywheelBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.simpleRelays.encased.*;
import com.simibubi.create.content.kinetics.turntable.TurntableBlockEntity;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Native extracted meshes: casing stays in chunk geometry; only actual kinetic parts rotate. */
public final class NativeKineticsRenderer<T extends KineticBlockEntity> implements BlockEntityRenderer<T,NativeKineticsRenderer.State> {
    private final KineticBlockEntityRenderer legacy;
    public NativeKineticsRenderer(BlockEntityRendererProvider.Context context) { legacy = new KineticBlockEntityRenderer(context); }
    public record Part(PartialModelRenderState model,Direction.Axis axis,float angle,Direction modelFacing,boolean vertical) {}
    public static final class State extends BlockEntityRenderState {
        final KineticRenderState legacy = new KineticRenderState();
        boolean fallback;
        final List<Part> parts = new ArrayList<>();
        public List<Part> getParts() { return List.copyOf(parts); }
        public boolean usesLegacyRenderer() { return fallback; }
    }
    @Override public State createRenderState() { return new State(); }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public void extractRenderState(T entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);
        state.parts.clear(); var block = entity.getBlockState(); var axis = ((IRotate)block.getBlock()).getRotationAxis(block);
        var pos = entity.getBlockPos(); int parity = (axis == Direction.Axis.X ? 0 : pos.getX())+(axis == Direction.Axis.Y ? 0 : pos.getY())+(axis == Direction.Axis.Z ? 0 : pos.getZ());
        float shaftAngle = entity.getAngle(partial)+(parity%2 == 0 ? 22.5f : 0);
        state.fallback = false;
        if (block.getBlock() instanceof EncasedShaftBlock) {
            var shaft = new PartialModelRenderState(); AllPartialModels.extract(AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,axis),shaft);
            state.parts.add(new Part(shaft,axis,shaftAngle,null,false));
        } else if (block.getBlock() instanceof EncasedCogwheelBlock cog) {
            var wheel = new PartialModelRenderState(); AllPartialModels.extract(cog.isLargeCog()?AllPartialModels.SHAFTLESS_LARGE_COGWHEEL:AllPartialModels.SHAFTLESS_COGWHEEL,wheel);
            float wheelAngle = entity.getAngle(partial)+(parity%2 == 0 ? 22.5f : cog.isLargeCog()?11.25f:0);
            state.parts.add(new Part(wheel,axis,wheelAngle,Direction.get(Direction.AxisDirection.POSITIVE,axis),true));
            for (var face : Direction.values()) if (cog.hasShaftTowards(entity.getLevel(),pos,block,face)) {
                var shaft = new PartialModelRenderState(); AllPartialModels.extract(AllPartialModels.SHAFT_HALF,shaft);
                state.parts.add(new Part(shaft,axis,cog.isLargeCog()?shaftAngle:wheelAngle,face,false));
            }
        } else if (entity instanceof ValveHandleBlockEntity valve) {
            var handle = new PartialModelRenderState(); AllPartialModels.extract(block,handle);
            state.parts.add(new Part(handle,axis,valve.getIndependentAngle(partial),null,false));
        } else if (entity instanceof TurntableBlockEntity) {
            var table = new PartialModelRenderState(); AllPartialModels.extract(block,table); state.parts.add(new Part(table,axis,shaftAngle,null,false));
        } else if (entity instanceof FlywheelBlockEntity flywheel) {
            var wheel = new PartialModelRenderState(); AllPartialModels.extract(block,wheel); state.parts.add(new Part(wheel,axis,flywheel.getVisualAngle(partial),null,false));
            var shaft = new PartialModelRenderState(); AllPartialModels.extract(AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,axis),shaft); state.parts.add(new Part(shaft,axis,shaftAngle,null,false));
        } else { state.fallback = true; legacy.extractRenderState(entity,state.legacy,partial,camera,overlay); }
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        if (state.fallback) { legacy.submit(state.legacy,pose,collector,camera); return; }
        for (var part : state.parts) {
            pose.pushPose(); pose.translate(.5,.5,.5);
            pose.rotate(switch (part.axis) { case X -> Axis.XP.rotationDegrees(part.angle); case Y -> Axis.YP.rotationDegrees(part.angle); case Z -> Axis.ZP.rotationDegrees(part.angle); });
            if (part.modelFacing != null) { var f = part.modelFacing; pose.rotate(new Quaternionf().rotationTo(0,part.vertical?1:0,part.vertical?0:1,f.getStepX(),f.getStepY(),f.getStepZ())); }
            pose.translate(-.5,-.5,-.5); part.model.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0); pose.popPose();
        }
    }
}
