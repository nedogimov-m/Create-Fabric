package com.simibubi.create.foundation.blockEntity.behaviour;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;

/** Create's value-box item transform, submitted through Minecraft's renderer. */
public final class ValueBoxRenderer {
    private ValueBoxRenderer() {}

    /** Gather the source distance policy during extraction, before detached submission. */
    public static boolean isFilterInRange(net.minecraft.world.level.block.entity.BlockEntity entity) {
        if(entity instanceof net.createmod.ponder.api.VirtualBlockEntity virtual&&virtual.isVirtual())return true;
        var camera=net.minecraft.client.Minecraft.getInstance().getCameraEntity();
        if(camera==null)return true;
        float max=com.simibubi.create.infrastructure.config.AllConfigs.client().filterItemRenderDistance.getF();
        return !(camera.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(entity.getBlockPos()))>max*max);
    }

    public static void renderItemIntoValueBox(ItemStackRenderState item, PoseStack pose,
                                              SubmitNodeCollector collector, int light, float offset) {
        boolean blockItem = item.usesBlockLight();
        float scale = (blockItem ? 1 : 0.5f) + 1 / 64f;
        pose.scale(scale, scale, scale);
        pose.translate(0, 0, (blockItem ? 0 : -0.15f) + offset);
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
    }

    /** Source flat control filter: actual GUI mesh, squashed geometry and detached source lighting normal. */
    public static void renderFlatItemIntoValueBox(ItemStackRenderState item,PoseStack pose,SubmitNodeCollector collector,int light){
        if(item.isEmpty())return;
        pose.pushPose();pose.rotateDegrees(com.mojang.math.Axis.XP,230);var normal=new org.joml.Matrix3f(pose.last().normal());pose.popPose();
        pose.pushPose();pose.translate(0,0,-.25+1/32d+.001);pose.rotateDegrees(com.mojang.math.Axis.YP,180);
        var squashed=new PoseStack();squashed.mulPose(pose.last().pose());squashed.scale(.5f,.5f,1/1024f);squashed.last().normal().set(normal);
        item.submit(squashed,collector,light,OverlayTexture.NO_OVERLAY,0);pose.popPose();
    }

    public static float customZOffset(Item item) {
        if (item instanceof BlockItem blockItem) {
            var block = blockItem.getBlock();
            if (block instanceof ShaftBlock || block instanceof FenceBlock
                || block.builtInRegistryHolder().is(BlockTags.BUTTONS) || block == Blocks.END_ROD)
                return -0.1f;
        }
        return 0;
    }
}
