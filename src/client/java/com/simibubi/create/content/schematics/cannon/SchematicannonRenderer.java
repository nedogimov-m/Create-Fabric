package com.simibubi.create.content.schematics.cannon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Source articulated cannon, recoil and real launched block/item models through native extraction. */
public final class SchematicannonRenderer implements BlockEntityRenderer<SchematicannonBlockEntity,SchematicannonRenderer.State> {
    private static final ExtraModelKey<BlockStateModel> CONNECTOR=ExtraModelKey.create(()->"create:block/schematicannon/connector"),PIPE=ExtraModelKey.create(()->"create:block/schematicannon/pipe");
    private static final Map<SchematicannonBlockEntity,Set<UUID>> SMOKE=new WeakHashMap<>();
    private final ItemModelResolver items;
    public record Launch(Vec3 offset,float progress,PartialModelRenderState block,ItemStackRenderState item){}
    public static final class State extends BlockEntityRenderState {
        public final PartialModelRenderState connector=new PartialModelRenderState(),pipe=new PartialModelRenderState();
        public List<Launch> launches=List.of();
        public double yaw,pitch,recoil;
    }
    public SchematicannonRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    public static void registerModels(){ModelLoadingPlugin.register(context->{context.addModel(CONNECTOR,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/schematicannon/connector")));context.addModel(PIPE,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/schematicannon/pipe")));});SchematicannonBlockEntity.CLIENT_TICK=SchematicannonRenderer::tick;}
    public static void clear(){SMOKE.clear();}
    private static void tick(SchematicannonBlockEntity cannon){
        var seen=SMOKE.computeIfAbsent(cannon,ignored->new HashSet<>());var present=new HashSet<UUID>();
        for(var launch:cannon.flyingBlocks){present.add(launch.jobId());boolean first=seen.add(launch.jobId());if(first&&launch.ticksRemaining==launch.totalTicks&&cannon.getLevel() instanceof ClientLevel level){var offset=cannonOffset(cannon,launch);var start=Vec3.atCenterOf(cannon.getBlockPos().above()).add(offset).subtract(.5,.5,.5);var random=level.getRandom();for(int i=0;i<10;i++){double x=offset.x*.01,y=(offset.y+1)*.01,z=offset.z*.01;level.addParticle(ParticleTypes.CLOUD,start.x+random.nextFloat()-x*40,start.y+random.nextFloat()-y*40,start.z+random.nextFloat()-z*40,x,y,z);}}if(launch.ticksRemaining>0)launch.update(cannon.getLevel());}
        seen.retainAll(present);
    }
    private static Vec3 cannonOffset(SchematicannonBlockEntity cannon,LaunchedItem launch){var distance=Vec3.atCenterOf(launch.target).subtract(Vec3.atCenterOf(cannon.getBlockPos().above()));double height=distance.length()*.6+distance.y;return distance.add(0,height,0).normalize().scale(2);}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(SchematicannonBlockEntity cannon,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(cannon,state,partial,camera,overlay);AllPartialModels.extract(CONNECTOR,state.connector);AllPartialModels.extract(PIPE,state.pipe);
        var target=cannon.printer.getCurrentTarget();state.yaw=cannon.defaultYaw;state.pitch=40;state.recoil=0;
        if(target!=null){var previous=cannon.previousTarget;var point=previous==null?Vec3.atLowerCornerOf(target):Vec3.atLowerCornerOf(previous).lerp(Vec3.atLowerCornerOf(target),partial);var diff=point.subtract(Vec3.atLowerCornerOf(cannon.getBlockPos()));state.yaw=Math.toDegrees(Math.atan2(diff.x,diff.z));double distance=Math.sqrt(diff.x*diff.x+diff.z*diff.z);state.pitch=Math.toDegrees(Math.atan2(distance,diff.y*3+distance*2))+10;}
        var launches=new ArrayList<Launch>();
        for(var launch:cannon.flyingBlocks){if(launch.ticksRemaining<=0||launch.totalTicks<=0)continue;state.recoil=Math.max(state.recoil,launch.ticksRemaining+1-partial-launch.totalTicks+10);
            var start=Vec3.atCenterOf(cannon.getBlockPos().above());var end=Vec3.atCenterOf(launch.target);var diff=end.subtract(start);double height=diff.length()*.6+diff.y;var cannonOffset=cannonOffset(cannon,launch);start=start.add(cannonOffset);diff=end.subtract(start);
            float t=Math.clamp((launch.totalTicks-(launch.ticksRemaining+1-partial))/launch.totalTicks,0,1);double y=2*(1-t)*t*height+t*t*diff.y;var offset=diff.scale(t).multiply(1,0,1).add(.5,y+1.5,.5).add(cannonOffset);
            PartialModelRenderState block=null;ItemStackRenderState item=null;
            if(launch instanceof LaunchedItem.ForBlockState actual){block=new PartialModelRenderState();AllPartialModels.extract(launch instanceof LaunchedItem.ForBelt?AllBlocks.SHAFT.defaultBlockState():actual.state,block);}
            else{item=new ItemStackRenderState();items.updateForTopItem(item,launch.stack,ItemDisplayContext.GROUND,cannon.getLevel(),null,0);}
            launches.add(new Launch(offset,t,block,item));
        }
        state.launches=List.copyOf(launches);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,0,.5);pose.rotateDegrees(Axis.YP,(float)(state.yaw+90));pose.translate(-.5,0,-.5);state.connector.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        pose.pushPose();pose.translate(.5,15/16d,.5);pose.rotateDegrees(Axis.YP,(float)(state.yaw+90));pose.rotateDegrees(Axis.ZP,(float)state.pitch);pose.translate(-.5,-15/16d,-.5);pose.translate(0,-state.recoil/100,0);state.pipe.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        for(var launch:state.launches){pose.pushPose();pose.translate(launch.offset);pose.translate(.125,.125,.125);pose.rotateDegrees(Axis.YP,360*launch.progress);pose.rotateDegrees(Axis.XP,360*launch.progress);pose.translate(-.125,-.125,-.125);float scale=launch.block!=null?.3f:1.2f;pose.scale(scale,scale,scale);if(launch.block!=null)launch.block.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);else launch.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    }
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public int getViewDistance(){return 128;}
}
