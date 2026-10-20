package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.*;

final class ChainConveyorRenderingTest {
    private static final BlockPos P=new BlockPos(2303,8,5),Q=P.east(10);
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 2308 12 12");
        context.waitFor(client->Math.abs(client.player.getX()-2308.5)<.1,300);
        var ready=new AtomicBoolean();
        for(int i=0;i<300&&!ready.get();i++){server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();ready.set(level.getChunkSource().getChunkNow(P.getX()>>4,0)!=null&&level.getChunkSource().getChunkNow(Q.getX()>>4,0)!=null);});if(!ready.get())context.waitTicks(1);}
        if(!ready.get())throw new AssertionError("Chain scene chunks unavailable");
        context.waitFor(client->client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(P.getX()>>4,P.getZ()>>4))&&client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(Q.getX()>>4,Q.getZ()>>4)),300);
        server.runCommand("fill 2298 3 0 2318 3 15 minecraft:smooth_quartz");
        server.runCommand("fill 2301 7 8 2305 7 11 minecraft:smooth_quartz");
        server.runCommand("fill 2311 7 8 2315 7 11 minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(P,AllBlocks.CHAIN_CONVEYOR.defaultBlockState());level.setBlockAndUpdate(Q,AllBlocks.CHAIN_CONVEYOR.defaultBlockState());level.setBlockAndUpdate(P.below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.UP));((KineticBlockEntity)level.getBlockEntity(P.below())).setGeneratedSpeed(0);var player=level.getServer().getPlayerList().getPlayers().getFirst();player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_CHAIN,10));player.inventoryMenu.broadcastFullState();});
        server.runCommand("tp @p 2303.5 8 9.5");server.runCommand("gamemode survival @p");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().is(Items.IRON_CHAIN),200);look(context,Vec3.atCenterOf(P));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),200);context.getInput().pressKey(options->options.keyUse);context.waitTicks(3);
        server.runCommand("tp @p 2313.5 8 9.5");context.waitFor(client->Math.abs(client.player.getX()-2313.5)<.1,200);look(context,Vec3.atCenterOf(Q));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(Q),200);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.level.getBlockEntity(P) instanceof ChainConveyorBlockEntity chain&&chain.getConnections().contains(Q.subtract(P)),200);
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();if(player.getMainHandItem().getCount()!=6)throw new AssertionError("Actual chain clicks did not consume four chains");});
        manualParcelInteraction(context,world);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var first=(ChainConveyorBlockEntity)level.getBlockEntity(P);var second=(ChainConveyorBlockEntity)level.getBlockEntity(Q);var box=PackageItem.containing(List.of(new ItemStack(Items.DIAMOND,7)));PackageItem.addAddress(box,"Market");first.addLoopingPackage(new ChainConveyorPackage(45,box));first.addTravellingPackage(new ChainConveyorPackage(3,box),Q.subtract(P));second.addLoopingPackage(new ChainConveyorPackage(180,box));((KineticBlockEntity)level.getBlockEntity(P.below())).setGeneratedSpeed(64);});
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 2308 11 14");context.waitFor(client->client.player.isSpectator()&&client.level.getBlockEntity(Q) instanceof ChainConveyorBlockEntity chain&&chain.getSpeed()==64,200);look(context,new Vec3(2308,8,5));world.getConnection().waitForChunksRender();context.waitTicks(10);
        try {
            context.waitFor(client->client.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,P.above())==15&&client.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,Q.above())==15,300);
        } catch(AssertionError failure) {
            context.runOnClient(client->Create.LOGGER.error("Chain scene light: P={}, Q={}, enabled P={}, Q={}, work={}, blocks P={}, Q={}",client.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,P.above()),client.level.getBrightness(net.minecraft.world.level.LightLayer.SKY,Q.above()),client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(P.getX()>>4,P.getZ()>>4)),client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(Q.getX()>>4,Q.getZ()>>4)),client.level.getLightEngine().hasLightWork(),client.level.getBlockState(P.above()),client.level.getBlockState(Q.above())));
            server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();Create.LOGGER.error("Chain scene server light: P={}, Q={}",level.getBrightness(net.minecraft.world.level.LightLayer.SKY,P.above()),level.getBrightness(net.minecraft.world.level.LightLayer.SKY,Q.above()));});
            throw failure;
        }
        world.getConnection().waitForChunksRender();context.runOnClient(ChainConveyorRenderingTest::auditAll);context.takeScreenshot("create-vulkan-chain-conveyor-forward");
        server.runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(P.below())).setGeneratedSpeed(-64));context.waitFor(client->((ChainConveyorBlockEntity)client.level.getBlockEntity(Q)).getSpeed()==-64,200);context.waitTicks(8);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();assertCargo(level);((KineticBlockEntity)level.getBlockEntity(P.below())).setGeneratedSpeed(0);});
        context.waitFor(client->((ChainConveyorBlockEntity)client.level.getBlockEntity(P)).getSpeed()==0,200);context.runOnClient(ChainConveyorRenderingTest::auditAll);context.takeScreenshot("create-vulkan-chain-conveyor-reversed");
        var oldMesh=new AtomicReference<Object>();context.runOnClient(client->oldMesh.set(state(client,P).wheel.mesh));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);
        context.runOnClient(client->{auditAll(client);if(state(client,P).wheel.mesh==oldMesh.get())throw new AssertionError("Chain wheel mesh survived a resource reload");});
        var ids=new AtomicReference<Set<UUID>>();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();ids.set(assertCargo(level));level.getChunkSource().addTicket(new Ticket(TicketType.FORCED,33),new ChunkPos(P.getX()>>4,P.getZ()>>4));});
        server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();
        for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var first=(ChainConveyorBlockEntity)LoadedLevelAccess.blockEntity(level,P);if(first==null)throw new AssertionError("Forced source chunk disappeared");first.removeInvalidConnections();KineticNetworks.tick(level);unloaded.set(level.getChunkSource().getChunkNow(Q.getX()>>4,Q.getZ()>>4)==null);});}
        if(!unloaded.get())throw new AssertionError("Chain conveyor kept remote endpoint loaded");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var first=(ChainConveyorBlockEntity)LoadedLevelAccess.blockEntity(level,P);for(int i=0;i<100;i++){first.removeInvalidConnections();ChainConveyorBlockEntity.tick(level,P,first.getBlockState(),first);KineticNetworks.tick(level);}if(!first.getConnections().contains(Q.subtract(P))||level.getChunkSource().getChunkNow(Q.getX()>>4,Q.getZ()>>4)!=null)throw new AssertionError("Suspended edge disappeared or loaded remote chunk");});
        server.runCommand("tp @p 2308 11 14");context.waitFor(client->Math.abs(client.player.getX()-2308.5)<.1&&client.level.getBlockEntity(Q) instanceof ChainConveyorBlockEntity,400);context.waitTicks(5);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(!assertCargo(level).equals(ids.get()))throw new AssertionError("Parcel ownership/IDs changed across real chunk unload");var first=(ChainConveyorBlockEntity)LoadedLevelAccess.blockEntity(level,P);var second=(ChainConveyorBlockEntity)LoadedLevelAccess.blockEntity(level,Q);if(!first.isConnectedTo(second))throw new AssertionError("Mutual connection did not recover");level.getChunkSource().removeTicketWithRadius(TicketType.FORCED,new ChunkPos(P.getX()>>4,P.getZ()>>4),0);});
        look(context,new Vec3(2308,8,5));world.getConnection().waitForChunksRender();context.runOnClient(ChainConveyorRenderingTest::auditAll);context.takeScreenshot("create-vulkan-chain-conveyor-chunk-return");
        unloadedRemoval(context,world);
    }
    private static void unloadedRemoval(ClientGameTestContext context,TestSingleplayerContext world) {
        // Keep the destroyed block inside its chunk: vanilla neighbor updates at a border
        // can load the neighboring chunk independently of conveyor propagation.
        var from=P.west(2);var to=Q.west(2);var server=world.getServer();
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(var pos:List.of(P,Q)){((ChainConveyorBlockEntity)level.getBlockEntity(pos)).clearContent();level.destroyBlock(pos,false);}level.setBlockAndUpdate(from,AllBlocks.CHAIN_CONVEYOR.defaultBlockState());level.setBlockAndUpdate(to,AllBlocks.CHAIN_CONVEYOR.defaultBlockState());((ChainConveyorBlockEntity)level.getBlockEntity(from)).connectTo((ChainConveyorBlockEntity)level.getBlockEntity(to),null);level.getChunkSource().addTicket(new Ticket(TicketType.FORCED,33),ChunkPos.containing(from));});
        server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();
        for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();unloaded.set(level.getChunkSource().getChunkNow(to.getX()>>4,to.getZ()>>4)==null);});}
        if(!unloaded.get())throw new AssertionError("Remote endpoint did not unload for removal test");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.destroyBlock(from,false);boolean loaded=level.getChunkSource().getChunkNow(to.getX()>>4,to.getZ()>>4)!=null;int pending=ChainConveyorSavedData.get(level).pendingCount();if(loaded||pending!=1)throw new AssertionError("Destruction must record removal without loading remote endpoint: loaded="+loaded+", pending="+pending);});
        server.runCommand("tp @p 2308 11 14");context.waitFor(client->client.level.getBlockEntity(to) instanceof ChainConveyorBlockEntity conveyor&&conveyor.getConnections().isEmpty(),400);context.waitTicks(3);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.destroyBlock(to,false);int chains=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new AABB(Vec3.atLowerCornerOf(from),Vec3.atLowerCornerOf(to)).inflate(10)).stream().map(net.minecraft.world.entity.item.ItemEntity::getItem).filter(item->item.is(Items.IRON_CHAIN)).mapToInt(ItemStack::getCount).sum();if(chains!=4)throw new AssertionError("Two endpoint destructions across unload must drop four chains once, got "+chains);if(ChainConveyorSavedData.get(level).pendingCount()!=0)throw new AssertionError("Loaded endpoint did not acknowledge pending removal");level.getChunkSource().removeTicketWithRadius(TicketType.FORCED,ChunkPos.containing(from),0);});
    }
    private static void manualParcelInteraction(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();var target=new AtomicReference<Vec3>();server.runCommand("setblock 2308 6 9 minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var first=(ChainConveyorBlockEntity)level.getBlockEntity(P);var stats=first.getConnectionStats(Q.subtract(P));target.set(stats.start().lerp(stats.end(),.5));var box=PackageItem.containing(List.of(new ItemStack(Items.DIAMOND,7)));PackageItem.addAddress(box,"Manual");box.setCount(2);var player=level.getServer().getPlayerList().getPlayers().getFirst();player.setItemInHand(InteractionHand.MAIN_HAND,box);player.inventoryMenu.broadcastFullState();});
        server.runCommand("tp @p 2308.5 7 9.5");context.waitFor(client->Math.abs(client.player.getX()-2308.5)<.1&&Math.abs(client.player.getY()-7)<.1&&PackageItem.isPackage(client.player.getMainHandItem()),200);look(context,target.get());context.waitTicks(3);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.player.getMainHandItem().getCount()==1&&((ChainConveyorBlockEntity)client.level.getBlockEntity(P)).packageCount()==1,200);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var first=(ChainConveyorBlockEntity)level.getBlockEntity(P);var parcel=first.getTravellingPackages().getOrDefault(Q.subtract(P),List.of()).getFirst();if(!PackageItem.getAddress(parcel.item).equals("Manual")||parcel.item.getCount()!=1)throw new AssertionError("Native attach changed parcel data or consumed wrong count");target.set(ChainConveyorInteraction.packageBounds(parcel,0).getCenter());});
        look(context,target.get());context.waitTicks(3);context.getInput().pressKey(options->options.keyAttack);
        context.waitFor(client->((ChainConveyorBlockEntity)client.level.getBlockEntity(P)).packageCount()==0,200);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();int parcels=0;for(int i=0;i<player.getInventory().getContainerSize();i++){var item=player.getInventory().getItem(i);if(!PackageItem.isPackage(item))continue;parcels+=item.getCount();if(!PackageItem.getAddress(item).equals("Manual")||PackageItem.getContents(item).getFirst().getCount()!=7)throw new AssertionError("Native pickup lost parcel contents");}if(parcels!=2)throw new AssertionError("Native attach/pickup should preserve two inventory parcels, got "+parcels);});
    }
    private static Set<UUID> assertCargo(ServerLevel level) {
        var ids=new HashSet<UUID>();var sessionIds=new HashSet<Integer>();int count=0;
        for(var pos:List.of(P,Q)){var conveyor=(ChainConveyorBlockEntity)LoadedLevelAccess.blockEntity(level,pos);if(conveyor==null)throw new AssertionError("Endpoint missing");var parcels=new ArrayList<>(conveyor.getLoopingPackages());conveyor.getTravellingPackages().values().forEach(parcels::addAll);for(var parcel:parcels){count++;if(!ids.add(parcel.identity)||!sessionIds.add(parcel.netId)||!PackageItem.getAddress(parcel.item).equals("Market")||PackageItem.getContents(parcel.item).getFirst().getCount()!=7)throw new AssertionError("Cargo identity/address/contents corrupted");}}
        if(count!=3)throw new AssertionError("Expected three real parcels, got "+count);return ids;
    }
    private static ChainConveyorRenderer.State state(net.minecraft.client.Minecraft client,BlockPos pos) { return (ChainConveyorRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos),.5f,null,true); }
    private static void auditAll(net.minecraft.client.Minecraft client) { audit(client,P);audit(client,Q);if(state(client,P).boxes.size()+state(client,Q).boxes.size()!=3)throw new AssertionError("All three real parcels must have extracted model and rigging state"); }
    private static void audit(net.minecraft.client.Minecraft client,BlockPos pos) {
        var state=state(client,pos);if(state==null||state.strands.size()!=1)throw new AssertionError("Native chain strand missing");
        state.strands.getFirst().mesh().mesh.forEach(quad->{for(int i=0;i<4;i++){float x=quad.normalX(i),y=quad.normalY(i),z=quad.normalZ(i);if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z)||x*x+y*y+z*z<.9)throw new AssertionError("Transformed chain requires finite unit normals, including native quads without explicit vertex normals");}});
        var meshes=new ArrayList<PartialModelRenderState>(List.of(state.wheel,state.shaft,state.guard,state.strands.getFirst().mesh()));
        state.boxes.forEach(box->{meshes.add(box.parcel());meshes.add(box.rig());});
        for(var model:meshes){if(model.mesh==null||model.mesh.size()==0)throw new AssertionError("Chain model has no mesh");model.mesh.forEach(quad->{for(int vertex=0;vertex<4;vertex++)if(!Float.isFinite(quad.x(vertex))||!Float.isFinite(quad.y(vertex))||!Float.isFinite(quad.z(vertex))||!Float.isFinite(quad.u(vertex))||!Float.isFinite(quad.v(vertex)))throw new AssertionError("Chain mesh contains nonfinite position or UV");});}
    }
    private static void look(ClientGameTestContext context,Vec3 target) { var rotation=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(rotation.x,rotation.y); }
}
