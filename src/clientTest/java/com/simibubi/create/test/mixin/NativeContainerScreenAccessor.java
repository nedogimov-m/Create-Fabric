package com.simibubi.create.test.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read actual native slot origins for physical test input, including recipe-book offsets. */
@Mixin(AbstractContainerScreen.class)
public interface NativeContainerScreenAccessor {
    @Accessor("leftPos") int create$getLeft();
    @Accessor("topPos") int create$getTop();
}
