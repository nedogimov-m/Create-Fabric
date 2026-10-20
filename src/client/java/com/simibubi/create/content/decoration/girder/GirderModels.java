package com.simibubi.create.content.decoration.girder;

import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.Direction;

/** Real source bracket/curve beam models, owned by this slice and baked on every native resource reload. */
public final class GirderModels {
    private static final Map<String,ExtraModelKey<BlockStateModel>> MODELS=new LinkedHashMap<>();
    public static final Map<Direction,ExtraModelKey<BlockStateModel>> BRACKETS=new EnumMap<>(Direction.class);
    public static final ExtraModelKey<BlockStateModel> SEGMENT_TOP=partial("segment_top"),SEGMENT_MIDDLE=partial("segment_middle"),SEGMENT_BOTTOM=partial("segment_bottom");
    static {for(var direction:Direction.Plane.HORIZONTAL)BRACKETS.put(direction,partial("bracket_"+direction.getName()));}
    private static ExtraModelKey<BlockStateModel> partial(String path){var key=ExtraModelKey.<BlockStateModel>create(()->"create:block/metal_girder/"+path);MODELS.put(path,key);return key;}
    public static void register(){ModelLoadingPlugin.register(context->MODELS.forEach((path,key)->context.addModel(key,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/metal_girder/"+path)))));ConnectedGirderModel.register();}
    private GirderModels(){}
}
