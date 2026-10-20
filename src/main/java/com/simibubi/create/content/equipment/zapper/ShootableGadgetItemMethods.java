package com.simibubi.create.content.equipment.zapper;

import com.simibubi.create.AllDataComponents;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Original dual-wield, cooldown and barrel math, using actual native stack cooldown groups. */
public final class ShootableGadgetItemMethods {
    public static void applyCooldown(Player player,ItemStack item,InteractionHand hand,Predicate<ItemStack> predicate,int cooldown){
        if(cooldown<=0)return;
        boolean other=predicate.test(player.getItemInHand(hand==InteractionHand.MAIN_HAND?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND));
        player.getCooldowns().addCooldown(item,other?cooldown*2/3:cooldown);
    }
    public static void sendPackets(Player player,Function<Boolean,? extends ShootGadgetPacket> factory){
        if(!(player instanceof ServerPlayer actual))return;
        var tracking=factory.apply(false);for(var observer:PlayerLookup.tracking(actual))if(observer!=actual)ServerPlayNetworking.send(observer,tracking);
        ServerPlayNetworking.send(actual,factory.apply(true));
    }
    public static boolean shouldSwap(Player player,ItemStack item,InteractionHand hand,Predicate<ItemStack> predicate){
        boolean swap=item.has(AllDataComponents.SHAPER_SWAP),main=hand==InteractionHand.MAIN_HAND;
        boolean other=predicate.test(player.getItemInHand(main?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND));
        if(main&&swap&&other)return true;
        if(main&&!swap&&other)item.set(AllDataComponents.SHAPER_SWAP,true);
        if(!main&&swap)item.remove(AllDataComponents.SHAPER_SWAP);
        if(!main&&other)player.getMainHandItem().remove(AllDataComponents.SHAPER_SWAP);
        player.startUsingItem(hand);return false;
    }
    public static Vec3 getGunBarrelVec(Player player,boolean mainHand,Vec3 forward){
        Vec3 start=player.position().add(0,player.getEyeHeight(),0);
        float yaw=(float)(player.getYRot()/-180*Math.PI),pitch=(float)(player.getXRot()/-180*Math.PI);
        int flip=mainHand==(player.getMainArm()==HumanoidArm.RIGHT)?-1:1;
        return start.add(new Vec3(flip*forward.x,forward.y,forward.z).xRot(pitch).yRot(yaw));
    }
    public static Vec3 rotateZ(Vec3 vector,double degrees){float angle=(float)(degrees/180f*Math.PI);double sin=net.minecraft.util.Mth.sin(angle),cos=net.minecraft.util.Mth.cos(angle);return new Vec3(vector.x*cos-vector.y*sin,vector.y*cos+vector.x*sin,vector.z);}
    /** Exact source Catnip lookAt basis, including its vertical fallback. */
    public static Vec3 lookAt(Vec3 vector,Vec3 forward){forward=forward.normalize();Vec3 up=new Vec3(0,1,0);double dot=forward.dot(up);if(Math.abs(dot)>1-1.0E-3)up=new Vec3(0,0,dot>0?1:-1);var right=forward.cross(up).normalize();up=right.cross(forward).normalize();return new Vec3(vector.x*right.x+vector.y*up.x+vector.z*forward.x,vector.x*right.y+vector.y*up.y+vector.z*forward.y,vector.x*right.z+vector.y*up.z+vector.z*forward.z);}
    private ShootableGadgetItemMethods(){}
}
