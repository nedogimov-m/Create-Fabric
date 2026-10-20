package com.simibubi.create.content.logistics.tunnel;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsClient;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollOptionBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.feature.TextFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Actual installed source owners, native extraction, native submit queues and RenderPearl vertices. */
public final class ValueBoxNativeFrameProbe {
    public static void tunnel(ClientGameTestContext context,TestSingleplayerContext world,BlockPos pos){
        context.waitFor(client->modeFrame(pos,BrassTunnelBlockEntity.SelectionMode.SPLIT)!=null,200);
        var frozen=new AtomicReference<ValueBox.State>();var bytes=new AtomicReference<List<String>>();
        context.runOnClient(client->{
            var tunnel=(BrassTunnelBlockEntity)client.level.getBlockEntity(pos);
            var state=modeFrame(pos,tunnel.getSelectionMode());
            frozen.set(state);bytes.set(receipt(state));
            var icon=state.draws().stream().filter(ValueBox.IconDraw.class::isInstance).map(ValueBox.IconDraw.class::cast)
                .filter(draw->draw.icon()==tunnel.getSelectionMode().getIcon()).findFirst().orElseThrow();
            if(icon.color()!=0xFF592424)throw new AssertionError("Source RGB override lost opaque native ARGB alpha");
            var setting=tunnel.selectionMode;
            var text=new ValueBox.TextValueBox(setting.label,new AABB(Vec3.ZERO,Vec3.ZERO).inflate(.5),pos,Component.literal(setting.formatValue()));
            text.withOwner(setting).transform(setting.getSlotPositioning());
            if(setting.getSlotPositioning() instanceof com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided sided)sided.fromSide(Direction.UP);
            var extracted=text.extractRenderState(client.gameRenderer.mainCamera().position(),.5f,1);
            requireText(extracted,setting.formatValue(),0xFF592424,0);
        });
        world.getServer().runOnServer(ignored->{
            var tunnel=(BrassTunnelBlockEntity)world.getConnection().getServerLevel().getBlockEntity(pos);
            if(!tunnel.setSelectionMode(BrassTunnelBlockEntity.SelectionMode.ROUND_ROBIN))throw new AssertionError("Actual tunnel group rejected mode mutation");
        });
        context.waitFor(client->client.level.getBlockEntity(pos) instanceof BrassTunnelBlockEntity tunnel
            &&tunnel.getSelectionMode()==BrassTunnelBlockEntity.SelectionMode.ROUND_ROBIN
            &&modeFrame(pos,BrassTunnelBlockEntity.SelectionMode.ROUND_ROBIN)!=null,200);
        context.runOnClient(client->{
            if(!bytes.get().equals(receipt(frozen.get())))throw new AssertionError("Native extracted source value box retained mutable setting/transform/world data");
            receipt(modeFrame(pos,BrassTunnelBlockEntity.SelectionMode.ROUND_ROBIN));
        });
        world.getServer().runOnServer(ignored->{
            if(!((BrassTunnelBlockEntity)world.getConnection().getServerLevel().getBlockEntity(pos)).setSelectionMode(BrassTunnelBlockEntity.SelectionMode.SPLIT))throw new AssertionError("Actual tunnel mode restore rejected");
        });
        context.waitFor(client->((BrassTunnelBlockEntity)client.level.getBlockEntity(pos)).getSelectionMode()==BrassTunnelBlockEntity.SelectionMode.SPLIT
            &&modeFrame(pos,BrassTunnelBlockEntity.SelectionMode.SPLIT)!=null,200);
        replacementWarmup(context,pos);
    }
    private static void replacementWarmup(ClientGameTestContext context,BlockPos pos){
        context.getInput().holdKey(options->options.keyUse);
        context.waitFor(client->client.options.keyUse.isDown()&&client.gui.screen()==null,40);
        context.runOnClient(client->{
            var tunnel=(BrassTunnelBlockEntity)client.level.getBlockEntity(pos);var original=tunnel.selectionMode;var value=original.get();
            var input=ValueSettingsClient.INSTANCE;
            try{
                if(!client.options.keyUse.isDown())throw new AssertionError("Replacement regression requires the actual native use key to stay held");
                input.startInteractionWith(pos,ScrollValueBehaviour.TYPE,InteractionHand.MAIN_HAND,Direction.UP);
                if(input.interactHeldTicks!=0)throw new AssertionError("Actual registered source owner did not start warmup");
                var successor=new ScrollOptionBehaviour<>(BrassTunnelBlockEntity.SelectionMode.class,original.label,tunnel,original.getSlotPositioning());
                successor.onlyActiveWhen(tunnel::hasDistributionBehaviour);successor.value=original.getValue();
                tunnel.selectionMode=successor;tunnel.attachBehaviourLate(successor);
                if(tunnel.getBehaviour(ScrollValueBehaviour.TYPE)!=successor)throw new AssertionError("Native canonical replacement was not installed");
                input.tick();
                if(input.interactHeldTicks!=-1||client.gui.screen()!=null||tunnel.getSelectionMode()!=value)throw new AssertionError("Warmup followed a same-TYPE successor instead of cancelling the original identity");
            }finally{tunnel.selectionMode=original;tunnel.attachBehaviourLate(original);input.cancelInteraction();}
        });
        context.getInput().releaseKey(options->options.keyUse);
        context.waitTicks(2);
    }
    public static void filterCount(ClientGameTestContext context,BlockPos pos){
        context.waitFor(client->{
            if(client.level==null||!(client.level.getBlockEntity(pos) instanceof FunnelBlockEntity funnel)
                ||funnel.getBehaviour(FilteringBehaviour.TYPE)!=funnel.filtering||funnel.filtering.count!=17)return false;
            var expected=Integer.toString(funnel.filtering.count);
            return ValueBoxRenderHandler.latest().boxes().stream().filter(box->box.position().equals(pos)&&box.highlighted())
                .anyMatch(box->box.draws().stream().anyMatch(draw->{
                    if(!(draw instanceof ValueBox.TextDraw text))return false;
                    var actual=new StringBuilder();text.text().accept((index,style,codepoint)->{actual.appendCodePoint(codepoint);return true;});
                    return actual.toString().equals(expected);
                }));
        },200);
        context.runOnClient(client->{
            var funnel=(FunnelBlockEntity)client.level.getBlockEntity(pos);var actual=funnel.filtering;
            if(actual.count!=17||actual.upTo||!actual.getFilter().is(Items.APPLE)||client.player.getMainHandItem().getCount()!=8)throw new AssertionError("Actual source frame changed native filter/count/payment custody");
            var frame=ValueBoxRenderHandler.latest().boxes().stream().filter(box->box.position().equals(pos)&&box.highlighted())
                .filter(box->box.draws().stream().anyMatch(ValueBox.TextDraw.class::isInstance)).findFirst().orElseThrow();
            requireText(frame,Integer.toString(actual.count),0xFFEDEDED,0xFF333333);receipt(frame);
        });
    }
    private static ValueBox.State modeFrame(BlockPos pos,BrassTunnelBlockEntity.SelectionMode mode){
        return ValueBoxRenderHandler.latest().boxes().stream().filter(box->box.position().equals(pos)&&box.highlighted())
            .filter(box->box.draws().stream().anyMatch(draw->draw instanceof ValueBox.IconDraw icon&&icon.icon()==mode.getIcon())).findFirst().orElse(null);
    }
    private static List<SubmitNode> submit(ValueBox.State state){
        if(state==null)throw new AssertionError("Actual native value-box extraction missing");
        var storage=new SubmitNodeStorage();state.submit(storage);var nodes=new ArrayList<SubmitNode>();
        for(var collection:storage.getSubmitsPerOrder().values())for(var phase:collection.allPhases())phase.sortInto((node,flag)->nodes.add(node));
        if(nodes.isEmpty())throw new AssertionError("Source value box bypassed the actual native submit phases");
        return nodes;
    }
    private static void requireText(ValueBox.State state,String expected,int color,int outline){
        boolean found=false;
        for(var node:submit(state))if(node instanceof TextFeatureRenderer.Submit text&&text.content() instanceof TextFeatureRenderer.Content.Text content){
            var string=new StringBuilder();content.string().accept((index,style,codepoint)->{string.appendCodePoint(codepoint);return true;});
            if(!string.toString().equals(expected))continue;
            if(content.color()!=color||content.outlineColor()!=outline||text.lightCoords()!=LightCoordsUtil.FULL_BRIGHT)throw new AssertionError("Actual native source text lost tint/outline/fullbright payload");
            found=true;
        }
        if(!found)throw new AssertionError("Actual native source text does not match the canonical registered owner's value: "+expected);
    }
    private static List<String> receipt(ValueBox.State state){
        var result=new ArrayList<String>();int icons=0;
        for(var node:submit(state)){
            if(node instanceof CustomFeatureRenderer.Submit custom){
                var format=custom.renderType().format();
                try(var memory=new ByteBufferBuilder(1024)){
                    var builder=new BufferBuilder(memory,PrimitiveTopology.QUADS,format);custom.customGeometryRenderer().render(custom.pose(),builder);
                    try(var mesh=builder.buildOrThrow()){
                        if(mesh.drawState().vertexCount()!=4)throw new AssertionError("Actual source value-box icon is not one real native quad");
                        var data=mesh.vertexBuffer().duplicate().order(ByteOrder.nativeOrder());int stride=format.getVertexSize();
                        int position=format.getElement(DefaultVertexFormat.POSITION_SEMANTIC_NAME).offset(),uv=format.getElement(DefaultVertexFormat.UV0_SEMANTIC_NAME).offset(),color=format.getElement(DefaultVertexFormat.COLOR_SEMANTIC_NAME).offset(),light=format.getElement(DefaultVertexFormat.UV2_SEMANTIC_NAME).offset();
                        float minU=1,maxU=0,minV=1,maxV=0;int argb=0;
                        for(int vertex=0;vertex<4;vertex++){
                            int at=vertex*stride;
                            for(int axis=0;axis<3;axis++)if(!Float.isFinite(data.getFloat(at+position+axis*4)))throw new AssertionError("Actual native source box contains non-finite vertices");
                            float u=data.getFloat(at+uv),v=data.getFloat(at+uv+4);minU=Math.min(minU,u);maxU=Math.max(maxU,u);minV=Math.min(minV,v);maxV=Math.max(maxV,v);
                            int tint=(data.get(at+color+3)&255)<<24|(data.get(at+color)&255)<<16|(data.get(at+color+1)&255)<<8|data.get(at+color+2)&255;
                            if(vertex==0)argb=tint;else if(argb!=tint)throw new AssertionError("Native value-box icon tint varies between source vertices");
                            if(data.getInt(at+light)!=LightCoordsUtil.FULL_BRIGHT)throw new AssertionError("Native source value-box icon lost fullbright light");
                        }
                        boolean matches=false;
                        for(var draw:state.draws())if(draw instanceof ValueBox.IconDraw icon&&icon.color()==argb
                            &&Math.abs(minU-icon.icon().getIconX()/256f)<1e-6&&Math.abs(minV-icon.icon().getIconY()/256f)<1e-6
                            &&Math.abs(maxU-minU-1/16f)<1e-6&&Math.abs(maxV-minV-1/16f)<1e-6)matches=true;
                        if(!matches)throw new AssertionError("Actual native quad does not carry its genuine source icon UV/color");
                        var copy=new byte[data.remaining()];data.get(copy);result.add(HexFormat.of().formatHex(copy));icons++;
                    }
                }
            }else if(node instanceof TextFeatureRenderer.Submit text&&text.content() instanceof TextFeatureRenderer.Content.Text content){
                var string=new StringBuilder();content.string().accept((index,style,codepoint)->{string.appendCodePoint(codepoint);return true;});
                result.add(text.pose().toString()+":"+string+":"+content.color()+":"+content.outlineColor()+":"+text.lightCoords());
            }else throw new AssertionError("Unexpected native source value-box feature node: "+node);
        }
        if(icons!=state.draws().stream().filter(ValueBox.IconDraw.class::isInstance).count())throw new AssertionError("Actual native source icon submit count mismatch");
        return List.copyOf(result);
    }
    private ValueBoxNativeFrameProbe(){}
}
