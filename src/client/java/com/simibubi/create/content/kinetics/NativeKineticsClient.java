package com.simibubi.create.content.kinetics;

import com.simibubi.create.AllBlockEntityTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public final class NativeKineticsClient {
    public static void register() {
        com.simibubi.create.foundation.item.ItemTooltipModifiers.register();
        com.simibubi.create.content.kinetics.crank.ValveHandleSettingsClient.register();
        NativeKineticsCTModel.register();
        BlockEntityRenderers.register(AllBlockEntityTypes.ENCASED_SHAFT,NativeKineticsRenderer::new);
        BlockEntityRenderers.register(AllBlockEntityTypes.ENCASED_COGWHEEL,NativeKineticsRenderer::new);
        BlockEntityRenderers.register(AllBlockEntityTypes.ENCASED_LARGE_COGWHEEL,NativeKineticsRenderer::new);
        BlockEntityRenderers.register(AllBlockEntityTypes.VALVE_HANDLE,NativeKineticsRenderer::new);
        BlockEntityRenderers.register(AllBlockEntityTypes.TURNTABLE,NativeKineticsRenderer::new);
        BlockEntityRenderers.register(AllBlockEntityTypes.FLYWHEEL,NativeKineticsRenderer::new);
    }
    private NativeKineticsClient() {}
}
