package com.simibubi.create.content.schematics.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.schematics.client.SchematicOutline;
import net.minecraft.world.phys.Vec3;

public class DeployTool extends PlacementToolBase {
    @Override public void init(){super.init();selectionRange=-1;}
    @Override public void updateSelection(){
        if(schematicHandler.isActive()&&selectionRange==-1)selectionRange=Math.clamp((int)(schematicHandler.getBounds().getCenter().length()/2),1,100);
        selectIgnoreBlocks=com.simibubi.create.AllKeys.ACTIVATE_TOOL.isPressed();super.updateSelection();
    }
    @Override public boolean handleMouseWheel(double delta){
        selectIgnoreBlocks=com.simibubi.create.AllKeys.ACTIVATE_TOOL.isPressed();if(!selectIgnoreBlocks)return super.handleMouseWheel(delta);selectionRange=Math.clamp((int)(selectionRange+delta),1,100);return true;
    }
    @Override public boolean handleRightClick(){
        if(selectedPos==null)return super.handleRightClick();var center=schematicHandler.getBounds().getCenter();var target=selectedPos.offset(-(int)center.x,0,-(int)center.z);
        var item=schematicHandler.getActiveSchematicItem();if(item!=null&&!item.isEmpty()){item.set(AllDataComponents.SCHEMATIC_DEPLOYED,true);item.set(AllDataComponents.SCHEMATIC_ANCHOR,target);schematicHandler.getTransformation().startAt(target);}
        schematicHandler.getTransformation().moveTo(target);schematicHandler.markDirty();schematicHandler.deploy();return true;
    }
    @Override public void extractTool(PoseStack pose,SchematicToolRenderState.Builder output,Vec3 camera,float partial){
        if(selectedPos==null)return;pose.pushPose();var target=lastChasingSelectedPos.lerp(chasingSelectedPos,partial);var transform=schematicHandler.getTransformation();var bounds=schematicHandler.getBounds();var center=bounds.getCenter();
        pose.translate(target.x-(int)center.x-camera.x,target.y-camera.y,target.z-(int)center.z-camera.z);var origin=new Vec3(bounds.getXsize()/2,0,bounds.getZsize()/2);var pivot=origin.add(transform.getRotationOffset(true));
        pose.translate(pivot);pose.rotateDegrees(com.mojang.math.Axis.YP,transform.getCurrentRotation());pose.translate(pivot.scale(-1));output.addOutline(SchematicToolRenderState.Role.DEPLOYMENT,pose,SchematicOutline.extract(bounds,null,0x6886C5));pose.popPose();
    }
}
