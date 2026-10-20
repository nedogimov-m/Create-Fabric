package com.simibubi.create.content.redstone.nixieTube;

import com.simibubi.create.Create;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/** Exact original signal-panel/computer partials, rebaked through native Fabric model reloads. */
public final class NixieSignalModels {
    private static final Map<String,ExtraModelKey<BlockStateModel>> MODELS=new LinkedHashMap<>();
    public static final ExtraModelKey<BlockStateModel> PANEL=partial("panel"),
        COMPUTER_WHITE_CUBE=partial("computer_white_cube"),COMPUTER_WHITE_GLOW=partial("computer_white_glow"),
        COMPUTER_WHITE=partial("computer_white_tube"),COMPUTER_WHITE_BASE=partial("computer_white_tube_base");
    private static ExtraModelKey<BlockStateModel> partial(String name){var key=ExtraModelKey.<BlockStateModel>create(()->"create:block/track_signal/"+name);MODELS.put(name,key);return key;}
    public static void register(){
        NixieSignalRenderTypes.register();
        ModelLoadingPlugin.register(context->MODELS.forEach((name,key)->context.addModel(key,
            SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/track_signal/"+name)))));
    }
    private NixieSignalModels(){}
}
