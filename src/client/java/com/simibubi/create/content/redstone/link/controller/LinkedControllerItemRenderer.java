package com.simibubi.create.content.redstone.link.controller;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Native item layers with immutable extracted meshes; no live model or client access in submit. */
public final class LinkedControllerItemRenderer implements SpecialModelRenderer<LinkedControllerItemRenderer.State>{
    private static final ExtraModelKey<BlockStateModel> NORMAL=ExtraModelKey.create(()->"create:controller/item"),
        POWERED=ExtraModelKey.create(()->"create:controller/powered"),BUTTON=ExtraModelKey.create(()->"create:controller/button");
    private static final Vector3fc[] EXTENTS={new Vector3f(-.5f,-.5f,-.5f),new Vector3f(.5f,-.5f,-.5f),new Vector3f(-.5f,.5f,-.5f),new Vector3f(-.5f,-.5f,.5f),new Vector3f(.5f,.5f,-.5f),new Vector3f(.5f,-.5f,.5f),new Vector3f(-.5f,.5f,.5f),new Vector3f(.5f,.5f,.5f)};
    private static final int[][] BUTTON_OFFSETS={{2,8},{6,8},{4,10},{4,6},{3,3},{5,3}};
    public static final class State{
        public final PartialModelRenderState body=new PartialModelRenderState(),button=new PartialModelRenderState();
        public final float[] depression=new float[6];public boolean active,firstPerson;public int hand=1,lightOverride=-1;public float equip;
    }
    public static State extract(boolean active,boolean depress,float partial){
        var state=new State();state.active=active;AllPartialModels.extract(active?POWERED:NORMAL,state.body);
        if(active){AllPartialModels.extract(BUTTON,state.button);if(depress)for(int i=0;i<6;i++)state.depression[i]=LinkedControllerClientHandler.button(i,partial);}
        return state;
    }
    @Override public State extractArgument(ItemStack stack){return extract(LinkedControllerClientHandler.MODE!=LinkedControllerClientHandler.Mode.IDLE,true,1);}
    @Override public void getExtents(Consumer<Vector3fc> output){for(var point:EXTENTS)output.accept(point);}
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,int light,int overlay,boolean foil,int outline){
        if(state==null)return;if(state.lightOverride>=0)light=state.lightOverride;
        pose.pushPose();if(state.firstPerson){pose.translate(0,state.equip/4,state.equip/4*state.hand);pose.rotateDegrees(Axis.YP,state.equip*-30*state.hand);pose.rotateDegrees(Axis.ZP,state.equip*-30);}
        // Source PartialItemModelRenderer centers every raw 0..1 mesh around the item origin.
        pose.translate(-.5,-.5,-.5);
        state.body.submit(pose,collector,light,overlay,outline);
        if(state.active)for(int i=0;i<6;i++){pose.pushPose();pose.translate(BUTTON_OFFSETS[i][0]/16d,-.75/16*state.depression[i],BUTTON_OFFSETS[i][1]/16d);state.button.submit(pose,collector,light,overlay,outline);pose.popPose();}
        pose.popPose();
    }
    public static void register(){
        ModelLoadingPlugin.register(context->{
            context.addModel(NORMAL,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("item/linked_controller/item")));
            context.addModel(POWERED,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("item/linked_controller/powered")));
            context.addModel(BUTTON,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("item/linked_controller/button")));
            context.modifyItemModelAfterBake().register((original,baked)->{
                if(!baked.itemId().equals(Create.asResource("linked_controller")))return original;
                var resolved=baked.bakingContext().blockModelBaker().getModel(Create.asResource("item/linked_controller"));
                var properties=ModelRenderProperties.fromResolvedModel(baked.bakingContext().blockModelBaker(),resolved,resolved.getTopTextureSlots());
                var transform=new Matrix4f(baked.transformation());var renderer=new LinkedControllerItemRenderer();
                return (renderState,stack,resolver,display,level,owner,seed)->{
                    var client=Minecraft.getInstance();var player=client.player;
                    boolean active=player!=null&&LinkedControllerClientHandler.MODE!=LinkedControllerClientHandler.Mode.IDLE;
                    boolean main=player!=null&&player.getMainHandItem()==stack;
                    boolean off=player!=null&&!(player.getMainHandItem().getItem()instanceof LinkedControllerItem)&&player.getOffhandItem()==stack;
                    boolean first=display==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||display==ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
                    boolean right=client.options.mainHand().get()==HumanoidArm.RIGHT;
                    boolean hand=main&&display==(right?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                        ||off&&display==(right?ItemDisplayContext.FIRST_PERSON_LEFT_HAND:ItemDisplayContext.FIRST_PERSON_RIGHT_HAND);
                    active&=display==ItemDisplayContext.GUI&&(main||off)||first&&hand;
                    float partial=client.getDeltaTracker().getGameTimeDeltaPartialTick(false);float equip=LinkedControllerClientHandler.equip(partial);
                    if(!active&&!(first&&hand&&equip>.001f)){original.update(renderState,stack,resolver,display,level,owner,seed);return;}
                    var state=extract(active,true,partial);
                    state.firstPerson=first;state.hand=display==ItemDisplayContext.FIRST_PERSON_LEFT_HAND?-1:1;state.equip=equip;
                    if(LinkedControllerClientHandler.MODE==LinkedControllerClientHandler.Mode.BIND){double time=client.level==null?0:client.level.getGameTime()+partial;int brightness=(int)(5+(Math.sin(time/4)+1)*5);state.lightOverride=LightCoordsUtil.pack(0,brightness);}
                    renderState.setAnimated();var layer=renderState.newLayer();properties.applyToLayer(layer,display);layer.setLocalTransform(transform);layer.setupSpecialModel(renderer,state);layer.setExtents(()->EXTENTS);
                };
            });
        });
    }
}
