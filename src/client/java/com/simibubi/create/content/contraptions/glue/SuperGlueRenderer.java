package com.simibubi.create.content.contraptions.glue;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class SuperGlueRenderer extends EntityRenderer<SuperGlueEntity,EntityRenderState> {
    public SuperGlueRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public EntityRenderState createRenderState(){return new EntityRenderState();}
    @Override public boolean shouldRender(SuperGlueEntity entity,Frustum frustum,double x,double y,double z,float partial){return false;}
}
