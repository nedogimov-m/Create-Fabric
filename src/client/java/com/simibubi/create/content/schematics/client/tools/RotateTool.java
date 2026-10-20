package com.simibubi.create.content.schematics.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.Vec3;

public class RotateTool extends PlacementToolBase {
    @Override public boolean handleMouseWheel(double delta){schematicHandler.getTransformation().rotate90(delta>0);schematicHandler.markDirty();return true;}
    @Override public void extractOnSchematic(PoseStack pose,SchematicToolRenderState.Builder output,Vec3 camera,float partial){
        if(schematicHandler.isDeployed()){var bounds=schematicHandler.getBounds();double height=bounds.getYsize()+Math.max(20,bounds.getYsize());var center=bounds.getCenter().add(schematicHandler.getTransformation().getRotationOffset(false));output.addLine(SchematicToolRenderState.Role.ROTATION_AXIS,pose,camera,center.subtract(0,height/2,0),center.add(0,height/2,0),0xFFDDDDDD,1.5f);}
        super.extractOnSchematic(pose,output,camera,partial);
    }
}
