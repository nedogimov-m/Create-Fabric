package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.decoration.slidingDoor.DoorControl;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.test.mixin.StationMapStorageAccessor;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.stream.StreamSupport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.*;
import net.minecraft.world.phys.*;

/** Actual track graph/station, real map native interaction, packet, disk save and fresh native codec. */
public final class StationMapGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private record Site(BlockPos rail,BlockPos target,BlockPos station){}
    private static Site site(GameTestHelper h){
        for(int dx=0;dx<=8;dx++)for(int dz=0;dz<=8;dz++){
            var rail=new BlockPos(4+dx,6,4+dz);var target=rail.east();var station=target.south(2);var source=h.absolutePos(station);var track=h.absolutePos(target);
            var shape=MapItemSavedData.createFresh(source.getX(),source.getZ(),(byte)0,true,false,h.getLevel().dimension());
            if(Math.abs(source.getX()-shape.centerX)<=61&&Math.abs(source.getZ()-shape.centerZ)<=61&&Math.abs(track.getX()-shape.centerX)<=61&&Math.abs(track.getZ()-shape.centerZ)<=61)return new Site(rail,target,station);
        }throw new AssertionError("No source station/map tile interior in the native test region");
    }
    private static void ready(GameTestHelper h){var at=site(h);for(int i=0;i<15;i++)h.assertTrue(LoadedEntityAccess.isReady(h.getLevel(),h.absolutePos(at.rail.east(i))),"Actual source track cells must be natively entity-ready");h.assertTrue(LoadedEntityAccess.isReady(h.getLevel(),h.absolutePos(at.station)),"Actual source station must be natively entity-ready");}
    private static StationBlockEntity station(GameTestHelper h){
        var at=site(h);var rail=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);
        for(int i=0;i<15;i++){h.setBlock(at.rail.east(i).below(),Blocks.STONE);h.setBlock(at.rail.east(i),rail);}
        for(int i=0;i<15;i++)h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(at.rail.east(i)),rail,false).complete(),"Actual native line must own a complete graph before station binding");
        h.setBlock(at.station.below(),Blocks.STONE);h.setBlock(at.station,AllBlocks.TRACK_STATION);var owner=h.getBlockEntity(at.station,StationBlockEntity.class);
        h.assertTrue(owner.setTarget(new TrackTargetingSelection(GlobalPos.of(h.getLevel().dimension(),h.absolutePos(at.target)),true,new Vec3(1,0,0),Optional.empty()))&&owner.getStation()!=null,"Actual station point binds to the real canonical railway graph");return owner;
    }
    private static List<MapDecoration> markers(MapItemSavedData data){return StreamSupport.stream(data.getDecorations().spliterator(),false).filter(marker->marker.type().value()==AllMapDecorationTypes.STATION_MAP_DECORATION.value()).toList();}
    private static ItemStack map(GameTestHelper h,StationBlockEntity station){return MapItem.create(h.getLevel(),station.getBlockPos().getX(),station.getBlockPos().getZ(),(byte)0,true,false);}
    private static net.minecraft.world.InteractionResult use(GameTestHelper h,ServerPlayer player,StationBlockEntity station,ItemStack map){player.setItemInHand(InteractionHand.MAIN_HAND,map);var pos=station.getBlockPos();return player.gameMode.useItemOn(player,h.getLevel(),map,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));}

    @GameTest(structure=STRUCTURE,maxTicks=200) public void actualStationMapInteractionNativePacketAndActualSavedFileRetainOneNamedMarker(GameTestHelper h){
        h.startSequence().thenWaitUntil(()->ready(h)).thenExecute(()->{
            var station=station(h);
            try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(station.getBlockPos()).add(0,0,2))){
                var player=joined.player();var map=map(h,station);var data=MapItem.getSavedData(map,h.getLevel());var id=map.get(DataComponents.MAP_ID);var storage=h.getLevel().getServer().getDataStorage();h.assertTrue(storage.get(MapItemSavedData.type(id))==data,"Actual native server-global map storage owns this exact map factory instance");storage.saveAndJoin();
                h.assertTrue(!data.isDirty(),"Actual owning native map storage save clears the newly-created map dirty flag");
                var used=use(h,player,station,map);h.assertTrue(used.consumesAction(),"Real survival map use reaches the station marker action: "+used);
                h.assertTrue(data.isDirty(),"Real station marker admission marks the actual saved map owner dirty");
                h.assertTrue(map.getCount()==1,"Actual station interaction retains exactly one filled map");
                h.assertTrue(player.getMainHandItem()==map,"Actual held map remains the same native inventory stack");
                h.assertTrue(player.containerMenu==player.inventoryMenu,"Actual filled-map station interaction never opens the station menu");
                var owner=(StationMapData)(Object)data;h.assertTrue(owner.create$stationMarkers().size()==1&&markers(data).size()==1&&owner.create$stationMarkers().getFirst().getTarget().equals(station.targeting().getGlobalPosition())&&markers(data).getFirst().name().orElseThrow().getString().equals(station.getStation().name()),"Native map owns exactly one station id at actual track target with actual name");
                data.tickCarriedBy(player,map,null);var packet=data.getUpdatePacket(id,player);h.assertTrue(packet instanceof ClientboundMapItemDataPacket,"Actual holding player receives real native map update packet");
                var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
                try{ClientboundMapItemDataPacket.STREAM_CODEC.encode(buffer,(ClientboundMapItemDataPacket)packet);var decoded=ClientboundMapItemDataPacket.STREAM_CODEC.decode(buffer);var client=MapItemSavedData.createForClient(data.scale,data.locked,data.dimension);decoded.applyToMap(client);h.assertTrue(markers(client).equals(markers(data))&&buffer.readableBytes()==0,"Actual native packet/registry codec applies exact station type, coordinates and component name to fresh client map data");}finally{buffer.release();}
                storage.saveAndJoin();var type=MapItemSavedData.type(id);var path=((StationMapStorageAccessor)(Object)storage).create$stationMapDataFile(type.id());
                CompoundTag raw;try{raw=storage.readTagFromDisk(path,type.dataFixType(),SharedConstants.getCurrentVersion().dataVersion().version());}catch(Exception problem){throw new IllegalStateException(problem);}
                var payload=raw.getCompoundOrEmpty("data");h.assertTrue(payload.getListOrEmpty("create:stations").size()==1,"Actual native disk map file contains the owned station list");var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);var fresh=MapItemSavedData.CODEC.parse(ops,payload).getOrThrow();
                h.assertTrue(((StationMapData)(Object)fresh).create$stationMarkers().equals(owner.create$stationMarkers())&&((StationMapData)(Object)fresh).create$stationMarkers().getFirst().getSource().equals(station.getBlockPos())&&markers(fresh).equals(markers(data))&&fresh.centerX==data.centerX&&fresh.centerZ==data.centerZ&&fresh.scale==data.scale&&Arrays.equals(fresh.colors,data.colors),"Actual disk read and fresh native map factory retain all ordinary map pixels/center/scale and exact station state");
                var locked=data.locked();var lockedFresh=MapItemSavedData.CODEC.parse(ops,MapItemSavedData.CODEC.encodeStart(ops,locked).getOrThrow()).getOrThrow();h.assertTrue(lockedFresh.locked&&((StationMapData)(Object)lockedFresh).create$stationMarkers().equals(owner.create$stationMarkers())&&markers(lockedFresh).equals(markers(data)),"Actual native locked copy retains complete persistent station owner alongside copied decoration");
                h.assertTrue(use(h,player,station,map).consumesAction()&&owner.create$stationMarkers().isEmpty()&&markers(data).isEmpty()&&map.getCount()==1,"Second real native map interaction removes that same marker without map debit");
            }
        }).thenSucceed();
    }

    @GameTest(structure=STRUCTURE,maxTicks=200) public void nativeMapColumnUpdateRefreshesRealRenamedStationAndRemovesRealBrokenStation(GameTestHelper h){
        h.startSequence().thenWaitUntil(()->ready(h)).thenExecute(()->{
            var station=station(h);try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(station.getBlockPos()).add(0,0,2))){
                var player=joined.player();var map=map(h,station);var data=MapItem.getSavedData(map,h.getLevel());h.assertTrue(use(h,player,station,map).consumesAction(),"Actual native source map marker admitted");var target=((StationMapData)(Object)data).create$stationMarkers().getFirst().getTarget();
                h.assertTrue(station.configure(player,"Updated native station",false,DoorControl.ALL),"Real permitted station configuration changes actual canonical name");data.checkBanners(h.getLevel(),target.getX(),target.getZ());
                h.assertTrue(markers(data).size()==1&&markers(data).getFirst().name().orElseThrow().getString().equals("Updated native station")&&((StationMapData)(Object)data).create$stationMarkers().size()==1,"Actual native map column refresh replaces name with one marker and one tracked count");
                h.getLevel().setBlockAndUpdate(station.getBlockPos(),Blocks.AIR.defaultBlockState());data.checkBanners(h.getLevel(),target.getX(),target.getZ());h.assertTrue(markers(data).isEmpty()&&((StationMapData)(Object)data).create$stationMarkers().isEmpty(),"Actual destroyed station is removed from native decoration and persistent map owner on normal column update");
            }
        }).thenSucceed();
    }

    @GameTest(structure=STRUCTURE,maxTicks=200) public void nativeTrackedDecorationLimitRejectsAdditionButAllowsRemovingExistingStation(GameTestHelper h){
        h.startSequence().thenWaitUntil(()->ready(h)).thenExecute(()->{
            var station=station(h);try(var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(station.getBlockPos()).add(0,0,2))){
                var player=joined.player();var map=map(h,station);var data=MapItem.getSavedData(map,h.getLevel());var owner=(StationMapData)(Object)data;
                for(int i=0;i<256;i++)owner.addStationMarker(new StationMarker(station.getBlockPos(),new BlockPos(data.centerX+i%16-8,64,data.centerZ+i/16-8),Component.literal("Actual limit "+i)));
                h.assertTrue(data.isTrackedCountOverLimit(255)&&!data.isTrackedCountOverLimit(256)&&owner.create$stationMarkers().size()==256&&markers(data).size()==256,"Native26.3 strict-greater counter and actual owner/decorations prove exactly256 before source-limit interaction");
                var denied=use(h,player,station,map);h.assertTrue(!denied.consumesAction(),"At source capacity256 the actual survival station action refuses a new station: "+denied);
                h.assertTrue(owner.create$stationMarkers().size()==256&&markers(data).size()==256&&map.getCount()==1,"Actual capacity refusal retains all256 markers and the same single map");
                h.assertTrue(player.containerMenu==player.inventoryMenu,"Actual capacity refusal does not open station UI");
                var second=map(h,station);var other=MapItem.getSavedData(second,h.getLevel());var otherOwner=(StationMapData)(Object)other;h.assertTrue(use(h,player,station,second).consumesAction(),"Second actual native map adds actual station first");
                for(int i=0;i<255;i++)otherOwner.addStationMarker(new StationMarker(station.getBlockPos(),new BlockPos(other.centerX+i%16-8,64,other.centerZ+i/16-8),Component.literal("Actual limit "+i)));
                h.assertTrue(other.isTrackedCountOverLimit(255)&&!other.isTrackedCountOverLimit(256)&&otherOwner.create$stationMarkers().size()==256&&markers(other).size()==256,"Second actual map owns exactly256 tracked station decorations before removal");
                h.assertTrue(use(h,player,station,second).consumesAction(),"Clicking an existing actual station removes it before the full-capacity gate");
                h.assertTrue(!other.isTrackedCountOverLimit(255)&&other.isTrackedCountOverLimit(254)&&otherOwner.create$stationMarkers().size()==255&&markers(other).size()==255&&second.getCount()==1,"Actual existing-marker removal releases exactly one tracked decoration without map debit at full source capacity");
            }
        }).thenSucceed();
    }

    @GameTest public void sourceMarkerCodecUpgradesOriginalPositionsAndFlattenedNameWithoutChangingIdentity(GameTestHelper h){
        var source=new CompoundTag();var at=new CompoundTag();at.putInt("X",17);at.putInt("Y",66);at.putInt("Z",-8);source.put("source",at);var target=at.copy();target.putInt("X",23);source.put("target",target);source.putString("name","{\"text\":\"Original station\",\"color\":\"gold\"}");var before=source.copy();
        var marker=StationMarker.load(source,h.getLevel().registryAccess());var fresh=StationMarker.load(marker.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
        h.assertTrue(source.equals(before)&&marker.equals(fresh)&&fresh.getSource().equals(marker.getSource())&&marker.getSource().equals(new BlockPos(17,66,-8))&&marker.getTarget().equals(new BlockPos(23,66,-8))&&marker.getId().equals("create:station-23,66,-8")&&marker.getName().getString().equals("Original station")&&marker.getName().getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(net.minecraft.ChatFormatting.GOLD)),"Actual native marker codec keeps genuine source/target/name/style/id across original3955 field shapes and modern typed label save");h.succeed();
    }
}
