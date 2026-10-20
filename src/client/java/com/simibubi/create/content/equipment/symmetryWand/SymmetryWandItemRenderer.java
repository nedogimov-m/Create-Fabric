package com.simibubi.create.content.equipment.symmetryWand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.EquipmentItemRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.*;
import net.minecraft.world.item.*;

/** Pinned base, solid core, glass halo and floating/rotating bits, detached during extraction. */
public final class SymmetryWandItemRenderer extends EquipmentItemRenderer {
    public static final SymmetryWandItemRenderer INSTANCE=new SymmetryWandItemRenderer();
    private SymmetryWandItemRenderer(){for(var part:new String[]{"item","core","core_glow","bits"})model(part,modelPath(part));}
    @Override protected String modelPath(String name){return "item/wand_of_symmetry/"+name;}
    @Override public State extract(ItemStack stack,ItemDisplayContext display,float partial,float time){var state=new State();var pose=new PoseStack();draw(state,"item",pose);draw(state,"core",pose);draw(state,"core_glow",pose);float worldTime=time/20;pose.translate(0,Mth.sin(worldTime)*.05f,0);pose.rotateDegrees(Axis.YP,worldTime*-10%360);draw(state,"bits",pose);return state;}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline){
        if(state==null)return;pose.pushPose();pose.translate(.5,.5,.5);for(int index=0;index<state.draws.size();index++){var draw=state.draws.get(index);pose.pushPose();pose.mulPose(draw.transform());collector.submitItem(pose,state.display,index==0?light:LightCoordsUtil.FULL_BRIGHT,overlay,outline,new int[0],draw.quads(),foil?ItemStackRenderState.FoilType.STANDARD:ItemStackRenderState.FoilType.NONE);pose.popPose();}pose.popPose();
    }
}
