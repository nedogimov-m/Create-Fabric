// Native26.3 extraction boundary over actual source Ponder execution state.
package net.createmod.ponder.foundation.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.render.BlockEntityRenderHelper.FailureState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.catnip.outliner.Outline;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.InputWindowElement;
import net.createmod.ponder.foundation.element.MinecartElementImpl;
import net.createmod.ponder.foundation.element.ParrotElementImpl;
import net.createmod.ponder.foundation.element.TextWindowElement;
import net.createmod.ponder.foundation.element.WorldSectionElementImpl;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/** Detached content for the actual native viewport and scene-overlay stages.
 * entities() retains the combined capture order: the first worldEntityCount entries are ordinary
 * world entities; the remaining entries are source renderLast cart/parrot elements.
 */
public record PonderSceneFrame(List<Section> sections,List<Entity> entities,List<Item> items,
    List<TextOverlay> texts,List<InputOverlay> inputs,List<Outline.Geometry> outlines,
    ParticlesRenderState particles,List<PonderSceneElement.RenderState> sceneElements,
    List<PonderOverlayElement.RenderState> overlayElements,int worldEntityCount) {
  public PonderSceneFrame {
    sections=List.copyOf(sections);entities=List.copyOf(entities);items=List.copyOf(items);texts=List.copyOf(texts);
    inputs=List.copyOf(inputs);outlines=List.copyOf(outlines);
    sceneElements=List.copyOf(sceneElements);overlayElements=List.copyOf(overlayElements);
    if(worldEntityCount<0||worldEntityCount>entities.size())throw new IllegalArgumentException("Invalid captured world entity count");
  }
  /** Retains the existing native frame constructor for callers with only builtin content. */
  public PonderSceneFrame(List<Section> sections,List<Entity> entities,List<Item> items,
      List<TextOverlay> texts,List<InputOverlay> inputs,List<Outline.Geometry> outlines,ParticlesRenderState particles) {
    this(sections,entities,items,texts,inputs,outlines,particles,List.of(),List.of(),entities.size());
  }
  public record BlockEntity(BlockPos position,BlockEntityRenderState state,BlockEntityRenderer renderer,FailureState failure){}
  public record Section(Matrix4f transform,List<PartialModelRenderState> blocks,List<BlockEntity> blockEntities,
      int light,Map<BlockPos,Integer> breaking,VoxelShape selectedShape){
    public Section {transform=new Matrix4f(transform);blocks=List.copyOf(blocks);blockEntities=List.copyOf(blockEntities);breaking=Map.copyOf(breaking);}
    @Override public Matrix4f transform(){return new Matrix4f(transform);}
  }
  public record Entity(Matrix4f transform,EntityRenderState state,EntityRenderer renderer){
    public Entity{transform=new Matrix4f(transform);}
    @Override public Matrix4f transform(){return new Matrix4f(transform);}
  }
  public record Item(Matrix4f transform,ItemStackRenderState state,int light){
    public Item{transform=new Matrix4f(transform);}
    @Override public Matrix4f transform(){return new Matrix4f(transform);}
  }
  public record TextOverlay(String text,PonderPalette palette,Vec3 target,int independentY,boolean nearScene,float fade){}
  public record InputOverlay(Vec3 position,Pointing direction,Identifier key,String keyText,
      ScreenElement icon,ItemStack item,float fade){
    public InputOverlay{item=item.copy();}
    @Override public ItemStack item(){return item.copy();}
  }

  /** Camera/frustum are explicit virtual-scene inputs, never the live world's camera authority. */
  public static PonderSceneFrame extract(PonderScene scene,float partial,Camera camera,Frustum frustum){
    var world=scene.getWorld();world.scenePartialTicks(partial);
    var sections=new ArrayList<Section>();var entities=new ArrayList<Entity>();var items=new ArrayList<Item>();
    var texts=new ArrayList<TextOverlay>();var inputs=new ArrayList<InputOverlay>();
    scene.forEachVisible(WorldSectionElementImpl.class,section->sections.add(section.extract(world,partial,camera.position())));
    for(var entity:List.copyOf(world.getEntityList()))
      entities.add(entity(entity,new Matrix4f().translate((float)net.minecraft.util.Mth.lerp(partial,entity.xOld,entity.getX()),
          (float)net.minecraft.util.Mth.lerp(partial,entity.yOld,entity.getY()),
          (float)net.minecraft.util.Mth.lerp(partial,entity.zOld,entity.getZ())),partial,LightCoordsUtil.FULL_BRIGHT));
    int worldEntityCount=entities.size();
    scene.forEachVisible(MinecartElementImpl.class,cart->{
      var entity=cart.extractEntity(scene);var pose=new PoseStack();cart.applyFade(pose,partial);
      pose.translate(cart.extractLocation());pose.translate(net.minecraft.util.Mth.lerp(partial,entity.xo,entity.getX()),
          net.minecraft.util.Mth.lerp(partial,entity.yo,entity.getY()),net.minecraft.util.Mth.lerp(partial,entity.zo,entity.getZ()));
      pose.rotateDegrees(com.mojang.math.Axis.YP,cart.extractYaw(partial));
      entities.add(entity(entity,pose.last().pose(),partial,cart.lightCoordsFromFade(cart.getFade(partial))));
    });
    scene.forEachVisible(ParrotElementImpl.class,parrot->{
      var entity=parrot.extractEntity(scene);var pose=parrot.extractTransform(scene,partial);
      items.addAll(parrot.extractAdditionalItems(scene,partial,pose));
      entities.add(entity(entity,pose.last().pose(),partial,parrot.lightCoordsFromFade(parrot.getFade(partial))));
    });
    scene.forEachVisible(TextWindowElement.class,text->texts.add(text.snapshot(partial)));
    scene.forEachVisible(InputWindowElement.class,input->inputs.add(input.snapshot(partial)));
    var sceneElements=new ArrayList<PonderSceneElement.RenderState>();
    var overlayElements=new ArrayList<PonderOverlayElement.RenderState>();
    scene.forEachVisible(PonderSceneElement.class,element->{
      var state=element.extractSceneRenderState(scene,partial);if(state!=null)sceneElements.add(state);
    });
    scene.forEachVisible(PonderOverlayElement.class,element->{
      var state=element.extractOverlayRenderState(scene,partial);if(state!=null)overlayElements.add(state);
    });
    return new PonderSceneFrame(sections,entities,items,texts,inputs,scene.getOutliner().extract(Vec3.ZERO,partial),
        world.extractParticles(frustum,camera,partial),sceneElements,overlayElements,worldEntityCount);
  }

  @SuppressWarnings({"rawtypes","unchecked"})
  private static Entity entity(net.minecraft.world.entity.Entity entity,Matrix4f transform,float partial,int light){
    EntityRenderer renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
    EntityRenderState state=renderer.createRenderState();renderer.extractRenderState(entity,state,partial);
    state.lightCoords=light;return new Entity(transform,state,renderer);
  }

  /** Submission consults only captured native states and the caller's render camera. */
  @SuppressWarnings({"rawtypes","unchecked"})
  public void submitWorld(PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
    submitElements(PonderSceneElement.Phase.BEFORE_WORLD,pose,collector,camera);
    for(var section:sections){
      pose.pushPose();
      try {
        pose.mulPose(section.transform);
        for(var block:section.blocks)block.submit(pose,collector,section.light,OverlayTexture.NO_OVERLAY,0);
        for(var block:section.blockEntities){
          if(block.failure.hasFailed())continue;
          pose.pushPose();
          try {
            pose.translate(block.position.getX(),block.position.getY(),block.position.getZ());
            block.renderer.submit(block.state,pose,collector,camera);
          } catch(Exception exception){block.failure.report(exception,true);}
          finally {pose.popPose();}
        }
      } finally {pose.popPose();}
    }
    submitElements(PonderSceneElement.Phase.WORLD,pose,collector,camera);
    for(var item:items){
      pose.pushPose();
      try {pose.mulPose(item.transform);item.state.submit(pose,collector,item.light,OverlayTexture.NO_OVERLAY,0);}
      finally {pose.popPose();}
    }
    // Cart/parrot belong to source renderLast; ordinary world entities follow that phase.
    for(int i=worldEntityCount;i<entities.size();i++)submitEntity(entities.get(i),pose,collector,camera);
    submitElements(PonderSceneElement.Phase.AFTER_WORLD,pose,collector,camera);
    for(int i=0;i<worldEntityCount;i++)submitEntity(entities.get(i),pose,collector,camera);
  }

  @SuppressWarnings({"rawtypes","unchecked"})
  private static void submitEntity(Entity entity,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
    pose.pushPose();
    try {pose.mulPose(entity.transform);entity.renderer.submit(entity.state,pose,collector,camera);}
    finally {pose.popPose();}
  }

  private void submitElements(PonderSceneElement.Phase phase,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera) {
    for(var state:sceneElements) {
      pose.pushPose();
      try {state.submit(phase,pose,collector,camera);}
      finally {pose.popPose();}
    }
  }
}
