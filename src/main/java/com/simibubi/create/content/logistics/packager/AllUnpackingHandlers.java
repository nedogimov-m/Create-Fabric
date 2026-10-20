package com.simibubi.create.content.logistics.packager;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.impl.unpacking.*;

public final class AllUnpackingHandlers {
    public static void registerDefaults(){UnpackingHandler.REGISTRY.register(AllBlocks.BASIN,BasinUnpackingHandler.INSTANCE);UnpackingHandler.REGISTRY.register(AllBlocks.MECHANICAL_CRAFTER,CrafterUnpackingHandler.INSTANCE);UnpackingHandler.REGISTRY.register(AllBlocks.CREATIVE_CRATE,com.simibubi.create.api.packager.unpacking.VoidingUnpackingHandler.INSTANCE);}
    private AllUnpackingHandlers(){}
}
