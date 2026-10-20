package com.simibubi.create.test;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.foundation.mixin.accessor.*;
import com.simibubi.create.foundation.utility.*;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.storage.*;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.level.storage.PlayerDataStorage;

/** Authored source-writer fixtures; real files/native manager/player login. All cases UNRUN. */
public final class NeoForgePlayerRegionOwnershipGameTests {
    private record Fixture(PlayerDataStorage storage,UUID player,UUID cart,Path primary,Path backup,
            CompoundTag originalPlayer,CompoundTag root,CompoundTag region,ChunkPos chunk) {}
    private static ListTag rows(Tag... values){var list=new ListTag();for(var value:values)list.add(value);return list;}
    private static CompoundTag base(GameTestHelper h,UUID id,BlockPos at){
        var source=new CompoundTag();source.store("UUID",UUIDUtil.CODEC,id);source.put("Pos",rows(DoubleTag.valueOf(at.getX()+.5),DoubleTag.valueOf(at.getY()),DoubleTag.valueOf(at.getZ()+.5)));
        source.put("Motion",rows(DoubleTag.valueOf(0),DoubleTag.valueOf(0),DoubleTag.valueOf(0)));source.put("Rotation",rows(FloatTag.valueOf(0),FloatTag.valueOf(0)));
        source.putFloat("FallDistance",0);source.putShort("Fire",(short)-1);source.putShort("Air",(short)300);source.putBoolean("OnGround",false);source.putBoolean("Invulnerable",false);source.putInt("PortalCooldown",0);return source;
    }
    private static CompoundTag cart(GameTestHelper h,UUID id,BlockPos at,int diamonds){
        var source=base(h,id,at);source.putString("id","minecraft:chest_minecart");source.putBoolean("CustomDisplayTile",false);
        var item=new CompoundTag();item.putString("id","minecraft:diamond");item.putInt("count",diamonds);item.putByte("Slot",(byte)26);source.put("Items",rows(item));return source;
    }
    private static CompoundTag envelope(ChunkPos at,CompoundTag... roots){var tag=new CompoundTag();tag.putInt("DataVersion",3955);tag.putIntArray("Position",new int[]{at.x(),at.z()});tag.put("Entities",rows(roots));return tag;}
    private static Fixture fixture(GameTestHelper h,BlockPos at) throws Exception {
        var storage=NeoForgePlayerStorageImport.actualStorage(h.getLevel().getServer());var id=UUID.randomUUID();var cart=UUID.randomUUID();
        var directory=((PlayerDataStorageAccessor)storage).create$getPlayerDirectory().toPath();var primary=directory.resolve(id+".dat");var backup=directory.resolve(id+".dat_old");
        h.assertTrue(!Files.exists(primary)&&!Files.exists(backup),"Fresh actual native player filenames required");
        var player=NeoForgePlayerDataGameTests.original(h,id);var root=cart(h,cart,at,37);var vehicle=new CompoundTag();vehicle.store("Attach",UUIDUtil.CODEC,cart);vehicle.put("Entity",root.copy());player.put("RootVehicle",vehicle);player.put("Pos",root.get("Pos").copy());
        var chunk=new ChunkPos(Math.floorDiv(at.getX(),16),Math.floorDiv(at.getZ(),16));var fixture=new Fixture(storage,id,cart,primary,backup,player,root,envelope(chunk,root.copy()),chunk);NbtIo.writeCompressed(player.copy(),primary);return fixture;
    }
    private static void clean(Fixture f) throws Exception{Files.deleteIfExists(f.primary);Files.deleteIfExists(f.backup);}
    private static CompoundTag read(Path p) throws Exception{return NbtIo.readCompressed(p,NbtAccounter.unlimitedHeap());}
    private static void refuses(GameTestHelper h,Runnable work,String message){boolean refused=false;try{work.run();}catch(IllegalArgumentException expected){refused=true;}h.assertTrue(refused,message);}
    private static PersistentEntitySectionManager<Entity> manager(GameTestHelper h){return ((ServerLevelEntityManagerAccessor)(Object)h.getLevel()).create$getEntityManager();}
    private static EntityStorage storage(GameTestHelper h){return (EntityStorage)((CarriageNativeSourceManagerAccessor)(Object)manager(h)).create$getPermanentSourceStorage();}
    private static SimpleRegionStorage region(GameTestHelper h){return ((NeoForgeOriginalImportFailure.EntityStorageAccess)(Object)storage(h)).create$originalImportRegionStorage();}

