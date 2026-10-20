package com.simibubi.create.content.equipment.potatoCannon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.EquipmentClient;
import com.simibubi.create.content.equipment.EquipmentItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;

public final class PotatoCannonItemRenderer extends EquipmentItemRenderer {
    public static final PotatoCannonItemRenderer INSTANCE=new PotatoCannonItemRenderer();
    private PotatoCannonItemRenderer(){model("item",modelPath("item"));model("cog",modelPath("cog"));}
    @Override protected String modelPath(String name){return "item/potato_cannon/"+name;}
    @Override protected State extract(ItemStack stack,ItemDisplayContext display,float partial,float time){
        var state=new State();var pose=new PoseStack();draw(state,"item",pose);float angle=time*-2.5f;var player=Minecraft.getInstance().player;
        if(player!=null){boolean main=player.getMainHandItem()==stack,off=player.getOffhandItem()==stack;if(main||off){float speed=EquipmentClient.POTATO_CANNON.getAnimation(main^(player.getMainArm()==HumanoidArm.LEFT),partial);angle+=360*Mth.clamp(speed*5,0,1);}}
        pose.translate(0,.5f/16,0);pose.rotateDegrees(Axis.ZP,angle%360);pose.translate(0,-.5f/16,0);draw(state,"cog",pose);return state;
    }
}
