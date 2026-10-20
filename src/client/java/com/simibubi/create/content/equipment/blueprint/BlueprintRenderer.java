package com.simibubi.create.content.equipment.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.render.ChunkSectionLayerHelper;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

/** Source board/GUI-icon transforms, extracted once into owned native states and immutable meshes. */
public final class BlueprintRenderer extends EntityRenderer<BlueprintEntity,BlueprintRenderer.State> {
    private final ItemModelResolver itemModels;
    private final Mesh[] sourceBodies=new Mesh[3],unshadedBodies=new Mesh[3];

    public BlueprintRenderer(EntityRendererProvider.Context context){super(context);itemModels=context.getItemModelResolver();}
    public static void register(){EntityRenderers.register(AllEntityTypes.CRAFTING_BLUEPRINT,BlueprintRenderer::new);BlueprintPortrait.register();}

    public record Icon(int section,boolean primary,ItemStackRenderState model,Matrix4fc transform){}
    public record Snapshot(int size,float yaw,float pitch,Mesh board,Matrix4fc boardTransform,
            Matrix3fc fakeNormalTransform,List<Icon> icons,int boardLight,int itemLight,int outlineColor){}
    public static final class State extends EntityRenderState {
        private Snapshot snapshot;
        public Snapshot snapshot(){return snapshot;}
    }
    @Override public State createRenderState(){return new State();}

    @Override public void extractRenderState(BlueprintEntity entity,State state,float partialTick){
        super.extractRenderState(entity,state,partialTick);
        int size=entity.size();float yaw=Mth.lerp(partialTick,entity.yRotO,entity.getYRot()),pitch=entity.getXRot();
        var board=new PartialModelRenderState();
        AllPartialModels.extract(size==3?AllPartialModels.CRAFTING_BLUEPRINT_3x3:size==2?AllPartialModels.CRAFTING_BLUEPRINT_2x2:AllPartialModels.CRAFTING_BLUEPRINT_1x1,board);
        Mesh mesh=unshadedBoard(size,board.mesh);
        var bodyPose=new PoseStack();bodyPose.rotateDegrees(Axis.YP,-yaw);bodyPose.rotateDegrees(Axis.XP,90+pitch);
        bodyPose.translate(-.5,-1/32f,-.5);if(size==2)bodyPose.translate(.5,0,-.5);

        float fakeNormalPitch=-15;int blockLight=state.lightCoords>>4&15,skyLight=state.lightCoords>>20&15;
        boolean vertical=pitch!=0;
        if(pitch==-90)fakeNormalPitch=-45;
        else if(pitch==90||yaw%180!=0){blockLight/=1.35;skyLight/=1.35;}
        int itemLight=Mth.floor(skyLight+.5)<<20|(Mth.floor(blockLight+.5)&15)<<4;
        var normalPose=new PoseStack();normalPose.rotateDegrees(Axis.YP,vertical?0:-yaw);normalPose.rotateDegrees(Axis.XP,fakeNormalPitch);

        var iconPose=new PoseStack();iconPose.rotateDegrees(Axis.YP,-yaw);iconPose.rotateDegrees(Axis.XP,pitch);iconPose.translate(0,0,1/32f+.001);
        if(size==3)iconPose.translate(-1,-1,0);
        List<Icon> icons=new ArrayList<>(size*size*2);
        for(int row=0;row<size;row++){
            iconPose.pushPose();
            for(int column=0;column<size;column++){
                int section=row*size+column;var display=entity.getSection(section).getDisplayItems();
                iconPose.pushPose();iconPose.scale(.5f,.5f,1/1024f);
                extractIcon(entity,icons,iconPose,section,true,display.getFirst());
                extractIcon(entity,icons,iconPose,section,false,display.getSecond());
                iconPose.popPose();iconPose.translate(1,0,0);
            }
            iconPose.popPose();iconPose.translate(0,1,0);
        }
        state.snapshot=new Snapshot(size,yaw,pitch,mesh,new Matrix4f(bodyPose.last().pose()),
            new Matrix3f(normalPose.last().normal()),List.copyOf(icons),state.lightCoords,itemLight,state.outlineColor);
    }

    private void extractIcon(BlueprintEntity entity,List<Icon> icons,PoseStack pose,int section,boolean primary,ItemStack source){
        if(source.isEmpty())return;
        var model=new ItemStackRenderState();itemModels.updateForTopItem(model,source.copy(),ItemDisplayContext.GUI,entity.level(),null,0);
        pose.pushPose();if(!primary){pose.translate(.325f,-.325f,1);pose.scale(.625f,.625f,1);}
        icons.add(new Icon(section,primary,model,new Matrix4f(pose.last().pose())));pose.popPose();
    }

    private Mesh unshadedBoard(int size,Mesh source){
        int index=size-1;if(sourceBodies[index]!=source){
            var mesh=Renderer.get().mutableMesh();var emitter=mesh.emitter();
            source.forEach(quad->emitter.copyFrom(quad).chunkLayer(ChunkSectionLayer.SOLID)
                .shadeDirectionOverride(Direction.UP).ambientOcclusion(TriState.FALSE).emissive(false).emit());
            sourceBodies[index]=source;unshadedBodies[index]=mesh.immutableCopy();
        }
        return unshadedBodies[index];
    }

    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        Snapshot snapshot=state.snapshot;
        if(snapshot==null){super.submit(state,pose,collector,camera);return;}
        pose.pushPose();pose.mulPose(snapshot.boardTransform);
        collector.submitBlockModel(pose,ChunkSectionLayerHelper::getMovingBlockRenderType,false,List.of(),snapshot.board,
            new int[0],snapshot.boardLight,OverlayTexture.NO_OVERLAY,snapshot.outlineColor);pose.popPose();
        super.submit(state,pose,collector,camera);
        Matrix3f baseNormal=new Matrix3f(pose.last().normal());
        for(var icon:snapshot.icons){
            pose.pushPose();pose.mulPose(icon.transform);pose.last().normal().set(baseNormal).mul(snapshot.fakeNormalTransform);
            icon.model.submit(pose,collector,snapshot.itemLight,OverlayTexture.NO_OVERLAY,snapshot.outlineColor);pose.popPose();
        }
    }
}
