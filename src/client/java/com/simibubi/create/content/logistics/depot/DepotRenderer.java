package com.simibubi.create.content.logistics.depot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Immutable native extraction of source slide, ring outputs, upright camera pose and stack layers. */
public final class DepotRenderer implements BlockEntityRenderer<DepotBlockEntity,DepotRenderer.State> {
    public record ItemPose(ItemStackRenderState item,Matrix4f pose){}
    private final ItemModelResolver items;
    public DepotRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    public static final class State extends BlockEntityRenderState {public List<ItemPose> items=List.of();}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(DepotBlockEntity depot,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(depot,state,partial,camera,overlay);
        state.items=extract(depot.depotBehaviour,items,new PoseStack(),partial,camera);
    }
    static List<ItemPose> extract(DepotBehaviour depot,ItemModelResolver models,PoseStack pose,float partial,Vec3 camera){
        List<ItemPose> result=new ArrayList<>();pose.pushPose();pose.translate(.5,15/16f,.5);
        var transported=new ArrayList<>(depot.getIncomingItems());var held=depot.getHeldItem();if(held!=null)transported.add(held.copy());
        var worldPosition=Vec3.atCenterOf(depot.getPos());
        for(var item:transported){
            pose.pushPose();nudge(pose,0);float offset=Mth.lerp(partial,item.prevBeltPosition,item.beltPosition),side=Mth.lerp(partial,item.prevSideOffset,item.sideOffset);
            if(item.insertedFrom.getAxis().isHorizontal()){
                pose.translate(Vec3.atLowerCornerOf(item.insertedFrom.getOpposite().getUnitVec3i()).scale(.5f-offset));
                boolean alongX=item.insertedFrom.getClockWise().getAxis()==Direction.Axis.X;if(!alongX)side*=-1;
                pose.translate(alongX?side:0,0,alongX?0:side);
            }
            extractItem(result,depot,models,pose,item.stack,item.angle,new Random(0),worldPosition,camera,false);pose.popPose();
        }
        for(int index=0;index<depot.inventory.outputs.size();index++){
            var stack=depot.inventory.outputs.get(index).stack();if(stack.isEmpty())continue;
            pose.pushPose();nudge(pose,index);boolean upright=BeltHelper.isItemUpright(stack);pose.rotateDegrees(Axis.YP,45*index);pose.translate(.35,0,0);
            if(upright)pose.rotateDegrees(Axis.YP,-45*index);
            var random=new Random(index+1);int angle=(int)(360*random.nextFloat());
            extractItem(result,depot,models,pose,stack,upright?angle+90:angle,random,worldPosition,camera,false);pose.popPose();
        }
        pose.popPose();return List.copyOf(result);
    }
    private static void extractItem(List<ItemPose> result,DepotBehaviour depot,ItemModelResolver models,PoseStack pose,ItemStack stack,int angle,Random random,Vec3 worldPosition,Vec3 camera,boolean alwaysUpright){
        if(stack.isEmpty())return;var model=new ItemStackRenderState();models.updateForTopItem(model,stack,ItemDisplayContext.FIXED,depot.getWorld(),null,0);
        boolean block=model.usesBlockLight(),upright=BeltHelper.isItemUpright(stack)||alwaysUpright&&!block;
        int layers=Mth.log2(stack.getCount())/2;
        pose.pushPose();pose.rotateDegrees(Axis.YP,angle);
        if(upright){var difference=worldPosition.subtract(camera);pose.rotate(Axis.YP,(float)(Mth.atan2(difference.x,difference.z)+Math.PI));pose.translate(0,3/32d,-1/16f);}
        for(int index=0;index<=layers;index++){
            pose.pushPose();if(block&&random!=null)pose.translate(random.nextFloat()*.0625f*index,0,random.nextFloat()*.0625f*index);
            if(PackageItem.isPackage(stack)&&!alwaysUpright){pose.translate(0,4/16f,0);pose.scale(1.5f,1.5f,1.5f);}
            else if(block&&alwaysUpright){pose.translate(0,1/16f,0);pose.scale(.755f,.755f,.755f);}
            else pose.scale(.5f,.5f,.5f);
            if(!block&&!upright){pose.translate(0,-3/16f,0);pose.rotateDegrees(Axis.XP,90);}
            result.add(new ItemPose(model,new Matrix4f(pose.last().pose())));pose.popPose();
            if(!upright){if(!block)pose.rotateDegrees(Axis.YP,10);pose.translate(0,block?1/64d:1/16d,0);}
            else pose.translate(0,0,-1/16f);
        }
        pose.popPose();
    }
    /** Genuine transform-library nudge formula; only scalar source math is retained. */
    static void nudge(PoseStack pose,int seed){
        long bits=seed*31L*493286711L;bits=bits*bits*4392167121L+bits*98761L;
        float x=(((bits>>16&7L)+.5f)/8-.5f)*.004f;
        float y=(((bits>>20&7L)+.5f)/8-.5f)*.004f;
        float z=(((bits>>24&7L)+.5f)/8-.5f)*.004f;pose.translate(x,y,z);
    }
    static void submitItems(List<ItemPose> items,PoseStack pose,SubmitNodeCollector collector,int light){
        for(var item:items){pose.pushPose();pose.mulPose(item.pose);item.item.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){submitItems(state.items,pose,collector,state.lightCoords);}
}
