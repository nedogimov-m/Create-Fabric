package com.simibubi.create.content.equipment.symmetryWand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.*;
import com.simibubi.create.content.equipment.symmetryWand.mirror.SymmetryMirror;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/** Client adapter replaces common-side Flywheel/PoseStack links with native detached geometry. */
public final class SymmetryMirrorClient {
    private static final Map<String,ExtraModelKey<BlockStateModel>> MODELS=new HashMap<>();
    static{for(var type:List.of(SymmetryMirror.PLANE,SymmetryMirror.CROSS_PLANE,SymmetryMirror.TRIPLE_PLANE))MODELS.put(type,ExtraModelKey.create(()->"create:symmetry/"+type));}
    public static void register(){ModelLoadingPlugin.register(context->{MODELS.forEach((type,key)->context.addModel(key,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/symmetry_effect/"+switch(type){case SymmetryMirror.PLANE->"plane";case SymmetryMirror.CROSS_PLANE->"crossplane";default->"tripleplane";}))));});}
    public static PartialModelRenderState extract(SymmetryMirror mirror){var state=new PartialModelRenderState();var key=MODELS.get(mirror.typeName());if(key!=null)AllPartialModels.extract(key,state);return state;}
    public static void applyModelTransform(SymmetryMirror mirror,PoseStack pose){pose.translate(.5,.5,.5);pose.rotateDegrees(Axis.YP,mirror.modelRotation());pose.translate(-.5,-.5,-.5);}
    private SymmetryMirrorClient(){}
}
