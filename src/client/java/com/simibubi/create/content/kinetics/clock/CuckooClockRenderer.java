package com.simibubi.create.content.kinetics.clock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllPartialModels;
import net.createmod.catnip.math.AngleHelper;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
/** Original hour/minute pivots,135-degree doors and actual pig/creeper figure in native detached submissions. */
public class CuckooClockRenderer implements BlockEntityRenderer<CuckooClockBlockEntity,CuckooClockRenderer.State> {
 public record Part(String name,PartialModelRenderState model,Matrix4fc pose){}
 public static final class State extends BlockEntityRenderState {public List<Part> parts=List.of();public float hourAngle,minuteAngle,doorAngle,figureOffset,shaftAngle;public CuckooClockBlockEntity.Animation animation;public Direction facing;}
 public CuckooClockRenderer(BlockEntityRendererProvider.Context context){}
 @Override public State createRenderState(){return new State();}
 private static float radians(float degrees){return (float)Math.toRadians(degrees);}
 private static Matrix4f orientation(float yaw){return new Matrix4f().translate(.5f,.5f,.5f).rotateY(radians(yaw)).translate(-.5f,-.5f,-.5f);}
 private static void part(List<Part> parts,String name,ExtraModelKey<BlockStateModel> key,Matrix4f matrix){var model=new PartialModelRenderState();AllPartialModels.extract(key,model);parts.add(new Part(name,model,new Matrix4f(matrix)));}
 @Override public void extractRenderState(CuckooClockBlockEntity clock,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
  BlockEntityRenderer.super.extractRenderState(clock,state,partial,camera,overlay);state.facing=clock.getBlockState().getValue(CuckooClockBlock.HORIZONTAL_FACING);state.animation=clock.animationType;state.hourAngle=clock.hourHand.getValue(partial);state.minuteAngle=clock.minuteHand.getValue(partial);float angle=0;
  if(state.animation!=null){float value=clock.animationProgress.getValue(partial);int step=state.animation==CuckooClockBlockEntity.Animation.SURPRISE?3:15;for(int phase=30;phase<=60;phase+=step){float local=value-phase;if(local< -step/3)continue;else if(local<0)angle=Mth.lerp((value-(phase-5))/5,0,135);else if(local<step/3)angle=135;else if(local<2*step/3)angle=Mth.lerp((value-(phase+5))/5,135,0);}}
  state.doorAngle=angle;state.figureOffset=-(angle/135)*.5f+10/16f;float yaw=AngleHelper.horizontalAngle(state.facing.getCounterClockWise());var parts=new ArrayList<Part>();float x=2/16f,y=6/16f,z=8/16f;
  part(parts,"hour",AllPartialModels.CUCKOO_HOUR_HAND,orientation(yaw).translate(x,y,z).rotateX(radians(state.hourAngle)).translate(-x,-y,-z));
  part(parts,"minute",AllPartialModels.CUCKOO_MINUTE_HAND,orientation(yaw).translate(x,y,z).rotateX(radians(state.minuteAngle)).translate(-x,-y,-z));
  for(boolean left:new boolean[]{true,false}){float pivotZ=(left?6:10)/16f;part(parts,left?"leftDoor":"rightDoor",left?AllPartialModels.CUCKOO_LEFT_DOOR:AllPartialModels.CUCKOO_RIGHT_DOOR,orientation(yaw).translate(x,0,pivotZ).rotateY(radians(angle*(left?-1:1))).translate(-x,0,-pivotZ));}
  if(state.animation!=CuckooClockBlockEntity.Animation.NONE)part(parts,"figure",state.animation==CuckooClockBlockEntity.Animation.PIG?AllPartialModels.CUCKOO_PIG:AllPartialModels.CUCKOO_CREEPER,orientation(yaw).translate(state.figureOffset,0,0));
  var axis=state.facing.getAxis();var pos=clock.getBlockPos();int parity=(axis==Direction.Axis.X?0:pos.getX())+(axis==Direction.Axis.Y?0:pos.getY())+(axis==Direction.Axis.Z?0:pos.getZ());state.shaftAngle=clock.getAngle(partial)+(parity%2==0?22.5f:0);var shaft=new Matrix4f().translate(.5f,.5f,.5f);if(axis==Direction.Axis.X)shaft.rotateX(radians(state.shaftAngle));else shaft.rotateZ(radians(state.shaftAngle));var rear=state.facing.getOpposite();shaft.rotate(new Quaternionf().rotationTo(0,0,1,rear.getStepX(),rear.getStepY(),rear.getStepZ())).translate(-.5f,-.5f,-.5f);part(parts,"shaft",AllPartialModels.SHAFT_HALF,shaft);state.parts=List.copyOf(parts);
 }
 @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){for(var part:state.parts){pose.pushPose();pose.mulPose(part.pose());part.model().submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}}
 @Override public int getViewDistance(){return 128;}
}
