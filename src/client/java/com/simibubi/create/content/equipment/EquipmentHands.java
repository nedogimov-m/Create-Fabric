package com.simibubi.create.content.equipment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.extendoGrip.*;
import com.simibubi.create.content.equipment.potatoCannon.PotatoCannonItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.*;
import org.joml.Matrix4f;

/** Source hand math is fully extracted; submit consumes only snapshots and captured native renderer. */
public final class EquipmentHands {
    public static final class Hand {
        public boolean custom,suppressed,extendo,extraItem,right;
        public final Matrix4f arm=new Matrix4f(),tool=new Matrix4f();
        public ItemStackRenderState item,extra;
        void clear(){custom=suppressed=extendo=extraItem=right=false;item=extra=null;arm.identity();tool.identity();extraTransform.identity();}
        final Matrix4f extraTransform=new Matrix4f();
    }
    public static final class State {
        public final Hand main=new Hand(),off=new Hand();
        public final ItemStackRenderState offGripInMain=new ItemStackRenderState();
        AvatarRenderer<?> renderer;
    }
    public static void extract(LocalPlayer player,float partial,FirstPersonHandsAndItemsRenderState nativeState){
        var state=((EquipmentHandsAccess)nativeState).create$equipmentHands();state.main.clear();state.off.clear();state.offGripInMain.clear();state.renderer=null;
        if(nativeState.isScoping)return;
        boolean offGrip=nativeState.offHandItem.is(com.simibubi.create.AllItems.EXTENDO_GRIP),mainGrip=nativeState.mainHandItem.is(com.simibubi.create.AllItems.EXTENDO_GRIP);
        boolean rightMain=player.getMainArm()==HumanoidArm.RIGHT;
        if(offGrip||mainGrip){
            var hand=state.main;hand.custom=hand.extendo=true;hand.right=rightMain;hand.item=nativeState.mainHandRenderState;
            float equip=nativeState.mainHandItem.getItem() instanceof BlockItem?0:(1-Mth.lerp(partial,nativeState.oldMainHandHeight,nativeState.mainHandHeight))/4;
            var pose=new PoseStack();float flip=rightMain?1:-1;pose.translate(flip*(.64000005f-.1f),-.4f+equip*-.6f,-.71999997f+.3f);
            pose.pushPose();armBase(pose,flip);pose.translate(flip*.05f,-.3f,-.3f);hand.arm.set(pose.last().pose());pose.popPose();
            pose.translate(flip*-.1f,0,-.3f);hand.tool.set(pose.last().pose());
            if(offGrip){var display=rightMain?ItemDisplayContext.FIRST_PERSON_RIGHT_HAND:ItemDisplayContext.FIRST_PERSON_LEFT_HAND;Minecraft.getInstance().getItemModelResolver().updateForLiving(state.offGripInMain,nativeState.offHandItem,display,player);hand.item=state.offGripInMain;hand.extraItem=true;hand.extra=nativeState.mainHandRenderState;
                pose.mulPose(ExtendoGripItemRenderer.INSTANCE.cameraTransform(display));pose.translate(flip*-.05f,.15f,-1.2f);float animation=Mth.lerp(partial,ExtendoGripRenderHandler.lastMainHandAnimation,ExtendoGripRenderHandler.mainHandAnimation);pose.translate(0,0,-animation*animation*animation*2.25f);
                if(nativeState.mainHandItem.getItem() instanceof BlockItem&&nativeState.mainHandRenderState.usesBlockLight()){pose.rotateDegrees(Axis.YP,flip*45);pose.translate(flip*.15f,-.15f,-.05f);pose.scale(1.25f,1.25f,1.25f);}hand.extraTransform.set(pose.last().pose());
                state.off.custom=state.off.suppressed=true;
            }
        }
        if(!state.main.custom&&(nativeState.mainHandItem.getItem() instanceof PotatoCannonItem||nativeState.mainHandItem.getItem() instanceof com.simibubi.create.content.equipment.zapper.ZapperItem))cannon(state.main,player,partial,rightMain,InteractionHand.MAIN_HAND,nativeState);
        if(!state.off.custom&&(nativeState.offHandItem.getItem() instanceof PotatoCannonItem||nativeState.offHandItem.getItem() instanceof com.simibubi.create.content.equipment.zapper.ZapperItem))cannon(state.off,player,partial,!rightMain,InteractionHand.OFF_HAND,nativeState);
        if(state.main.custom||state.off.custom)state.renderer=(AvatarRenderer<?>)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
    }
    private static void cannon(Hand hand,LocalPlayer player,float partial,boolean right,InteractionHand which,FirstPersonHandsAndItemsRenderState state){
        hand.custom=true;hand.right=right;hand.item=which==InteractionHand.MAIN_HAND?state.mainHandRenderState:state.offHandRenderState;
        var actual=which==InteractionHand.MAIN_HAND?state.mainHandItem:state.offHandItem;com.simibubi.create.content.equipment.zapper.ShootableGadgetRenderHandler handler=actual.getItem() instanceof com.simibubi.create.content.equipment.zapper.ZapperItem?EquipmentClient.ZAPPER:EquipmentClient.POTATO_CANNON;float recoil=handler.getAnimation(right,partial),equip=handler.suppressEquip(right)?0:1-(which==InteractionHand.MAIN_HAND?Mth.lerp(partial,state.oldMainHandHeight,state.mainHandHeight):Mth.lerp(partial,state.oldOffHandHeight,state.offHandHeight));
        float swing=state.attackHand==which?player.getSwingAnimation(partial):0,flip=right?1:-1,sqrt=Mth.sqrt(swing),f2=-.3f*Mth.sin(sqrt*(float)Math.PI),f3=.4f*Mth.sin(sqrt*(float)Math.PI*2),f4=-.4f*Mth.sin(swing*(float)Math.PI),f5=Mth.sin(swing*swing*(float)Math.PI),f6=Mth.sin(sqrt*(float)Math.PI);
        var pose=new PoseStack();pose.translate(flip*(f2+.64f-.1f),f3+-.4f+equip*-.6f,f4+-.72f+.3f+recoil);pose.rotateDegrees(Axis.YP,flip*75);pose.rotateDegrees(Axis.YP,flip*f6*70);pose.rotateDegrees(Axis.ZP,flip*f5*-20);armAfterYaw(pose,flip);handler.handPose(pose,flip,equip,recoil,partial);hand.arm.set(pose.last().pose());
        pose=new PoseStack();pose.translate(flip*(f2+.64f-.1f),f3+-.4f+equip*-.6f,f4+-.72f-.1f+recoil);pose.rotateDegrees(Axis.YP,flip*f6*70);pose.rotateDegrees(Axis.ZP,flip*f5*-20);handler.toolPose(pose,flip,equip,recoil,partial);hand.tool.set(pose.last().pose());
    }
    private static void armBase(PoseStack pose,float flip){pose.rotateDegrees(Axis.YP,flip*75);armAfterYaw(pose,flip);}
    private static void armAfterYaw(PoseStack pose,float flip){pose.translate(flip*-1,3.6f,3.5f);pose.rotateDegrees(Axis.ZP,flip*120);pose.rotateDegrees(Axis.XP,200);pose.rotateDegrees(Axis.YP,flip*-135);pose.translate(flip*5.6f,0,0);pose.rotateDegrees(Axis.YP,flip*40);}
    public static boolean submit(PlayerRenderState player,FirstPersonHandsAndItemsRenderState nativeState,InteractionHand which,PoseStack pose,SubmitNodeCollector collector,int light){
        var state=((EquipmentHandsAccess)nativeState).create$equipmentHands();var hand=which==InteractionHand.MAIN_HAND?state.main:state.off;if(!hand.custom)return false;if(hand.suppressed||nativeState.isScoping)return true;
        var avatar=player.avatarRenderState;if(avatar==null)return true;
        if(!avatar.isInvisible&&state.renderer!=null){pose.pushPose();pose.mulPose(hand.arm);var texture=avatar.skin.body().texturePath();if(!com.simibubi.create.content.equipment.armor.NetheriteBacktankFirstPersonRenderer.submitHand(state.renderer,pose,collector,hand.right,player)){if(hand.right)state.renderer.renderRightHand(pose,collector,light,texture,avatar.showRightSleeve);else state.renderer.renderLeftHand(pose,collector,light,texture,avatar.showLeftSleeve);}pose.popPose();}
        pose.pushPose();pose.mulPose(hand.tool);hand.item.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        if(hand.extraItem){pose.pushPose();pose.mulPose(hand.extraTransform);hand.extra.submit(pose,collector,light,OverlayTexture.NO_OVERLAY,0);pose.popPose();}return true;
    }
    private EquipmentHands(){}
}
