package com.simibubi.create.content.processing.burner;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.phys.Vec3;

public final class BlazeBurnerRenderer implements BlockEntityRenderer<BlazeBurnerBlockEntity, BlazeBurnerRenderer.State> {
    public BlazeBurnerRenderer(BlockEntityRendererProvider.Context context) {}
    public static final class State extends BlockEntityRenderState {
        final PartialModelRenderState head = new PartialModelRenderState();
        final PartialModelRenderState smallRods = new PartialModelRenderState();
        final PartialModelRenderState largeRods = new PartialModelRenderState();
        final PartialModelRenderState flame = new PartialModelRenderState();
        public final PartialModelRenderState goggles=new PartialModelRenderState();
        public final PartialModelRenderState hat=new PartialModelRenderState();
        public boolean inert;
        float headY, smallY, largeY, yaw, hatYaw;
        public boolean hasHead(){return !head.isEmpty();}
        public boolean hasFlame(){return !flame.isEmpty();}
        public float headYaw(){return yaw;}
        public float hatYawOffset(){return hatYaw;}
    }
    @Override public State createRenderState() { return new State(); }
    @Override public void extractRenderState(BlazeBurnerBlockEntity burner, State state, float partialTick, Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay overlay) {
        BlockEntityRenderer.super.extractRenderState(burner, state, partialTick, cameraPos, overlay);
        extractShared(burner,state,partialTick,false);
    }
    public static State portrait(BlazeBurnerBlockEntity burner,float partial){var state=new State();state.lightCoords=LightCoordsUtil.FULL_BRIGHT;extractShared(burner,state,partial,true);state.yaw=90;return state;}
    public static State inContraption(BlazeBurnerBlockEntity burner,float partial,float yaw){var state=new State();state.lightCoords=LightCoordsUtil.FULL_BRIGHT;extractShared(burner,state,partial,false,true);state.yaw=yaw;return state;}
    private static void extractShared(BlazeBurnerBlockEntity burner,State state,float partialTick,boolean portrait){
        extractShared(burner,state,partialTick,portrait,false);
    }
    private static void extractShared(BlazeBurnerBlockEntity burner,State state,float partialTick,boolean portrait,boolean actor){
        state.head.clear(); state.smallRods.clear(); state.largeRods.clear(); state.flame.clear();state.goggles.clear();state.hat.clear();
        state.hatYaw = 180;
        var heat = burner.getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL);
        if (heat == BlazeBurnerBlock.HeatLevel.NONE) return;
        if((portrait||actor)&&heat.ordinal()<BlazeBurnerBlock.HeatLevel.FADING.ordinal())heat=BlazeBurnerBlock.HeatLevel.FADING;
        boolean active = heat.ordinal() >= BlazeBurnerBlock.HeatLevel.FADING.ordinal();
        boolean above = !actor && !burner.getLevel().getBlockState(burner.getBlockPos().above()).isAir();
        if(portrait&&burner.getBlockState().getValue(BlazeBurnerBlock.HEAT_LEVEL).ordinal()<BlazeBurnerBlock.HeatLevel.FADING.ordinal())above=false;
        state.inert=!active;
        if(burner.trainHat)AllPartialModels.extract(AllPartialModels.TRAIN_HAT,state.hat);
        else if(portrait||!actor&&com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler.getBurnerTicker(burner.getLevel(),burner.getBlockPos())!=null)AllPartialModels.extract(AllPartialModels.LOGISTICS_HAT,state.hat);
        boolean superheated = heat == BlazeBurnerBlock.HeatLevel.SEETHING;
        AllPartialModels.extract(superheated ? above ? AllPartialModels.BLAZE_SUPER_ACTIVE : AllPartialModels.BLAZE_SUPER
                : active ? above && heat == BlazeBurnerBlock.HeatLevel.KINDLED ? AllPartialModels.BLAZE_ACTIVE : AllPartialModels.BLAZE_IDLE
                : AllPartialModels.BLAZE_INERT, state.head);
        if(burner.goggles)AllPartialModels.extract(active?AllPartialModels.BLAZE_GOGGLES:AllPartialModels.BLAZE_GOGGLES_SMALL,state.goggles);
        float time = burner.getLevel().getGameTime() + partialTick + Math.floorMod(burner.getBlockPos().hashCode(), 13) * 16;
        float lift = active && above ? 0.175f : 0;
        state.headY = (float) Math.sin(time / 16) / (active ? 64 : 16) - lift * 0.75f;
        state.smallY = (float) Math.sin(time / 16 + Math.PI) / 64 + lift + 0.125f;
        state.largeY = (float) Math.sin(time / 16 + Math.PI / 2) / 64 + lift - 3f / 16;
        var facing = burner.getBlockState().getValue(BlazeBurnerBlock.FACING);
        state.yaw = switch (facing) { case NORTH -> 180; case SOUTH -> 0; case EAST -> 270; default -> 90; };
        var player = Minecraft.getInstance().player;
        if (!actor && (!active || !above) && player != null) state.yaw = (float) -Math.toDegrees(Math.atan2(player.getX() - burner.getBlockPos().getX() - 0.5,
                player.getZ() - burner.getBlockPos().getZ() - 0.5));
        if (active) {
            AllPartialModels.extract(superheated ? AllPartialModels.BLAZE_SUPER_RODS : AllPartialModels.BLAZE_RODS, state.smallRods);
            AllPartialModels.extract(superheated ? AllPartialModels.BLAZE_SUPER_RODS_LARGE : AllPartialModels.BLAZE_RODS_LARGE, state.largeRods);
            if (above) extractFlame(state, heat, burner.getLevel().getGameTime() + partialTick);
        }
    }
    private static void extractFlame(State state, BlazeBurnerBlock.HeatLevel heat, float time) {
        AllPartialModels.extract(AllPartialModels.BLAZE_FLAME, state.flame);
        var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);
        var source = atlas.getSprite(Create.asResource("block/blaze_burner_flame"));
        var target = atlas.getSprite(Create.asResource(heat == BlazeBurnerBlock.HeatLevel.SEETHING
            ? "block/blaze_burner_flame_superheated_scroll" : "block/blaze_burner_flame_scroll"));
        float speed = 1 / 32f + heat.ordinal() / 64f;
        float uScroll = (time * speed / 2 % 1) / 2;
        float vScroll = (time * speed % 1) / 2;
        var mesh = Renderer.get().mutableMesh();
        var emitter = mesh.emitter();
        state.flame.mesh.forEach(quad -> {
            emitter.copyFrom(quad).chunkLayer(ChunkSectionLayer.CUTOUT);
            for (int vertex = 0; vertex < 4; vertex++) {
                float u = (quad.u(vertex) - source.getU0()) / (source.getU1() - source.getU0());
                float v = (quad.v(vertex) - source.getV0()) / (source.getV1() - source.getV0());
                emitter.uv(vertex, target.getU(u / 2 + uScroll), target.getV(v / 2 + vScroll));
            }
            emitter.emit();
        });
        state.flame.mesh = mesh.immutableCopy();
    }
    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {draw(state,pose,collector);}
    public static void draw(State state,PoseStack pose,SubmitNodeCollector collector){
        pose.pushPose();
        pose.translate(0.5, state.headY + 0.5, 0.5);
        pose.rotate(Axis.YP.rotationDegrees(state.yaw));
        pose.translate(-0.5, -0.5, -0.5);
        if (!state.head.isEmpty()) state.head.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        if (!state.flame.isEmpty()) state.flame.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
        if(!state.goggles.isEmpty()){pose.pushPose();pose.translate(0,8f/16,0);state.goggles.submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
        if(!state.hat.isEmpty()){pose.pushPose();pose.translate(.5,state.inert?.625:.75,.5);pose.rotate(Axis.YP.rotationDegrees(state.hatYaw));if(state.inert)pose.scale(.75f,.75f,.75f);state.hat.submit(pose,collector,LightCoordsUtil.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,0);pose.popPose();}
        pose.popPose();
        drawRods(state.smallRods, state.smallY, pose, collector);
        drawRods(state.largeRods, state.largeY, pose, collector);
    }
    private static void drawRods(PartialModelRenderState model, float y, PoseStack pose, SubmitNodeCollector collector) {
        if (model.isEmpty()) return;
        pose.pushPose(); pose.translate(0, y, 0);
        model.submit(pose, collector, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
    }
}
