package com.simibubi.create.content.logistics.itemHatch;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
/** Native source filter item extraction/submission, at the original hatch slot transform. */
public final class ItemHatchRenderer implements BlockEntityRenderer<ItemHatchBlockEntity,ItemHatchRenderer.State> {
 private final ItemModelResolver items;
 public ItemHatchRenderer(BlockEntityRendererProvider.Context context){items=context.itemModelResolver();}
 public static final class State extends BlockEntityRenderState {final ItemStackRenderState filter=new ItemStackRenderState();Vec3 offset;float yaw,itemOffset;}
 @Override public State createRenderState(){return new State();}
 @Override public void extractRenderState(ItemHatchBlockEntity hatch,State state,float partial,Vec3 camera,ModelFeatureRenderer.CrumblingOverlay overlay){BlockEntityRenderer.super.extractRenderState(hatch,state,partial,camera,overlay);state.filter.clear();var sample=hatch.filtering.getFilter();if(!ValueBoxRenderer.isFilterInRange(hatch)||sample.isEmpty())return;state.offset=HatchFilterSlot.position(hatch.getBlockState());state.yaw=HatchFilterSlot.angle(hatch.getBlockState());state.itemOffset=ValueBoxRenderer.customZOffset(sample.getItem());items.updateForTopItem(state.filter,sample,ItemDisplayContext.FIXED,hatch.getLevel(),null,0);}
 @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){if(state.filter.isEmpty())return;pose.pushPose();pose.translate(state.offset.x,state.offset.y,state.offset.z);pose.rotateDegrees(Axis.YP,state.yaw);pose.rotateDegrees(Axis.XP,-45);pose.scale(HatchFilterSlot.SCALE,HatchFilterSlot.SCALE,HatchFilterSlot.SCALE);ValueBoxRenderer.renderItemIntoValueBox(state.filter,pose,collector,state.lightCoords,state.itemOffset);pose.popPose();}
}
