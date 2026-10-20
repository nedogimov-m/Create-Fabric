package com.simibubi.create.content.trains.display;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.equipment.bell.BellRenderer;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Style;
import net.minecraft.util.*;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Extracted source monospace/spinning glyphs and real shaftless cog in the native submit pipeline. */
public final class FlapDisplayRenderer implements BlockEntityRenderer<FlapDisplayBlockEntity,FlapDisplayRenderer.State> {
    private static final Map<Mesh,Mesh> CUTOUT_COGS=Collections.synchronizedMap(new WeakHashMap<>());
    private static void cutout(PartialModelRenderState state){if(state.mesh==null)return;var original=state.mesh;state.mesh=CUTOUT_COGS.computeIfAbsent(original,key->{var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();key.forEach(quad->emitter.copyFrom(quad).chunkLayer(ChunkSectionLayer.CUTOUT).emit());return mesh.immutableCopy();});state.translucent=false;}
    public record Glyph(float x,float y,FormattedCharSequence text,int color,int light){}
    public static final class State extends BlockEntityRenderState {public final PartialModelRenderState cog=new PartialModelRenderState();public final List<Glyph> glyphs=new ArrayList<>();public Direction facing;public float yaw,angle;public boolean controller,running;public int width,height;}
    public FlapDisplayRenderer(BlockEntityRendererProvider.Context context){}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(FlapDisplayBlockEntity entity,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(entity,state,partial,camera,overlay);state.facing=entity.getBlockState().getValue(FlapDisplayBlock.HORIZONTAL_FACING);state.yaw=BellRenderer.horizontalAngle(state.facing);state.controller=entity.isController;state.running=entity.isSpeedRequirementFulfilled();state.width=entity.xSize;state.height=entity.ySize;
        var axis=state.facing.getAxis();var pos=entity.getBlockPos();int parity=(axis==Direction.Axis.X?0:pos.getX())+pos.getY()+(axis==Direction.Axis.Z?0:pos.getZ());state.angle=entity.getAngle(partial)+(parity%2==0?22.5f:0);state.cog.clear();AllPartialModels.extract(AllPartialModels.SHAFTLESS_COGWHEEL,state.cog);cutout(state.cog);state.glyphs.clear();if(!state.controller)return;
        var font=Minecraft.getInstance().font;long ticks=state.running?entity.getLevel().getGameTime():0;float time=state.running?ticks+partial:0;var lines=entity.getLines();
        for(int row=0;row<lines.size();row++){var sections=lines.get(row).getSections();float total=0;for(var section:sections)total+=section.size+(section.hasGap?8:1);float sectionX=state.width*16-total/2+1;int color=entity.getLineColor(row);boolean glow=entity.isLineGlowing(row);int light=glow?LightCoordsUtil.FULL_BRIGHT:state.lightCoords;
            for(int index=0;index<sections.size();index++){var section=sections.get(index);var options=FlapDisplaySection.getFlapCycle(section.cycle);String text=!section.singleFlap||!section.isSpinning(0)?section.text:options[Math.floorMod(ticks/3+index*13L,options.length)];var advance=new float[]{0};float baseX=sectionX;int lineIndex=row;int baseColor=color;
                StringDecomposer.iterateFormatted(text,Style.EMPTY,(charIndex,style,codepoint)->{
                    float dim=1;int glyph=codepoint;int flapIndex=section.flapCount()==0?-1:Math.min(charIndex,section.flapCount()-1);
                    if(!section.singleFlap&&section.isSpinning(flapIndex)){float speed=section.spinningTicks>5&&section.spinningTicks<20?1.75f:2.5f;float cycle=time/speed+charIndex*16.83f+lineIndex*.75f,progress=cycle%1;int replacement=options[Math.floorMod((int)cycle,options.length)].charAt(0);glyph=!state.running?replacement:progress>.5f?progress>.75f?'_':'-':replacement;dim=.75f;}
                    var original=new String(Character.toChars(glyph));float width=font.getSplitter().stringWidth(original);
                    if(section.singleFlap&&section.isSpinning(0)){if(ticks%3==0)glyph=width==6?'-':width==1?'\'':glyph;if(ticks%3==2)glyph=width==6?'_':width==1?'.':glyph;if(ticks%3!=1)dim=.75f;}
                    float standard=section.wideFlaps?FlapDisplaySection.WIDE_MONOSPACE:FlapDisplaySection.MONOSPACE;if(!section.singleFlap)advance[0]+=(standard-width)/2;
                    int rgb=style.getColor()==null?dim(baseColor,dim):style.getColor().getValue();int argb=(glow?249:217)<<24|rgb&0xFFFFFF;var detachedStyle=style.withColor(rgb).withBold(false);state.glyphs.add(new Glyph(baseX+advance[0],4.5f+lineIndex*16,FormattedCharSequence.forward(new String(Character.toChars(glyph)),detachedStyle),argb,light));advance[0]+=section.singleFlap?width:standard-(standard-width)/2;return true;
                });sectionX+=section.size+(section.hasGap?8:1);
            }
        }
    }
    private static int dim(int color,float amount){return (int)((color>>16&255)*amount)<<16|(int)((color>>8&255)*amount)<<8|(int)((color&255)*amount);}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.translate(.5,.5,.5);pose.rotate(switch(state.facing.getAxis()){case X->Axis.XP.rotationDegrees(state.angle);case Y->Axis.YP.rotationDegrees(state.angle);case Z->Axis.ZP.rotationDegrees(state.angle);});pose.rotate(new Quaternionf().rotationTo(0,1,0,state.facing.getStepX(),0,state.facing.getStepZ()));pose.translate(-.5,-.5,-.5);state.cog.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        if(!state.controller)return;pose.pushPose();pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,state.yaw);pose.translate(-.5,-.5,-.5-3/16f);pose.translate(0,1,1);pose.scale(1/32f,-1/32f,1/32f);pose.translate(0,0,.5);for(var glyph:state.glyphs)collector.submitText(pose,glyph.x(),glyph.y(),glyph.text(),false,Font.DisplayMode.NORMAL,glyph.light(),glyph.color(),0,0);pose.popPose();
    }
    @Override public boolean shouldRenderOffScreen(){return true;}
    @Override public int getViewDistance(){return 128;}
}
