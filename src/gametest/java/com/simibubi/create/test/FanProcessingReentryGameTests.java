package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Regression: old-count outputs cannot debit later real source stock after synchronous callbacks. */
public final class FanProcessingReentryGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos SOURCE=new BlockPos(3,3,3),DEST=SOURCE.south(3);
    private static ItemStack original(GameTestHelper h,String test){var stack=new ItemStack(Items.STICK,7);stack.set(DataComponents.CUSTOM_NAME,Component.literal(FanProcessingReentryTestFixtures.PREFIX+test+" "+h.absolutePos(SOURCE)));return stack;}
    private static BarrelBlockEntity barrel(GameTestHelper h,BlockPos pos){h.setBlock(pos,Blocks.BARREL);return h.getBlockEntity(pos,BarrelBlockEntity.class);}
    private static void moveThree(GameTestHelper h,Storage<ItemVariant> source,BarrelBlockEntity destination,ItemStack original){
        try(var tx=Transaction.openNested(Transaction.getCurrentUnsafe())){
            var variant=ItemVariant.of(original);h.assertValueEqual(source.extract(variant,3,tx),3L,"Callback extracts three genuine original source items through actual native Fabric storage");
            h.assertValueEqual(ContainerStorage.of(destination,Direction.UP).insert(variant,3,tx),3L,"Same actual transaction credits three moved original sticks to real barrel");tx.commit();
        }
    }
    private static void moved(GameTestHelper h,BarrelBlockEntity destination,ItemStack original){
        var actual=destination.getItem(0);h.assertTrue(actual.is(Items.STICK)&&actual.getCount()==3&&ItemVariant.of(original).matches(actual),"Callback's legitimately moved three original named sticks remain in actual native barrel");
    }
    private static ItemEntity item(GameTestHelper h,BlockPos pos,ItemStack input){var at=Vec3.atCenterOf(h.absolutePos(pos));var item=new ItemEntity(h.getLevel(),at.x,at.y,at.z,input.copy(),0,0,0);item.setNoGravity(true);h.assertTrue(h.getLevel().addFreshEntity(item),"Actual native section admits original seven-item owner");item.setAttached(FanProcessing.PROGRESS,new FanProcessing.Progress(FanProcessing.id(FanProcessingReentryTestFixtures.TYPE),ItemVariant.of(input),7,0,Long.MIN_VALUE));return item;}
    private static void moveEntityThree(GameTestHelper h,ItemEntity source,BarrelBlockEntity destination,ItemStack input){
        h.assertTrue(h.getLevel().getEntity(source.getUUID())==source&&source.getItem().getCount()==7&&ItemVariant.of(input).matches(source.getItem()),"Callback resolves actual original native ItemEntity owner");
        var current=source.getItem();source.setItem(current.copyWithCount(4));destination.setItem(0,current.copyWithCount(3));
    }
    private static long outputs(GameTestHelper h,BlockPos pos,ItemStack original){
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(pos)).inflate(2)).stream().filter(item->!item.isRemoved()&&(item.getItem().is(Items.EMERALD)||item.getItem().is(Items.DIAMOND))&&FanProcessingReentryTestFixtures.marker(item.getItem()).equals(FanProcessingReentryTestFixtures.marker(original))).mapToLong(item->item.getItem().getCount()).sum();
    }

    @GameTest(structure=STRUCTURE)
    public void entityCallbackAndNativeExtraAdmissionCannotConvertChangedOriginalStock(GameTestHelper h)throws Exception{
        var original=original(h,"entity-process");var destination=barrel(h,DEST);var source=item(h,SOURCE,original);var completed=new AtomicInteger();
        FanProcessingReentryTestFixtures.arm(original,level->moveEntityThree(h,source,destination,original));
        try{FanProcessing.process(source,FanProcessingReentryTestFixtures.TYPE,completed::incrementAndGet);}finally{FanProcessingReentryTestFixtures.disarm(original);}
        moved(h,destination,original);
        h.assertTrue(source.getItem().is(Items.STICK)&&source.getItem().getCount()==4,"Callback moved3 must leave original4, not stale7 result; actual="+source.getItem());
        h.assertValueEqual(outputs(h,SOURCE,original),0L,"No stale callback output is admitted for changed original ItemEntity");h.assertValueEqual(completed.get(),0,"Changed entity input never completes");h.assertValueEqual(FanProcessingReentryTestFixtures.calls(original),1,"Actual registered process callback ran exactly once");

        // Independent second owner: original changes only during a real native extra admission callback.
        var admissionInput=original(h,"entity-admission");var admissionPos=SOURCE.east(4);var admissionDestination=barrel(h,DEST.east(4));var admissionSource=item(h,admissionPos,admissionInput);var witnessed=new AtomicInteger();var admissionCompleted=new AtomicInteger();
        try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity.level()==h.getLevel()&&entity instanceof ItemEntity output&&output.getItem().is(Items.DIAMOND)&&FanProcessingReentryTestFixtures.marker(output.getItem()).equals(FanProcessingReentryTestFixtures.marker(admissionInput)),entity->{witnessed.incrementAndGet();moveEntityThree(h,admissionSource,admissionDestination,admissionInput);})){FanProcessing.process(admissionSource,FanProcessingReentryTestFixtures.TYPE,admissionCompleted::incrementAndGet);}
        h.assertValueEqual(witnessed.get(),1,"Actual native section admission invokes the synchronous stock transfer callback");moved(h,admissionDestination,admissionInput);
        h.assertTrue(admissionSource.isAlive()&&admissionSource.getItem().is(Items.STICK)&&admissionSource.getItem().getCount()==4,"Admission callback's remaining four original sticks retain genuine native custody");
        h.assertValueEqual(outputs(h,admissionPos,admissionInput),0L,"Provisional extras are discarded when original input changes during actual admission");h.assertValueEqual(admissionCompleted.get(),0,"Changed source during extra admission never completes");h.succeed();
    }

    @GameTest(structure=STRUCTURE)
    public void depotCallbackTransferRejectsStaleSevenOutputsAndKeepsActualFour(GameTestHelper h){
        var original=original(h,"depot");h.setBlock(SOURCE,AllBlocks.DEPOT);var depot=h.getBlockEntity(SOURCE,DepotBlockEntity.class);var destination=barrel(h,DEST);var completed=new AtomicInteger();
        try(var tx=Transaction.openOuter()){h.assertValueEqual(depot.inventory.automation.insert(ItemVariant.of(original),7,tx),7L,"Seven original named sticks enter actual depot slot");tx.commit();}
        var held=depot.getTransportedItem();held.processedBy=FanProcessingReentryTestFixtures.TYPE;held.processingTime=0;
        FanProcessingReentryTestFixtures.arm(original,level->moveThree(h,depot.inventory.automation,destination,original));
        try{FanProcessing.process(depot.depotBehaviour,FanProcessingReentryTestFixtures.TYPE,completed::incrementAndGet);}finally{FanProcessingReentryTestFixtures.disarm(original);}
        moved(h,destination,original);var current=depot.getHeldItem();
        h.assertTrue(current.is(Items.STICK)&&current.getCount()==4,"Callback moved3 must leave canonical depot4, not stale7 output; actual="+current);
        h.assertTrue(depot.inventory.outputs.stream().allMatch(slot->slot.stack().isEmpty()),"No stale pending output enters actual depot buffers");h.assertValueEqual(completed.get(),0,"Rejected stale depot conversion never completes");h.assertValueEqual(FanProcessingReentryTestFixtures.calls(original),1,"Actual registered depot process callback ran once");h.succeed();
    }

    @GameTest(structure=STRUCTURE)
    public void beltCallbackTransferRejectsPostCallbackRebindingAndKeepsActualFour(GameTestHelper h){
        var start=SOURCE.below();var end=start.east(4);h.setBlock(start,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));h.setBlock(end,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));
        h.assertTrue(BeltConnectorItem.createBelts(h.getLevel(),h.absolutePos(start),h.absolutePos(end)),"Actual native connector establishes genuine belt storage");var controller=h.getBlockEntity(start,BeltBlockEntity.class);var segment=h.getBlockEntity(start.east(2),BeltBlockEntity.class);
        var original=original(h,"belt");var destination=barrel(h,DEST);var completed=new AtomicInteger();var storage=controller.getInventory().segment(2,Direction.UP);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(storage.insert(ItemVariant.of(original),7,tx),7L,"Seven original named sticks enter actual belt segment");tx.commit();}
        var held=controller.getInventory().getTransportedItems().getFirst();held.processedBy=FanProcessingReentryTestFixtures.TYPE;held.processingTime=0;
        FanProcessingReentryTestFixtures.arm(original,level->moveThree(h,storage,destination,original));
        try{FanProcessing.process(segment,FanProcessingReentryTestFixtures.TYPE,completed::incrementAndGet);}finally{FanProcessingReentryTestFixtures.disarm(original);}
        moved(h,destination,original);var current=controller.getInventory().getTransportedItems();h.assertValueEqual(current.size(),1,"Actual belt retains one original cargo owner");
        h.assertTrue(current.getFirst().stack.is(Items.STICK)&&current.getFirst().stack.getCount()==4,"Callback moved3 must leave actual belt4, not stale7 output rebound to4; actual="+current.getFirst().stack);
        h.assertTrue(current.getFirst().pendingFanResults==null,"Prepared stale7 output is discarded rather than rebound to post-callback4");h.assertValueEqual(completed.get(),0,"Rejected stale belt conversion never completes");h.assertValueEqual(FanProcessingReentryTestFixtures.calls(original),1,"Actual registered belt process callback ran once");h.succeed();
    }
}
