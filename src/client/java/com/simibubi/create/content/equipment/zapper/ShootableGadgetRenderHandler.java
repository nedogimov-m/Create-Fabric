package com.simibubi.create.content.equipment.zapper;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Original recoil state and public extension API; native extraction captures every submit input. */
public abstract class ShootableGadgetRenderHandler {
    protected float leftHandAnimation,rightHandAnimation,lastLeftHandAnimation,lastRightHandAnimation;
    protected boolean dontReequipLeft,dontReequipRight;
    public void tick(){lastLeftHandAnimation=leftHandAnimation;lastRightHandAnimation=rightHandAnimation;leftHandAnimation*=animationDecay();rightHandAnimation*=animationDecay();}
    public float getAnimation(boolean right,float partial){return Mth.lerp(partial,right?lastRightHandAnimation:lastLeftHandAnimation,right?rightHandAnimation:leftHandAnimation);}
    protected float animationDecay(){return .8f;}
    public void shoot(InteractionHand hand,Vec3 location){var player=Minecraft.getInstance().player;if(player==null)return;boolean right=hand==InteractionHand.MAIN_HAND^(player.getMainArm()==HumanoidArm.LEFT);if(right){rightHandAnimation=.2f;dontReequipRight=false;}else{leftHandAnimation=.2f;dontReequipLeft=false;}playSound(hand,location);}
    public void dontAnimateItem(InteractionHand hand){var player=Minecraft.getInstance().player;if(player==null)return;boolean right=hand==InteractionHand.MAIN_HAND^(player.getMainArm()==HumanoidArm.LEFT);dontReequipRight|=right;dontReequipLeft|=!right;}
    public boolean suppressEquip(boolean right){return right?rightHandAnimation>.01f||dontReequipRight:leftHandAnimation>.01f||dontReequipLeft;}
    public void reset(){leftHandAnimation=rightHandAnimation=lastLeftHandAnimation=lastRightHandAnimation=0;dontReequipLeft=dontReequipRight=false;}
    protected abstract void playSound(InteractionHand hand,Vec3 position);
    protected abstract boolean appliesTo(ItemStack stack);
    protected abstract void transformTool(PoseStack pose,float flip,float equip,float recoil,float partial);
    protected abstract void transformHand(PoseStack pose,float flip,float equip,float recoil,float partial);
    public final void toolPose(PoseStack pose,float flip,float equip,float recoil,float partial){transformTool(pose,flip,equip,recoil,partial);}
    public final void handPose(PoseStack pose,float flip,float equip,float recoil,float partial){transformHand(pose,flip,equip,recoil,partial);}
}
