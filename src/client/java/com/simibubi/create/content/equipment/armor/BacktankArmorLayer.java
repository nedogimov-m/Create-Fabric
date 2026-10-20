package com.simibubi.create.content.equipment.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.model.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.*;

public class BacktankArmorLayer<S extends LivingEntityRenderState,M extends EntityModel<? super S>> extends RenderLayer<S,M> {
    public static final RenderStateDataKey<Frame> BACKTANK=RenderStateDataKey.create(()->"create:backtank_armor");
    public record Frame(PartialModelRenderState body,PartialModelRenderState shaft,PartialModelRenderState cogs,float angle,boolean netherite,boolean foil){}
    private final HumanoidModel<net.minecraft.client.renderer.entity.state.HumanoidRenderState> inner=new HumanoidModel<>(net.minecraft.client.model.geom.builders.LayerDefinition.create(HumanoidModel.createMesh(new net.minecraft.client.model.geom.builders.CubeDeformation(.5f),0),64,32).bakeRoot());
    public BacktankArmorLayer(RenderLayerParent<S,M> renderer){super(renderer);inner.head.visible=inner.hat.visible=inner.leftLeg.visible=inner.rightLeg.visible=false;}
    public static void extract(LivingEntity entity,LivingEntityRenderState state,float partial){
        Frame frame=null;var item=BacktankItem.getWornBy(entity);
        if(item!=null&&entity.getPose()!=Pose.SLEEPING){var block=item.getBlock().defaultBlockState();var body=new PartialModelRenderState();var shaft=new PartialModelRenderState();var cogs=new PartialModelRenderState();boolean netherite=block.is(AllBlocks.NETHERITE_BACKTANK);AllPartialModels.extract(netherite?AllPartialModels.NETHERITE_BACKTANK_BODY:AllPartialModels.COPPER_BACKTANK_BODY,body);AllPartialModels.extract(BacktankRenderer.getShaftModel(block),shaft);AllPartialModels.extract(BacktankRenderer.getCogsModel(block),cogs);frame=new Frame(body,shaft,cogs,(entity.level().getGameTime()+partial)*2%360,netherite,entity.getItemBySlot(BacktankItem.SLOT).hasFoil());}
        var data=(FabricRenderState)state;if(frame!=null||data.getData(BACKTANK)!=null)data.setData(BACKTANK,frame);
    }
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,S state,float yaw,float pitch){var frame=((FabricRenderState)state).getData(BACKTANK);if(frame==null||!(getParentModel() instanceof HumanoidModel<?> model))return;if(frame.netherite&&state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid){collector.submitModel(inner,humanoid,pose,(frame.foil?net.minecraft.client.renderer.rendertype.RenderTypes.armorCutoutNoCullGlint(Create.asResource("textures/models/armor/netherite_diving_layer_2.png")):net.minecraft.client.renderer.rendertype.RenderTypes.armorCutoutNoCull(Create.asResource("textures/models/armor/netherite_diving_layer_2.png"))),light,OverlayTexture.NO_OVERLAY,0);}pose.pushPose();model.body.translateAndRotate(pose);pose.translate(-.5,10/16.,1);pose.scale(1,-1,-1);pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,180);pose.translate(-.5,-.5,-.5);BacktankRenderer.submitPartial(pose,collector,frame.body,light,0,frame.foil);pose.popPose();pose.pushPose();pose.translate(0,-3/16.,0);BacktankRenderer.submitPartial(pose,collector,frame.shaft,light,0,frame.foil);pose.popPose();BacktankRenderer.submitCogs(pose,collector,frame.cogs,0,frame.angle,light,0,frame.foil);pose.popPose();}
}
