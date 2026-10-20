package com.simibubi.create.content.trains.station;

import com.simibubi.create.Create;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

/** Source station geometry baked through native Fabric after every resource reload. */
public final class StationModels {
  private static final Map<String, ExtraModelKey<BlockStateModel>> MODELS = new LinkedHashMap<>();
  public static final ExtraModelKey<BlockStateModel> FLAG_OFF = model("track_station/flag_off");
  public static final ExtraModelKey<BlockStateModel> FLAG_ASSEMBLE =
      model("track_station/flag_assemble");
  public static final ExtraModelKey<BlockStateModel> TARGET = model("track_overlay/station");
  public static final ExtraModelKey<BlockStateModel> ASSEMBLY = model("track_overlay/assembling");

  private static ExtraModelKey<BlockStateModel> model(String path) {
    var key = ExtraModelKey.<BlockStateModel>create(() -> "create:block/" + path);
    MODELS.put(path, key);
    return key;
  }

  public static void register() {
    ModelLoadingPlugin.register(
        context ->
            MODELS.forEach(
                (path, key) ->
                    context.addModel(
                        key,
                        SimpleUnbakedExtraModel.blockStateModel(
                            Create.asResource("block/" + path)))));
  }

  private StationModels() {}
}
