package com.simibubi.create.test.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.equipment.EquipmentHands;
import com.simibubi.create.test.EquipmentNativeFrameProbe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=EquipmentHands.class,remap=false)
abstract class EquipmentCustomHandsProbeMixin {
    @Inject(method="submit",at=@At("HEAD")) private static void createTest$enter(PlayerRenderState player,FirstPersonHandsAndItemsRenderState hands,InteractionHand which,PoseStack pose,SubmitNodeCollector collector,int light,CallbackInfoReturnable<Boolean> cir){EquipmentNativeFrameProbe.custom(player,hands,which,pose,null);}
    @Inject(method="submit",at=@At("RETURN")) private static void createTest$exit(PlayerRenderState player,FirstPersonHandsAndItemsRenderState hands,InteractionHand which,PoseStack pose,SubmitNodeCollector collector,int light,CallbackInfoReturnable<Boolean> cir){EquipmentNativeFrameProbe.custom(player,hands,which,pose,cir.getReturnValue());}
}
