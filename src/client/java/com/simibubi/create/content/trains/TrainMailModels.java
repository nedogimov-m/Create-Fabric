package com.simibubi.create.content.trains;
import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
public final class TrainMailModels {
  public static final ExtraModelKey<BlockStateModel> POSTBOX_FLAG=ExtraModelKey.create(()->"create:block/package_postbox/flag");
  public static final ExtraModelKey<BlockStateModel> OBSERVER_OVERLAY=ExtraModelKey.create(()->"create:block/track_overlay/observer");
  public static void register(){ModelLoadingPlugin.register(context->{context.addModel(POSTBOX_FLAG,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/package_postbox/flag")));context.addModel(OBSERVER_OVERLAY,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/track_overlay/observer")));});}
  private TrainMailModels(){}
}
