package com.simibubi.create.content.redstone;

import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;

/** Immutable per-extraction tint, leaving the reload-scoped source partial cache untouched. */
public final class RedstoneIndicator {
    public static int color(float progress){float p=Math.clamp(progress,0,1);return 0xFF000000|Math.round(44+(205-44)*p)<<16|Math.round(3*(1-p))<<8;}
    public static void tint(PartialModelRenderState model,int color){
        var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();model.mesh.forEach(quad->{emitter.copyFrom(quad);emitter.multiplyColor(color);emitter.emit();});model.mesh=mesh.immutableCopy();
    }
    private RedstoneIndicator(){}
}
