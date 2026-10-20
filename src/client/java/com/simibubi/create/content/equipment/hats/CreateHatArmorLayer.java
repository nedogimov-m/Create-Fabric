package com.simibubi.create.content.equipment.hats;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.trains.schedule.hat.TrainHatInfo;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

public final class CreateHatArmorLayer<S extends LivingEntityRenderState,M extends EntityModel<? super S>> extends RenderLayer<S,M>{
    private static final Map<ModelPart,Map<String,String>> COMPATIBLE_PATHS=new WeakHashMap<>();
    public CreateHatArmorLayer(RenderLayerParent<S,M> parent){super(parent);}
    public static List<ModelPart> headParts(ModelPart root,TrainHatInfo info){
        String head=root.hasChild("head")?"head":root.hasChild("head_parts")?"head_parts":"";
        String first=info.part().split("/",2)[0];
        if(!first.isEmpty()&&!root.hasChild(first)&&!head.isEmpty()){
            var nested=com.simibubi.create.content.trains.schedule.hat.TrainHatModelParts.getAdjustedPart(info,root.getChild(head),"");if(nested.size()>1){var result=new ArrayList<ModelPart>();result.add(root);result.addAll(nested);return result;}
        }
        var preferred=com.simibubi.create.content.trains.schedule.hat.TrainHatModelParts.getAdjustedPart(info,root,head);if(cube(preferred.getLast(),info.cubeIndex())!=null)return preferred;
        String wanted=info.part().isEmpty()?"head":info.part();
        var paths=COMPATIBLE_PATHS.computeIfAbsent(root,key->new HashMap<>());
        String path=paths.computeIfAbsent(wanted,key->{var matches=new LinkedHashSet<String>();root.visit(new PoseStack(),(pose,name,index,cube)->{if(name.endsWith("/"+key))matches.add(name);});return matches.size()==1?matches.iterator().next():"";});
        return path.isEmpty()?preferred:com.simibubi.create.content.trains.schedule.hat.TrainHatModelParts.getAdjustedPart(new TrainHatInfo(path,info.cubeIndex(),info.offset(),info.scale()),root,"");
    }
    public static ModelPart.Cube cube(ModelPart head,int index){var cubes=new ArrayList<ModelPart.Cube>();head.visit(new PoseStack(),(pose,path,cubeIndex,cube)->{if(path.isEmpty())cubes.add(cube);});return cubes.isEmpty()?null:cubes.get(Math.clamp(index,0,cubes.size()-1));}
    @Override public void submit(PoseStack pose,SubmitNodeCollector collector,int light,S state,float yRot,float xRot){
        var hat=((FabricRenderState)state).getData(EntityHats.HAT);if(hat==null)return;var parts=headParts(getParentModel().root(),hat.info());if(parts.isEmpty())return;
        pose.pushPose();for(var part:parts)part.translateAndRotate(pose);var cube=cube(parts.getLast(),hat.info().cubeIndex());
        if(cube!=null){var offset=hat.info().offset();pose.translate(offset.x/16,(cube.minY-cube.maxY+offset.y)/16,offset.z/16);float scale=Math.max(cube.maxX-cube.minX,cube.maxZ-cube.minZ)/8*hat.info().scale();pose.scale(scale,scale,scale);}
        pose.scale(1,-1,-1);pose.translate(0,-2.25/16.,0);pose.rotateDegrees(Axis.XP,-8.5f);hat.model().submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }
}
