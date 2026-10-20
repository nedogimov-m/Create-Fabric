package com.simibubi.create.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.google.gson.GsonBuilder;
import com.simibubi.create.*;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.createmod.catnip.outliner.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.*;

/** Actual singleton cluster/item APIs, detached native frame, finite source fade, resource reload and disconnect. Authored1 UNRUN. */
public final class GlobalOutlinerRenderingTest implements FabricClientGameTest {
    private static final BlockPos P=new BlockPos(16,6,16);
    private static final String CLUSTER="create-native-global-cluster-fixture",ITEM="create-native-global-item-fixture",VALUE="create-native-valuebox-exclusion-fixture";
    private static final int GREEN=0x55EE77,PURPLE=0xAA77DD,CYAN=0x39CCE1,ORANGE=0xED9442,DIM_LIGHT=0x00A00060;
    private static final String NATIVE_BOX="create-native-source-cuboid-fixture",NATIVE_LINE="create-native-source-free-line-fixture";
    private static final Vec3 ITEM_POS=Vec3.atCenterOf(P).add(0,2,0);
    private static final AtomicReference<Set<Object>> KEPT_KEYS=new AtomicReference<>(Set.of());
    private static boolean keeperRegistered;
    /** A source API caller keeps only its own fixture keys; no world, BE, player or source stack is retained. */
    private static void ensureKeeper(){if(keeperRegistered)return;keeperRegistered=true;ClientTickEvents.END_CLIENT_TICK.register(client->{for(var key:KEPT_KEYS.get())Outliner.getInstance().keep(key);});}
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{if(!RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(Locale.ROOT).contains("vulkan"))throw new AssertionError("Singleton Outliner must use the actual native Vulkan backend");});
        // A genuine independent source Outliner is the same API ownership used by local Ponder scenes.
        var local=new Outliner();
        var captured=new AtomicReference<OutlinerRenderHandler.Frame>();
        var sample=new AtomicReference<ItemStack>();
        try(var world=context.worldBuilder().adjustSettings(creator->creator.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()){
            context.runOnClient(client->{local.showItem(ITEM,ITEM_POS,new ItemStack(AllBlocks.COGWHEEL,7));local.showCluster(CLUSTER,List.of(P,P.east()));});
            world.getServer().runCommand("fill 12 4 12 22 4 23 minecraft:smooth_quartz");world.getServer().runCommand("tp @p 17.0 5 22.5");
            context.waitFor(client->client.level!=null&&client.level.isLoaded(P)&&client.player.onGround()&&client.player.position().distanceToSqr(new Vec3(17,5,22.5))<.05,400);
            world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(P,AllBlocks.ANDESITE_CASING.defaultBlockState());level.setBlockAndUpdate(P.east(),AllBlocks.ANDESITE_CASING.defaultBlockState());var player=level.getServer().getPlayerList().getPlayers().getFirst();var stock=new ItemStack(AllBlocks.COGWHEEL,7);stock.set(DataComponents.CUSTOM_NAME,Component.literal("Seven original singleton-outline cogwheels"));player.getInventory().setItem(player.getInventory().getSelectedSlot(),stock);player.connection.send(player.getInventory().createInventoryUpdatePacket(player.getInventory().getSelectedSlot()));player.inventoryMenu.broadcastChanges();});
            context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.COGWHEEL.asItem())&&client.player.getMainHandItem().getCount()==7&&client.level.getBlockState(P).is(AllBlocks.ANDESITE_CASING)&&client.level.getBlockState(P.east()).is(AllBlocks.ANDESITE_CASING),220);
            look(context,Vec3.atCenterOf(P).add(.5,1,0));context.waitTicks(3);
            context.runOnClient(client->{sample.set(client.player.getMainHandItem().copy());install(client,sample.get());});
            context.waitFor(client->complete(OutlinerRenderHandler.latest()),5);
            context.runOnClient(client->{var frame=OutlinerRenderHandler.latest();captured.set(frame);assertFrame(frame);if(local.getOutlines().size()!=2)throw new AssertionError("Singleton extraction changed a real independent local Outliner owner");
                var source=Outliner.getInstance().getOutlines().get(ITEM).getOutline();var previous=source.extract(Vec3.ZERO,0).items().getFirst();
                sample.get().set(DataComponents.CUSTOM_NAME,Component.literal("Updated source-only visual sample"));
                var current=source.extract(Vec3.ZERO,0).items().getFirst();
                if(!current.stack().getHoverName().getString().equals("Updated source-only visual sample")||previous.stack().getHoverName().getString().equals("Updated source-only visual sample"))throw new AssertionError("Source ItemOutline lost the caller reference before extraction or mutated an existing extracted snapshot");
            });
            context.takeScreenshot("create-vulkan-singleton-outliner-native-item-model-and-textured-two-cell-cluster");
            var nativeGeometry=context.computeOnClient(client->{
                try(var resource=client.getResourceManager().getResource(OutlineRenderer.BLANK).orElseThrow().open()){
                    var hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(resource.readAllBytes()));
                    if(!hash.equals("8c6b0e152f49cb2d3ac29028fd752d869f6fad2585c2364534525c8cb612e9e7"))throw new AssertionError("Actual loaded native outline blank texture differs from the pinned source asset");
                    System.out.println("CREATE_OUTLINER_NATIVE_BLANK_SHA256 "+hash);
                }catch(Exception failure){throw new AssertionError("Actual native source outline texture must resolve",failure);}
                var watch=OutlinerNativeGeometryAudit.begin(GREEN,CYAN,ORANGE,DIM_LIGHT);var outliner=Outliner.getInstance();
                // Real public source APIs, same active singleton and unmodified native scene.
                outliner.showAABB(NATIVE_BOX,new AABB(P.west(2))).colored(CYAN).lineWidth(1/16f).lightmap(DIM_LIGHT).disableLineNormals().disableCull().withFaceTexture(()->Create.asResource("textures/special/checkerboard.png"));
                outliner.showLine(NATIVE_LINE,Vec3.atCenterOf(P.east(3)),Vec3.atCenterOf(P.east(3)).add(0,2,0)).colored(ORANGE).lineWidth(1/16f).lightmap(DIM_LIGHT).disableLineNormals();
                KEPT_KEYS.set(Set.of(ITEM,CLUSTER,VALUE,NATIVE_BOX,NATIVE_LINE));return watch;
            });
            context.waitFor(client->nativeGeometry.failure()!=null||nativeGeometry.completed()!=null,300);
            if(nativeGeometry.failure()!=null)throw new AssertionError(nativeGeometry.failure());
            var nativeReceipt=nativeGeometry.completed();if(nativeReceipt==null)throw new AssertionError("Every actual queued/deferred native source outline vertex receipt is required");
            System.out.println("CREATE_OUTLINER_NATIVE_FIDELITY "+new GsonBuilder().create().toJson(nativeReceipt));
            context.waitTicks(2);context.takeScreenshot("create-vulkan-singleton-outliner-closed-caps-native-light-normals-and-cull");
            context.runOnClient(client->{OutlinerNativeGeometryAudit.clear();Outliner.getInstance().remove(NATIVE_BOX);Outliner.getInstance().remove(NATIVE_LINE);KEPT_KEYS.set(Set.of(ITEM,CLUSTER,VALUE));});
            context.waitFor(client->OutlinerRenderHandler.latest().outlines().stream().noneMatch(geometry->(geometry.color()&0xFFFFFF)==CYAN||(geometry.color()&0xFFFFFF)==ORANGE),12);
            // Source explicit removal must stop only that singleton item, without invalidating prior detached native data.
            context.runOnClient(client->{KEPT_KEYS.set(Set.of(CLUSTER,VALUE));Outliner.getInstance().remove(ITEM);Outliner.getInstance().keep(CLUSTER);Outliner.getInstance().keep(VALUE);});
            context.waitFor(client->OutlinerRenderHandler.latest().items().isEmpty()&&cluster(OutlinerRenderHandler.latest()).isPresent(),4);
            context.runOnClient(client->{if(captured.get().items().size()!=1||captured.get().items().getFirst().state().isEmpty())throw new AssertionError("Removing source API entry mutated the already detached native item frame");if(OutlinerRenderHandler.latest().outlines().stream().anyMatch(geometry->(geometry.color()&0xFFFFFF)==PURPLE))throw new AssertionError("General singleton renderer submitted a ValueBox owned by its dedicated renderer");Outliner.getInstance().showItem(ITEM,ITEM_POS,sample.get());Outliner.getInstance().keep(CLUSTER);KEPT_KEYS.set(Set.of(ITEM,CLUSTER,VALUE));});
            context.waitFor(client->complete(OutlinerRenderHandler.latest()),4);
            // Stop all source keep calls before checking the genuine finite shrinking/fading lifetime.
            context.runOnClient(client->KEPT_KEYS.set(Set.of()));
            context.waitTicks(3);
            context.waitFor(client->{var frame=OutlinerRenderHandler.latest();return !frame.items().isEmpty()&&frame.items().getFirst().scale()>0&&frame.items().getFirst().scale()<1;},4);
            context.runOnClient(client->{var frame=OutlinerRenderHandler.latest();if(frame.items().getFirst().state().isEmpty())throw new AssertionError("Native source item fade lost its model instead of preserving source fading scale");});
            context.waitTicks(14);
            context.runOnClient(client->{if(Outliner.getInstance().getOutlines().containsKey(ITEM)||Outliner.getInstance().getOutlines().containsKey(CLUSTER)||!OutlinerRenderHandler.latest().items().isEmpty()||cluster(OutlinerRenderHandler.latest()).isPresent())throw new AssertionError("Singleton item/cluster retained stale frames beyond their genuine finite source fade lifetime");});
            var oldModel=context.computeOnClient(client->client.getModelManager().getBlockStateModelSet().get(AllBlocks.COGWHEEL.defaultBlockState()));var reload=context.computeOnClient(Minecraft::reloadResourcePacks);context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,700);reload.join();
            context.runOnClient(client->{if(client.getModelManager().getBlockStateModelSet().get(AllBlocks.COGWHEEL.defaultBlockState())==oldModel)throw new AssertionError("Actual native reload kept stale cogwheel models");install(client,sample.get());});context.waitFor(client->complete(OutlinerRenderHandler.latest()),5);
            context.runOnClient(client->assertFrame(OutlinerRenderHandler.latest()));context.takeScreenshot("create-vulkan-singleton-item-and-cluster-native-model-reload");
            world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var stock=level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem();if(!stock.is(AllBlocks.COGWHEEL.asItem())||stock.getCount()!=7||!level.getBlockState(P).is(AllBlocks.ANDESITE_CASING)||!level.getBlockState(P.east()).is(AllBlocks.ANDESITE_CASING)||!level.getEntitiesOfClass(ItemEntity.class,new AABB(P).inflate(8)).isEmpty())throw new AssertionError("Rendering the actual singleton API changed original native world or finite material custody");});
        }finally{context.runOnClient(client->{OutlinerNativeGeometryAudit.clear();KEPT_KEYS.set(Set.of());Outliner.getInstance().remove(ITEM);Outliner.getInstance().remove(CLUSTER);Outliner.getInstance().remove(VALUE);Outliner.getInstance().remove(NATIVE_BOX);Outliner.getInstance().remove(NATIVE_LINE);});}
        context.waitFor(client->client.level==null&&OutlinerRenderHandler.latest().items().isEmpty()&&OutlinerRenderHandler.latest().outlines().isEmpty(),200);
        if(local.getOutlines().size()!=2)throw new AssertionError("Native singleton disconnect cleanup cleared a separate source local Outliner");
    }
    private static void install(Minecraft client,ItemStack sample){
        ensureKeeper();KEPT_KEYS.set(Set.of(ITEM,CLUSTER,VALUE));
        var outliner=Outliner.getInstance();outliner.showCluster(CLUSTER,List.of(P,P.east())).colored(GREEN).withFaceTexture(()->Create.asResource("textures/special/checkerboard.png"));outliner.showItem(ITEM,ITEM_POS,sample);
        outliner.showOutline(VALUE,new ValueBox(Component.literal("Actual source ValueBox retains its dedicated renderer"),new AABB(0,0,0,1,1,1),P,client.level.getBlockState(P))).colored(PURPLE);
    }
    private static Optional<Outline.Geometry> cluster(OutlinerRenderHandler.Frame frame){return frame.outlines().stream().filter(geometry->(geometry.color()&0xFFFFFF)==GREEN).findFirst();}
    private static boolean complete(OutlinerRenderHandler.Frame frame){return frame.items().size()==1&&!frame.items().getFirst().state().isEmpty()&&cluster(frame).isPresent();}
    private static void assertFrame(OutlinerRenderHandler.Frame frame){
        var cluster=cluster(frame).orElseThrow();
        if(cluster.faces().size()!=10||cluster.segments().size()!=16||cluster.faces().stream().anyMatch(face->!face.texture().equals(Create.asResource("textures/special/checkerboard.png"))))throw new AssertionError("Actual adjacent source cluster must omit its internal face/edges and retain native textured visible geometry");
        if(frame.items().size()!=1||frame.items().getFirst().state().isEmpty()||!frame.items().getFirst().position().equals(ITEM_POS)||frame.items().getFirst().scale()<=0||frame.items().getFirst().scale()>1||frame.outlines().stream().anyMatch(geometry->!geometry.items().isEmpty()||(geometry.color()&0xFFFFFF)==PURPLE))throw new AssertionError("Actual singleton model was not detached, or source item position/fade/ValueBox exclusion changed");
    }
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
}
