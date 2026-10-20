package net.createmod.ponder.mixin.client;

import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to textures actually allocated, cleared and disposed by the native PIP owner. */
@Mixin(PictureInPictureRenderer.class)
public interface PonderNativePipTargetAccessor {
    @Accessor("texture") GpuTexture ponder$colorTexture();
    @Accessor("textureView") GpuTextureView ponder$colorView();
    @Accessor("depthTexture") GpuTexture ponder$depthTexture();
    @Accessor("depthTextureView") GpuTextureView ponder$depthView();
}
