package com.simibubi.create.content.contraptions.gantry;

import com.simibubi.create.Create;
import net.fabricmc.fabric.api.client.model.loading.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;

public final class GantryClient {
    public static final ExtraModelKey<BlockStateModel> COGS=ExtraModelKey.create(()->"create:block/gantry_carriage/wheels");
    public static void register(){
        ModelLoadingPlugin.register(context->context.addModel(COGS,SimpleUnbakedExtraModel.blockStateModel(Create.asResource("block/gantry_carriage/wheels"))));
        ClientPlayNetworking.registerGlobalReceiver(GantryContraptionUpdatePacket.TYPE,(packet,context)->{var level=context.client().level;if(level!=null&&level.getEntity(packet.entityId()) instanceof GantryContraptionEntity owner)owner.applyMotion(packet);});
    }
    private GantryClient(){}
}
