package com.simibubi.create.foundation.map;

import com.simibubi.create.Create;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.MapRenderState.MapDecorationRenderState;

/** Source station icon/label layout on actual extracted native map state and submission pipeline. */
public final class StationMapDecorationRenderer {
    public static boolean isStation(MapDecorationRenderState state){return state.atlasSprite!=null&&state.atlasSprite.contents().name().equals(Create.asResource("station"));}
    public static float labelX(MapDecorationRenderState state){return state.x/2f+64+.8f*(-Minecraft.getInstance().font.width(state.name)/2f+.5f);}
    private StationMapDecorationRenderer(){}
}
