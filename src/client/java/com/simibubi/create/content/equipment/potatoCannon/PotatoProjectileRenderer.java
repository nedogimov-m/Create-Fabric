package com.simibubi.create.content.equipment.potatoCannon;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

/** Genuine tracked projectile's actual item/render mode is extracted before Vulkan submission. */
public final class PotatoProjectileRenderer extends EntityRenderer<PotatoProjectileEntity,PotatoProjectileRenderer.State> {
    private final ItemModelResolver items;
    public static final class State extends EntityRenderState {public final ItemStackRenderState item=new ItemStackRenderState();public final Matrix4f transform=new Matrix4f();public float offset;}
    public PotatoProjectileRenderer(EntityRendererProvider.Context context){super(context);items=context.getItemModelResolver();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(PotatoProjectileEntity entity,State state,float partial){
        super.extractRenderState(entity,state,partial);state.item.clear();state.transform.identity();state.offset=(float)(entity.getBoundingBox().getYsize()/2-1/8d);
        var item=entity.getItem();if(item.isEmpty())return;
        items.updateForNonLiving(state.item,item,ItemDisplayContext.GROUND,entity);
        var camera=Minecraft.getInstance().getCameraEntity();
        entity.getRenderMode().transform(state.transform,entity,partial,camera==null?null:camera.getEyePosition(partial));
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.item.isEmpty())return;pose.pushPose();pose.translate(0,state.offset,0);pose.mulPose(state.transform);state.item.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();super.submit(state,pose,collector,camera);
    }
}
