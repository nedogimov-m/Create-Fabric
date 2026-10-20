package com.simibubi.create.content.trains.schedule.hat;

import java.util.*;
import net.minecraft.client.model.geom.ModelPart;

/** Client-only native model traversal, separate from the common data codec. */
public final class TrainHatModelParts {
    public static List<ModelPart> getAdjustedPart(TrainHatInfo info,ModelPart root,String defaultPart){
        var result=new ArrayList<ModelPart>();result.add(root);var parent=root;String path=info.part().isEmpty()?defaultPart:info.part();
        for(var name:path.split("/"))if(!name.isEmpty()&&parent.hasChild(name)){parent=parent.getChild(name);result.add(parent);}return result;
    }
    private TrainHatModelParts() {}
}
