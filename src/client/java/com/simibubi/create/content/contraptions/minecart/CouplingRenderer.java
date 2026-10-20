package com.simibubi.create.content.contraptions.minecart;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.contraptions.minecart.capability.MinecartController;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Actual native interpolation/model/light extraction; Vulkan submit reads immutable frame data only. */
public final class CouplingRenderer {
    private static final ExtraModelKey<BlockStateModel> ATTACHMENT=ExtraModelKey.create(()->"create:entity/minecart_coupling/attachment"),RING=ExtraModelKey.create(()->"create:entity/minecart_coupling/ring"),CONNECTOR=ExtraModelKey.create(()->"create:entity/minecart_coupling/connector");
    public record Piece(Vec3 position,Matrix4f transform,PartialModelRenderState model,int light){}
    public record Frame(List<Piece> pieces,int couplings,DrawableGizmoPrimitives debug){public Frame{pieces=List.copyOf(pieces);}}
    private record Endpoint(Vec3 origin,Matrix4f transform,float yaw,int light){Vec3 point(){var value=transform.transformPosition(new Vector3f());return origin.add(value.x,value.y,value.z);}}
    private static final Map<LevelRenderState,Frame> FRAMES=new WeakHashMap<>();private static Frame latest=new Frame(List.of(),0,new DrawableGizmoPrimitives());private static boolean debug;
    public static Frame latest(){return latest;}
    public static void setDebugRendering(boolean enabled){debug=enabled;}
    private static float radians(float value){return (float)Math.toRadians(value);}
    private static Endpoint endpoint(AbstractMinecart cart,Vec3 center,float partial){
        var frame=NativeCartRenderPose.extract(cart,partial);long seed=cart.getId()*493286711L;seed=seed*seed*4392167121L+seed*98761L;
        var jitter=new Vec3((((seed>>16&7)+.5)/8-.5)*.004,(((seed>>20&7)+.5)/8-.5)*.004,(((seed>>24&7)+.5)/8-.5)*.004);var origin=frame.position().add(jitter);var pose=new Matrix4f();
        if(frame.modern())pose.rotateY(radians(frame.yaw())).rotateZ(radians(frame.pitch())).translate(0,.5f,0);else{origin=origin.add(0,.5,0);pose.rotateY(radians(frame.yaw())).rotateZ(radians(frame.pitch()));}
        float hurt=cart.getHurtTime()-partial,damage=Math.max(0,cart.getDamage()-partial),roll=hurt>0?Mth.sin(hurt)*hurt*damage/10*cart.getHurtDir():0;
        boolean flip=frame.front().distanceToSqr(center)>frame.back().distanceToSqr(center);pose.rotateX(radians(roll)).translate(flip?-13/16f:13/16f,0,0).rotateY(flip?(float)Math.PI:0);
        int light=net.minecraft.util.LightCoordsUtil.getLightCoords(cart.level(),BlockPos.containing(cart.getBoundingBox().getCenter()));return new Endpoint(origin,pose,frame.yaw(),light);
    }
    private static PartialModelRenderState model(ExtraModelKey<BlockStateModel> key){var result=new PartialModelRenderState();AllPartialModels.extract(key,result);return result;}
    public static Frame extract(Minecraft client,float partial){
        var pieces=new ArrayList<Piece>();var primitives=new DrawableGizmoPrimitives();int[] count={0};if(client.level==null)return new Frame(pieces,0,primitives);
        CouplingHandler.forEachLoadedCoupling(client.level,pair->{
            var main=pair.getFirst();var first=main.cart();var second=pair.getSecond().cart();
            if(debug){var a=first.position().add(0,1,0);var b=second.position().add(0,1,0);float amount=(float)Math.clamp(Math.abs(main.getCouplingLength(true)-a.distanceTo(b))*8,0,1);int color=0xFF000000|Mth.lerpInt(amount,0xAB,0xEE)<<16|Mth.lerpInt(amount,0xF0,0x85)<<8|Mth.lerpInt(amount,0xE9,0x72);primitives.addLine(a,b,color,2);primitives.addLine(a,a.add(0,1/128d,0),0xFFFFFFFF,4);}
            if(main.hasContraptionCoupling(true))return;
            var center=first.position().add(second.position()).scale(.5);var a=endpoint(first,center,partial);var b=endpoint(second,center,partial);var start=a.point();var difference=b.point().subtract(start);if(difference.lengthSqr()<1e-12)return;
            float yaw=(float)-Math.toDegrees(Math.atan2(difference.z,difference.x)),pitch=(float)Math.toDegrees(Math.atan2(difference.y,difference.horizontalDistance()));
            var attachment=model(ATTACHMENT);var ring=model(RING);var connector=model(CONNECTOR);
            for(var end:List.of(a,b)){pieces.add(new Piece(end.origin,new Matrix4f(end.transform),attachment,end.light));pieces.add(new Piece(end.origin,new Matrix4f(end.transform).rotateY(radians(yaw-end.yaw)),ring,end.light));}
            int block=(((a.light>>4)&15)+((b.light>>4)&15))/2,sky=(((a.light>>20)&15)+((b.light>>20)&15))/2;
            var transform=new Matrix4f().rotateY(radians(yaw)).rotateZ(radians(pitch)).scale((float)difference.length(),1,1);pieces.add(new Piece(start,transform,connector,sky<<20|block<<4));count[0]++;
        });return new Frame(pieces,count[0],primitives);
    }
    public static void register(){
        ModelLoadingPlugin.register(context->{context.addModel(ATTACHMENT,SimpleUnbakedExtraModel.blockStateModel(com.simibubi.create.Create.asResource("entity/minecart_coupling/attachment")));context.addModel(RING,SimpleUnbakedExtraModel.blockStateModel(com.simibubi.create.Create.asResource("entity/minecart_coupling/ring")));context.addModel(CONNECTOR,SimpleUnbakedExtraModel.blockStateModel(com.simibubi.create.Create.asResource("entity/minecart_coupling/connector")));});
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{FRAMES.clear();latest=new Frame(List.of(),0,new DrawableGizmoPrimitives());});
        LevelExtractionEvents.END_EXTRACTION.register(context->{var client=Minecraft.getInstance();latest=extract(client,client.getDeltaTracker().getGameTimeDeltaPartialTick(false));FRAMES.put(context.levelState(),latest);});
        LevelRenderEvents.COLLECT_SUBMITS.register(context->{var frame=FRAMES.get(context.levelState());if(frame==null)return;var camera=context.levelState().cameraRenderState.pos;for(var piece:frame.pieces){var pose=new PoseStack();pose.translate(piece.position.x-camera.x,piece.position.y-camera.y,piece.position.z-camera.z);pose.mulPose(piece.transform);piece.model.submit(pose,context.submitNodeCollector(),piece.light,OverlayTexture.NO_OVERLAY,0);}frame.debug.submit(context.submitNodeCollector(),context.levelState().cameraRenderState,false);});
    }
    private CouplingRenderer(){}
}
