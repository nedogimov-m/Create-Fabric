package com.simibubi.create.content.equipment.toolbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

/** Source lid/drawer geometry and poses, submitted through native RenderPearl/Fabric meshes. */
public final class ToolBoxVisual {
    public static final ExtraModelKey<BlockStateModel> DRAWER=AllPartialModels.TOOLBOX_DRAWER;
    public static final Map<DyeColor,ExtraModelKey<BlockStateModel>> LIDS=AllPartialModels.TOOLBOX_LIDS,BODIES=AllPartialModels.TOOLBOX_BODIES;
    private static final Map<net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh,net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh> CUTOUT=Collections.synchronizedMap(new WeakHashMap<>());
    private static void cutout(PartialModelRenderState state){if(state.mesh==null)return;state.mesh=CUTOUT.computeIfAbsent(state.mesh,mesh->{var target=net.fabricmc.fabric.api.client.renderer.v1.Renderer.get().mutableMesh();var emitter=target.emitter();mesh.forEach(quad->emitter.copyFrom(quad).chunkLayer(net.minecraft.client.renderer.chunk.ChunkSectionLayer.CUTOUT).emit());return target.immutableCopy();});}
    public static final class Geometry {
        public final PartialModelRenderState lid=new PartialModelRenderState(),drawer=new PartialModelRenderState(),body=new PartialModelRenderState();
        public float lidAmount,drawerAmount,yaw;
    }
    public static Geometry extract(ToolboxBlockEntity box,float partial,boolean body){var result=new Geometry();AllPartialModels.extract(LIDS.get(box.getColor()),result.lid);AllPartialModels.extract(DRAWER,result.drawer);cutout(result.lid);cutout(result.drawer);if(body)AllPartialModels.extract(BODIES.get(box.getColor()),result.body);result.lidAmount=box.lid(partial);result.drawerAmount=box.drawers(partial);result.yaw=-box.getBlockState().getValue(ToolboxBlock.FACING).getOpposite().toYRot();return result;}
    public static void submit(Geometry state,PoseStack pose,SubmitNodeCollector collector,int light,boolean preview){pose.pushPose();if(!preview){pose.translate(.5,.5,.5);pose.rotate(Axis.YP.rotationDegrees(state.yaw));pose.translate(-.5,-.5,-.5);}else state.body.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);pose.pushPose();pose.translate(0,6/16f,12/16f);pose.rotate(Axis.XP.rotationDegrees((preview?105:135)*state.lidAmount));pose.translate(0,-6/16f,-12/16f);state.lid.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);pose.popPose();for(int offset=0;offset<2;offset++){pose.pushPose();pose.translate(0,offset/8f,-state.drawerAmount*.175f*(2-offset));state.drawer.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);pose.popPose();}pose.popPose();}
    private ToolBoxVisual(){}
}
