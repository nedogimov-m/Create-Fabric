package com.simibubi.create.content.fluids;

import com.simibubi.create.AllFluids;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;

/** The original fluid fog colour/distance, applied to extracted fog data without graphics-driver calls. */
public final class FluidFog {
    private FluidFog(){}
    public static void apply(Camera camera,ClientLevel level,FogData data){
        if(!camera.isInitialized())return;
        var pos=camera.blockPosition();var fluid=level.getFluidState(pos);
        boolean honey=fluid.getType().isSame(AllFluids.HONEY),chocolate=fluid.getType().isSame(AllFluids.CHOCOLATE);
        if((!honey&&!chocolate)||camera.position().y>=pos.getY()+fluid.getHeight(level,pos))return;
        int color=honey?0xEAAE2F:0x622020;float distance=(honey?12:3)*(honey?com.simibubi.create.infrastructure.config.AllConfigs.client().honeyTransparencyMultiplier.getF():com.simibubi.create.infrastructure.config.AllConfigs.client().chocolateTransparencyMultiplier.getF());
        data.color.set(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,1);
        data.environmentalStart=-8;data.environmentalEnd=distance;data.skyEnd=distance;data.cloudEnd=distance;
    }
}
