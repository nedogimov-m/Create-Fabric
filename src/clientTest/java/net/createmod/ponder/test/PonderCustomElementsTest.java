package net.createmod.ponder.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.mixin.client.GuiGraphicsExtractorAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.ponder.api.element.PonderOverlayElement;
import net.createmod.ponder.api.element.PonderSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.registration.PonderLocalization;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.createmod.ponder.foundation.ui.PonderViewport;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** One actual native Vulkan independent-element capture/submission/hide/replay case. Authored; UNRUN. */
public final class PonderCustomElementsTest implements FabricClientGameTest {
    private static final BlockPos P=new BlockPos(2,1,2);
    private static final int WORLD_COLOR=0xFF36C8ED,OVERLAY_COLOR=0xFFFFC640;
    private static final AtomicInteger TOKENS=new AtomicInteger();
    private static volatile Session active;

    public static final class Session {
        final PonderScene scene;final PonderUI ui;final Object hostOwner;final net.minecraft.world.level.block.state.BlockState hostState;
        WorldElement world;OverlayElement overlay;int generation;
        PonderViewport.State selected,hidden;WorldSnapshot frozenWorld;OverlaySnapshot frozenOverlay;Object ordinaryState;
        final List<PonderSceneElement.Phase> phases=new ArrayList<>();
        final List<PonderOverlayElement.Phase> overlayPhases=new ArrayList<>();
        Matrix3x2f overlayPose;boolean quadBuilt,fanBuilt,overlayBuilt,ordinarySubmitted,blitted,hiddenBlitted,freezeNext;
        int observedHiddenFrames;
        Session(PonderScene scene,PonderUI ui,Object hostOwner,net.minecraft.world.level.block.state.BlockState hostState){this.scene=scene;this.ui=ui;this.hostOwner=hostOwner;this.hostState=hostState;}
        void watch(){selected=null;hidden=null;frozenWorld=null;frozenOverlay=null;ordinaryState=null;phases.clear();overlayPhases.clear();overlayPose=null;quadBuilt=fanBuilt=overlayBuilt=ordinarySubmitted=blitted=hiddenBlitted=false;freezeNext=true;}
        boolean complete(){return blitted&&quadBuilt&&fanBuilt&&overlayBuilt&&ordinarySubmitted&&phases.equals(List.of(PonderSceneElement.Phase.values()))&&overlayPhases.equals(List.of(PonderOverlayElement.Phase.values()));}
    }
    private static final class WorldElement implements PonderSceneElement {
        final int generation;int ticks,color=WORLD_COLOR;float size=1;boolean visible=true;
        WorldElement(int generation){this.generation=generation;}
        @Override public void tick(PonderScene scene){ticks++;}
        @Override public boolean isVisible(){return visible;}
        @Override public void setVisible(boolean value){visible=value;}
        @Override public RenderState extractSceneRenderState(PonderScene scene,float partial){return new WorldSnapshot(TOKENS.incrementAndGet(),generation,color,size);}
    }
    private static final class OverlayElement implements PonderOverlayElement {
        final int generation;int ticks,color=OVERLAY_COLOR;boolean visible=true;
        OverlayElement(int generation){this.generation=generation;}
        @Override public void tick(PonderScene scene){ticks++;}
        @Override public boolean isVisible(){return visible;}
        @Override public void setVisible(boolean value){visible=value;}
        @Override public RenderState extractOverlayRenderState(PonderScene scene,float partial){return new OverlaySnapshot(TOKENS.incrementAndGet(),generation,color);}
    }
    /** Only scalar captured data; no source element, scene, world, callback or mutable pose field. */
    private record WorldSnapshot(int token,int generation,int color,float size) implements PonderSceneElement.RenderState {
        @Override public void submit(PonderSceneElement.Phase phase,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
            var session=active;boolean selected=session!=null&&session.frozenWorld!=null&&session.frozenWorld.token==token;
            if(selected){
                require(pose.last().pose().m30()==0&&pose.last().pose().m00()==1,"Each actual native addon phase receives an isolated parent pose");
                session.phases.add(phase);require(color==WORLD_COLOR&&size==1,"Native submitted snapshot retains pre-mutation captured values");
            }
            if(phase==PonderSceneElement.Phase.WORLD){
                int capturedToken=token,capturedColor=color;float capturedSize=size;
                pose.translate(2,1.5,2);
                collector.submitCustomGeometry(pose,RenderTypes.debugQuads(),(matrix,vertices)->{
                    geometry(capturedToken,capturedColor,0);
                    vertices.addVertex(matrix,-capturedSize,0,0).setColor(capturedColor);
                    vertices.addVertex(matrix,capturedSize,0,0).setColor(capturedColor);
                    vertices.addVertex(matrix,capturedSize,capturedSize,0).setColor(capturedColor);
                    vertices.addVertex(matrix,-capturedSize,capturedSize,0).setColor(capturedColor);
                });
                collector.submitCustomGeometry(pose,RenderTypes.debugTriangleFan(),(matrix,vertices)->{
                    geometry(capturedToken,capturedColor,1);
                    vertices.addVertex(matrix,0,0,.2f).setColor(capturedColor);
                    vertices.addVertex(matrix,.7f,0,.2f).setColor(capturedColor);
                    vertices.addVertex(matrix,.7f,.7f,.2f).setColor(capturedColor);
                    vertices.addVertex(matrix,0,.7f,.2f).setColor(capturedColor);
                });
            }
            // Deliberately leak the local pose; the framework's finally scope must contain it.
            pose.translate(10000,0,0);
        }
    }
    private record OverlaySnapshot(int token,int generation,int color) implements PonderOverlayElement.RenderState {
        @Override public void extract(PonderOverlayElement.Phase phase,GuiGraphicsExtractor gui,Font font,PonderOverlayElement.Viewport viewport){
            var session=active;boolean selected=session!=null&&session.frozenOverlay!=null&&session.frozenOverlay.token==token;
            if(selected){
                session.overlayPhases.add(phase);require(color==OVERLAY_COLOR,"Actual GUI extraction uses detached pre-mutation addon overlay snapshot");
                if(phase==PonderOverlayElement.Phase.BEFORE_BUILTINS)session.overlayPose=new Matrix3x2f(gui.pose());
                else require(session.overlayPose.equals(new Matrix3x2f(gui.pose()),.00001f),"Addon GUI pose changes cannot leak across builtin overlay extraction");
            }
            if(phase==PonderOverlayElement.Phase.AFTER_BUILTINS){
                int x=viewport.x()+24,y=viewport.y()+42;var pose=new Matrix3x2f(gui.pose());
                var bounds=new ScreenRectangle(x,y,150,18).transformMaxBounds(pose);
                ((GuiGraphicsExtractorAccessor)gui).create$getRenderState().addGuiElement(new OverlayQuad(token,color,x,y,pose,bounds));
            }
            gui.pose().translate(10000,10000);
        }
    }
    /** Real native GUI geometry is built later, after the source overlay object has changed. */
    private record OverlayQuad(int token,int color,int x,int y,Matrix3x2fc pose,ScreenRectangle bounds) implements GuiElementRenderState {
        OverlayQuad{pose=new Matrix3x2f(pose);}
        public Matrix3x2fc pose(){return new Matrix3x2f(pose);}
        @Override public RenderPipeline pipeline(){return RenderPipelines.GUI;}
        @Override public TextureSetup textureSetup(){return TextureSetup.noTexture();}
        @Override public ScreenRectangle scissorArea(){return null;}
        @Override public void buildVertices(VertexConsumer vertices){
            var session=active;if(session!=null&&session.frozenOverlay!=null&&session.frozenOverlay.token==token){require(color==OVERLAY_COLOR,"Actual native GUI vertices retain captured addon color");session.overlayBuilt=true;}
            vertices.addVertexWith2DPose(pose,x,y).setColor(color);vertices.addVertexWith2DPose(pose,x,y+18).setColor(color);
            vertices.addVertexWith2DPose(pose,x+150,y+18).setColor(color);vertices.addVertexWith2DPose(pose,x+150,y).setColor(color);
        }
    }
    private static void geometry(int token,int color,int layer){
        var session=active;if(session==null||session.frozenWorld==null||session.frozenWorld.token!=token)return;
        require(color==WORLD_COLOR,"Actual native deferred geometry reads frozen addon values");
        if(layer==0)session.quadBuilt=true;else session.fanBuilt=true;
    }
    /** Called only by a test mixin at real PonderViewport.extract RETURN. */
    public static void captured(PonderScene scene,PonderViewport.State state){
        var session=active;if(session==null||session.scene!=scene)return;
        if(session.freezeNext&&state.frame().sceneElements().size()==1&&state.frame().overlayElements().size()==1){
            session.selected=state;session.frozenWorld=(WorldSnapshot)state.frame().sceneElements().getFirst();
            session.frozenOverlay=(OverlaySnapshot)state.frame().overlayElements().getFirst();session.freezeNext=false;
            require(state.frame().worldEntityCount()==1&&state.frame().entities().size()==2,"Native frame preserves one ordinary entity and one source renderLast cart in its combined entities accessor");
            session.ordinaryState=state.frame().entities().getFirst().state();
            require(session.frozenWorld.generation==session.generation&&session.frozenOverlay.generation==session.generation,"Actual native frame captures current independently implemented addon elements");
            session.world.color=0xFFDD33AA;session.world.size=3;session.overlay.color=0xFF4422CC;
        }
        if(state.frame().sceneElements().isEmpty()&&state.frame().overlayElements().isEmpty()&&session.world!=null&&!session.world.visible){
            session.observedHiddenFrames++;if(session.hidden==null)session.hidden=state;
        }
    }
    /** Called at the actual typed native item-entity submission boundary. */
    public static void ordinarySubmitted(Object state){
        var session=active;if(session==null||session.ordinaryState!=state)return;
        require(session.phases.equals(List.of(PonderSceneElement.Phase.values())),"Source renderLast addon phase precedes actual ordinary world-entity submission");session.ordinarySubmitted=true;
    }
    /** Called only after actual native PictureInPictureRenderer.blitTexture returns. */
    public static void blitted(Object state){var session=active;if(session==null)return;if(state==session.selected)session.blitted=true;if(state==session.hidden)session.hiddenBlitted=true;}

    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->require(RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(Locale.ROOT).contains("vulkan"),"Independent Ponder elements must use the actual native Vulkan device"));
        var retained=new AtomicReference<Session>();
        try(var joined=context.worldBuilder().create()){
            joined.getServer().runOnServer(ignored->{var level=joined.getConnection().getServerLevel();level.setBlockAndUpdate(P,Blocks.BARREL.defaultBlockState());((BarrelBlockEntity)level.getBlockEntity(P)).setItem(0,new ItemStack(Items.DIAMOND,7));});
            context.waitFor(client->client.level!=null&&client.level.getBlockEntity(P) instanceof BarrelBlockEntity,200);
            context.runOnClient(client->{
                var world=new PonderLevel(BlockPos.ZERO,client.level);
                for(int x=0;x<5;x++)for(int z=0;z<5;z++)world.setBlock(new BlockPos(x,0,z),Blocks.STONE.defaultBlockState(),0);
                world.setBlock(P,AllBlocks.DEPOT.defaultBlockState(),0);world.createBackup();
                var scene=new PonderScene(world,new PonderLocalization(),"create_test",BuiltInRegistries.ITEM.getKey(AllBlocks.SHAFT.asItem()),List.of(),List.of());
                var builder=scene.builder();builder.title("independent_native_elements","Independent native element fixture");builder.configureBasePlate(0,0,5);builder.showBasePlate();
                builder.world().createItemEntity(new Vec3(1.5,1.4,2.5),Vec3.ZERO,new ItemStack(Items.EMERALD));
                builder.special().createCart(new Vec3(3,1,3),0,(level,x,y,z)->{var cart=new Minecart(EntityTypes.MINECART,level);cart.setPos(x,y,z);return cart;});
                builder.addInstruction(current->{var session=retained.get();session.generation++;session.world=new WorldElement(session.generation);session.overlay=new OverlayElement(session.generation);current.addElement(session.world);current.addElement(session.overlay);});
                builder.idle(20);builder.addInstruction(current->{var session=retained.get();session.world.setVisible(false);session.overlay.setVisible(false);});builder.idle(80);
                var ui=new PonderUI(List.of(scene),null);var session=new Session(scene,ui,client.level.getBlockEntity(P),client.level.getBlockState(P));retained.set(session);active=session;
                client.gui.setScreen(ui);ui.setFocused(null);require(ui.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_SPACE,0,0)),"Actual Ponder UI pauses through its normal native key route");
                String particles=client.particleEngine.countParticles();scene.tick();scene.tick();
                require(session.world.ticks>0&&session.overlay.ticks>0,"Normal source instruction insertion and scene ticks reach both independent interfaces");
                host(client,session,particles);session.watch();
            });
            context.waitFor(client->retained.get().complete(),180);
            context.runOnClient(client->{var session=retained.get();require(session.frozenWorld.color==WORLD_COLOR&&session.world.color!=WORLD_COLOR&&session.frozenOverlay.color==OVERLAY_COLOR&&session.overlay.color!=OVERLAY_COLOR,"Actual submitted and GUI-built frame remains detached after extraction-time source mutation");});
            context.takeScreenshot("create-vulkan-ponder-independent-world-and-overlay-native-submission");
            var firstWorld=retained.get().world;var firstOverlay=retained.get().overlay;
            var firstFrame=retained.get().selected;
            context.runOnClient(client->{var session=retained.get();String particles=client.particleEngine.countParticles();session.ui.seekToTime(25);require(!session.world.visible&&!session.overlay.visible,"Normal scheduled hide instructions deactivate both independent element types");host(client,session,particles);});
            context.waitFor(client->retained.get().hiddenBlitted,100);
            context.runOnClient(client->{var session=retained.get();require(session.hidden.frame().sceneElements().isEmpty()&&session.hidden.frame().overlayElements().isEmpty()&&firstFrame.frame().sceneElements().size()==1&&firstFrame.frame().overlayElements().size()==1,"Hidden native frame has no addon content while earlier detached frame stays unchanged");});
            context.takeScreenshot("create-vulkan-ponder-independent-elements-hidden-by-source-instructions");
            var oldTicks=new AtomicInteger();
            context.runOnClient(client->{
                var session=retained.get();String particles=client.particleEngine.countParticles();session.ui.seekToTime(0);session.scene.tick();session.scene.tick();
                require(session.world!=firstWorld&&session.overlay!=firstOverlay&&!session.scene.getElements().contains(firstWorld)&&!session.scene.getElements().contains(firstOverlay),"Source replay recreates independent elements through original instructions and removes stale instances");
                oldTicks.set(firstWorld.ticks);host(client,session,particles);session.watch();
            });
            context.waitFor(client->retained.get().complete(),180);
            context.runOnClient(client->{var session=retained.get();require(firstWorld.ticks==oldTicks.get()&&session.world.ticks>0&&session.frozenWorld.generation==2&&firstFrame.frame().sceneElements().getFirst()!=session.frozenWorld,"Replay ticks and native submission use the fresh addon owner while captured previous content remains detached");host(client,session,client.particleEngine.countParticles());});
            context.takeScreenshot("create-vulkan-ponder-independent-elements-fresh-source-replay");
            context.runOnClient(client->client.gui.setScreen(null));
            joined.getServer().runOnServer(ignored->{var owner=joined.getConnection().getServerLevel().getBlockEntity(P);require(owner instanceof BarrelBlockEntity barrel&&barrel.getItem(0).is(Items.DIAMOND)&&barrel.getItem(0).getCount()==7,"Actual host native barrel and original seven diamonds survive local extraction/submission/hide/replay");});
        }finally{context.runOnClient(client->{active=null;client.gui.setScreen(null);});}
    }
    private static void host(Minecraft client,Session session,String particles){require(client.level.getBlockState(P)==session.hostState&&client.level.getBlockEntity(P)==session.hostOwner&&client.particleEngine.countParticles().equals(particles),"Local scene operations preserve actual host block/BE identity and host particle engine");}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
