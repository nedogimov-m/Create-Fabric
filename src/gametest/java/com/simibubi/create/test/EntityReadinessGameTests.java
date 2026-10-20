package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.foundation.mixin.accessor.*;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.entity.*;

/** Native data/visibility transitions must never turn a partial query into world ownership. */
public class EntityReadinessGameTests {
    private static final BlockPos P=new BlockPos(3,5,3),T=P.south(3),B=P.below();
    @FunctionalInterface private interface Checked { void run()throws Exception; }
    private static PersistentEntitySectionManager<Entity> manager(GameTestHelper h){return ((ServerLevelEntityManagerAccessor)h.getLevel()).create$getEntityManager();}
    private static long key(GameTestHelper h){return ChunkPos.pack(h.absolutePos(P));}
    private static void visibility(GameTestHelper h,Visibility next,Checked body)throws Exception{
        visibility(h,Set.of(key(h)),next,body);
    }
    private static void visibility(GameTestHelper h,Collection<Long> chunks,Visibility next,Checked body)throws Exception{
        var manager=manager(h);var visibility=((EntitySectionVisibilityAccessor)manager).create$getChunkVisibility();var saved=new LinkedHashMap<Long,Visibility>();
        // Check every original native state before the first tracking/unload mutation.
        for(long chunk:chunks){var old=visibility.get(chunk);h.assertTrue(old!=null&&old.isAccessible(),"Fixture begins with queryable native entity sections at "+ChunkPos.unpack(chunk));saved.put(chunk,old);}
        try{for(long chunk:saved.keySet())manager.updateChunkStatus(ChunkPos.unpack(chunk),next);body.run();}
        finally{for(var entry:saved.entrySet())manager.updateChunkStatus(ChunkPos.unpack(entry.getKey()),entry.getValue());for(var entry:saved.entrySet())h.assertValueEqual(visibility.get(entry.getKey()),entry.getValue(),"Every actual native entity chunk restores its original visibility");}
    }
    @SuppressWarnings("unchecked") private static void missingData(GameTestHelper h,Checked body)throws Exception{
        missingData(h,P,body);
    }
    @SuppressWarnings("unchecked") private static void missingData(GameTestHelper h,BlockPos local,Checked body)throws Exception{
        var field=PersistentEntitySectionManager.class.getDeclaredField("chunkLoadStatuses");field.setAccessible(true);
        var states=(Long2ObjectMap<Object>)field.get(manager(h));long key=ChunkPos.pack(h.absolutePos(local));
        h.assertTrue(h.getLevel().areEntitiesLoaded(key),"Native fixture data initially loaded");
        var old=states.remove(key);try{body.run();}finally{states.put(key,old);}
    }
    private static ItemStack payload(){var stack=new ItemStack(Items.DIAMOND,7);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Ready-only cargo"));return stack;}
    private static void barrel(GameTestHelper h){h.setBlock(P,Blocks.BARREL);h.getBlockEntity(P,BarrelBlockEntity.class).setItem(0,payload());}
    private static BearingContraption capture(GameTestHelper h)throws AssemblyException{var c=new BearingContraption(false,Direction.UP);h.assertTrue(c.assemble(h.getLevel(),h.absolutePos(P.below())),"Capture succeeded");return c;}
    private static SuperGlueEntity glue(GameTestHelper h){h.setBlock(P.east(),Blocks.STONE);var entity=new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(P),h.absolutePos(P.east())));h.assertTrue(h.getLevel().addFreshEntity(entity),"Real source glue added");return entity;}
    private static void rejected(GameTestHelper h,Checked body)throws Exception{boolean rejected=false;try{body.run();}catch(AssemblyException expected){rejected=expected.canRetry();}h.assertTrue(rejected,"Readiness failure is retryable");}
    private static void restore(GameTestHelper h,Contraption c)throws AssemblyException{h.assertTrue(c.tryAddBlocksToWorld(h.getLevel(),new StructureTransform(h.absolutePos(T),0,0,0)),"World restoration");}

    @GameTest public void trackedVisibilityDoesNotRequireEntityTickingForCapture(GameTestHelper h)throws Exception{
        h.setBlock(P,Blocks.STONE);
        visibility(h,Visibility.TRACKED,()->{h.assertTrue(LoadedEntityAccess.isReady(h.getLevel(),h.absolutePos(P)),"TRACKED is sufficient");h.assertValueEqual(capture(h).getBlocks().size(),1,"Ordinary boundary assembly works without ENTITY_TICKING");});h.succeed();
    }
    @GameTest public void hiddenSectionsCannotTurnGluedBlocksIntoOneBlockCapture(GameTestHelper h)throws Exception{
        barrel(h);var glue=glue(h);var c=new BearingContraption(false,Direction.UP);
        var id=glue.getUUID();visibility(h,new LinkedHashSet<>(List.of(key(h),ChunkPos.pack(glue.blockPosition()))),Visibility.HIDDEN,()->{
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(h.absolutePos(P).getX()>>4,h.absolutePos(P).getZ()>>4)!=null,"Block chunk remains loaded");
            h.assertFalse(SuperGlueEntity.isGlued(h.getLevel(),h.absolutePos(P),Direction.EAST,null),"Native query really cannot see the hidden glue");
            rejected(h,()->c.assemble(h.getLevel(),h.absolutePos(P.below())));
            h.assertTrue(c.phase()==Contraption.Phase.EMPTY&&c.getBlocks().isEmpty()&&!c.getStorage().ownsContents(),"No partial captured owner survives");
            h.assertBlockPresent(Blocks.BARREL,P);h.assertBlockPresent(Blocks.STONE,P.east());h.assertTrue(glue.isAlive(),"Glue not consumed");
        });
        var queried=new HashSet<SuperGlueEntity>();h.assertTrue(h.getLevel().getEntity(id)==glue&&SuperGlueEntity.isGlued(h.getLevel(),h.absolutePos(P),Direction.EAST,queried)&&queried.contains(glue),"Original native glue UUID and actual query return after every owning chunk restores visibility");
        var ready=capture(h);h.assertValueEqual(ready.getBlocks().size(),2,"Both blocks captured once visible");h.succeed();
    }
    @GameTest public void visibleSectionsStillRequireCompletedEntityData(GameTestHelper h)throws Exception{
        barrel(h);missingData(h,()->{h.assertFalse(LoadedEntityAccess.isReady(h.getLevel(),h.absolutePos(P)),"Visibility alone does not imply persisted data ready");rejected(h,()->capture(h));h.assertValueEqual(h.getBlockEntity(P,BarrelBlockEntity.class).getItem(0).getCount(),7,"Private cargo untouched");});h.assertValueEqual(capture(h).getBlocks().size(),1,"Ready capture recovers");h.succeed();
    }
    @GameTest public void entityReadinessIsRevalidatedBeforeAnySourceRemoval(GameTestHelper h)throws Exception{
        barrel(h);glue(h);var c=capture(h);
        missingData(h,()->{rejected(h,()->c.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO));h.assertValueEqual(c.phase(),Contraption.Phase.CAPTURED,"Still a read-only capture");h.assertBlockPresent(Blocks.BARREL,P);h.assertBlockPresent(Blocks.STONE,P.east());});
        var changed=payload();changed.setCount(9);h.getBlockEntity(P,BarrelBlockEntity.class).setItem(0,changed);
        h.assertTrue(c.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Take ownership only after native data returns");restore(h,c);
        h.assertValueEqual(h.getBlockEntity(T,BarrelBlockEntity.class).getItem(0).getCount(),9,"Latest world inventory preserved");h.succeed();
    }
    @GameTest public void staleGlueIdentityCannotConsumeAReplacementVolume(GameTestHelper h)throws Exception{
        barrel(h);var old=glue(h);var c=capture(h);old.discard();var replacement=new SuperGlueEntity(h.getLevel(),old.getBoundingBox());replacement.setUUID(old.getUUID());h.assertTrue(h.getLevel().addFreshEntity(replacement),"Replacement native identity loaded");
        rejected(h,()->c.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO));h.assertBlockPresent(Blocks.BARREL,P);h.assertBlockPresent(Blocks.STONE,P.east());h.assertTrue(replacement.isAlive(),"New source entity not discarded through an old handle");
        var fresh=capture(h);h.assertTrue(fresh.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Fresh capture owns current glue");h.assertTrue(replacement.isRemoved(),"Current glue transferred once");h.succeed();
    }
    @GameTest public void restorationWaitsWithoutLosingOwnedPrivateInventory(GameTestHelper h)throws Exception{
        barrel(h);var c=capture(h);h.assertTrue(c.tryRemoveBlocksFromWorld(h.getLevel(),BlockPos.ZERO),"Captured owner");
        missingData(h,T,()->{rejected(h,()->restore(h,c));h.assertValueEqual(c.phase(),Contraption.Phase.REMOVED,"Owned structure remains intact");h.assertBlockPresent(Blocks.AIR,T);h.assertValueEqual(c.getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0).getCount(),7,"Private moving payload not consumed");});
        restore(h,c);h.assertTrue(ItemStack.isSameItemSameComponents(h.getBlockEntity(T,BarrelBlockEntity.class).getItem(0),payload()),"Original components restored");h.succeed();
    }
    @GameTest public void retryClassificationAndHighlightedPositionSurviveErrorCodec(GameTestHelper h){
        for(var e:List.of(AssemblyException.entitiesNotReady(h.absolutePos(P)),AssemblyException.structureChanged(h.absolutePos(P)))){
            var n=new CompoundTag();AssemblyException.write(n,h.getLevel().registryAccess(),e);var loaded=AssemblyException.read(n,h.getLevel().registryAccess());h.assertTrue(loaded.canRetry()&&loaded.getPosition().equals(h.absolutePos(P)),"Transient classification/position persisted");
            n.getCompoundOrEmpty("LastException").remove("Retryable");h.assertFalse(AssemblyException.read(n,h.getLevel().registryAccess()).canRetry(),"Legacy errors retain permanent default");
        }
        h.assertFalse(AssemblyException.unmovableBlock(h.absolutePos(P),Blocks.BEDROCK.defaultBlockState()).canRetry()||AssemblyException.unloadedChunk(h.absolutePos(P)).canRetry(),"Unrelated errors do not become polling loops");h.succeed();
    }
    @GameTest public void pendingBearingRetrySurvivesNativeLoadBeforeSetLevel(GameTestHelper h)throws Exception{
        barrel(h);h.setBlock(B,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));var bearing=h.getBlockEntity(B,MechanicalBearingBlockEntity.class);
        missingData(h,()->{bearing.assemble();h.assertTrue(!bearing.isRunning()&&bearing.getLastAssemblyException().canRetry(),"Incomplete native data defers assembly");});
        var saved=bearing.saveWithFullMetadata(h.getLevel().registryAccess());h.assertTrue(saved.getIntOr("AssemblyRetryTicks",0)>0,"Retry persisted");
        var loaded=(MechanicalBearingBlockEntity)BlockEntity.loadStatic(h.absolutePos(B),h.getBlockState(B),saved,h.getLevel().registryAccess());loaded.setLevel(h.getLevel());
        h.assertTrue(loaded.getLastAssemblyException()!=null&&loaded.getLastAssemblyException().canRetry(),"Deferred native NBT error decoded after setLevel");h.assertTrue(loaded.saveWithFullMetadata(h.getLevel().registryAccess()).getIntOr("AssemblyRetryTicks",0)>0,"Retry still pending after native load");
        MechanicalBearingBlockEntity.tick(h.getLevel(),h.absolutePos(B),h.getBlockState(B),bearing);
        h.assertValueEqual(bearing.saveWithFullMetadata(h.getLevel().registryAccess()).getIntOr("AssemblyRetryTicks",0),0,"Stopping input cancels deferred work");h.assertBlockPresent(Blocks.BARREL,P);h.succeed();
    }
    @GameTest public void permanentBearingFailureDoesNotScheduleAssemblyPolling(GameTestHelper h){
        h.setBlock(P,Blocks.BEDROCK);h.setBlock(B,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));var bearing=h.getBlockEntity(B,MechanicalBearingBlockEntity.class);bearing.assemble();
        h.assertTrue(bearing.getLastAssemblyException()!=null&&!bearing.getLastAssemblyException().canRetry(),"Ordinary movement error remains permanent");h.assertValueEqual(bearing.saveWithFullMetadata(h.getLevel().registryAccess()).getIntOr("AssemblyRetryTicks",0),0,"No endless retry for immovable blocks");h.assertBlockPresent(Blocks.BEDROCK,P);h.succeed();
    }
    @GameTest public void unknownChunksAreNeverLoadedByEntityReadinessQueries(GameTestHelper h)throws Exception{
        var far=new BlockPos(2400000,60,2400000);var checked=new HashSet<Long>();h.assertFalse(LoadedEntityAccess.isReady(h.getLevel(),far),"Unknown block/entity chunk unavailable");LoadedEntityAccess.requireGlueQueriesReady(h.getLevel(),far,checked);
        h.assertTrue(checked.isEmpty(),"Query does not invent visible chunks");h.runAfterDelay(3,()->{h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"No forced chunk ticket");h.succeed();});
    }
}
