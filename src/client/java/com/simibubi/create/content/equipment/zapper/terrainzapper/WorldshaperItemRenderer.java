package com.simibubi.create.content.equipment.zapper.terrainzapper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.zapper.ZapperItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;

public class WorldshaperItemRenderer extends ZapperItemRenderer {
    protected static final net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey<net.minecraft.client.renderer.block.dispatch.BlockStateModel> CORE=net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey.create(()->"create:equipment/item/handheld_worldshaper/core");
    protected static final net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey<net.minecraft.client.renderer.block.dispatch.BlockStateModel> CORE_GLOW=net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey.create(()->"create:equipment/item/handheld_worldshaper/core_glow");
    protected static final net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey<net.minecraft.client.renderer.block.dispatch.BlockStateModel> ACCELERATOR=net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey.create(()->"create:equipment/item/handheld_worldshaper/accelerator");
    public static final WorldshaperItemRenderer INSTANCE=new WorldshaperItemRenderer();
    public WorldshaperItemRenderer(){model("item",modelPath("item"));models.put("core",CORE);models.put("core_glow",CORE_GLOW);models.put("accelerator",ACCELERATOR);}
    @Override protected String modelPath(String name){return "item/handheld_worldshaper/"+name;}
    @Override protected State extract(ItemStack stack,ItemDisplayContext display,float partial,float time){var state=new ZapperState();if(display==ItemDisplayContext.GUI)renderBlockUsed(stack,state);var pose=new PoseStack();draw(state,"item",pose);var player=Minecraft.getInstance().player;boolean main=player!=null&&player.getMainHandItem()==stack,off=player!=null&&player.getOffhandItem()==stack;float animation=player==null?0:getAnimationProgress(partial,player.getMainArm()==HumanoidArm.LEFT,main),seconds=time/20;float multiplier=main||off?animation:Mth.sin(seconds*5);int intensity=(int)(15*Mth.clamp(multiplier,0,1));int glow=LightCoordsUtil.pack(intensity,Math.max(intensity,4));glowing(state,"core",pose,glow,false);glowing(state,"core_glow",pose,glow,true);float angle=(seconds*-25+(main||off?360*animation:0))%360;pose.translate(0,-.155,0);pose.rotateDegrees(Axis.ZP,angle);pose.translate(0,.155,0);draw(state,"accelerator",pose);return state;}
}
