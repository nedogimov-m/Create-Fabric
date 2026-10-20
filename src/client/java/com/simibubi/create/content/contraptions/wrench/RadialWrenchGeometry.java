package com.simibubi.create.content.contraptions.wrench;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Detached GUI quads replace source Tesselator/BufferUploader triangle fans on the native Vulkan pipeline. */
final class RadialWrenchGeometry {
    private record Vertex(float x,float y,int color) {}
    private record Ring(List<Vertex> vertices,Matrix3x2fc pose,ScreenRectangle bounds) implements GuiElementRenderState {
        Ring {vertices=List.copyOf(vertices);pose=new Matrix3x2f(pose);}
        @Override public RenderPipeline pipeline(){return RenderPipelines.GUI;}
        @Override public TextureSetup textureSetup(){return TextureSetup.noTexture();}
        @Override public ScreenRectangle scissorArea(){return null;}
        @Override public void buildVertices(VertexConsumer consumer){for(var vertex:vertices)consumer.addVertexWith2DPose(pose,vertex.x,vertex.y).setColor(vertex.color);}
    }
    static void ring(GuiGraphicsExtractor gui,int x,int y,int inner,int outer,int sectors,int selected){
        if(sectors<2)return;var vertices=new ArrayList<Vertex>();double sector=360d/sectors;
        for(int index=0;index<sectors;index++){
            boolean highlighted=index==selected;int innerColor=highlighted?0x40CCCC33:0x0CFFFFFF,outerColor=highlighted?0x99CCCC33:0x4CFFFFFF;
            int steps=Math.max(1,(int)Math.ceil(sector/3));
            for(int step=0;step<steps;step++){
                double a=Math.toRadians(index*sector-90-sector/2+step*sector/steps),b=Math.toRadians(index*sector-90-sector/2+(step+1)*sector/steps);
                quad(vertices,x,y,inner,outer,a,b,innerColor,outerColor);
                if(highlighted)quad(vertices,x,y,outer+2,outer+3,a,b,outerColor,outerColor);
                quad(vertices,x,y,inner-3,inner-2,a,b,0x7FFFFFFF,0x7FFFFFFF);
            }
        }
        var pose=new Matrix3x2f(gui.pose());var bounds=new ScreenRectangle(x-outer-4,y-outer-4,2*outer+8,2*outer+8).transformMaxBounds(pose);
        ((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addGuiElement(new Ring(vertices,pose,bounds));
    }
    static void indicator(GuiGraphicsExtractor gui,int x,int y,double mouseX,double mouseY){
        double dx=mouseX-x,dy=mouseY-y,length=Math.hypot(dx,dy);if(length<=45)return;double theta=Math.atan2(dy,dx);var vertices=new ArrayList<Vertex>();
        var tip=pointer(x,y,53,0,theta,0xBFCCCCCC);var left=pointer(x,y,48,-5,theta,0x66CCCCCC);var middle=pointer(x,y,49,0,theta,0x66CCCCCC);var right=pointer(x,y,48,5,theta,0x66CCCCCC);
        vertices.add(tip);vertices.add(left);vertices.add(middle);vertices.add(middle);vertices.add(tip);vertices.add(middle);vertices.add(right);vertices.add(right);
        var pose=new Matrix3x2f(gui.pose());var bounds=new ScreenRectangle(x-60,y-60,120,120).transformMaxBounds(pose);
        ((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addGuiElement(new Ring(vertices,pose,bounds));
    }
    private static Vertex pointer(int x,int y,float distance,float side,double theta,int color){return new Vertex(x+(float)(Math.cos(theta)*distance-Math.sin(theta)*side),y+(float)(Math.sin(theta)*distance+Math.cos(theta)*side),color);}
    static void center(GuiGraphicsExtractor gui,int x,int y){
        for(int direction:new int[]{-1,1}){gui.pose().pushMatrix();try{gui.pose().translate(x,y);gui.pose().rotate((float)Math.toRadians(direction*90));gui.fillGradient(-16,0,16,32,0xCC000000,0x99000000);gui.fillGradient(-16,32,16,49,0x99000000,0x33000000);gui.fillGradient(-16,49,16,65,0x33000000,0);}finally{gui.pose().popMatrix();}}
    }
    private static void quad(List<Vertex> vertices,int x,int y,int inner,int outer,double a,double b,int innerColor,int outerColor){
        vertices.add(at(x,y,inner,a,innerColor));vertices.add(at(x,y,outer,a,outerColor));vertices.add(at(x,y,outer,b,outerColor));vertices.add(at(x,y,inner,b,innerColor));
    }
    private static Vertex at(int x,int y,int radius,double angle,int color){return new Vertex(x+(float)Math.cos(angle)*radius,y+(float)Math.sin(angle)*radius,color);}
    private RadialWrenchGeometry(){}
}
