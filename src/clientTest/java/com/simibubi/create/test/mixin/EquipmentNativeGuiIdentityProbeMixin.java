package com.simibubi.create.test.mixin;
import com.simibubi.create.test.EquipmentNativeGuiProbe;
import net.minecraft.client.renderer.item.*;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ItemModelResolver.class)
abstract class EquipmentNativeGuiIdentityProbeMixin {
    @Inject(method="appendItemLayers",at=@At("RETURN")) private void createTest$actualNativeIdentity(ItemStackRenderState state,ItemStack stack,ItemDisplayContext display,Level level,ItemOwner owner,int seed,CallbackInfo ci){EquipmentNativeGuiProbe.identity(state,stack,display);com.simibubi.create.test.RegisteredItemModelClientTest.observe(state,stack,display);}
}
