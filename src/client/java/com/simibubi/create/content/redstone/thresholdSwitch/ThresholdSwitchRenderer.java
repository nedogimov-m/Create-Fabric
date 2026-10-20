package com.simibubi.create.content.redstone.thresholdSwitch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.redstone.DirectedDirectionalBlock;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import java.util.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

public final class ThresholdSwitchRenderer implements BlockEntityRenderer<ThresholdSwitchBlockEntity,ThresholdSwitchRenderer.State> {
    private final ItemModelResolver items;
    public ThresholdSwitchRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
    private record Filter(ItemStackRenderState item,Vec3 position,float yaw,float pitch,float roll,float offset){}
    public static final class State extends BlockEntityRenderState {private final List<Filter> filters=new ArrayList<>();public int sampleFaces(){return filters.size();}}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(ThresholdSwitchBlockEntity observer,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){
        BlockEntityRenderer.super.extractRenderState(observer,state,partial,camera,overlay);state.filters.clear();var sample=observer.filtering.getFilter();if(!ValueBoxRenderer.isFilterInRange(observer)||sample.isEmpty())return;var block=observer.getBlockState();
        for(var side:Direction.values()){
            if(!ThresholdSwitchBlock.FILTER_SLOT.activeSide(block,side))continue;
            var item=new ItemStackRenderState();items.updateForTopItem(item,sample,ItemDisplayContext.FIXED,observer.getLevel(),null,0);float yaw=180+switch(side){case NORTH->180;case EAST->90;case WEST->270;default->0;};float pitch=side==Direction.UP?90:side==Direction.DOWN?270:0;
            var facing=block.getValue(DirectedDirectionalBlock.FACING);float roll=side==Direction.UP?180-switch(facing){case NORTH->180;case EAST->90;case WEST->270;default->0;}:0;
            state.filters.add(new Filter(item,ThresholdSwitchBlock.FILTER_SLOT.position(side),yaw,pitch,roll,ValueBoxRenderer.customZOffset(sample.getItem())));
        }
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){for(var filter:state.filters){pose.pushPose();pose.translate(filter.position.x,filter.position.y,filter.position.z);pose.rotateDegrees(Axis.YP,filter.yaw);pose.rotateDegrees(Axis.XP,filter.pitch);pose.rotateDegrees(Axis.ZP,filter.roll);pose.scale(.5f,.5f,.5f);ValueBoxRenderer.renderItemIntoValueBox(filter.item,pose,collector,state.lightCoords,filter.offset);pose.popPose();}}
}
