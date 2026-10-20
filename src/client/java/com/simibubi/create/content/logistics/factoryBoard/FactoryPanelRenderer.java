package com.simibubi.create.content.logistics.factoryBoard;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Original panel geometry, items, labels and connection meshes in native extracted states. */
public final class FactoryPanelRenderer implements BlockEntityRenderer<FactoryPanelBlockEntity,FactoryPanelRenderer.State> {
    private final ItemModelResolver items;
    private final Font font;
    public FactoryPanelRenderer(BlockEntityRendererProvider.Context context) { items=context.itemModelResolver();font=context.font(); }
    public static final class State extends BlockEntityRenderState {
        public float xRot,yRot;
        public final List<Panel> panels=new ArrayList<>();
    }
    public static final class Panel {
        public FactoryPanelBlock.PanelSlot slot;
        public final PartialModelRenderState base=new PartialModelRenderState(),light=new PartialModelRenderState(),halo=new PartialModelRenderState();
        public final ItemStackRenderState sample=new ItemStackRenderState();
        public final List<Path> paths=new ArrayList<>();
        public org.joml.Vector3f center;
        public net.minecraft.util.FormattedCharSequence count;
        public float countX;
        public boolean lit;
    }
    private record Path(PartialModelRenderState model,float x,float y,float z) {}
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(FactoryPanelBlockEntity gauge,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(gauge,state,partial,camera,overlay);state.panels.clear();var block=gauge.getBlockState();state.xRot=(float)Math.toDegrees(FactoryPanelBlock.getXRot(block));state.yRot=(float)Math.toDegrees(FactoryPanelBlock.getYRot(block));
        for(var behaviour:gauge.panels.values())if(behaviour.active){
            var panel=new Panel();panel.slot=behaviour.slot;var key=gauge.restocker?behaviour.count==0?AllPartialModels.FACTORY_PANEL_RESTOCKER:AllPartialModels.FACTORY_PANEL_RESTOCKER_WITH_BULB:behaviour.count==0?AllPartialModels.FACTORY_PANEL:AllPartialModels.FACTORY_PANEL_WITH_BULB;AllPartialModels.extract(key,panel.base);
            if(behaviour.count>0){var bulb=behaviour.redstonePowered||behaviour.isMissingAddress()?AllPartialModels.FACTORY_PANEL_RED_LIGHT:AllPartialModels.FACTORY_PANEL_LIGHT;AllPartialModels.extract(bulb,panel.light);panel.lit=behaviour.getBulb(partial)>.125f;if(panel.lit){AllPartialModels.extract(bulb,panel.halo);int value=Math.clamp((int)(200*(1-2*Math.pow(behaviour.getBulb(partial)-.75f,2))),0,255);color(panel.halo,0xFF000000|value<<16|value<<8|value,true);}}
            var center=FactoryPanelSlotPositioning.getCenterOfSlot(block,behaviour.slot);panel.center=new org.joml.Vector3f((float)center.x,(float)center.y,(float)center.z);
            if(!behaviour.getFilter().isEmpty()){items.updateForTopItem(panel.sample,behaviour.getFilter(),ItemDisplayContext.FIXED,gauge.getLevel(),null,0);String units=behaviour.upTo?"":"▤",amount=behaviour.waitingForNetwork?"?":behaviour.getLevelInStorage()>=com.simibubi.create.content.logistics.BigItemStack.INF?"∞":Integer.toString(behaviour.getLevelInStorage()/(behaviour.upTo?1:behaviour.getFilter().getMaxStackSize()))+units;String label=amount+(behaviour.count==0?"":(behaviour.getPromised()==0?"":"▴")+"/"+behaviour.count+units);panel.count=net.minecraft.network.chat.Component.literal(label).withColor(behaviour.count==0?0xF1EFE8:behaviour.satisfied?0xD7FFA8:behaviour.promisedSatisfied?0xFFCD75:0xFFBFA8).getVisualOrderText();panel.countX=-font.width(panel.count)/2f;}
            for(var connection:behaviour.targetedBy.values())extractPath(behaviour,panel,connection,partial);
            for(var connection:behaviour.targetedByLinks.values())extractPath(behaviour,panel,connection,partial);
            state.panels.add(panel);
        }
    }
    private void extractPath(FactoryPanelBehaviour behaviour,Panel panel,FactoryPanelConnection connection,float partial) {
        var path=connection.getPath(behaviour.getWorld(),behaviour.panelBE().getBlockState(),behaviour.getPanelPosition());float x=0,z=0,glow=behaviour.getBulb(partial),height=1+(behaviour.promisedSatisfied?1:behaviour.satisfied?0:2);int tint=behaviour.getIngredientStatusColor();
        if(!behaviour.redstonePowered&&!behaviour.waitingForNetwork&&glow>0&&!behaviour.satisfied){float pulse=1-(1-glow)*(1-glow);tint=mix(tint,connection.success?0xEAF2EC:0xE5654B,pulse);if(!behaviour.promisedSatisfied)height+=(connection.success?1:2)*pulse;}
        boolean animated=!behaviour.isMissingAddress()&&!behaviour.waitingForNetwork&&!behaviour.satisfied&&!behaviour.redstonePowered;
        var arrows=animated?AllPartialModels.FACTORY_PANEL_ANIMATED_ARROWS:AllPartialModels.FACTORY_PANEL_ARROWS;var lines=animated?AllPartialModels.FACTORY_PANEL_ANIMATED_LINES:AllPartialModels.FACTORY_PANEL_LINES;
        for(int i=0;i<path.size();i++){var direction=path.get(i);x+=direction.getStepX()*.5f;z+=direction.getStepZ()*.5f;var model=new PartialModelRenderState();AllPartialModels.extract((i==0?arrows:lines).get(direction.getOpposite()),model);color(model,0xFF000000|tint,false);panel.paths.add(new Path(model,x,(height+direction.get2DDataValue()%2*.125f)/512f,z));}
    }
    private static int mix(int from,int to,float amount) { int result=0;for(int shift=0;shift<=16;shift+=8){int start=from>>shift&255,end=to>>shift&255;result|=Math.clamp((int)(start+(end-start)*amount),0,255)<<shift;}return result; }
    private static void color(PartialModelRenderState model,int color,boolean emissive) { var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();model.mesh.forEach(quad->{emitter.copyFrom(quad).multiplyColor(color).emissive(emissive).emit();});model.mesh=mesh.immutableCopy(); }
    private static void transform(State state,PoseStack pose) { pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.yRot);pose.rotateDegrees(Axis.XP,state.xRot+90);pose.rotateDegrees(Axis.YP,180);pose.translate(-.5,-.5,-.5); }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
        for(var panel:state.panels){
            pose.pushPose();transform(state,pose);pose.translate(panel.slot.xOffset*.5,0,panel.slot.yOffset*.5);panel.base.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);if(!panel.light.isEmpty())panel.light.submit(pose,collector,panel.lit?LightCoordsUtil.FULL_BRIGHT:state.lightCoords,OverlayTexture.NO_OVERLAY,0);if(panel.lit)collector.submitBlockModel(pose,layer->net.minecraft.client.renderer.rendertype.RenderTypes.eyes(TextureAtlas.LOCATION_BLOCKS),true,List.of(),panel.halo.mesh,new int[0],LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);pose.popPose();
            for(var path:panel.paths){pose.pushPose();transform(state,pose);pose.translate(panel.slot.xOffset*.5+.25+path.x(),path.y(),panel.slot.yOffset*.5+.25+path.z());path.model().submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
            if(!panel.sample.isEmpty()){pose.pushPose();pose.translate(panel.center.x,panel.center.y,panel.center.z);pose.rotateDegrees(Axis.YP,state.yRot+180);pose.rotateDegrees(Axis.XP,-state.xRot);pose.scale(.325f,.325f,.325f);panel.sample.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
            if(panel.count!=null){pose.pushPose();pose.translate(panel.center.x,panel.center.y,panel.center.z);pose.rotateDegrees(Axis.YP,state.yRot+180);pose.rotateDegrees(Axis.XP,-state.xRot);pose.translate(0,-.125,.002);pose.scale(-.005f,-.005f,.005f);collector.submitText(pose,panel.countX,0,panel.count,false,Font.DisplayMode.NORMAL,LightCoordsUtil.FULL_BRIGHT,0xFFFFFFFF,0,0xFF222222);pose.popPose();}
        }
    }
    @Override public boolean shouldRenderOffScreen() { return true; }
}
