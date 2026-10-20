package com.simibubi.create.content.kinetics.chainConveyor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Native extracted meshes; parcel spring state follows the network ID between block entities. */
public final class ChainConveyorRenderer implements BlockEntityRenderer<ChainConveyorBlockEntity,ChainConveyorRenderer.State> {
    private static final Map<Identifier,ExtraModelKey<BlockStateModel>> RIGGING = new LinkedHashMap<>();
    private static final WeakHashMap<Level,Map<Integer,Physics>> PHYSICS = new WeakHashMap<>();
    private static final class Physics {
        Vec3 target,previousTarget,position,previousPosition,motion=Vec3.ZERO;
        float yaw,previousYaw;
        long tick=Long.MIN_VALUE;
    }
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState wheel=new PartialModelRenderState(),shaft=new PartialModelRenderState(),guard=new PartialModelRenderState();
        public final List<Strand> strands=new ArrayList<>();
        public final List<Box> boxes=new ArrayList<>();
        public float angle;
    }
    public record Strand(PartialModelRenderState mesh,float yaw) {}
    public record Box(PartialModelRenderState parcel,PartialModelRenderState rig,Vec3 offset,float yaw,float xTilt,float zTilt,float hook,boolean reversed,int light) {}
    public ChainConveyorRenderer(BlockEntityRendererProvider.Context context) {}
    public static ExtraModelKey<BlockStateModel> rigging(net.minecraft.world.item.ItemStack item){return item.getItem() instanceof PackageItem box?RIGGING.get(box.style.getRiggingModel()):null;}
    public static void register() {
        for (var style : PackageStyles.STYLES) RIGGING.computeIfAbsent(style.getRiggingModel(),id -> ExtraModelKey.create());
        ModelLoadingPlugin.register(context -> RIGGING.forEach((id,key) -> context.addModel(key,SimpleUnbakedExtraModel.blockStateModel(id))));
        BlockEntityRenderers.register(AllBlockEntityTypes.CHAIN_CONVEYOR,ChainConveyorRenderer::new);
    }
    @Override public State createRenderState() { return new State(); }
    @Override public boolean shouldRenderOffScreen() { return true; }
    @Override public int getViewDistance() { return 256; }
    @Override public boolean shouldRender(ChainConveyorBlockEntity conveyor,Vec3 camera) {
        if (Vec3.atCenterOf(conveyor.getBlockPos()).distanceToSqr(camera)<256*256) return true;
        return conveyor.getConnections().stream().anyMatch(offset -> Vec3.atCenterOf(conveyor.getBlockPos().offset(offset)).distanceToSqr(camera)<256*256);
    }
    @Override public void extractRenderState(ChainConveyorBlockEntity conveyor,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(conveyor,state,partial,camera,overlay);
        ChainConveyorInteractionHandler.track(conveyor);
        AllPartialModels.extract(AllPartialModels.CHAIN_CONVEYOR_WHEEL,state.wheel);
        AllPartialModels.extract(AllPartialModels.CHAIN_CONVEYOR_SHAFT,state.shaft);
        AllPartialModels.extract(AllPartialModels.CHAIN_CONVEYOR_GUARD,state.guard);
        state.angle=conveyor.getAngle(partial); state.strands.clear(); state.boxes.clear();
        var level=conveyor.getLevel(); var origin=Vec3.atLowerCornerOf(conveyor.getBlockPos());
        double phase=Mth.positiveModulo((level.getGameTime()+partial)*Math.abs(conveyor.getSpeed())/360d,1d);
        for (var offset : conveyor.getConnections()) {
            var stats=conveyor.getConnectionStats(offset); if (stats==null) continue;
            state.strands.add(new Strand(chainMesh(stats,origin,phase),(float)Math.toDegrees(Math.atan2(stats.end().x-stats.start().x,stats.end().z-stats.start().z))));
        }
        var cache=PHYSICS.computeIfAbsent(level,ignored -> new HashMap<>());
        if (level.getGameTime()%100==0) cache.values().removeIf(physics -> level.getGameTime()-physics.tick>30);
        for (var parcel : conveyor.getLoopingPackages()) extractBox(conveyor,state,parcel,cache,partial,origin);
        for (var list : conveyor.getTravellingPackages().values()) for (var parcel : list) extractBox(conveyor,state,parcel,cache,partial,origin);
    }
    private static PartialModelRenderState chainMesh(ChainConveyorBlockEntity.ConnectionStats stats,Vec3 origin,double phase) {
        var base=new PartialModelRenderState(); AllPartialModels.extract(Blocks.IRON_CHAIN.defaultBlockState(),base);
        var mesh=Renderer.get().mutableMesh(); var out=mesh.emitter();
        var direction=stats.end().subtract(stats.start()).normalize();
        var horizontal=new Vec3(-direction.z,0,direction.x).normalize();
        var vertical=horizontal.cross(direction).normalize(); var start=stats.start().subtract(origin);
        var sprite=Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS).getSprite(Identifier.withDefaultNamespace("block/iron_chain"));
        for (double section=-phase;section<stats.chainLength();section++) {
            double along=section;
            base.mesh.forEach(quad -> {
                out.copyFrom(quad);
                var edgeA=new Vec3(quad.x(1)-quad.x(0),quad.y(1)-quad.y(0),quad.z(1)-quad.z(0));
                var edgeB=new Vec3(quad.x(2)-quad.x(0),quad.y(2)-quad.y(0),quad.z(2)-quad.z(0));
                var faceNormal=edgeA.cross(edgeB).normalize();
                for (int i=0;i<4;i++) {
                    double localY=Math.clamp(quad.y(i)+along,0,stats.chainLength())-along;
                    var position=start.add(horizontal.scale(quad.x(i)-.5)).add(vertical.scale(quad.z(i)-.5)).add(direction.scale(localY+along));
                    var sourceNormal=new Vec3(quad.normalX(i),quad.normalY(i),quad.normalZ(i));
                    if(!Double.isFinite(sourceNormal.lengthSqr())||sourceNormal.lengthSqr()<.01)sourceNormal=faceNormal;
                    var normal=horizontal.scale(sourceNormal.x).add(direction.scale(sourceNormal.y)).add(vertical.scale(sourceNormal.z));
                    out.pos(i,(float)position.x,(float)position.y,(float)position.z).normal(i,(float)normal.x,(float)normal.y,(float)normal.z).uv(i,quad.u(i),sprite.getV((float)(1-localY)));
                }
                out.cullFace(null).emit();
            });
        }
        var result=new PartialModelRenderState(); result.mesh=mesh.immutableCopy(); return result;
    }
    private static void extractBox(ChainConveyorBlockEntity conveyor,State state,ChainConveyorPackage parcel,Map<Integer,Physics> cache,float partial,Vec3 origin) {
        if (parcel.worldPosition==null || !(parcel.item.getItem() instanceof PackageItem item)) return;
        long now=conveyor.getLevel().getGameTime(); var physics=cache.computeIfAbsent(parcel.netId,ignored -> new Physics());
        if (physics.position==null || now-physics.tick>30) {
            physics.target=physics.previousTarget=parcel.worldPosition.subtract(0,9/16d,0);
            physics.position=physics.previousPosition=physics.target; physics.yaw=physics.previousYaw=parcel.yaw;
        }
        if (physics.tick!=now) {
            physics.previousPosition=physics.position; physics.previousTarget=physics.target; physics.previousYaw=physics.yaw;
            if (physics.position.distanceToSqr(parcel.worldPosition)>2.25) physics.position=parcel.worldPosition.add(physics.position.subtract(parcel.worldPosition).normalize().scale(1.5));
            physics.motion=physics.motion.add(0,-.25,0).scale(.75).add(parcel.worldPosition.subtract(physics.position).scale(.25));
            physics.position=physics.position.add(physics.motion); physics.target=parcel.worldPosition.subtract(0,9/16d,0);
            physics.yaw=Mth.rotLerp(.25f,physics.yaw,parcel.yaw); physics.tick=now;
        }
        var position=physics.previousPosition.lerp(physics.position,partial); var target=physics.previousTarget.lerp(physics.target,partial);
        float yaw=Mth.rotLerp(partial,physics.previousYaw,physics.yaw); var dangle=target.add(0,.5,0).subtract(position).yRot((float)Math.toRadians(-yaw));
        float zTilt=Mth.clamp(Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-dangle.x,dangle.y)))/2,-25,25);
        float xTilt=Mth.clamp(Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(dangle.z,dangle.y)))/2,-25,25);
        var model=new PartialModelRenderState(); var key=PackageRenderer.model(parcel.item); if (key==null) return; AllPartialModels.extract(key,model);
        var rig=new PartialModelRenderState(); AllPartialModels.extract(RIGGING.get(item.style.getRiggingModel()),rig);
        int light=net.minecraft.util.LightCoordsUtil.getLightCoords(conveyor.getLevel(),BlockPos.containing(position));
        state.boxes.add(new Box(model,rig,target.subtract(origin),yaw,xTilt,zTilt,PackageItem.getHookDistance(parcel.item),conveyor.isReversed(),light));
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        state.wheel.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        pose.pushPose(); pose.translate(.5,.5,.5); pose.rotateDegrees(Axis.YP,state.angle); pose.translate(-.5,-.5,-.5);
        state.shaft.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0); pose.popPose();
        for (var strand : state.strands) {
            strand.mesh.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
            pose.pushPose(); pose.translate(.5,.5,.5); pose.rotateDegrees(Axis.YP,strand.yaw); pose.translate(-.5,-.5,-.5);
            state.guard.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0); pose.popPose();
        }
        for (var box : state.boxes) for (boolean rig : new boolean[]{true,false}) {
            pose.pushPose(); pose.translate(box.offset.x,box.offset.y+10/16d,box.offset.z); pose.rotateDegrees(Axis.YP,box.yaw); pose.rotateDegrees(Axis.ZP,box.zTilt); pose.rotateDegrees(Axis.XP,box.xTilt);
            if (rig && box.reversed) pose.rotateDegrees(Axis.YP,180);
            pose.translate(-.5,-.5-box.hook+7/16d,-.5);
            (rig?box.rig:box.parcel).submit(pose,collector,box.light,OverlayTexture.NO_OVERLAY,0); pose.popPose();
        }
    }
}
