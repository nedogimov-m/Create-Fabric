package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.*;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.*;
import net.minecraft.world.phys.*;

/** Real map interaction/network/held+frame renderer/reload on native Vulkan. Authored, UNRUN. */
public final class StationMapRenderingTest implements FabricClientGameTest {
    private static final BlockPos RAIL=new BlockPos(0,4,8),TARGET=RAIL.east(4),STATION=TARGET.south(2),FRAME=new BlockPos(8,5,11);
    private static final String INITIAL="Vulkan Central",RENAMED="Vulkan Reloaded 26";
    private static final String SOURCE_IMAGE_SHA256="14ff606c8a0cc07309a2b4dc4316f9d9a3bb51addfafa76865d4980624e68b87";
    private static final Path REPORT=Path.of(System.getProperty("create.test.stationMapReport","build/reports/create-station-map/native-client-runtime.json")).toAbsolutePath();
    private record MapInfo(MapId id,byte x,byte y,byte rotation) {}
    private record Phase(String name,StationMapRenderAudit.FrameReceipt frame,String screenshot,String screenshotSHA256,StationMapPixelAudit.Receipt visibleLabelPixels) {}
    private static final class Report {
        String status="RUNNING",backend,device,sourceImageSHA256,failure;boolean sodiumLoaded;
        final List<StationMapRenderAudit.PacketReceipt> packets=new ArrayList<>();final List<Phase> phases=new ArrayList<>();
        boolean actualReloadCompleted,actualAtlasSpriteRecreated;
    }
    @Override public void runTest(ClientGameTestContext context){
        var report=new Report();var priorCamera=new AtomicReference<CameraType>();var priorHudHidden=new AtomicReference<Boolean>();
        try(var world=context.worldBuilder().adjustSettings(settings->settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()){
            context.runOnClient(client->{
                var actual=RenderSystem.getDevice().getDeviceInfo();report.backend=actual.backendName();report.device=actual.name();report.sodiumLoaded=FabricLoader.getInstance().isModLoaded("sodium");
                require(report.backend.toLowerCase(Locale.ROOT).contains("vulkan"),"Actual Vulkan backend required: "+report.backend);require(!report.sodiumLoaded,"Requested actual no-Sodium runtime required");
                require(client.level!=null&&client.player!=null,"Actual joined native client world/player required before map items");
                priorCamera.set(client.options.getCameraType());client.options.setCameraType(CameraType.FIRST_PERSON);
                priorHudHidden.set(client.gui.hud.isHidden());if(client.gui.hud.isHidden())client.gui.hud.toggle();
                try(var stream=client.getResourceManager().getResource(Create.asResource("textures/map/decorations/station.png")).orElseThrow().open()){
                    report.sourceImageSHA256=sha256(stream);require(report.sourceImageSHA256.equals(SOURCE_IMAGE_SHA256),"Actual loaded station resource must equal the pinned source PNG");
                }catch(IOException error){throw new UncheckedIOException(error);}
            });write(report);
            var server=world.getServer();server.runCommand("fill -4 3 -4 20 3 18 minecraft:smooth_quartz");server.runCommand("tp @p 4.5 4 12.5");
            context.waitFor(client->client.level.isLoaded(RAIL)&&client.level.isLoaded(RAIL.east(14))&&client.level.isLoaded(STATION)&&Math.abs(client.player.getX()-4.5)<.1&&Math.abs(client.player.getZ()-12.5)<.1,300);
            waitReady(context,world);
            var mapStack=new AtomicReference<ItemStack>();var info=new AtomicReference<MapInfo>();
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();
                var rail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
                for(int i=0;i<15;i++)level.setBlockAndUpdate(RAIL.east(i),rail);
                for(int i=0;i<15;i++)require(TrackPropagator.reconcile(level,RAIL.east(i),rail,false).complete(),"Actual native line must own its complete canonical graph");
                level.setBlockAndUpdate(STATION,AllBlocks.TRACK_STATION.defaultBlockState());var owner=station(level);
                require(owner.setTarget(new TrackTargetingSelection(GlobalPos.of(level.dimension(),TARGET),true,new Vec3(1,0,0),Optional.empty()))&&owner.getStation()!=null,"Actual station must bind the real source track graph");
                require(owner.configure(player,INITIAL,false,DoorControl.ALL),"Real permitted source station configuration must set actual canonical label");
                var stack=MapItem.create(level,STATION.getX(),STATION.getZ(),(byte)0,true,false);var data=MapItem.getSavedData(stack,level);
                require(Math.abs(STATION.getX()-data.centerX)<=61&&Math.abs(STATION.getZ()-data.centerZ)<=61&&Math.abs(TARGET.getX()-data.centerX)<=61&&Math.abs(TARGET.getZ()-data.centerZ)<=61,"Actual station/track must lie inside the native source map tile");
                player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.MAIN_HAND,stack);mapStack.set(stack);info.set(new MapInfo(stack.get(DataComponents.MAP_ID),(byte)0,(byte)0,(byte)0));
            });
            var firstPacket=context.computeOnClient(client->StationMapRenderAudit.expectPacket(info.get().id,INITIAL));
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();var stack=mapStack.get();var owner=station(level);
                var result=player.gameMode.useItemOn(player,level,stack,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(STATION),Direction.UP,STATION,false));
                require(result.consumesAction()&&stack.getCount()==1&&player.getMainHandItem()==stack&&player.containerMenu==player.inventoryMenu,"Actual native map toggle must retain item and avoid station UI");
                var data=MapItem.getSavedData(stack,level);var marker=marker(data);require(marker.name().orElseThrow().getString().equals(owner.getStation().name())&&owner.getStation().name().equals(INITIAL),"Native saved map label must come from actual canonical station");
                info.set(new MapInfo(info.get().id,marker.x(),marker.y(),marker.rot()));sendActualPacket(level,stack);
            });
            waitPacket(context,world,mapStack.get(),firstPacket,info.get(),INITIAL,report);
            context.waitFor(client->client.player.getMainHandItem().is(Items.FILLED_MAP)&&info.get().id.equals(client.player.getMainHandItem().get(DataComponents.MAP_ID))&&client.player.getOffhandItem().isEmpty()&&client.gui.screen()==null,200);
            context.getInput().lookAt(new BlockPos(4,0,12));world.getConnection().waitForChunksRender();context.waitTicks(12);
            var held=capture(context,info.get(),INITIAL,false,"held-native-packet",report);require(!held.onFrame(),"Actual held map must use the native non-frame path");

            var frameId=new AtomicInteger();server.runCommand("fill 6 4 10 10 8 10 minecraft:white_concrete");
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();var map=mapStack.get();
                var frame=new ItemFrame(level,FRAME,Direction.SOUTH);frame.setItem(map.copy());frame.setRotation(0);require(frame.survives()&&level.addFreshEntity(frame),"Actual world-owned native item frame must hold real map value");frameId.set(frame.getId());
                int carried=(player.getInventory().getSelectedSlot()+1)%9;player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.getInventory().setItem(carried,map);
            });server.runCommand("tp @p 8.5 4 13.5");
            context.waitFor(client->client.level.getEntity(frameId.get()) instanceof ItemFrame frame&&info.get().id.equals(frame.getItem().get(DataComponents.MAP_ID))&&client.player.getMainHandItem().isEmpty()&&Math.abs(client.player.getX()-8.5)<.1&&Math.abs(client.player.getZ()-13.5)<.1,300);
            context.getInput().lookAt(FRAME);world.getConnection().waitForChunksRender();context.waitTicks(8);
            var beforeReload=capture(context,info.get(),INITIAL,true,"frame-native-atlas",report);require(beforeReload.onFrame(),"Actual world item frame must use native onFrame filtering");
            var spriteBefore=new AtomicReference<net.minecraft.client.renderer.texture.TextureAtlasSprite>();
            context.runOnClient(client->spriteBefore.set(lastSample.actualSprite()));

            var renamePacket=context.computeOnClient(client->StationMapRenderAudit.expectPacket(info.get().id,RENAMED));
            server.runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();var owner=station(level);
                require(owner.configure(player,RENAMED,false,DoorControl.ALL),"Actual native station permission/configuration must rename canonical marker source");
                var data=MapItem.getSavedData(mapStack.get(),level);var target=owner.targeting().getPositionForMapMarker();data.checkBanners(level,target.getX(),target.getZ());
                require(marker(data).name().orElseThrow().getString().equals(RENAMED),"Actual normal native map column update must refresh source label");sendActualPacket(level,mapStack.get());
            });
            waitPacket(context,world,mapStack.get(),renamePacket,info.get(),RENAMED,report);capture(context,info.get(),RENAMED,true,"frame-real-rename-packet",report);
            var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone(),600);context.runOnClient(client->reload.join());report.actualReloadCompleted=true;
            capture(context,info.get(),RENAMED,true,"frame-after-native-resource-reload",report);
            context.runOnClient(client->{
                report.actualAtlasSpriteRecreated=lastSample.actualSprite()!=null&&lastSample.actualSprite()!=spriteBefore.get();require(report.actualAtlasSpriteRecreated,"Actual native resource reload must recreate the station atlas sprite, not reuse retired extracted sprite");
                var data=client.level.getMapData(info.get().id);require(data!=null&&marker(data).name().orElseThrow().getString().equals(RENAMED),"Actual client map/network ownership must survive resource reload");
            });
            require(report.packets.size()==2&&report.phases.size()==4,"Both real packets and every actual held/frame/rename/reload phase require native render receipts/screenshots");report.status="PASS";
        }catch(Throwable failure){report.status="FAIL";var text=new StringWriter();failure.printStackTrace(new PrintWriter(text));report.failure=text.toString();if(failure instanceof AssertionError assertion)throw assertion;throw new AssertionError(failure);}
        finally{context.runOnClient(client->{StationMapRenderAudit.clear();if(priorCamera.get()!=null)client.options.setCameraType(priorCamera.get());if(priorHudHidden.get()!=null&&client.gui.hud.isHidden()!=priorHudHidden.get())client.gui.hud.toggle();});write(report);}
    }
    private StationMapRenderAudit.Sample lastSample;
    private StationMapRenderAudit.FrameReceipt capture(ClientGameTestContext context,MapInfo map,String name,boolean onFrame,String phase,Report report){
        var sample=context.computeOnClient(client->StationMapRenderAudit.begin(phase,map.id,name,onFrame));lastSample=sample;
        context.waitFor(client->sample.failure()!=null||sample.completed()!=null,300);if(sample.failure()!=null)throw new AssertionError(sample.failure());
        var receipt=context.computeOnClient(client->sample.completed());require(receipt!=null&&receipt.x()==map.x&&receipt.y()==map.y&&receipt.rotation()==map.rotation,"Actual native renderer must retain exact server-packet coordinates/rotation");
        context.waitFor(client->client.gui.overlay()==null&&client.gui.screen()==null,200);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.waitTicks(2);var screenshot=context.takeScreenshot("create-vulkan-station-map-"+phase);String hash;try(var bytes=Files.newInputStream(screenshot)){hash=sha256(bytes);}catch(IOException error){throw new UncheckedIOException(error);}
        int labelWidth=context.computeOnClient(client->client.font.width(net.minecraft.network.chat.Component.literal(name)));
        var pixels=StationMapPixelAudit.inspect(screenshot,name,receipt.x(),receipt.y(),labelWidth);
        report.phases.add(new Phase(phase,receipt,screenshot.toAbsolutePath().toString(),hash,pixels));write(report);return receipt;
    }
    private static void waitPacket(ClientGameTestContext context,TestSingleplayerContext world,ItemStack stack,StationMapRenderAudit.NetworkSample sample,MapInfo map,String name,Report report){
        // Native HoldingPlayer.nextUpdatePacket permits decoration updates on every fifth request.
        // Request on actual ticks, retaining the native dirty/cadence and real network apply path.
        for(int tick=0;tick<300&&sample.completed()==null;tick++){
            world.getServer().runOnServer(ignored->sendActualPacket(world.getConnection().getServerLevel(),stack));
            context.waitTicks(1);
        }
        var actual=sample.completed();require(actual!=null,"Actual native holding-player update packet did not arrive within300 ticks");require(actual.actualClientDataMatches()&&actual.actualPacketStationCount()==1&&actual.label().equals(name)&&actual.x()==map.x&&actual.y()==map.y&&actual.rotation()==map.rotation&&actual.renderOnFrame(),"Actual native network packet must apply exact one source station to real client map data");report.packets.add(actual);write(report);
    }
    private static void waitReady(ClientGameTestContext context,TestSingleplayerContext world){
        var ready=new AtomicBoolean();for(int i=0;i<300&&!ready.get();i++){
            world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();boolean all=LoadedEntityAccess.isReady(level,STATION);for(int x=0;x<15;x++)all&=LoadedEntityAccess.isReady(level,RAIL.east(x));ready.set(all);});if(!ready.get())context.waitTicks(1);
        }require(ready.get(),"Actual native track/station cells must be entity-ready before canonical graph binding");
    }
    private static StationBlockEntity station(ServerLevel level){var entity=level.getBlockEntity(STATION);require(entity instanceof StationBlockEntity,"Actual world station block entity required");return (StationBlockEntity)entity;}
    private static MapDecoration marker(MapItemSavedData data){var rows=new ArrayList<MapDecoration>();for(var row:data.getDecorations())if(row.type().value()==AllMapDecorationTypes.STATION_MAP_DECORATION.value())rows.add(row);require(rows.size()==1,"Actual native map must contain exactly one source station decoration");return rows.getFirst();}
    private static void sendActualPacket(ServerLevel level,ItemStack stack){var player=level.getServer().getPlayerList().getPlayers().getFirst();var data=MapItem.getSavedData(stack,level);data.tickCarriedBy(player,stack,null);var packet=data.getUpdatePacket(stack.get(DataComponents.MAP_ID),player);if(packet==null)return;require(packet instanceof ClientboundMapItemDataPacket,"Actual native holding-player update packet required");player.connection.send(packet);}
    private static String sha256(InputStream stream){try{var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int count;while((count=stream.read(buffer))!=-1)digest.update(buffer,0,count);return HexFormat.of().formatHex(digest.digest());}catch(Exception error){throw new IllegalStateException(error);}}
    private static void write(Report report){try{Files.createDirectories(REPORT.getParent());Files.writeString(REPORT,new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");}catch(IOException error){throw new UncheckedIOException(error);}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
