package com.simibubi.create.content.logistics.box;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/** Geometry and rotation are extracted before submit; no live entity or model reads in submit. */
public final class PackageRenderer extends EntityRenderer<PackageEntity,PackageRenderer.State> {
    private static final Map<Identifier,ExtraModelKey<BlockStateModel>> MODELS=new LinkedHashMap<>();
    public static final class State extends EntityRenderState {public final PartialModelRenderState model=new PartialModelRenderState();public float yaw;public int entityId;}
    public PackageRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.5f;}
    public static ExtraModelKey<BlockStateModel> model(net.minecraft.world.item.ItemStack box){return MODELS.get(BuiltInRegistries.ITEM.getKey(box.getItem()));}
    public static void register(){
        for(var item:PackageStyles.ALL_BOXES)MODELS.put(BuiltInRegistries.ITEM.getKey(item),ExtraModelKey.create());
        ModelLoadingPlugin.register(context->MODELS.forEach((id,key)->context.addModel(key,SimpleUnbakedExtraModel.blockStateModel(id.withPath("item/"+id.getPath())))));
        EntityRenderers.register(AllEntityTypes.PACKAGE,PackageRenderer::new);
    }
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(PackageEntity entity,State state,float partial){
        super.extractRenderState(entity,state,partial);state.yaw=Mth.rotLerp(partial,entity.yRotO,entity.getYRot());state.entityId=entity.getId();state.model.clear();
        var key=model(entity.getBox());if(key!=null)AllPartialModels.extract(key,state.model);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.rotateDegrees(Axis.YP,-state.yaw-90);pose.translate(-.5,0,-.5);state.model.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();super.submit(state,pose,collector,camera);
    }
}
