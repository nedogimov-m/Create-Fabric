package com.simibubi.create.content.trains.entity;

import com.simibubi.create.test.NativeSurvivalTestPlayer;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Genuine mixed native tree snapshots and malformed imported reference graphs. Runtime UNRUN. */
public final class CarriagePassengerGraphGameTests {
    private static final BlockPos P=new BlockPos(3,3,3);
    private record Fixture(Entity root,Entity branch,MinecartChest cargo,NativeSurvivalTestPlayer first,NativeSurvivalTestPlayer second) implements AutoCloseable {
        @Override public void close(){second.close();first.close();}
    }
    private static Fixture fixture(GameTestHelper h){
        var root=h.spawnWithNoFreeWill(EntityTypes.PIG,P);var branch=h.spawnWithNoFreeWill(EntityTypes.COW,P.east());var at=Vec3.atCenterOf(h.absolutePos(P));var cargo=new MinecartChest(EntityTypes.CHEST_MINECART,h.getLevel());cargo.setPos(at);cargo.setNoGravity(true);var diamonds=new ItemStack(Items.DIAMOND,7);diamonds.set(DataComponents.CUSTOM_NAME,Component.literal("Original native NPC cargo"));cargo.setItem(0,diamonds);h.assertTrue(h.getLevel().addFreshEntity(cargo),"Real native chest minecart and original named cargo admitted");
        var first=NativeSurvivalTestPlayer.join(h,at);var second=NativeSurvivalTestPlayer.join(h,at.add(1,0,0));
        try{for(var joined:List.of(first,second)){joined.player().getInventory().clearContent();var emeralds=new ItemStack(Items.EMERALD,11);emeralds.set(DataComponents.CUSTOM_NAME,Component.literal("Original literal player stock"));joined.player().getInventory().setItem(0,emeralds);}
            h.assertTrue(branch.startRiding(root,true,true)&&cargo.startRiding(root,true,true)&&first.player().startRiding(root,true,true)&&second.player().startRiding(branch,true,true),"Genuine native NPC tree with two literal player leaves uses actual parent edges and native child order");return new Fixture(root,branch,cargo,first,second);
        }catch(RuntimeException|Error failure){second.close();first.close();throw failure;}
    }
    private static CompoundTag nativeSave(Entity entity){var problems=new ProblemReporter.Collector(entity.problemPath());var output=TagValueOutput.createWithContext(problems,entity.registryAccess());if(!entity.saveAsPassenger(output)||!problems.isEmpty())throw new AssertionError("Genuine native save: "+problems.getReport());return output.buildResult();}
    private static void nativeOrder(GameTestHelper h,CarriagePassengerGraph graph,Entity parent){var expected=parent.getPassengers();var saved=graph.children(parent.getUUID());h.assertTrue(saved.size()==expected.size(),"Every actual native child edge is represented");for(int i=0;i<expected.size();i++)h.assertTrue(saved.get(i).id().equals(expected.get(i).getUUID())&&saved.get(i).parent().orElseThrow().equals(parent.getUUID())&&saved.get(i).order()==i,"Exact source native object order and immediate parent UUID are preserved");}
    @GameTest public void genuineMixedTreeSnapshotKeepsLiteralPlayersAndNamedNpcCargoInOriginalWorld(GameTestHelper h){
        try(var f=fixture(h)){
            var firstInventory=f.first.player().getInventory().getItem(0);var secondInventory=f.second.player().getInventory().getItem(0);var originalCargo=f.cargo.getItem(0);var parent=f.root.getVehicle();var captured=CarriagePassengerSnapshots.capture(f.root,UUID.randomUUID());var graph=captured.graph();
            h.assertTrue(captured.current()&&graph.nodes().size()==5&&graph.players().equals(Set.of(f.first.player().getUUID(),f.second.player().getUUID()))&&f.root.getVehicle()==parent&&f.first.player().getVehicle()==f.root&&f.second.player().getVehicle()==f.branch&&f.cargo.getItem(0)==originalCargo&&originalCargo.getCount()==7&&f.first.player().getInventory().getItem(0)==firstInventory&&firstInventory.getCount()==11&&f.second.player().getInventory().getItem(0)==secondInventory&&secondInventory.getCount()==11,"Read-only flat native snapshot never detaches a real rider, replaces player/NPC objects or transfers their exact original cargo/inventory aliases");
            nativeOrder(h,graph,f.root);nativeOrder(h,graph,f.branch);
            for(var node:graph.nodes())if(node instanceof CarriagePassengerGraph.Npc npc)h.assertTrue(npc.payload().isPresent()&&!npc.payload().get().contains("Passengers"),"Each genuine native NPC serializer supplies one flat nonrecursive complete node");
            for(var tag:graph.write().getListOrEmpty("Nodes")){var row=(CompoundTag)tag;if(row.getStringOr("Kind","").equals("player"))h.assertTrue(!row.contains("Payload")&&!row.contains("Type")&&!row.contains("Inventory")&&row.read("UUID",UUIDUtil.CODEC).isPresent(),"Player is represented only by its UUID, parent and order; no native player NBT, inventory or constructor path");}
            var ordinary=nativeSave(f.root);h.assertTrue(!ordinary.getListOrEmpty("Passengers").isEmpty()&&f.first.player().getClass()==ServerPlayer.class&&f.second.player().getClass()==ServerPlayer.class,"Ordinary later native saves retain genuine recursive NPC child traversal after exact snapshot scope closes");
            f.second.player().stopRiding();h.assertTrue(!captured.current()&&graph.node(f.second.player().getUUID()) instanceof CarriagePassengerGraph.PlayerReference,"Actual voluntary native unmount invalidates the captured topology witness without changing its immutable reference snapshot");
        }
        h.succeed();
    }
    private static void reject(GameTestHelper h,CompoundTag bad,String reason){boolean refused=false;try{CarriagePassengerGraph.read(bad);}catch(IllegalArgumentException failure){refused=true;}h.assertTrue(refused,reason);}
    @GameTest public void malformedImportedPlayerGraphsRefuseBeforeAnyNpcDecodeOrMaterialTransfer(GameTestHelper h){
        try(var f=fixture(h)){
            var graph=CarriagePassengerSnapshots.capture(f.root,UUID.randomUUID()).graph();var saved=graph.write();var rows=saved.getListOrEmpty("Nodes");int playerIndex=-1,npcIndex=-1;for(int i=0;i<rows.size();i++){var row=rows.getCompoundOrEmpty(i);if(row.getStringOr("Kind","").equals("player"))playerIndex=i;else if(row.contains("Parent"))npcIndex=i;}
            var duplicate=saved.copy();duplicate.getListOrEmpty("Nodes").add(duplicate.getListOrEmpty("Nodes").getCompoundOrEmpty(playerIndex).copy());reject(h,duplicate,"One actual UUID cannot be claimed twice as NPC/player reference");
            var unknown=saved.copy();unknown.getListOrEmpty("Nodes").getCompoundOrEmpty(playerIndex).store("Parent",UUIDUtil.CODEC,UUID.randomUUID());reject(h,unknown,"Unknown immediate parent refuses before NPC payload decode");
            var cycle=saved.copy();cycle.getListOrEmpty("Nodes").getCompoundOrEmpty(npcIndex).store("Parent",UUIDUtil.CODEC,cycle.getListOrEmpty("Nodes").getCompoundOrEmpty(npcIndex).read("UUID",UUIDUtil.CODEC).orElseThrow());reject(h,cycle,"A disconnected self-cycle cannot become an actual native parent tree");
            var playerPayload=saved.copy();playerPayload.getListOrEmpty("Nodes").getCompoundOrEmpty(playerIndex).put("Payload",nativeSave(f.cargo));reject(h,playerPayload,"A player reference carrying copied entity data or inventory is invalid");
            var belowPlayer=saved.copy();belowPlayer.getListOrEmpty("Nodes").getCompoundOrEmpty(npcIndex).store("Parent",UUIDUtil.CODEC,f.first.player().getUUID());reject(h,belowPlayer,"Native no-save player types cannot be parent vehicles even with force=true");
            var badOrder=saved.copy();badOrder.getListOrEmpty("Nodes").getCompoundOrEmpty(playerIndex).putInt("Order",99);reject(h,badOrder,"A missing native sibling order cannot alter first/controlling passenger semantics");
            var fakeNpc=saved.copy();var fake=fakeNpc.getListOrEmpty("Nodes").getCompoundOrEmpty(playerIndex);fake.putString("Kind","npc");fake.putString("Type","minecraft:player");reject(h,fakeNpc,"A player UUID cannot be reinterpreted through a native NPC factory type");
            boolean collision=false;try{graph.requireDisjointBodies(Set.of(f.root.getUUID()));}catch(IllegalArgumentException failure){collision=true;}
            h.assertTrue(collision&&f.first.player().getVehicle()==f.root&&f.second.player().getVehicle()==f.branch&&f.cargo.getItem(0).getCount()==7&&f.first.player().getInventory().countItem(Items.EMERALD)==11&&f.second.player().getInventory().countItem(Items.EMERALD)==11,"All malformed imported records refuse before changing exact native source mounts, NPC cargo or original literal player inventories");
        }
        h.succeed();
    }
    @GameTest public void actualLegacyNativeNpcTreeAndDirectPlayerMigrateWithoutPlayerCloning(GameTestHelper h){
        try(var f=fixture(h)){
            var old=nativeSave(f.root);var revision=UUID.randomUUID();var migrated=CarriagePassengerGraph.fromLegacy(old,revision);h.assertTrue(migrated.root().equals(f.root.getUUID())&&migrated.revision().equals(revision)&&migrated.nodes().size()==3&&migrated.players().isEmpty(),"Genuine old recursive native serialization contains NPCs only and migrates into exact flat NPC identities");
            var encoded=CarriagePassengerGraph.CODEC.encodeStart(NbtOps.INSTANCE,migrated).getOrThrow();var decoded=CarriagePassengerGraph.CODEC.parse(NbtOps.INSTANCE,encoded).getOrThrow();h.assertTrue(decoded.write().equals(migrated.write())&&decoded.node(f.cargo.getUUID()) instanceof CarriagePassengerGraph.Npc,"Actual NBT codec retains exact native NPC payloads, parent edges, order and graph generation");
            var cargo=(CarriagePassengerGraph.Npc)decoded.node(f.cargo.getUUID());var escaped=cargo.payload().orElseThrow();escaped.putString("id","minecraft:player");h.assertTrue(!cargo.payload().orElseThrow().equals(escaped),"External mutable NBT access cannot rewrite the retained native type or cargo payload");
            var direct=new CompoundTag();direct.store("PlayerPassenger",UUIDUtil.CODEC,f.first.player().getUUID());var player=CarriagePassengerGraph.fromLegacy(direct,UUID.randomUUID());h.assertTrue(player.nodes().size()==1&&player.node(f.first.player().getUUID()) instanceof CarriagePassengerGraph.PlayerReference&&player.players().equals(Set.of(f.first.player().getUUID()))&&h.getLevel().getServer().getPlayerList().getPlayer(f.first.player().getUUID())==f.first.player(),"Actual existing direct-player UUID format migrates to a reference to the one current native PlayerList object");
            h.assertTrue(!f.cargo.startRiding(f.first.player(),true,true)&&f.cargo.getVehicle()==f.root,"Genuine native server startRiding itself refuses NPC children below a no-save player parent, preserving the original actual edge");
        }
        h.succeed();
    }
}