    @GameTest public void genuineRegionSeatUuidRemainsInertWithoutSyntheticPlayerRootVehicleOrPlayerBody(GameTestHelper h){
        var at=h.absolutePos(new BlockPos(3,3,3));var id=UUID.randomUUID();var absent=UUID.randomUUID();var root=base(h,id,at);root.putString("id","create:stationary_contraption");
        root.putBoolean("Stalled",false);root.putBoolean("Initialized",true);root.putString("Axis","Y");root.putFloat("Angle",0);var relative=new CompoundTag();relative.putInt("X",0);relative.putInt("Y",0);relative.putInt("Z",0);var controller=relative.copy();controller.putInt("Y",-1);root.put("ControllerRelative",controller);
        var body=new CompoundTag();body.putString("Type","create:bearing");body.put("Anchor",relative.copy());body.putBoolean("Stalled",false);body.putBoolean("BottomlessSupply",false);body.putInt("Sails",0);body.putInt("Facing",Direction.UP.get3DDataValue());
        for(String field:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","SubContraptions"))body.put(field,new ListTag());
        var captured=AllBlocks.SEATS.get(DyeColor.WHITE).defaultBlockState();var state=new CompoundTag();state.putString("Name",BuiltInRegistries.BLOCK.getKey(captured.getBlock()).toString());var properties=new CompoundTag();captured.getValues().forEach(value->properties.putString(value.property().getName(),value.valueName()));if(!properties.isEmpty())state.put("Properties",properties);var row=new CompoundTag();row.putLong("Pos",BlockPos.ZERO.asLong());row.putInt("State",0);var blocks=new CompoundTag();blocks.put("Palette",rows(state));blocks.put("BlockList",rows(row));body.put("Blocks",blocks);
        h.assertTrue(com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(captured) instanceof com.simibubi.create.content.contraptions.actors.seat.SeatMovementBehaviour,"Actual captured seat requires its genuine registered movement context");
        var actor=new CompoundTag();actor.put("Pos",relative.copy());actor.put("Motion",rows(DoubleTag.valueOf(0),DoubleTag.valueOf(0),DoubleTag.valueOf(0)));actor.put("RelativeMotion",rows(DoubleTag.valueOf(0),DoubleTag.valueOf(0),DoubleTag.valueOf(0)));actor.putBoolean("Stall",false);actor.putBoolean("FirstMovement",false);var actorData=new CompoundTag();actorData.putInt("SeatIndex",0);actor.put("Data",actorData);body.put("Actors",rows(actor));
        if(com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.get(captured)!=null){var interaction=new CompoundTag();interaction.put("Pos",relative.copy());body.put("Interactors",rows(interaction));}
        var seat=new CompoundTag();seat.put("Pos",relative.copy());body.put("Seats",rows(seat));var mapping=new CompoundTag();mapping.store("Id",UUIDUtil.CODEC,absent);mapping.putInt("Seat",0);body.put("Passengers",rows(mapping));root.put("Contraption",body);
        var source=envelope(new ChunkPos(Math.floorDiv(at.getX(),16),Math.floorDiv(at.getZ(),16)),root);var before=source.copy();var converted=NeoForgeEntityChunkImport.convert(source,new ChunkPos(Math.floorDiv(at.getX(),16),Math.floorDiv(at.getZ(),16)),h.getLevel().registryAccess());
        var actual=EntityType.loadEntityRecursive(converted.getListOrEmpty("Entities").getCompoundOrEmpty(0),h.getLevel(),new EntitySpawnRequest(EntitySpawnReason.LOAD,false),EntityProcessor.NOP);
        h.assertTrue(source.equals(before)&&actual instanceof ControlledContraptionEntity&&actual.getPassengers().isEmpty()&&h.getLevel().getEntity(absent)==null
            &&((ControlledContraptionEntity)actual).getContraption().getSeatMapping().get(absent)==0&&actual.shouldBeSaved()&&!actual.hasExactlyOnePlayerPassenger(),"Actual native region body retains only its source inert seat UUID; no player record/body or RootVehicle authority was fabricated");
        refuses(h,()->NeoForgeStandaloneEntityImport.convertTree(root,3955,h.getLevel().registryAccess()),"Generic/player-owned conversion retains its explicit owner requirement; only actual region envelopes use inert maps");h.succeed();
    }

    @GameTest(maxTicks=200) public void realRegionBatchKeepsPlayerOwnedDuplicateInActualPlayerFileBeforeNativeAdmission(GameTestHelper h) throws Exception {
        var at=new BlockPos(24000+Math.floorMod(UUID.randomUUID().hashCode(),1000)*16,128,24000);var f=fixture(h,at);var other=UUID.randomUUID();var raw=envelope(f.chunk,f.root.copy(),cart(h,other,at.offset(1,0,1),19));var io=region(h);var manager=manager(h);
        h.assertTrue(!manager.areEntitiesLoaded(f.chunk.pack())&&io.read(f.chunk).join().isEmpty(),"Actual unrequested/unloaded native entity record must be fresh before authored fixture seed");
        io.write(f.chunk,raw.copy()).join();io.synchronize(true).join();manager.updateChunkStatus(f.chunk,Visibility.TRACKED);
        h.startSequence().thenWaitUntil(()->h.assertTrue(manager.areEntitiesLoaded(f.chunk.pack()),"Actual native manager must pass its real pending-load admission boundary")).thenExecute(()->{
            try {
                var admitted=h.getLevel().getEntity(other);
                h.assertTrue(h.getLevel().getEntity(f.cart)==null&&admitted instanceof MinecartChest&&((MinecartChest)admitted).getItem(26).is(Items.DIAMOND)&&((MinecartChest)admitted).getItem(26).getCount()==19
                    &&read(f.primary).equals(f.originalPlayer)&&io.read(f.chunk).join().orElseThrow().equals(raw),"Exact canonical37-diamond player tree was never native-parsed/admitted; the other actual19-diamond region body admitted normally and both original records stayed intact");
                admitted.discard();manager.updateChunkStatus(f.chunk,Visibility.HIDDEN);io.write(f.chunk,(CompoundTag)null).join();clean(f);
            }catch(Exception failure){throw new IllegalStateException(failure);}
        }).thenSucceed();
    }

    @GameTest public void stalePlayerPayloadAndNewPrimaryCannotUsePreparedOriginalTokenOrReviveDatOld(GameTestHelper h) throws Exception {
        var f=fixture(h,h.absolutePos(new BlockPos(3,3,3)));
        try {
            var prepared=NeoForgePlayerRegionImport.prepare(f.region,f.chunk,h.getLevel());h.assertTrue(prepared.converted().getListOrEmpty("Entities").isEmpty(),"Preparation defers the exact duplicate to its one actual player-file owner");
            var changed=f.originalPlayer.copy();changed.getCompoundOrEmpty("RootVehicle").getCompoundOrEmpty("Entity").getListOrEmpty("Items").getCompoundOrEmpty(0).putInt("count",36);NbtIo.writeCompressed(changed,f.primary);
            refuses(h,prepared::requireCurrent,"Same actual filename with changed typed material cannot authorize an old pending batch");
            NbtIo.writeCompressed(f.originalPlayer.copy(),f.backup);var current=NeoForgePlayerDataImport.convert(f.originalPlayer,f.player,h.getLevel());current.remove("RootVehicle");NbtIo.writeCompressed(current,f.primary);
            refuses(h,()->NeoForgePlayerRegionImport.prepare(f.region,f.chunk,h.getLevel()),"Current native primary without RootVehicle supersedes its historical original backup; backup is never revived as material authority");
            h.assertTrue(read(f.backup).equals(f.originalPlayer)&&read(f.primary).getIntOr("DataVersion",-1)==SharedConstants.getCurrentVersion().dataVersion().version()&&h.getLevel().getEntity(f.cart)==null,"Negative pure-guard checks create/publish no cart and retain both actual files");h.succeed();
        }finally{clean(f);}
    }

    @GameTest public void actualNativeKnownUuidSupersedesPreparedOfflineRawPlayerSnapshot(GameTestHelper h) throws Exception {
        var f=fixture(h,h.absolutePos(new BlockPos(3,3,3)));Entity actual=null;
        try {
            var prepared=NeoForgePlayerRegionImport.prepare(f.region,f.chunk,h.getLevel());var current=NeoForgeStandaloneEntityImport.playerRootVehicle(f.originalPlayer,3955,h.getLevel().registryAccess());
            actual=EntityType.loadEntityRecursive(current.getCompoundOrEmpty("Entity"),h.getLevel(),new EntitySpawnRequest(EntitySpawnReason.LOAD,false),EntityProcessor.NOP);
            h.assertTrue(actual!=null&&h.getLevel().addFreshEntity(actual),"Genuine native level must own the current material UUID before stale guard check");
            refuses(h,prepared::requireCurrent,"An actual known native UUID supersedes the unchanged old player file before region publication");
            h.assertTrue(h.getLevel().getEntity(f.cart)==actual&&((MinecartChest)actual).getItem(26).getCount()==37&&read(f.primary).equals(f.originalPlayer),"Only the one actual current cart keeps all37 diamonds; no source copy was re-instantiated");h.succeed();
        }finally{if(actual!=null)actual.discard();clean(f);}
    }

    @GameTest(maxTicks=200) public void realOnlinePlayerSupersedesPreparedRawFileBeforeAnyRegionMaterialPublication(GameTestHelper h) throws Exception {
        // PrepareSpawnTask uses the real PLAYER_SPAWN radius3. Complete native
        // chunk/entity work before starting its real-time login deadline.
        var spawn=h.absolutePos(new BlockPos(3,3,3));var spawnChunk=new ChunkPos(Math.floorDiv(spawn.getX(),16),Math.floorDiv(spawn.getZ(),16));
        for(int x=spawnChunk.x()-3;x<=spawnChunk.x()+3;x++)for(int z=spawnChunk.z()-3;z<=spawnChunk.z()+3;z++)h.getLevel().getChunk(x,z);
        h.getLevel().waitForEntities(spawnChunk,3);
        var f=fixture(h,h.absolutePos(new BlockPos(3,3,3)));var prepared=NeoForgePlayerRegionImport.prepare(f.region,f.chunk,h.getLevel());
        var rejoin=new CarriagePlayerOwnerGameTests.Rejoin(h,new GameProfile(f.player,"region_"+f.player.toString().substring(0,8)));
        h.startSequence().thenWaitUntil(rejoin::requireReady).thenExecute(()->{
            try {
                var joined=rejoin.spawn();h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayer(f.player)==joined.player(),"Real native PrepareSpawnTask/PlayerList must own the original player UUID");
                refuses(h,prepared::requireCurrent,"Actual online player supersedes its unchanged raw original RootVehicle context before any region batch publication");
                h.assertTrue(read(f.primary).equals(f.originalPlayer),"Online supersession is observed from real native ownership, not a manufactured replacement file");
            }catch(Exception failure){throw new IllegalStateException(failure);}finally{
                rejoin.close();var cart=h.getLevel().getEntity(f.cart);if(cart!=null)cart.discard();try{clean(f);}catch(Exception failure){throw new IllegalStateException(failure);}
            }
        }).thenSucceed();
    }
}
