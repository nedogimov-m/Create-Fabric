package com.simibubi.create.content.redstone.nixieTube;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import com.simibubi.create.content.equipment.bell.BellRenderer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.util.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;

/** Source glyphs and genuine ComputerSignal lamps, extracted into detached native submit geometry. */
public final class NixieTubeRenderer implements BlockEntityRenderer<NixieTubeBlockEntity,NixieTubeRenderer.State> {
    public record Colors(int bright,int dark){}
    public record Tube(FormattedCharSequence text,float width,float offset,int front,int shadow,int backShadow){}
    public record ComputerLamp(float offset,float width,float height,boolean near,
            PartialModelRenderState core,PartialModelRenderState glow,
            PartialModelRenderState base,PartialModelRenderState tube){}
    public static final class State extends BlockEntityRenderState {
        public final List<Tube> tubes=new ArrayList<>();
        public final List<ComputerLamp> lamps=new ArrayList<>();
        public final PartialModelRenderState panel=new PartialModelRenderState();
        public String first,second;public float yaw,roll,height;public DyeColor color;
        public boolean computerMode,invertPanel;
    }
    public NixieTubeRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    public static Colors colors(DyeColor color){return switch(color){
        case BLACK->new Colors(0x45403B,0x21201F);case RED->new Colors(0xB13937,0x632737);case GREEN->new Colors(0x208A46,0x1D6045);case BROWN->new Colors(0xAC855C,0x68533E);
        case BLUE->new Colors(0x5391E1,0x504B90);case GRAY->new Colors(0x5D666F,0x313538);case LIGHT_GRAY->new Colors(0x95969B,0x707070);case PURPLE->new Colors(0x9F54AE,0x63366C);
        case CYAN->new Colors(0x3EABB4,0x3C7872);case PINK->new Colors(0xD5A8CB,0xB86B95);case LIME->new Colors(0xA3DF55,0x4FB16F);case YELLOW->new Colors(0xE6D756,0xE9AC29);
        case LIGHT_BLUE->new Colors(0x69CED2,0x508AA5);case ORANGE->new Colors(0xEE9246,0xD94927);case MAGENTA->new Colors(0xF062B0,0xC04488);case WHITE->new Colors(0xEDEAE5,0xBBB6B0);
    };}
    private static int mix(int first,int second,float progress){int r=first>>16&255,g=first>>8&255,b=first&255;return 0xFF000000|(int)(r+((second>>16&255)-r)*progress)<<16|(int)(g+((second>>8&255)-g)*progress)<<8|(int)(b+((second&255)-b)*progress);}
    @Override public void extractRenderState(NixieTubeBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);var block=entity.getBlockState();var face=block.getValue(NixieTubeBlock.FACE);state.yaw=BellRenderer.horizontalAngle(block.getValue(NixieTubeBlock.FACING))-90+(face==DoubleFaceAttachedBlock.DoubleAttachFace.WALL_REVERSED?180:0);state.roll=face==DoubleFaceAttachedBlock.DoubleAttachFace.WALL?-90:face==DoubleFaceAttachedBlock.DoubleAttachFace.WALL_REVERSED?90:0;state.height=face==DoubleFaceAttachedBlock.DoubleAttachFace.CEILING?5:3;state.color=NixieTubeBlock.colorOf(block);
        state.tubes.clear();state.lamps.clear();state.panel.clear();state.computerMode=entity.computerSignal!=null;state.invertPanel=false;
        if(state.computerMode){extractComputer(entity,state,partial,camera);return;}
        var text=entity.getDisplayedStrings();state.first=text.first();state.second=text.second();var colors=colors(state.color);var font=Minecraft.getInstance().font;
        for(int index=0;index<2;index++){var glyph=index==0?state.first:state.second;state.tubes.add(new Tube(FormattedCharSequence.forward(glyph,Style.EMPTY),font.width(glyph),index==0?-4/16f:4/16f,mix(colors.bright(),colors.dark(),entity.getLevel().getRandom().nextFloat()/4),0xFF000000|colors.dark(),mix(colors.dark(),0,.35f)));}
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.computerMode){submitComputer(state,pose,collector);return;}
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.yaw);pose.rotateDegrees(Axis.ZP,state.roll);
        for(var tube:state.tubes){pose.pushPose();pose.translate(tube.offset(),0,0);pose.scale(1/20f,-1/20f,1/20f);
            pose.pushPose();pose.translate((tube.width()-.5f)/-2,-state.height,0);text(pose,collector,tube.text(),tube.front());pose.translate(.5,.5,-1/16f);text(pose,collector,tube.text(),tube.shadow());pose.popPose();
            pose.pushPose();pose.scale(-1,1,1);pose.translate((tube.width()-.5f)/-2,-state.height,0);text(pose,collector,tube.text(),tube.shadow());pose.translate(-.5,.5,-1/16f);text(pose,collector,tube.text(),tube.backShadow());pose.popPose();pose.popPose();
        }pose.popPose();
    }
    /** Snapshot every mutable source byte now; submit retains no BE, DTO, world or model-manager reference. */
    private static void extractComputer(NixieTubeBlockEntity entity,State state,float partial,Vec3 camera){
        state.first="";state.second="";
        var block=entity.getBlockState();var facing=NixieTubeBlock.getFacing(block);
        state.invertPanel=facing==Direction.DOWN;
        boolean inverted=facing==Direction.DOWN||block.getValue(NixieTubeBlock.FACE)==DoubleFaceAttachedBlock.DoubleAttachFace.WALL_REVERSED;
        boolean horizontal=facing.getAxis().isHorizontal();
        double time=entity.getLevel().getGameTime()+partial;
        boolean near=Vec3.atCenterOf(entity.getBlockPos()).distanceToSqr(camera)<96*96;
        AllPartialModels.extract(NixieSignalModels.PANEL,state.panel);
        for(int index=0;index<2;index++){
            var source=index==0?entity.computerSignal.first:entity.computerSignal.second;
            int period=source.blinkPeriod,off=source.blinkOffTime;
            // Source bytes are signed. Period0 disables;1 and negative periods stay continuously on.
            if(period==0||period>1&&time%period<off)continue;
            float width=horizontal?source.glowWidth:source.glowHeight;
            float height=horizontal?source.glowHeight:source.glowWidth;
            int red=source.r&255,green=source.g&255,blue=source.b&255;
            int tint=0xFF000000|red<<16|green<<8|blue;
            int glow=0xFF000000|Math.min((red*6+256)>>3,255)<<16
                |Math.min((green*6+256)>>3,255)<<8|Math.min((blue*6+256)>>3,255);
            state.lamps.add(new ComputerLamp((index==0)==inverted?4/16f:-4/16f,width,height,near,
                near?lampModel(NixieSignalModels.COMPUTER_WHITE_CUBE,0xFFFFFFFF):null,
                near?lampModel(NixieSignalModels.COMPUTER_WHITE_GLOW,glow):null,
                lampModel(NixieSignalModels.COMPUTER_WHITE_BASE,0xFF0C0C0C),
                lampModel(NixieSignalModels.COMPUTER_WHITE,tint)));
        }
    }
    private static PartialModelRenderState lampModel(ExtraModelKey<BlockStateModel> key,int tint){
        var state=new PartialModelRenderState();AllPartialModels.extract(key,state);
        var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();
        state.mesh.forEach(quad->{emitter.copyFrom(quad);emitter.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
            emitter.emissive(true);emitter.multiplyColor(tint);emitter.emit();});
        state.mesh=mesh.immutableCopy();state.translucent=true;return state;
    }
    private static void submitComputer(State state,PoseStack pose,SubmitNodeCollector collector){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.yaw);pose.rotateDegrees(Axis.ZP,state.roll);pose.translate(-.5,-.5,-.5);
        if(state.invertPanel){pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.ZP,180);pose.translate(-.5,-.5,-.5);}
        state.panel.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        pose.translate(.5,7.5/16f,.5);
        for(var lamp:state.lamps){
            pose.pushPose();pose.translate(lamp.offset(),0,0);
            if(lamp.near()){
                pose.pushPose();pose.scale(lamp.width(),lamp.height(),1);submitLamp(lamp.core(),false,pose,collector);pose.popPose();
                pose.pushPose();pose.scale(lamp.width()+1.125f,lamp.height()+1.125f,2);submitLamp(lamp.glow(),true,pose,collector);pose.popPose();
            }
            pose.pushPose();pose.scale(1+1.25f/16,1+1.25f/16,1+1.25f/16);submitLamp(lamp.base(),true,pose,collector);pose.popPose();
            pose.pushPose();pose.scale(1+1/16f,1+1/16f,1+1/16f);submitLamp(lamp.tube(),true,pose,collector);pose.popPose();
            pose.popPose();
        }
        pose.popPose();
    }
    private static void submitLamp(PartialModelRenderState model,boolean additive,PoseStack pose,SubmitNodeCollector collector){
        collector.submitBlockModel(pose,layer->additive?NixieSignalRenderTypes.ADDITIVE:NixieSignalRenderTypes.CORE,
            true,List.of(),model.mesh,new int[0],LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);
    }
    private static void text(PoseStack pose,SubmitNodeCollector collector,FormattedCharSequence text,int color){collector.submitText(pose,0,0,text,false,Font.DisplayMode.NORMAL,LightCoordsUtil.FULL_BRIGHT,color,0,0);}
    @Override public int getViewDistance(){return 128;}
}
