package com.simibubi.create.content.schematics.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.schematics.client.SchematicOutline;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class FlipTool extends PlacementToolBase {
    @Override public void init(){super.init();renderSelectedFace=false;}
    private void mirror(){if(schematicSelected&&selectedFace!=null&&selectedFace.getAxis().isHorizontal()){schematicHandler.getTransformation().flip(selectedFace.getAxis());schematicHandler.markDirty();}}
    @Override public boolean handleRightClick(){mirror();return true;}
    @Override public boolean handleMouseWheel(double delta){mirror();return true;}
    @Override public void extractOnSchematic(PoseStack pose,SchematicToolRenderState.Builder output,Vec3 camera,float partial){
        if(schematicHandler.isDeployed()&&schematicSelected&&selectedFace!=null&&selectedFace.getAxis().isHorizontal()){
            var direction=Direction.get(Direction.AxisDirection.POSITIVE,selectedFace.getClockWise().getAxis()).getUnitVec3();var bounds=schematicHandler.getBounds();var size=new Vec3(bounds.getXsize(),bounds.getYsize(),bounds.getZsize());var contracted=size.multiply(direction);
            var plane=bounds.contract(contracted.x,contracted.y,contracted.z).inflate(1-direction.x,1-direction.y,1-direction.z).move(direction.scale(.5).multiply(size));output.addOutline(SchematicToolRenderState.Role.MIRROR_PLANE,pose,SchematicOutline.extract(plane,null,0xDDDDDD));
        }
        super.extractOnSchematic(pose,output,camera,partial);
    }
}
