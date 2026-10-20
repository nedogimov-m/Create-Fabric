package com.simibubi.create.content.equipment.extendoGrip;

import com.simibubi.create.AllPartialModels;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;

public final class ExtendoGripRenderHandler {
    public static float mainHandAnimation,lastMainHandAnimation;
    public static ExtraModelKey<BlockStateModel> pose=AllPartialModels.DEPLOYER_HAND_PUNCHING;
    public static void tick(){lastMainHandAnimation=mainHandAnimation;mainHandAnimation*=Mth.clamp(mainHandAnimation,.8f,.99f);}
    public static void prepare(LocalPlayer player,ItemStack main,ItemStack off,float partial){
        pose=AllPartialModels.DEPLOYER_HAND_PUNCHING;
        if(off.is(com.simibubi.create.AllItems.EXTENDO_GRIP)&&main.getItem() instanceof BlockItem){var state=new ItemStackRenderState();Minecraft.getInstance().getItemModelResolver().updateForLiving(state,main,ItemDisplayContext.GUI,player);if(state.usesBlockLight())pose=AllPartialModels.DEPLOYER_HAND_HOLDING;}
        if(off.is(com.simibubi.create.AllItems.EXTENDO_GRIP)||main.is(com.simibubi.create.AllItems.EXTENDO_GRIP)){float swing=player.getSwingAnimation(partial);if(swing>0&&1-swing>mainHandAnimation)mainHandAnimation=.95f;}
    }
    public static void pick(Minecraft client,float partial){
        var player=client.player;if(player==null||client.level==null||!ExtendoGripItem.isHoldingExtendoGrip(player)||client.hitResult instanceof BlockHitResult block&&block.getType()!=HitResult.Type.MISS)return;
        double range=player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)-(player.isCreative()?0:.5);if(range<=0)return;var eye=player.getEyePosition(partial);var look=player.getViewVector(1);var end=eye.add(look.scale(range));
        var hit=ProjectileUtil.getEntityHitResult(player,eye,end,player.getBoundingBox().expandTowards(look.scale(range)).inflate(1),entity->!entity.isSpectator()&&entity.isPickable(),range*range);
        if(hit!=null){client.hitResult=hit;if(hit.getEntity() instanceof LivingEntity||hit.getEntity() instanceof ItemFrame)client.crosshairPickEntity=hit.getEntity();}
    }
    private ExtendoGripRenderHandler(){}
}
