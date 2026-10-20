package com.simibubi.create.content.equipment.potatoCannon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.zapper.ShootableGadgetRenderHandler;
import com.simibubi.create.foundation.particle.AirParticleData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public final class PotatoCannonRenderHandler extends ShootableGadgetRenderHandler {
    private float nextPitch;
    @Override public void playSound(InteractionHand hand,Vec3 position){var level=Minecraft.getInstance().level;if(level!=null)PotatoProjectileEntity.playLaunchSound(level,position,nextPitch);}
    @Override protected boolean appliesTo(ItemStack stack){return stack.getItem() instanceof PotatoCannonItem;}
    public void beforeShoot(float pitch,Vec3 location,Vec3 motion,ItemStack stack){nextPitch=pitch;var level=Minecraft.getInstance().level;if(level==null||stack.isEmpty())return;for(int i=0;i<2;i++){var velocity=offset(motion.scale(.1f),level.getRandom(),.025f);level.addParticle(new ItemParticleOption(ParticleTypes.ITEM,ItemStackTemplate.fromNonEmptyStack(stack)),location.x,location.y,location.z,velocity.x,velocity.y,velocity.z);var air=offset(motion.scale(2),level.getRandom(),.5f);level.addParticle(new AirParticleData(1,.25f),location.x,location.y,location.z,air.x,air.y,air.z);}}
    private static Vec3 offset(Vec3 vector,net.minecraft.util.RandomSource random,float radius){return vector.add((random.nextFloat()-.5f)*radius*2,(random.nextFloat()-.5f)*radius*2,(random.nextFloat()-.5f)*radius*2);}
    @Override protected void transformTool(PoseStack pose,float flip,float equip,float recoil,float partial){pose.translate(flip*-.1f,0,.14f);pose.scale(.75f,.75f,.75f);pose.rotateDegrees(Axis.XP,recoil*80);}
    @Override protected void transformHand(PoseStack pose,float flip,float equip,float recoil,float partial){pose.translate(flip*-.09,-.275,-.25);pose.rotateDegrees(Axis.ZP,flip*-10);}
}
