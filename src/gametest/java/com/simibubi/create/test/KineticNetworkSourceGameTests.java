package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import com.simibubi.create.test.GeneratingKineticTestFixtures.Generator;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.HashMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;

/** Actual registered owner/native graph integration. Every case remains UNCOMPILED / UNRUN. */
public final class KineticNetworkSourceGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos P=new BlockPos(6,5,6);
    private static Generator source(GameTestHelper h,BlockPos pos) {
        h.setBlock(pos,GeneratingKineticTestFixtures.BLOCK);
        var source=h.getBlockEntity(pos,Generator.class);
        source.initialize();
        return source;
    }

    @GameTest(structure=STRUCTURE) public void mutablePublicSourceMapsAndReassignmentOwnActualStressAndSpeedCallbacks(GameTestHelper h) {
        var source=source(h,P);
        h.setBlock(P.above(),AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));
        var shaft=h.getBlockEntity(P.above(),KineticBlockEntity.class);
        KineticNetworks.track(h.getLevel(),shaft);KineticNetworks.tick(h.getLevel());
        var group=source.getOrCreateNetwork();
        h.assertTrue(group==shaft.getOrCreateNetwork()&&group==Create.TORQUE_PROPAGATOR.getOrCreateNetworkFor(source),"Source and actual native shaft share the canonical source object");
        int callbacks=source.speedCallbacks;
        group.sources.put(source,0f);group.updateCapacity();
        h.assertTrue(shaft.getSpeed()==0&&shaft.isOverStressed()&&source.speedCallbacks==callbacks+1,"Public map mutation stops actual owners and invokes one real effective-speed callback");
        group.sources=new HashMap<>(group.sources);
        group.sources.entrySet().iterator().next().setValue(80f);
        KineticNetworks.tick(h.getLevel());
        h.assertTrue(source.getSpeed()==2.5f&&shaft.getSpeed()==2.5f&&shaft.getStressCapacity()==200,"Replacement and entry mutation are consumed by the real end-level tick");
        h.assertTrue(source.speedCallbacks==callbacks+2&&source.previousEffectiveSpeed==0,"Resuming the same native owners publishes exactly one further callback");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void sourceFloatSignedAndNaNStressValuesReachActualOwnersWithoutClamping(GameTestHelper h) {
        var source=source(h,P);var group=source.getOrCreateNetwork();
        group.sources.put(source,-4f);group.members.put(source,-5f);group.updateNetwork();
        h.assertTrue(source.getStressCapacity()==-10&&source.getStressImpact()==-12.5&&source.getSpeed()==2.5f,"Signed source float totals retain source comparison semantics");
        group.sources.put(source,Float.NaN);group.members.put(source,Float.NaN);group.updateNetwork();
        h.assertTrue(Double.isNaN(source.getStressCapacity())&&Double.isNaN(source.getStressImpact())&&!source.isOverStressed(),"NaN reaches actual owner snapshots and source capacity < stress comparison remains false");
        source.setSpeed(0);
        h.assertTrue(Float.isNaN(group.getActualStressOf(source)),"Zero times NaN remains NaN in the real member map");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void applyNewSpeedUsesItsArgumentsAndOppositeIncomingSourceIsActuallyDestroyed(GameTestHelper h) {
        var first=source(h,P);
        first.applyNewSpeed(2.5f,3.75f);
        h.assertValueEqual(first.getTheoreticalSpeed(),3.75f,"Public applyNewSpeed uses the supplied transition argument independently of provider output");
        first.generate(5f);
        var incoming=source(h,P.above());
        h.assertTrue(incoming.hasSource()&&incoming.getSource().equals(first.getBlockPos()),"Second actual source is driven by the existing canonical network");
        incoming.generate(-1.25f);
        h.assertBlockPresent(Blocks.AIR,P.above());
        h.assertTrue(h.getBlockEntity(P,Generator.class)==first&&!first.isRemoved(),"Only the actual opposing incoming generator is destroyed");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void customNonlocalPropagationUsesActualSourceOwnersAndTheSameNativeGraph(GameTestHelper h) {
        var left=source(h,P);var right=source(h,P.east(3));
        right.generate(0);
        left.customTarget=right.getBlockPos();left.customRatio=2;
        right.customTarget=left.getBlockPos();right.customRatio=.5f;
        left.attachKinetics();KineticNetworks.tick(h.getLevel());
        h.assertTrue(right.getSpeed()==5&&right.getGeneratingSource().equals(left.getBlockPos()),"Source addon hook and additional loaded location feed actual native speed and provenance");
        h.assertTrue(left.getOrCreateNetwork()==right.getOrCreateNetwork(),"Custom edge shares the actual canonical stress network");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualUnloadAndFactoryReloadPreserveCanonicalTotalsWithoutDuplicatingSourceCapacity(GameTestHelper h) {
        var source=source(h,P);KineticNetworks.tick(h.getLevel());
        var group=source.getOrCreateNetwork();group.updateNetwork();
        var data=source.saveWithFullMetadata(h.getLevel().registryAccess());
        float capacity=group.calculateCapacity(),stress=group.calculateStress();int size=group.getSize();
        source.onChunkUnloaded();source.setRemoved();
        h.assertTrue(group.calculateCapacity()==capacity&&group.calculateStress()==stress&&group.getSize()==size,"Actual unload converts the exact source contributions to unloaded totals");
        var restored=(Generator)BlockEntity.loadStatic(source.getBlockPos(),source.getBlockState(),data,h.getLevel().registryAccess());
        restored.setLevel(h.getLevel());h.getLevel().setBlockEntity(restored);restored.initialize();group.updateNetwork();
        h.assertTrue(restored.getOrCreateNetwork()==group&&group.calculateCapacity()==capacity&&group.calculateStress()==stress&&group.getSize()==size,"Actual registry factory reload consumes the saved unloaded contribution exactly once");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void virtualSpeedNbtInstructionWinsOverTheOldAliasAndCannotJoinARealServerNetwork(GameTestHelper h) {
        var source=source(h,P);var group=source.getOrCreateNetwork();int size=group.getSize();
        var data=source.saveWithFullMetadata(h.getLevel().registryAccess());data.putFloat("Speed",7.5f);data.putFloat("TheoreticalSpeed",99);
        var virtual=(Generator)BlockEntity.loadStatic(source.getBlockPos(),source.getBlockState(),data,h.getLevel().registryAccess());
        virtual.markVirtual();virtual.setLevel(h.getLevel());virtual.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,h.getLevel().registryAccess(),data));
        virtual.initialize();virtual.attachKinetics();
        h.assertTrue(virtual.getTheoreticalSpeed()==7.5f&&virtual.getOrCreateNetwork()==null&&group.getSize()==size,"Actual virtual BE NBT instruction drives scene speed without mutating real torque authority");
        h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void nativeAppliedSpeedSnapshotIsValidatedAgainstGenuineSourceStateBeforeMutation(GameTestHelper h) {
        var source=source(h,P);
        var stopped=new com.simibubi.create.content.kinetics.KineticGraph.Stress(0,5,true);
        source.setNetworkState(0,2.5f,false,stopped,java.util.Map.of());
        h.assertTrue(source.getSpeed()==0&&source.getTheoreticalSpeed()==2.5f,"Source stress flag derives actual stop while retaining theoretical RPM");
        boolean rejected=false;
        try { source.setNetworkState(1,2.5f,false,new com.simibubi.create.content.kinetics.KineticGraph.Stress(100,2.5,false),java.util.Map.of()); }
        catch(IllegalArgumentException incompatible) { rejected=true; }
        h.assertTrue(rejected&&source.getSpeed()==0&&source.getStressCapacity()==0,"Contradictory independent applied RPM is rejected before actual owner mutation");
        source.setNetworkState(2.5f,2.5f,false,new com.simibubi.create.content.kinetics.KineticGraph.Stress(100,2.5,false),java.util.Map.of());
        h.assertTrue(source.getSpeed()==2.5f&&source.getTheoreticalSpeed()==2.5f,"Valid native snapshot resumes the same genuine source owner");
        h.setBlock(P.east(2),AllBlocks.SEQUENCED_GEARSHIFT);
        var sequencer=h.getBlockEntity(P.east(2),com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.class);
        var normal=new com.simibubi.create.content.kinetics.KineticGraph.Stress(100,0,false);
        sequencer.setNetworkState(16,16,false,normal,java.util.Map.of());sequencer.run(0);
        var phase=sequencer.getSequencePhase();int timer=sequencer.getTimer();float progress=sequencer.getInstructionProgress();
        h.assertTrue(phase!=null&&!sequencer.isIdle(),"Actual native sequencer owns a real persisted phase");
        sequencer.setNetworkState(0,16,true,normal,java.util.Map.of());
        com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.tick(h.getLevel(),sequencer.getBlockPos(),sequencer.getBlockState(),sequencer);
        h.assertTrue(phase.equals(sequencer.getSequencePhase())&&sequencer.getTimer()==timer&&sequencer.getInstructionProgress()==progress,"Conflict freezes actual timer/progress and phase together");
        sequencer.setNetworkState(0,0,false,normal,java.util.Map.of());
        com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity.tick(h.getLevel(),sequencer.getBlockPos(),sequencer.getBlockState(),sequencer);
        h.assertTrue(sequencer.isIdle()&&sequencer.getSequencePhase()==null,"Resolving conflict to a final stop aborts the actual instruction without replaying its saved phase");
        h.succeed();
    }

    private static MechanicalBearingBlockEntity loadedCargo(GameTestHelper h) {
        h.setBlock(P,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));
        h.setBlock(P.above(),Blocks.BARREL);
        h.getBlockEntity(P.above(),BarrelBlockEntity.class).setItem(0,new ItemStack(Items.DIAMOND,13));
        var bearing=h.getBlockEntity(P,MechanicalBearingBlockEntity.class);bearing.assemble();
        h.assertTrue(bearing.getMovedContraption()!=null,"Real bearing captures the actual barrel owner");
        return bearing;
    }
    @GameTest(structure=STRUCTURE) public void conflictDestructionRestoresActualPaidStorageBeforeTheNativeBlockDropLifecycle(GameTestHelper h) {
        var bearing=loadedCargo(h);var owner=bearing.getMovedContraption();
        h.assertTrue(KineticNetworks.destroyForConflict(bearing),"Actual paid owner restores before conflict destruction");
        h.assertBlockPresent(Blocks.AIR,P);
        h.assertValueEqual(h.getBlockEntity(P.above(),BarrelBlockEntity.class).getItem(0).getCount(),13,"Exactly the original thirteen diamonds return to the real barrel");
        h.assertTrue(owner.isRemoved()&&h.getLevel().getEntity(owner.getUUID())==null,"Original moving owner is not duplicated or left live");
        h.succeed();
    }
    @SuppressWarnings("unchecked")
    @GameTest(structure=STRUCTURE) public void unavailableActualCargoDefersConflictDestructionAndRetainsTheOriginalOwnerForRetry(GameTestHelper h)throws Exception {
        var bearing=loadedCargo(h);var owner=bearing.getMovedContraption();
        var manager=((ServerLevelEntityManagerAccessor)h.getLevel()).create$getEntityManager();
        var field=PersistentEntitySectionManager.class.getDeclaredField("chunkLoadStatuses");field.setAccessible(true);
        var statuses=(Long2ObjectMap<Object>)field.get(manager);long key=ChunkPos.pack(h.absolutePos(P.above()));var ready=statuses.get(key);
        h.assertTrue(ready!=null,"Actual native entity readiness starts present");
        try {
            statuses.remove(key);
            h.assertFalse(KineticNetworks.destroyForConflict(bearing),"Missing native entity readiness refuses restoration and therefore deletion");
            h.assertTrue(h.getBlockEntity(P,MechanicalBearingBlockEntity.class)==bearing&&bearing.isConflicted()&&bearing.getSpeed()==0&&bearing.getMovedContraption()==owner,"Same actual controller freezes while retaining original paid owner");
            h.assertTrue(bearing.saveWithFullMetadata(h.getLevel().registryAccess()).getBooleanOr("KineticConflictPending",false),"Deferred real destruction intent is persisted for a native owner reload");
            var cargo=owner.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0);
            h.assertTrue(cargo.is(Items.DIAMOND)&&cargo.getCount()==13,"Deferred original storage retains every paid diamond");
        } finally { statuses.put(key,ready); }
        KineticNetworks.tick(h.getLevel());
        h.assertBlockPresent(Blocks.AIR,P);
        h.assertValueEqual(h.getBlockEntity(P.above(),BarrelBlockEntity.class).getItem(0).getCount(),13,"Native retry restores exact cargo once before destruction");
        h.succeed();
    }
}
