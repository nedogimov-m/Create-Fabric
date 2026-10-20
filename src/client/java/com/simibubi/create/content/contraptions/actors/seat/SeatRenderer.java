package com.simibubi.create.content.contraptions.actors.seat;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

public final class SeatRenderer extends EntityRenderer<SeatEntity, EntityRenderState> {
    public SeatRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public EntityRenderState createRenderState() { return new EntityRenderState(); }
    @Override public boolean shouldRender(SeatEntity entity, Frustum frustum, double x, double y, double z, float partialTick) { return false; }
}
