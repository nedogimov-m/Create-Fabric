package com.simibubi.create.content.equipment.extendoGrip;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.equipment.EquipmentItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.*;

/** Source scissor linkage, hand pose and cog transforms, extracted into native immutable draws. */
public final class ExtendoGripItemRenderer extends EquipmentItemRenderer {
    public static final ExtendoGripItemRenderer INSTANCE=new ExtendoGripItemRenderer();
    private ExtendoGripItemRenderer(){for(var name:new String[]{"item","cog","thin_short","wide_short","thin_long","wide_long"})model(name,modelPath(name));}
    @Override protected String modelPath(String name){return "item/extendo_grip/"+name;}
    @Override protected State extract(ItemStack stack,ItemDisplayContext display,float partial,float time){
        var state=new State();var pose=new PoseStack();float animation=display.firstPerson()?Mth.lerp(partial,ExtendoGripRenderHandler.lastMainHandAnimation,ExtendoGripRenderHandler.mainHandAnimation):.25f;animation=animation*animation*animation;
        float extension=Mth.lerp(animation,24,156),half=extension/2,opposite=180-extension;
        draw(state,"item",pose);pose.pushPose();pose.translate(0,1/16f,-7/16f);pose.scale(1,1,1+animation);pose.pushPose();
        pose.rotateDegrees(Axis.XP,-half);offset(pose,1);draw(state,"thin_short",pose);offset(pose,-1);
        pose.translate(0,5.5f/16,0);pose.rotateDegrees(Axis.XP,-opposite);offset(pose,1);draw(state,"wide_long",pose);offset(pose,-1);
        pose.translate(0,11/16f,0);pose.rotateDegrees(Axis.XP,opposite);offset(pose,1);pose.translate(0,.5f/16,0);draw(state,"thin_short",pose);offset(pose,-1);pose.popPose();pose.pushPose();
        pose.rotateDegrees(Axis.XP,-180+half);offset(pose,1);draw(state,"wide_short",pose);offset(pose,-1);
        pose.translate(0,5.5f/16,0);pose.rotateDegrees(Axis.XP,opposite);offset(pose,1);draw(state,"thin_long",pose);offset(pose,-1);
        pose.translate(0,11/16f,0);pose.rotateDegrees(Axis.XP,-opposite);offset(pose,1);pose.translate(0,.5f/16,0);draw(state,"wide_short",pose);offset(pose,-1);
        pose.translate(0,5.5f/16,0);pose.rotateDegrees(Axis.XP,180-half);pose.rotateDegrees(Axis.YP,180);pose.translate(0,0,-4/16f);pose.scale(1,1,1/(1+animation));
        draw(state,display.firstPerson()?ExtendoGripRenderHandler.pose:AllPartialModels.DEPLOYER_HAND_POINTING,pose);pose.popPose();pose.popPose();
        pose.pushPose();float angle=(time*-2+(display.firstPerson()?360*animation:0))%360;pose.translate(0,1/16f,0);pose.rotateDegrees(Axis.ZP,angle);pose.translate(0,-1/16f,0);draw(state,"cog",pose);pose.popPose();return state;
    }
    private static void offset(PoseStack pose,int sign){pose.translate(0,sign*.5,sign*.5);}
}
