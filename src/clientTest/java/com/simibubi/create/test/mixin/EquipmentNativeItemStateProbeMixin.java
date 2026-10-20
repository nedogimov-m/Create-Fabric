package com.simibubi.create.test.mixin;
import com.simibubi.create.test.EquipmentNativeFrameProbe;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemStackRenderState.class)
abstract class EquipmentNativeItemStateProbeMixin {
    @Inject(method="submit",at=@At("HEAD")) private void createTest$item(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.renderer.SubmitNodeCollector collector,int light,int overlay,int outline,CallbackInfo ci){EquipmentNativeFrameProbe.itemState((ItemStackRenderState)(Object)this,pose);}
    @Inject(method="submit",at=@At("RETURN")) private void createTest$finishItem(CallbackInfo ci){EquipmentNativeFrameProbe.finishItemState();}
}
