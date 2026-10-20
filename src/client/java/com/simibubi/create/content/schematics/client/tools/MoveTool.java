package com.simibubi.create.content.schematics.client.tools;

import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.Direction;

public class MoveTool extends PlacementToolBase {
    @Override public void init(){super.init();renderSelectedFace=true;}
    @Override public boolean handleMouseWheel(double delta){
        if(!schematicSelected||selectedFace==null||!selectedFace.getAxis().isHorizontal())return true;
        var transform=schematicHandler.getTransformation();var direction=selectedFace.getUnitVec3().scale(-Math.signum(delta)).multiply(transform.getMirrorModifier(Direction.Axis.X),1,transform.getMirrorModifier(Direction.Axis.Z));
        direction=StructureTransform.rotate(direction,transform.getRotationTarget(),Direction.Axis.Y);transform.move((int)Math.round(direction.x),0,(int)Math.round(direction.z));schematicHandler.markDirty();return true;
    }
}
