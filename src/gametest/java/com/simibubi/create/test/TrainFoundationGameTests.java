package com.simibubi.create.test;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.trains.TrainConfig;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.*;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Proposed genuine native config transport and actual physical-rail wheel migration regressions. */
public final class TrainFoundationGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos R=new BlockPos(3,6,4);
    private record NativeWheel(TrackGraph graph,TravellingPoint point){}
    private static NativeWheel wheel(GameTestHelper h){var state=AllBlocks.TRACK.defaultBlockState().setValue(TrackBlock.SHAPE,TrackShape.XO);for(int i=0;i<13;i++){h.setBlock(R.east(i).below(),Blocks.STONE);h.setBlock(R.east(i),state);}for(int i=0;i<13;i++)h.assertTrue(TrackPropagator.reconcile(h.getLevel(),h.absolutePos(R.east(i)),state,false).complete(),"Actual physical train migration rails must reconcile completely");var position=TrackGraphHelper.getGraphLocationAt(h.getLevel(),h.absolutePos(R.east(6)),Direction.AxisDirection.POSITIVE,new Vec3(1,0,0));h.assertTrue(position!=null,"Genuine native target resolves actual source rail metadata");var first=position.graph.locateNode(position.edge.getFirst());var second=position.graph.locateNode(position.edge.getSecond());return new NativeWheel(position.graph,new TravellingPoint(first,second,position.graph.getConnectionsFrom(first).get(second),position.position,false));}
    @GameTest(structure=STRUCTURE) public void nativeTrainConfigurationCodecPreservesAllSourceValues(GameTestHelper h){var values=new TrainConfig.Values(false,128,512,200,.5f,24,11,2,48,22,4);var encoded=TrainConfig.Values.CODEC.encodeStart(JsonOps.INSTANCE,values).getOrThrow();h.assertTrue(TrainConfig.Values.CODEC.parse(JsonOps.INSTANCE,encoded).getOrThrow().equals(values),"Actual native config codec retains all source train controls, speed, acceleration and assembly bounds");h.succeed();}
    @GameTest(structure=STRUCTURE) public void nativeTrainConfigurationRejectsMalformedDynamicValues(GameTestHelper h){var encoded=TrainConfig.Values.CODEC.encodeStart(JsonOps.INSTANCE,TrainConfig.DEFAULTS).getOrThrow().getAsJsonObject();encoded.addProperty("maxBogeyCount",201);h.assertTrue(TrainConfig.Values.CODEC.parse(JsonOps.INSTANCE,encoded).isError(),"Actual source bogey bound rejects malformed saved config");encoded.remove("maxBogeyCount");encoded.addProperty("trainAcceleration",-1);h.assertTrue(TrainConfig.Values.CODEC.parse(JsonOps.INSTANCE,encoded).isError(),"Actual negative acceleration rejected before native ticking");h.succeed();}
    @GameTest(structure=STRUCTURE) public void actualNativeConfigurationPayloadRoundTripsWithoutClientServerStateMutation(GameTestHelper h){var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());try{var before=TrainConfig.server();var values=new TrainConfig.Values(true,48,256,40,.9f,30,15,4,60,30,5);TrainConfig.Values.STREAM_CODEC.encode(buffer,values);h.assertTrue(TrainConfig.Values.STREAM_CODEC.decode(buffer).equals(values)&&buffer.readableBytes()==0&&TrainConfig.server()==before,"Genuine native transport is exact and never mutates authoritative server config by decoding");}finally{buffer.release();}h.succeed();}
    @GameTest(structure=STRUCTURE) public void actualWheelMigratesToTheSameGenuineDiscoveredRail(GameTestHelper h){var actual=wheel(h);var wheel=actual.point();var graph=actual.graph();var migrated=new TrainMigration(wheel).tryMigratingTo(graph);h.assertTrue(migrated!=null&&migrated.graph==graph&&migrated.edge.getFirst().equals(wheel.node1.getLocation())&&migrated.edge.getSecond().equals(wheel.node2.getLocation())&&Math.abs(migrated.position-wheel.position)<1e-9,"Actual physical rail wheel preserves both genuine endpoints and distance");h.succeed();}
    @GameTest(structure=STRUCTURE) public void nativeMigrationPersistencePreservesOriginalDimensionPaletteAndFallback(GameTestHelper h){var actual=wheel(h);var wheel=actual.point();var dimensions=new DimensionPalette();var encoded=new TrainMigration(wheel).write(dimensions);var palette=new net.minecraft.nbt.CompoundTag();dimensions.write(palette);var restored=TrainMigration.read(encoded,DimensionPalette.read(palette));var migrated=restored.tryMigratingTo(actual.graph());h.assertTrue(migrated!=null&&migrated.edge.getFirst().dimension.equals(h.getLevel().dimension())&&Math.abs(migrated.position-wheel.position)<1e-9,"Actual source-shaped migration NBT and real native dimension palette roundtrip");h.succeed();}
    @GameTest(structure=STRUCTURE) public void nativeMigrationRejectsNonfinitePersistedWheelState(GameTestHelper h){var dimensions=new DimensionPalette();var encoded=new TrainMigration(wheel(h).point()).write(dimensions);encoded.putDouble("Position",Double.NaN);boolean rejected=false;try{TrainMigration.read(encoded,dimensions);}catch(IllegalArgumentException invalid){rejected=true;}h.assertTrue(rejected,"Actual corrupt migration must fail before graph/wheel publication");h.succeed();}
}
