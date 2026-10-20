package com.simibubi.create.content.equipment.armor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class CardboardArmorHandlerClient {
    private record Choice(int index,long seen){}
    private static final Map<UUID,Choice> CHOICES=new HashMap<>();
    public static final RenderStateDataKey<Box> BOX=RenderStateDataKey.create(()->"create:cardboard_stealth_box");
    public record Box(PartialModelRenderState model,float yaw,float scale,double height){}
    private static int getCurrentBoxIndex(Player player){var old=CHOICES.get(player.getUUID());int index=old==null?player.getRandom().nextInt(PackageStyles.STANDARD_BOXES.size()):old.index;CHOICES.put(player.getUUID(),new Choice(index,player.level().getGameTime()));return index;}
    public static void clientTick(){var client=Minecraft.getInstance();if(client.level==null){CHOICES.clear();return;}long now=client.level.getGameTime();CHOICES.values().removeIf(choice->now-choice.seen>20||now<choice.seen);for(var player:client.level.players())if(CardboardArmorHandler.testForStealth(player))getCurrentBoxIndex(player);}
    @SuppressWarnings({"rawtypes","unchecked"}) public static void extract(LivingEntity entity,LivingEntityRenderState state,float partial){
        Box box=null;if(entity instanceof Player player&&CardboardArmorHandler.testForStealth(player)&&!PackageStyles.STANDARD_BOXES.isEmpty()){
            var model=new PartialModelRenderState();var key=PackageRenderer.model(new ItemStack(PackageStyles.STANDARD_BOXES.get(getCurrentBoxIndex(player))));if(key!=null){AllPartialModels.extract(key,model);var renderer=(EntityRenderer)Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);double offset=renderer.getRenderOffset(state).y;double movement=player.position().subtract(player.xo,player.yo,player.zo).length();double bob=player.onGround()?Math.min(Math.abs(Math.cos(((player.level().getGameTime()+partial)%256)/2))*-offset,movement*5):0;box=new Box(model,Mth.rotLerp(partial,player.yRotO,player.getYRot()),player.getScale(),-offset+bob);}
        }var data=(FabricRenderState)state;if(box!=null||data.getData(BOX)!=null)data.setData(BOX,box);
    }
    public static boolean submit(LivingEntityRenderState state,PoseStack pose,SubmitNodeCollector collector){var box=((FabricRenderState)state).getData(BOX);if(box==null)return false;pose.pushPose();pose.translate(0,box.height,0);pose.scale(box.scale,box.scale,box.scale);pose.rotateDegrees(Axis.YP,-box.yaw-90);pose.translate(-.5,0,-.5);box.model.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,state.outlineColor);pose.popPose();return true;}
    public static void clear(){CHOICES.clear();}
    private CardboardArmorHandlerClient(){}
}
