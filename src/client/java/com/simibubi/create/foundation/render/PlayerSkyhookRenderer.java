package com.simibubi.create.foundation.render;

import com.simibubi.create.content.kinetics.chainConveyor.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;

/** Source hanging pose, consuming only extracted data; native hat/sleeve children inherit their parent. */
public final class PlayerSkyhookRenderer {
    public record HangingPose(boolean left,float time){}
    public static final RenderStateDataKey<HangingPose> HANGING=RenderStateDataKey.create(()->"create:skyhook_pose");
    public static void extract(LivingEntity entity,LivingEntityRenderState state,float partial){
        HangingPose pose=null;
        if(entity instanceof Player player&&(ChainConveyorRidingHandler.hanging(player.getUUID())||entity==Minecraft.getInstance().player&&ChainConveyorRidingHandler.active())){
            boolean left=player.getMainArm()==HumanoidArm.LEFT^!player.getMainHandItem().is(ChainConveyorRidingState.CHAIN_RIDEABLE);pose=new HangingPose(left,(float)(entity.level().getGameTime()+partial));
        }
        var data=(FabricRenderState)state;if(pose!=null||data.getData(HANGING)!=null)data.setData(HANGING,pose);
    }
    public static void apply(HumanoidModel<?> model,HumanoidRenderState state){
        var pose=((FabricRenderState)state).getData(HANGING);if(pose==null)return;
        model.body.resetPose();model.leftArm.resetPose();model.rightArm.resetPose();model.leftLeg.resetPose();model.rightLeg.resetPose();model.head.x=0;
        float body=(float)Math.toRadians(15+Mth.sin((pose.time+10)*.3f/(float)Math.PI)*10);float limb=(float)Math.toRadians(Mth.sin(pose.time*.3f/(float)Math.PI)*15);if(pose.left)body=-body;
        model.body.zRot=model.head.zRot=body;
        var main=pose.left?model.leftArm:model.rightArm;var other=pose.left?model.rightArm:model.leftArm;main.y-=3;
        float pivotX=main.x*Mth.cos(body)-main.y*Mth.sin(body)+(pose.left?-1:1)*4.5f;
        float pivotY=main.x*Mth.sin(body)+main.y*Mth.cos(body)+2;
        main.xRot=(float)-Math.toRadians(150);main.zRot=(pose.left?-1:1)*(float)Math.toRadians(15);
        rotate(other,body);other.zRot=(pose.left?-1:1)*(float)-Math.toRadians(20)+.5f*body+limb;
        var lead=pose.left?model.leftLeg:model.rightLeg;var trail=pose.left?model.rightLeg:model.leftLeg;lead.y-=.2f;rotate(lead,body);lead.xRot=(float)-Math.toRadians(25);lead.zRot=(pose.left?-1:1)*(float)Math.toRadians(10)+.5f*body+limb;
        trail.y-=.8f;rotate(trail,body);trail.xRot=(float)Math.toRadians(10);trail.zRot=(pose.left?-1:1)*(float)-Math.toRadians(10)+.5f*body+limb;
        for(var part:new ModelPart[]{model.head,model.body,other,trail,lead}){part.x-=pivotX;part.y-=pivotY;}
        // 26.3 hat is a child of head. Applying the old independent hat transform would double it.
        model.hat.resetPose();
    }
    private static void rotate(ModelPart part,float angle){float x=part.x,y=part.y;part.x=x*Mth.cos(angle)-y*Mth.sin(angle);part.y=x*Mth.sin(angle)+y*Mth.cos(angle);}
    private PlayerSkyhookRenderer(){}
}
