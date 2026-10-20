package com.simibubi.create.content.trains.track;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.*;
import net.minecraft.util.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Original TrackModel position transform over real native quads and immutable BE render data. */
public final class TrackModel extends WrapperBlockStateModel {
    private TrackModel(BlockStateModel original){super(original);}
    public static void register(){ModelLoadingPlugin.register(context->context.modifyBlockModelAfterBake().register((model,bake)->bake.state().getBlock() instanceof TrackBlock?new TrackModel(model):model));}
    private static Double angle(BlockAndTintGetter level,BlockPos pos){return level.getBlockEntityRenderData(pos) instanceof TrackBlockEntity.TrackRenderData data?data.smoothingAngle().orElse(null):null;}
    private record Key(TrackModel model,Object wrapped,Double angle){}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random){var key=wrapped.createGeometryKey(level,pos,state,random);return key==null?null:new Key(this,key,angle(level,pos));}
    private static double horizontal(TrackShape shape){return switch(shape){case XO->0;case PD->45;case ZO->90;case ND->135;default->0;};}
    private static Vec3 rotateY(Vec3 vector,double degrees){double a=(float)(degrees/180f*Math.PI),s=Mth.sin(a),c=Mth.cos(a);return new Vec3(vector.x*c+vector.z*s,vector.y,vector.z*c-vector.x*s);}
    private static Vec3 rotateZ(Vec3 vector,double degrees){double a=(float)(degrees/180f*Math.PI),s=Mth.sin(a),c=Mth.cos(a);return new Vec3(vector.x*c-vector.y*s,vector.y*c+vector.x*s,vector.z);}
    private static Vec3 centeredY(Vec3 vector,double degrees){return rotateY(vector.subtract(.5,.5,.5),degrees).add(.5,.5,.5);}
    public static Vec3 transformVertex(Vec3 vertex,TrackShape shape,double angle){
        var vertical=new Vec3(0,-.25,0);var diagonal=shape==TrackShape.ND||shape==TrackShape.PD?new Vec3((Mth.SQRT_OF_TWO-1)/2,0,0):Vec3.ZERO;double h=horizontal(shape);
        var result=centeredY(vertex.add(vertical),h).add(diagonal);result=rotateZ(result,Math.abs(angle)).subtract(diagonal);return centeredY(result,-h+(angle<0?180:0)).subtract(vertical);
    }
    private static Vec3 transformNormal(Vec3 normal,TrackShape shape,double angle){double h=horizontal(shape);return rotateY(rotateZ(rotateY(normal,h),Math.abs(angle)),-h+(angle<0?180:0));}
    @Override public void emitQuads(QuadEmitter emitter,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull){
        var angle=angle(level,pos);if(angle==null||!Double.isFinite(angle)){wrapped.emitQuads(emitter,level,pos,state,random,cull);return;}var shape=state.getValue(TrackBlock.SHAPE);
        emitter.pushTransform(quad->{var fallback=quad.faceNormal();var face=new Vec3(fallback.x(),fallback.y(),fallback.z());for(int i=0;i<4;i++){
            var vertex=transformVertex(new Vec3(quad.x(i),quad.y(i),quad.z(i)),shape,angle);var original=quad.hasNormal(i)?new Vec3(quad.normalX(i),quad.normalY(i),quad.normalZ(i)):face;var normal=transformNormal(original,shape,angle).normalize();
            quad.pos(i,(float)vertex.x,(float)vertex.y,(float)vertex.z).normal(i,(float)normal.x,(float)normal.y,(float)normal.z);
        }quad.cullFace(null).nominalFace(null).shadeDirectionOverride(null);return true;});
        wrapped.emitQuads(emitter,level,pos,state,random,side->false);emitter.popTransform();
    }
}
