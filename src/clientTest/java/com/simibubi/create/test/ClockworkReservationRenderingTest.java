package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.*;
import java.util.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Proves an already tracking real client receives the post-reservation full hand snapshots. */
final class ClockworkReservationRenderingTest {
    private static final BlockPos B=new BlockPos(7007,5,5);
    private static final class ObserveReservation extends ClockworkBearingBlockEntity {
        private final ServerPlayer watcher;private final AtomicInteger trackedBeforeBind;
        private final Map<UUID,ControlledContraptionEntity> reservations=new HashMap<>();
        ObserveReservation(BlockPos pos,BlockState state,ServerPlayer watcher,AtomicInteger count){super(pos,state);this.watcher=watcher;trackedBeforeBind=count;}
        private void observeLoad(Entity entity,ServerLevel level){
            var reservation=reservations.get(entity.getUUID());if(reservation!=entity)return;
            if(level!=getLevel()||reservation.getContraption()!=null||!PlayerLookup.tracking(reservation).contains(watcher))throw new AssertionError("Real nearby player must already track the empty reservation before ownership binds");
            trackedBeforeBind.incrementAndGet();
        }
        @Override protected ControlledContraptionEntity createReservation(ServerLevel level,ClockworkContraption hand){
            var owner=new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION,level){
                @Override public void setContraption(Contraption structure){
                    if(structure!=null&&getContraption()!=structure)throw new AssertionError("Actual native setter must observe the original structure already bound by the pure ownership phase");
                    super.setContraption(structure);
                }
            };
            reservations.put(owner.getUUID(),owner);return owner;
        }
    }
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 7007.5 8 10.5");
        context.waitFor(client->Math.abs(client.player.getX()-7007.5)<.1&&client.level.isLoaded(B.above(2)),400);
        NativeScreenshotReadiness.beforeFloor(context, world, new BlockPos(7002, 3, 0), new BlockPos(7012, 3, 15), new BlockPos(7002, 4, 0), new BlockPos(7012, 4, 15));
        server.runCommand("fill 7002 3 0 7012 3 15 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(context, world, new BlockPos(7002, 3, 0), new BlockPos(7012, 3, 15));
        var hourId=new AtomicReference<UUID>();var minuteId=new AtomicReference<UUID>();var count=new AtomicInteger();
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var state=AllBlocks.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP);
            level.setBlockAndUpdate(B,state);level.setBlockAndUpdate(B.above(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(B.above(2),Blocks.BARREL.defaultBlockState());
            ((BarrelBlockEntity)level.getBlockEntity(B.above(2))).setItem(0,new ItemStack(Items.DIAMOND,7));
            var bearing=new ObserveReservation(B,state,level.getServer().getPlayerList().getPlayers().getFirst(),count);bearing.setLevel(level);level.setBlockEntity(bearing);
        });
        NativeContraptionFixtures.awaitCaptureReady(context,world,300,B.above(),B.above(2));
        // Native addEntity pairs immediately only when this player's real chunk tracking is ready.
        var chunkTracked=new AtomicBoolean();
        for(int i=0;i<300&&!chunkTracked.get();i++){
            server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var watcher=level.getServer().getPlayerList().getPlayers().getFirst();chunkTracked.set(level.getChunkSource().chunkMap.isChunkTracked(watcher,B.getX()>>4,B.getZ()>>4));});
            if(!chunkTracked.get())context.waitTicks(1);
        }
        if(!chunkTracked.get())throw new AssertionError("Native player chunk tracking never became ready before reservations");
        var observing=new AtomicReference<ObserveReservation>();
        ServerEntityEvents.ENTITY_LOAD.register((entity,level)->{var observer=observing.get();if(observer!=null)observer.observeLoad(entity,level);});
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var bearing=(ObserveReservation)level.getBlockEntity(B);
            observing.set(bearing);
            try{
                bearing.assemble();
                if(!bearing.isRunning()||bearing.getHourHand()==null||bearing.getMinuteHand()==null||count.get()!=2)throw new AssertionError("Both native reservations must be observed while already tracked; observed="+count.get()+", assembly="+(bearing.getLastAssemblyException()==null?"none":bearing.getLastAssemblyException().component.getString()));
                hourId.set(bearing.getHourHand().getUUID());minuteId.set(bearing.getMinuteHand().getUUID());
            }finally{observing.set(null);}
        });
        // No teleport, tracking restart, resource reload or manual sendFull may rescue this assertion.
        context.waitFor(client->{var a=find(client.level,hourId.get());var b=find(client.level,minuteId.get());return a!=null&&b!=null&&a.getContraption() instanceof ClockworkContraption h&&h.handType==ClockworkContraption.HandType.HOUR&&h.getBlocks().size()==1&&b.getContraption() instanceof ClockworkContraption m&&m.handType==ClockworkContraption.HandType.MINUTE&&m.getBlocks().size()==1;},300);
        context.getInput().lookAt(B.above());world.getConnection().waitForChunksRender();context.waitTicks(5);
        context.takeScreenshot("create-vulkan-clockwork-already-tracking-reservations-full-snapshots");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var bearing=(ClockworkBearingBlockEntity)level.getBlockEntity(B);if(!bearing.disassemble()||((BarrelBlockEntity)level.getBlockEntity(B.above(2))).getItem(0).getCount()!=7)throw new AssertionError("Native tracked reservation test lost restored cargo");});
    }
    private static ControlledContraptionEntity find(net.minecraft.client.multiplayer.ClientLevel level,UUID id){for(var entity:level.entitiesForRendering())if(entity instanceof ControlledContraptionEntity owner&&owner.getUUID().equals(id))return owner;return null;}
}
