package com.simibubi.create.content.trains.signal;

import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/** Actual source signal indicator/track overlay models, rebaked by the native resource loader. */
public final class SignalModels {
    private static final Map<String, ExtraModelKey<BlockStateModel>> MODELS = new LinkedHashMap<>();
    public static final ExtraModelKey<BlockStateModel> ON = partial("track_signal/indicator_on");
    public static final ExtraModelKey<BlockStateModel> OFF = partial("track_signal/indicator_off");
    public static final ExtraModelKey<BlockStateModel> OVERLAY = partial("track_overlay/signal");
    public static final ExtraModelKey<BlockStateModel> DUAL = partial("track_overlay/signal_dual");
    private static ExtraModelKey<BlockStateModel> partial(String path) {
        var key = ExtraModelKey.<BlockStateModel>create(() -> "create:block/" + path); MODELS.put(path, key); return key;
    }
    public static void register() { ModelLoadingPlugin.register(context -> MODELS.forEach((path, key) ->
        context.addModel(key, SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/" + path))))); }
    private SignalModels() {}
}
