package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.pulley.*;
import com.simibubi.create.content.kinetics.KineticGraph;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

/** Authored only: real source controllers/forest/cargo and native fault-save-load-restoration. UNRUN. */
public final class ContraptionAssemblyBindingGameTests {
    private record Source(BlockPos controller,BlockPos body,BlockPos bearing,BlockPos child,BlockPos actor,boolean pulley){}
    private record Snapshot(Source source,ControlledContraptionEntity root,OrientedContraptionEntity child,
                            CompoundTag controllerData,CompoundTag rootData,CompoundTag childData,
                            MovementContext originalActor,CompoundTag actorData){}
    private record ClockSnapshot(ControlledContraptionEntity hour,ControlledContraptionEntity minute,CompoundTag controllerData,CompoundTag hourData,CompoundTag minuteData){}
    private static ItemStack stock(boolean child){var stack=new ItemStack(Items.DIAMOND,child?11:7);stack.set(DataComponents.CUSTOM_NAME,Component.literal(child?"Eleven actual binding child diamonds":"Seven actual binding root diamonds"));return stack;}
    private static boolean exact(ItemStack actual,boolean child){var expected=stock(child);return actual.getCount()==expected.getCount()&&ItemStack.isSameItemSameComponents(actual,expected);}
    private static Source setup(GameTestHelper h,boolean pulley){
        var controller=pulley?new BlockPos(3,6,3):new BlockPos(3,2,3);var body=pulley?controller.below(3):controller.above();var bearing=body.east();var child=bearing.above();var actor=body.south();
        if(pulley){h.setBlock(controller,AllBlocks.ROPE_PULLEY);h.setBlock(controller.below(),AllBlocks.ROPE);h.setBlock(controller.below(2),AllBlocks.PULLEY_MAGNET);}
        else h.setBlock(controller,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));
        h.setBlock(body,Blocks.BARREL);h.getBlockEntity(body,BarrelBlockEntity.class).setItem(0,stock(false));h.setBlock(bearing,AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));h.setBlock(child,Blocks.BARREL);h.getBlockEntity(child,BarrelBlockEntity.class).setItem(0,stock(true));h.setBlock(actor,AssemblyBindingTestFixtures.ACTOR);
        for(var end:List.of(bearing,actor))h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(body),h.absolutePos(end)))),"Real narrow source glue joins parent barrel, actor and mechanical bearing only");
        if(pulley){var be=h.getBlockEntity(controller,PulleyBlockEntity.class);be.setNetworkState(16,16,false,new KineticGraph.Stress(512,64,false),Map.of(Direction.WEST,16f));}
        return new Source(controller,body,bearing,child,actor,pulley);
    }
    private static ControlledContraptionEntity owner(GameTestHelper h,Source source){return source.pulley?h.getBlockEntity(source.controller,PulleyBlockEntity.class).getMovedContraption():h.getBlockEntity(source.controller,MechanicalBearingBlockEntity.class).getMovedContraption();}
    private static BlockEntity controller(GameTestHelper h,Source source){return h.getLevel().getBlockEntity(h.absolutePos(source.controller));}
    private static CompoundTag save(Entity actual){var out=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,actual.registryAccess());if(!actual.saveAsPassenger(out))throw new AssertionError("Actual accepted native owner refused its registered-type save");return out.buildResult();}
    private static long stock(AbstractContraptionEntity actual,boolean child){long count=0;var expected=stock(child);for(var view:actual.getContraption().getStorage().getAllItems())if(ItemStack.isSameItemSameComponents(view.getResource().toStack(),expected))count+=view.getAmount();return count;}
    private static MovementContext actor(AbstractContraptionEntity actual){return actual.getContraption().getActors().stream().filter(entry->entry.left.state().is(AssemblyBindingTestFixtures.ACTOR)).findFirst().orElseThrow().right;}
    private static void noLooseStock(GameTestHelper h,Source source){h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(source.body)).inflate(5)).stream().noneMatch(item->ItemStack.isSameItemSameComponents(item.getItem(),stock(false))||ItemStack.isSameItemSameComponents(item.getItem(),stock(true))),"No invented/dropped duplicate paid stock beside actual native forest/world owners");}
    private static Snapshot observeAndSave(GameTestHelper h,Source source,AbstractContraptionEntity observed){
        h.assertTrue(observed instanceof ControlledContraptionEntity,"Actual callback belongs to a genuine registered source controlled root");var root=(ControlledContraptionEntity)observed;
        h.assertTrue(h.getLevel().getEntity(root.getUUID())==root&&root.hasOwnedStructure()&&root.getContraption().entity==root&&root.getControllerPos().equals(h.absolutePos(source.controller)),"Actual accepted root already owns REMOVED payload and its genuine source controller position");
        var graph=root.getContraption().getStabilizedSubContraptions();h.assertValueEqual(graph.children().size(),1,"All actual source captured child identities are already durable before first callback");var childId=graph.children().keySet().iterator().next();var actual=h.getLevel().getEntity(childId);
        h.assertTrue(actual instanceof OrientedContraptionEntity,"The source child is an actual accepted native owner, never only a local coordinator Node");var child=(OrientedContraptionEntity)actual;
        h.assertTrue(child.hasOwnedStructure()&&child.getContraption().phase()==Contraption.Phase.REMOVED&&child.getContraption().entity==child&&child.getContraption() instanceof StabilizedContraption&&graph.accepts(child)&&child.getParentId().equals(root.getUUID())&&Objects.equals(child.getParentLocation(),graph.location(childId)),"All REMOVED child payloads and saved parent/location relationships are bound before source callback");
        h.assertTrue(owner(h,source)==root,"Real source controller retained its actual accepted root before actor/bounds callback");var be=controller(h,source);var beData=be.saveWithFullMetadata(h.getLevel().registryAccess());var key=source.pulley?"MovedEntity":"MovedContraption";h.assertTrue(beData.read(key,UUIDUtil.CODEC).orElseThrow().equals(root.getUUID()),"Genuine native controller save names the existing root UUID before callback can throw");
        h.assertValueEqual(stock(root,false),7L,"Exactly seven original named root diamonds belong to accepted native root");h.assertValueEqual(stock(child,true),11L,"Exactly eleven original named child diamonds belong to its separate accepted native owner");h.assertValueEqual(stock(root,true)+stock(child,false),0L,"Root and child do not carry a copied second payload");
        for(var pos:List.of(source.body,source.bearing,source.child,source.actor))h.assertBlockPresent(Blocks.AIR,pos);noLooseStock(h,source);
        var rootData=save(root);var childData=save(child);var relation=(CompoundTag)rootData.getCompoundOrEmpty("Contraption").getListOrEmpty("SubContraptions").getFirst();h.assertTrue(relation.read("Id",UUIDUtil.CODEC).orElseThrow().equals(childId)&&!relation.contains("Contraption")&&childData.read("ParentContraption",UUIDUtil.CODEC).orElseThrow().equals(root.getUUID()),"Real native saves retain identity-only parent journal and one actual child structure");
        // Native passenger order may follow actor/bounds publication. Both real owners are saved;
        // reload uses an existing native passenger payload once, or the separate actual child save.
        // No paid item/context copy is ever supplied as a substitute material owner.
        var actualActor=actor(root);h.assertTrue(actualActor!=null&&actualActor.contraption==root.getContraption()&&actualActor.world==h.getLevel(),"Actor context is the genuine original lifecycle context on its actual bound source");return new Snapshot(source,root,child,beData,rootData,childData,actualActor,actualActor.data.copy());
    }
    private static void reloadAndRestore(GameTestHelper h,Snapshot snapshot,boolean automatic){
        var source=snapshot.source;var rootId=snapshot.root.getUUID();var childId=snapshot.child.getUUID();snapshot.root.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);snapshot.child.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);h.assertTrue(h.getLevel().getEntity(rootId)==null&&h.getLevel().getEntity(childId)==null,"Genuine native unload retires both old registered instances without restoring cargo");
        var current=controller(h,source);var restoredBE=BlockEntity.loadStatic(current.getBlockPos(),current.getBlockState(),snapshot.controllerData,h.getLevel().registryAccess());h.assertTrue(restoredBE!=null&&restoredBE!=current&&(source.pulley?restoredBE instanceof PulleyBlockEntity:restoredBE instanceof MechanicalBearingBlockEntity),"Actual native BE type factory/load replaces old cached controller owner");h.getLevel().removeBlockEntity(current.getBlockPos());h.getLevel().setBlockEntity(restoredBE);
        var loadedRoot=EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),snapshot.rootData),h.getLevel(),EntitySpawnReason.LOAD,EntityProcessor.NOP);h.assertTrue(loadedRoot instanceof ControlledContraptionEntity&&loadedRoot.getUUID().equals(rootId)&&loadedRoot.getType()==(source.pulley?AllEntityTypes.LINEAR_CONTRAPTION:AllEntityTypes.CONTROLLED_CONTRAPTION)&&h.getLevel().tryAddFreshEntityWithPassengers(loadedRoot),"Genuine native registry factory/recursive load returns the same saved root and its actual saved passengers");var restoredRoot=(ControlledContraptionEntity)loadedRoot;
        var actualChild=h.getLevel().getEntity(childId);
        if(actualChild==null){var loaded=EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),snapshot.childData),h.getLevel(),EntitySpawnReason.LOAD,EntityProcessor.NOP);h.assertTrue(loaded instanceof OrientedContraptionEntity&&loaded.getUUID().equals(childId)&&h.getLevel().addFreshEntity(loaded),"An actual separately saved child returns through the native registered type factory once");actualChild=loaded;}
        h.assertTrue(actualChild instanceof OrientedContraptionEntity&&actualChild.getType()==AllEntityTypes.ORIENTED_CONTRAPTION,"Exactly the saved real oriented child owner is loaded");var restoredChild=(OrientedContraptionEntity)actualChild;
        h.assertTrue(owner(h,source)==restoredRoot&&restoredRoot.getContraption().getStabilizedSubContraptions().accepts(restoredChild)&&restoredChild.getParentId().equals(rootId),"Fresh actual controller and both actual native loaded owners rejoin only their saved identities");h.assertTrue(stock(restoredRoot,false)==7&&stock(restoredChild,true)==11&&stock(restoredRoot,true)+stock(restoredChild,false)==0,"Native load restores exactly original stock without manually recreating actor contexts or inventory");
        h.assertTrue(restoredRoot.getContraption().getStabilizedSubContraptions().isRecovering()&&actor(restoredRoot).data.equals(snapshot.actorData),"Actual failed graph reload is recovery-only and preserves native serialized actor data without replaying startMoving");
        restoredRoot.getContraption().getStabilizedSubContraptions().reattachLoaded(h.getLevel());h.assertTrue(restoredChild.getVehicle()==restoredRoot,"Existing source native reattachment mounts the actual loaded child");
        if(automatic){
            h.assertFalse(snapshot.controllerData.getBooleanOr(source.pulley?"RecoveringAssembly":"GraphRecovery",false),"Real original controller has no parallel recovery flag: persisted native graph alone must trigger recovery");
            if(source.pulley)PulleyBlockEntity.tick(h.getLevel(),h.absolutePos(source.controller),h.getBlockState(source.controller),(PulleyBlockEntity)restoredBE);
            else MechanicalBearingBlockEntity.tick(h.getLevel(),h.absolutePos(source.controller),h.getBlockState(source.controller),(MechanicalBearingBlockEntity)restoredBE);
        }else h.assertTrue(source.pulley?((PulleyBlockEntity)restoredBE).disassemble():((MechanicalBearingBlockEntity)restoredBE).disassemble(),"Real source manual controller restoration accepts the genuine loaded graph");
        h.assertTrue(restoredRoot.isRemoved()&&restoredChild.isRemoved()&&owner(h,source)==null,automatic?"One genuine source controller tick detects persisted failed graph and retires both loaded owners without manual disassembly":"Actual source controller restoration retires both genuine loaded owners");h.assertTrue(exact(h.getBlockEntity(source.body,BarrelBlockEntity.class).getItem(0),false)&&exact(h.getBlockEntity(source.child,BarrelBlockEntity.class).getItem(0),true),"Native world barrels regain exactly seven plus eleven original component-bearing diamonds");
        h.assertTrue(stock(restoredRoot,false)+stock(restoredChild,true)==0,"Retained old native storage handles no longer expose world-owned paid material");noLooseStock(h,source);h.assertFalse(ContraptionAssemblyGraph.restore(restoredRoot),"Retired original native graph cannot restore a second paid copy");
    }
    private static void run(GameTestHelper h,boolean pulley,AssemblyBindingTestFixtures.Boundary boundary)throws Exception{
        run(h,pulley,boundary,false);
    }
    private static void run(GameTestHelper h,boolean pulley,AssemblyBindingTestFixtures.Boundary boundary,boolean automatic)throws Exception{
        boolean previous=ContraptionSettings.stabiliseStableContraptions;ContraptionSettings.stabiliseStableContraptions=true;
        try{var source=setup(h,pulley);var snapshot=new AtomicReference<Snapshot>();var exact=new AssemblyBindingTestFixtures.ScopedFault();boolean thrown=false;
            try(var scope=AssemblyBindingTestFixtures.fault(boundary,actual->snapshot.set(observeAndSave(h,source,actual)),exact)){
                try{if(pulley)h.getBlockEntity(source.controller,PulleyBlockEntity.class).assemble();else h.getBlockEntity(source.controller,MechanicalBearingBlockEntity.class).assemble();}catch(AssemblyBindingTestFixtures.ScopedFault actual){h.assertTrue(actual==exact,"Only exact scoped real source callback failure is observed");thrown=true;}
            }
            h.assertTrue(thrown&&snapshot.get()!=null,"Actual registered source actor/bounds callback executed after genuine forest transfer");var inside=snapshot.get();h.assertTrue(inside.root.getContraption().getStabilizedSubContraptions().isRecovering()&&actor(inside.root)==inside.originalActor&&inside.originalActor.data.equals(inside.actorData),"Actual callback failure keeps original actor context and durable whole-forest recovery metadata");var afterFailure=observeAndSave(h,source,inside.root);reloadAndRestore(h,afterFailure,automatic);h.succeed();
        }finally{ContraptionSettings.stabiliseStableContraptions=previous;}
    }
    @GameTest public void genuineMechanicalBearingFirstOwnedActorFailureSavesEveryNativeOwnerAndRestoresOriginalStock(GameTestHelper h)throws Exception{run(h,false,AssemblyBindingTestFixtures.Boundary.ACTOR_START);}
    @GameTest public void genuinePulleyFirstOwnedActorFailureSavesEveryNativeOwnerAndRestoresOriginalStock(GameTestHelper h)throws Exception{run(h,true,AssemblyBindingTestFixtures.Boundary.ACTOR_START);}
    @GameTest public void genuineMechanicalBearingFirstOwnedBoundsFailureSavesEveryNativeOwnerAndRestoresOriginalStock(GameTestHelper h)throws Exception{run(h,false,AssemblyBindingTestFixtures.Boundary.BOUNDS);}
    @GameTest public void genuinePulleyFirstOwnedBoundsFailureSavesEveryNativeOwnerAndRestoresOriginalStock(GameTestHelper h)throws Exception{run(h,true,AssemblyBindingTestFixtures.Boundary.BOUNDS);}
    @GameTest public void genuineMechanicalBearingTickRecoversOnlyActualFailedGraphAfterNativeReload(GameTestHelper h)throws Exception{run(h,false,AssemblyBindingTestFixtures.Boundary.BOUNDS,true);}
    @GameTest public void genuinePulleyTickRecoversOnlyActualFailedGraphAfterNativeReload(GameTestHelper h)throws Exception{run(h,true,AssemblyBindingTestFixtures.Boundary.BOUNDS,true);}
    private static ClockSnapshot saveClock(GameTestHelper h,BlockPos pos,AbstractContraptionEntity callback){
        var be=h.getBlockEntity(pos,ClockworkBearingBlockEntity.class);var hour=be.getHourHand();var minute=be.getMinuteHand();h.assertTrue(hour!=null&&minute!=null&&hour!=minute&&(callback==hour||callback==minute)&&h.getLevel().getEntity(hour.getUUID())==hour&&h.getLevel().getEntity(minute.getUUID())==minute,"Both genuine source clock hands are already distinct accepted native owners before callback");
        h.assertTrue(hour.hasOwnedStructure()&&minute.hasOwnedStructure()&&hour.getContraption().entity==hour&&minute.getContraption().entity==minute&&hour.getContraption() instanceof ClockworkContraption a&&a.handType==ClockworkContraption.HandType.HOUR&&minute.getContraption() instanceof ClockworkContraption b&&b.handType==ClockworkContraption.HandType.MINUTE&&hour.getControllerPos().equals(h.absolutePos(pos))&&minute.getControllerPos().equals(h.absolutePos(pos)),"Actual hour/minute source metadata and all removed payloads are bound before bounds callback");
        h.assertTrue(stock(hour,false)==7&&stock(minute,true)==11&&stock(hour,true)+stock(minute,false)==0,"Actual source clock roots own exactly their two original component-bearing barrel stocks");h.assertBlockPresent(Blocks.AIR,pos.above());h.assertBlockPresent(Blocks.AIR,pos.above(2));var data=be.saveWithFullMetadata(h.getLevel().registryAccess());h.assertTrue(data.read("HourOwner",UUIDUtil.CODEC).orElseThrow().equals(hour.getUUID())&&data.read("MinuteOwner",UUIDUtil.CODEC).orElseThrow().equals(minute.getUUID()),"Genuine source clock BE saves both actual native UUIDs before callback failure");return new ClockSnapshot(hour,minute,data,save(hour),save(minute));
    }
    @GameTest public void genuineClockworkTickRecoversBothActualGraphOnlyFailedHandsAfterNativeReload(GameTestHelper h)throws Exception{
        var pos=new BlockPos(3,2,3);h.setBlock(pos,AllBlocks.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));h.setBlock(pos.above(),Blocks.BARREL);h.setBlock(pos.above(2),Blocks.BARREL);h.getBlockEntity(pos.above(),BarrelBlockEntity.class).setItem(0,stock(false));h.getBlockEntity(pos.above(2),BarrelBlockEntity.class).setItem(0,stock(true));var be=h.getBlockEntity(pos,ClockworkBearingBlockEntity.class);var first=new AtomicReference<ClockSnapshot>();var exact=new AssemblyBindingTestFixtures.ScopedFault();boolean thrown=false;
        try(var scope=AssemblyBindingTestFixtures.fault(AssemblyBindingTestFixtures.Boundary.BOUNDS,actual->first.set(saveClock(h,pos,actual)),exact)){
            try{be.assemble();}catch(AssemblyBindingTestFixtures.ScopedFault actual){h.assertTrue(actual==exact,"Actual source clock bounds callback throws exact scoped failure");thrown=true;}
        }
        h.assertTrue(thrown&&first.get()!=null&&first.get().hour.getContraption().getStabilizedSubContraptions().isRecovering()&&first.get().minute.getContraption().getStabilizedSubContraptions().isRecovering()&&!be.isRecovering(),"Actual failure marks both real hand graphs while genuine controller recovery flag remains false");var failed=saveClock(h,pos,first.get().hour);h.assertFalse(failed.controllerData.getBooleanOr("Recovering",false),"Native post-error save proves graph-only recovery without invented controller state");var hourId=failed.hour.getUUID();var minuteId=failed.minute.getUUID();failed.hour.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);failed.minute.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        var restored=(ClockworkBearingBlockEntity)BlockEntity.loadStatic(h.absolutePos(pos),h.getBlockState(pos),failed.controllerData,h.getLevel().registryAccess());h.assertTrue(restored!=null&&restored!=be,"Actual native source clock BE factory restores saved hand identities");h.getLevel().removeBlockEntity(h.absolutePos(pos));h.getLevel().setBlockEntity(restored);
        for(var saved:List.of(failed.hourData,failed.minuteData)){var loaded=EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING,h.getLevel().registryAccess(),saved),h.getLevel(),EntitySpawnReason.LOAD,EntityProcessor.NOP);h.assertTrue(loaded instanceof ControlledContraptionEntity&&loaded.getType()==AllEntityTypes.CONTROLLED_CONTRAPTION&&h.getLevel().tryAddFreshEntityWithPassengers(loaded),"Real source hour/minute return through native registered entity factory, never substituted fake hands");}
        var hour=restored.getHourHand();var minute=restored.getMinuteHand();h.assertTrue(hour!=null&&minute!=null&&hour.getUUID().equals(hourId)&&minute.getUUID().equals(minuteId)&&hour.getContraption().getStabilizedSubContraptions().isRecovering()&&minute.getContraption().getStabilizedSubContraptions().isRecovering()&&!restored.isRecovering()&&stock(hour,false)==7&&stock(minute,true)==11,"Fresh native clock reconnects exactly both saved graph-only failed original stocks");ClockworkBearingBlockEntity.tick(h.getLevel(),h.absolutePos(pos),h.getBlockState(pos),restored);
        h.assertTrue(hour.isRemoved()&&minute.isRemoved()&&restored.getHourHand()==null&&restored.getMinuteHand()==null&&!restored.isRunning()&&exact(h.getBlockEntity(pos.above(),BarrelBlockEntity.class).getItem(0),false)&&exact(h.getBlockEntity(pos.above(2),BarrelBlockEntity.class).getItem(0),true),"One actual native clock tick detects failed graph-only hands and restores exactly original seven plus eleven diamonds");h.assertTrue(stock(hour,false)+stock(minute,true)==0&&!ContraptionAssemblyGraph.restore(hour)&&!ContraptionAssemblyGraph.restore(minute),"Retired native hands expose no second paid stock or repeat world restoration");h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(pos)).inflate(5)).stream().noneMatch(item->ItemStack.isSameItemSameComponents(item.getItem(),stock(false))||ItemStack.isSameItemSameComponents(item.getItem(),stock(true))),"No loose native duplicate clock stock");h.succeed();
    }
}
