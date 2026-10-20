// Native26.3 extraction adapter for source Ponder world sections. No legacy buffer or GL backend.
package net.createmod.ponder.foundation.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.render.BlockEntityRenderHelper.FailureState;
import java.util.ArrayList;
import java.util.List;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.scene.Selection;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class PonderSectionRenderer {
  private final PonderBlockModels models=new PonderBlockModels();
  private final java.util.Map<net.minecraft.world.level.block.entity.BlockEntity,FailureState> failures=
      new java.util.WeakHashMap<>();
  private List<PartialModelRenderState> baked;

  public void invalidate(){baked=null;models.clear();failures.clear();}

  @SuppressWarnings({"rawtypes","unchecked"})
  public PonderSceneFrame.Section extract(PonderLevel world,Selection selection,Matrix4f transform,
      float partial,Vec3 camera,int light,int lightLevel,BlockPos selected) {
    world.setMask(selection);
    try {
      if(baked==null){
        var solid=Renderer.get().mutableMesh();var translucent=Renderer.get().mutableMesh();
        for(var pos:selection){
          var state=world.getBlockState(pos);
          if(state.getRenderShape()!=RenderShape.MODEL)continue;
          var model=models.extract(world,pos,state);
          var emitter=(model.translucent?translucent:solid).emitter();
          emitter.pushTransform(quad->{quad.translate(pos.getX(),pos.getY(),pos.getZ());return true;});
          model.mesh.outputTo(emitter);emitter.popTransform();
        }
        var built=new ArrayList<PartialModelRenderState>();
        if(solid.size()!=0){var model=new PartialModelRenderState();model.mesh=solid.immutableCopy();built.add(model);}
        if(translucent.size()!=0){var model=new PartialModelRenderState();model.mesh=translucent.immutableCopy();model.translucent=true;built.add(model);}
        baked=List.copyOf(built);
      }
      var extracted=new ArrayList<PonderSceneFrame.BlockEntity>();
      var dispatcher=Minecraft.getInstance().getBlockEntityRenderDispatcher();
      world.pushFakeLight(lightLevel);
      try {
        for(var pos:selection){
          var entity=world.getBlockEntity(pos);if(entity==null)continue;
          var failure=failures.computeIfAbsent(entity,FailureState::new);
          if(failure.hasFailed())continue;
          try {
            BlockEntityRenderer renderer=dispatcher.getRenderer(entity);if(renderer==null)continue;
            var state=(BlockEntityRenderState)renderer.createRenderState();
            renderer.extractRenderState(entity,state,partial,camera,null);
            state.lightCoords=light;
            extracted.add(new PonderSceneFrame.BlockEntity(pos.immutable(),state,renderer,failure));
          } catch(Exception exception){failure.report(exception,true);}
        }
      } finally {world.popLight();}
      var breaking=new java.util.LinkedHashMap<BlockPos,Integer>();
      world.getBlockBreakingProgressions().forEach((pos,value)->{if(selection.test(pos))breaking.put(pos.immutable(),value);});
      var selectedShape=selected==null?null:world.getBlockState(selected).getShape(world,selected).move(selected.getX(),selected.getY(),selected.getZ());
      return new PonderSceneFrame.Section(transform,baked,extracted,light,breaking,selectedShape);
    } finally {world.clearMask();}
  }
}
