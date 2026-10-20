package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.*;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.ChunkPos;

/** Staged, uncompiled source time/dual-owner tests; no synthetic disabled child implementation. */
public class ClockworkGameTests {
    private static final BlockPos B=new BlockPos(4,3,4),H=B.above(),M=B.above(2);
    private static ClockworkBearingBlockEntity setup(GameTestHelper h,boolean minute) {
        h.setBlock(B,AllBlocks.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));h.setBlock(H,Blocks.STONE);
        if(minute){h.setBlock(M,Blocks.BARREL);var stack=new ItemStack(Items.DIAMOND,7);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Minute cargo"));h.getBlockEntity(M,BarrelBlockEntity.class).setItem(0,stack);}
        return h.getBlockEntity(B,ClockworkBearingBlockEntity.class);
    }
    @SuppressWarnings("unchecked") private static void missingData(GameTestHelper h,Runnable body)throws Exception{
        var manager=((ServerLevelEntityManagerAccessor)h.getLevel()).create$getEntityManager();var field=PersistentEntitySectionManager.class.getDeclaredField("chunkLoadStatuses");field.setAccessible(true);
        var states=(Long2ObjectMap<Object>)field.get(manager);long key=ChunkPos.pack(h.absolutePos(M));h.assertTrue(h.getLevel().areEntitiesLoaded(key),"Native entity data initially loaded");
        var saved=states.remove(key);try{body.run();}finally{states.put(key,saved);}
    }
    private static final class DuplicateSecondOwner extends ClockworkBearingBlockEntity{
        private UUID firstId;
        DuplicateSecondOwner(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){super(pos,state);}
        @Override protected ControlledContraptionEntity createReservation(ServerLevel level,ClockworkContraption hand){var owner=super.createReservation(level,hand);if(hand.handType==ClockworkContraption.HandType.HOUR)firstId=owner.getUUID();else owner.setUUID(firstId);return owner;}
    }
    @GameTest public void sourceTargetsRespectNoonMidnightAndFacingSigns(GameTestHelper h) {
        h.assertTrue(ClockworkTime.hourTarget(0,false,Direction.UP)==-180&&ClockworkTime.hourTarget(6000,false,Direction.UP)==0&&ClockworkTime.hourTarget(18000,false,Direction.UP)==0,"Source twelve-hour phase includes six-hour offset");
        h.assertTrue(ClockworkTime.hourTarget(6000,true,Direction.UP)==-180&&ClockworkTime.hourTarget(12000,true,Direction.UP)==-270,"Source twenty-four-hour targets");
        h.assertTrue(ClockworkTime.minuteTarget(500,Direction.UP)==-180&&ClockworkTime.minuteTarget(500,Direction.DOWN)==180&&ClockworkTime.minuteTarget(1000,Direction.UP)==0,"Source minute quantization and axis sign");h.succeed();
    }
    @GameTest public void nativeClockAdapterPreservesFastDimensionsWithoutOverflow(GameTestHelper h) {
        h.assertTrue(ClockworkTime.cycleTime(1000,true)==1000&&ClockworkTime.cycleTime(1000,false)==0,"Legacy non-natural dimensions run at 24x shared clock time");
        h.assertTrue(ClockworkTime.cycleTime(Long.MAX_VALUE,false)==Math.floorMod(Math.floorMod(Long.MAX_VALUE,24000)*24,24000),"Large native clock totals cannot overflow before reduction");
        h.assertTrue(ClockworkTime.cycleTime(-1,true)==23999,"Negative clock time wraps safely");h.succeed();
    }
    @GameTest public void approachUsesShortestPathAndCannotOvershoot(GameTestHelper h) {
        h.assertTrue(ClockworkTime.approachSpeed(359,1,4)==2&&ClockworkTime.approachSpeed(1,359,4)==-2,"Shortest path crosses zero");
        h.assertTrue(ClockworkTime.approachSpeed(0,3,20)==3&&ClockworkTime.approachSpeed(0,-3,-20)==-3&&ClockworkTime.approachSpeed(90,90,20)==0,"Distance clamps magnitude independently of input sign");h.succeed();
    }
    @GameTest public void adjacentUngluedBlocksBecomeTwoDistinctHands(GameTestHelper h)throws Exception {
        setup(h,true);var pair=ClockworkContraption.assembleClockworkAt(h.getLevel(),h.absolutePos(B),Direction.UP);
        h.assertTrue(pair!=null&&pair.getRight()!=null&&pair.getLeft().getBlocks().size()==1&&pair.getRight().getBlocks().size()==1,"Hour exclusion prevents merging the adjacent unglued minute hand");
        h.assertTrue(pair.getLeft().handType==ClockworkContraption.HandType.HOUR&&pair.getRight().handType==ClockworkContraption.HandType.MINUTE&&pair.getRight().offset==1,"Source hand metadata");
        h.assertTrue(pair.getLeft().anchor.equals(h.absolutePos(H))&&pair.getRight().anchor.equals(h.absolutePos(M)),"Each hand owns a separate anchor");h.succeed();
    }
    @GameTest public void emptySecondaryCaptureDoesNotCreateAnEmptyHand(GameTestHelper h)throws Exception {
        setup(h,false);var pair=ClockworkContraption.assembleClockworkAt(h.getLevel(),h.absolutePos(B),Direction.UP);h.assertTrue(pair!=null&&pair.getRight()==null&&pair.getLeft().getBlocks().size()==1,"Source single-hand clock");h.succeed();
    }
    @GameTest public void nativeAssemblyCreatesTwoRealOwnersAndTransfersMinuteCargo(GameTestHelper h) {
        var bearing=setup(h,true);bearing.assemble();var a=bearing.getHourHand();var b=bearing.getMinuteHand();
        h.assertTrue(bearing.isRunning()&&a!=null&&b!=null&&a!=b&&!a.getUUID().equals(b.getUUID()),"Native level accepted two distinct owned entities");h.assertBlockPresent(Blocks.AIR,H);h.assertBlockPresent(Blocks.AIR,M);
        var cargo=b.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0);
        h.assertTrue(cargo.is(Items.DIAMOND)&&cargo.getCount()==7&&cargo.has(DataComponents.CUSTOM_NAME),"Exact component-bearing cargo moved into minute owner");h.succeed();
    }
    @GameTest public void bothHandsRestoreAtTheirOriginalAnchorsWithExactCargo(GameTestHelper h) {
        var bearing=setup(h,true);bearing.assemble();h.assertTrue(bearing.disassemble(),"Paired preflight and restoration");h.assertBlockPresent(Blocks.STONE,H);h.assertBlockPresent(Blocks.BARREL,M);
        var cargo=h.getBlockEntity(M,BarrelBlockEntity.class).getItem(0);h.assertTrue(cargo.getCount()==7&&cargo.has(DataComponents.CUSTOM_NAME)&&!bearing.isRunning(),"Restoration preserves cargo/components and clears running state");h.succeed();
    }
    @GameTest(maxTicks=30) public void nativeEmptyHandDispatchTogglesBothActualClockworkOwners(GameTestHelper h) {
        var bearing=setup(h,true);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);h.useBlock(B,player);
        h.runAfterDelay(4,()->{
            h.assertTrue(bearing.isRunning()&&bearing.getHourHand()!=null&&bearing.getMinuteHand()!=null,"Native item-to-empty-hand dispatch reaches both real hand reservations");
            h.useBlock(B,player);h.assertTrue(!bearing.isRunning()&&bearing.getHourHand()==null&&bearing.getMinuteHand()==null,"The second native empty-hand dispatch restores both owners");
            h.assertTrue(h.getBlockEntity(M,BarrelBlockEntity.class).getItem(0).getCount()==7,"Dispatch retains exact minute cargo");h.succeed();
        });
    }
    @GameTest public void capturedMinuteMetadataRoundTripsThroughNativeTypeFactory(GameTestHelper h)throws Exception {
        setup(h,true);var pair=ClockworkContraption.assembleClockworkAt(h.getLevel(),h.absolutePos(B),Direction.UP);var minute=pair.getRight();
        var restored=Contraption.fromNBT(h.getLevel(),minute.writeNBT(h.getLevel().registryAccess(),false),false);
        h.assertTrue(restored instanceof ClockworkContraption c&&c.handType==ClockworkContraption.HandType.MINUTE&&c.offset==1&&c.getFacing()==Direction.UP,"Registered native type factory restores second-hand identity");h.succeed();
    }
    @GameTest public void bearingNativeLoadBeforeLevelPreservesModeAndOwnedHandIds(GameTestHelper h) {
        var bearing=setup(h,true);bearing.assemble();bearing.setOperationMode(ClockworkBearingBlockEntity.ClockHands.MINUTE_FIRST);var data=bearing.saveWithFullMetadata(h.getLevel().registryAccess());
        var loaded=(ClockworkBearingBlockEntity)BlockEntity.loadStatic(h.absolutePos(B),h.getBlockState(B),data,h.getLevel().registryAccess());
        h.assertTrue(loaded.getLevel()==null&&loaded.isRunning()&&loaded.getOperationMode()==ClockworkBearingBlockEntity.ClockHands.MINUTE_FIRST,"Native load retains mode/runtime ownership before level assignment");
        loaded.setLevel(h.getLevel());h.assertTrue(loaded.getHourHand()==bearing.getHourHand()&&loaded.getMinuteHand()==bearing.getMinuteHand(),"Exact saved native owner UUIDs resolve without recapturing world blocks");h.succeed();
    }
    @GameTest public void unrelatedNativeOwnerCannotReplaceSavedHourIdentity(GameTestHelper h) {
        var bearing=setup(h,true);bearing.assemble();var first=bearing.getHourHand();var other=B.east(3);
        h.setBlock(other,AllBlocks.CLOCKWORK_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));h.setBlock(other.above(),Blocks.STONE);
        var foreign=h.getBlockEntity(other,ClockworkBearingBlockEntity.class);foreign.assemble();var wrong=foreign.getHourHand();
        h.assertTrue(wrong!=null,"Second actual hour owner exists");wrong.setControllerPos(h.absolutePos(B));
        try{bearing.attach(wrong);h.assertTrue(bearing.getHourHand()==first,"A matching hand type/facing still cannot replace the saved owner UUID");}
        finally{wrong.setControllerPos(h.absolutePos(other));}h.succeed();
    }
    @GameTest public void pendingNativeEntityDataCannotPartiallyTakeEitherHand(GameTestHelper h)throws Exception{
        var bearing=setup(h,true);missingData(h,()->{bearing.assemble();h.assertTrue(!bearing.isRunning()&&bearing.getHourHand()==null&&bearing.getMinuteHand()==null,"No partial owned hand while native data is unavailable");h.assertBlockPresent(Blocks.STONE,H);h.assertBlockPresent(Blocks.BARREL,M);h.assertValueEqual(h.getBlockEntity(M,BarrelBlockEntity.class).getItem(0).getCount(),7,"Minute cargo remains in the world");});
        bearing.assemble();h.assertTrue(bearing.isRunning()&&bearing.getHourHand()!=null&&bearing.getMinuteHand()!=null,"Retry can capture both once native data returns");h.succeed();
    }
    @GameTest public void refusedNativeEntityReservationLeavesBothSourceHandsIntact(GameTestHelper h){
        setup(h,true);var replacement=new DuplicateSecondOwner(h.absolutePos(B),h.getBlockState(B));replacement.setLevel(h.getLevel());h.getLevel().setBlockEntity(replacement);replacement.assemble();
        h.assertTrue(!replacement.isRunning()&&replacement.getHourHand()==null&&replacement.getMinuteHand()==null,"Native duplicate-UUID rejection occurs before ownership transfer");
        h.assertBlockPresent(Blocks.STONE,H);h.assertBlockPresent(Blocks.BARREL,M);h.assertValueEqual(h.getBlockEntity(M,BarrelBlockEntity.class).getItem(0).getCount(),7,"Rejected second owner cannot remove first hand or cargo");h.succeed();
    }
    @GameTest public void pendingRestorationDataRetainsBothLiveOwnersAndTheirExactCargo(GameTestHelper h)throws Exception{
        var bearing=setup(h,true);bearing.assemble();var a=bearing.getHourHand();var b=bearing.getMinuteHand();
        missingData(h,()->{h.assertFalse(bearing.disassemble(),"Paired native restoration preflight refuses unavailable data");h.assertTrue(bearing.getHourHand()==a&&bearing.getMinuteHand()==b&&a.isAlive()&&b.isAlive(),"Both original owners retained");h.assertBlockPresent(Blocks.AIR,H);h.assertBlockPresent(Blocks.AIR,M);h.assertValueEqual(b.getContraption().getStorage().getAllItemStorages().get(BlockPos.ZERO).getStackInSlot(0).getCount(),7,"Owned minute cargo untouched");});
        h.assertTrue(bearing.disassemble(),"Restoration succeeds after native data returns");h.assertValueEqual(h.getBlockEntity(M,BarrelBlockEntity.class).getItem(0).getCount(),7,"Exact world cargo restored once");h.succeed();
    }
}
