package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import java.util.*;

public class FluidTankGameTests {
    private static final BlockPos ORIGIN = new BlockPos(2, 2, 2);
    private static final long B = FluidConstants.BUCKET;
    private static FluidTankBlockEntity part(GameTestHelper h, BlockPos pos) { return h.getBlockEntity(pos, FluidTankBlockEntity.class); }
    private static FluidTankBlockEntity tank(GameTestHelper h, BlockPos pos, boolean creative) { h.setBlock(pos, creative ? AllBlocks.CREATIVE_FLUID_TANK : AllBlocks.FLUID_TANK); return part(h, pos); }
    private static FluidTankBlockEntity build(GameTestHelper h, int width, int height) {
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) tank(h, ORIGIN.offset(x, y, z), false);
        return part(h, ORIGIN);
    }
    private static long insert(Storage<FluidVariant> storage, FluidVariant fluid, long amount, boolean commit) {
        try (var tx = Transaction.openOuter()) { long result = storage.insert(fluid, amount, tx); if (commit) tx.commit(); return result; }
    }
    private static long extract(Storage<FluidVariant> storage, FluidVariant fluid, long amount, boolean commit) {
        try (var tx = Transaction.openOuter()) { long result = storage.extract(fluid, amount, tx); if (commit) tx.commit(); return result; }
    }
    private static long total(GameTestHelper h, int width, int height) {
        var masters = new HashSet<BlockPos>(); long total = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < width; z++) {
            var be = h.getLevel().getBlockEntity(h.absolutePos(ORIGIN.offset(x, y, z))); if (!(be instanceof FluidTankBlockEntity part)) continue;
            var master = part.getControllerBE(); if (master != null && masters.add(master.getBlockPos())) total += master.getTankInventory().getFluidAmount();
        }
        return total;
    }
    @GameTest
    public void singleTankCapacityAndTransactionalRollback(GameTestHelper h) {
        var tank = tank(h, ORIGIN, false); var water = FluidVariant.of(Fluids.WATER);
        h.assertValueEqual(insert(tank.storage(), water, B, false), B, "Simulated fill"); h.assertTrue(tank.getTankInventory().isEmpty(), "Aborted fill empty");
        h.assertValueEqual(insert(tank.storage(), water, 10 * B, true), 8 * B, "Eight bucket capacity");
        h.assertValueEqual(extract(tank.storage(), water, 2 * B, false), 2 * B, "Simulated drain"); h.assertValueEqual(tank.getTankInventory().getFluidAmount(), 8 * B, "Drain rollback");
        h.assertValueEqual(insert(tank.storage(), FluidVariant.of(Fluids.LAVA), B, true), 0L, "Different fluids do not mix"); h.succeed();
    }
    @GameTest(maxTicks = 30)
    public void verticalColumnSharesCapacityAcrossAllFaces(GameTestHelper h) {
        build(h, 1, 4);
        h.runAfterDelay(8, () -> {
            var master = part(h, ORIGIN); h.assertValueEqual(master.getHeight(), 4, "Column height"); h.assertValueEqual(master.getWidth(), 1, "Column width");
            for (var face : Direction.values()) {
                var storage = FluidStorage.SIDED.find(h.getLevel(), h.absolutePos(ORIGIN.above(2)), face);
                h.assertTrue(storage != null, "Fluid handler on " + face); h.assertValueEqual(insert(storage, FluidVariant.of(Fluids.WATER), B, true), B, "All six faces insert");
            }
            h.assertValueEqual(master.getTankInventory().getFluidAmount(), 6 * B, "Shared reservoir"); h.assertValueEqual(master.getTankInventory().getCapacity(), 32 * B, "Shared capacity");
            h.assertTrue(h.getBlockState(ORIGIN).getValue(FluidTankBlock.BOTTOM), "Bottom lid"); h.assertFalse(h.getBlockState(ORIGIN).getValue(FluidTankBlock.TOP), "Bottom not top");
            h.assertTrue(h.getBlockState(ORIGIN.above(3)).getValue(FluidTankBlock.TOP), "Top lid"); h.succeed();
        });
    }
    @GameTest(maxTicks = 30)
    public void twoByTwoTankHasCornerWindowsAndOneController(GameTestHelper h) {
        build(h, 2, 3);
        h.runAfterDelay(8, () -> {
            var master = part(h, ORIGIN); h.assertValueEqual(master.getWidth(), 2, "Square width"); h.assertValueEqual(master.getHeight(), 3, "Square height");
            h.assertValueEqual(master.getTankInventory().getCapacity(), 96 * B, "Twelve cells");
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) for (int y = 0; y < 3; y++) h.assertValueEqual(part(h, ORIGIN.offset(x,y,z)).getController(), h.absolutePos(ORIGIN), "Same controller");
            h.assertValueEqual(h.getBlockState(ORIGIN).getValue(FluidTankBlock.SHAPE), FluidTankBlock.Shape.WINDOW_NW, "NW window");
            h.assertValueEqual(h.getBlockState(ORIGIN.offset(1,0,1)).getValue(FluidTankBlock.SHAPE), FluidTankBlock.Shape.WINDOW_SE, "SE window"); h.succeed();
        });
    }
    @GameTest(maxTicks = 30)
    public void threeByThreeTankUsesSideCentreWindows(GameTestHelper h) {
        build(h, 3, 2);
        h.runAfterDelay(10, () -> {
            var master = part(h, ORIGIN); h.assertValueEqual(master.getWidth(), 3, "Maximum width"); h.assertValueEqual(master.getHeight(), 2, "Two layers");
            h.assertValueEqual(h.getBlockState(ORIGIN.offset(1,0,0)).getValue(FluidTankBlock.SHAPE), FluidTankBlock.Shape.WINDOW, "Side centre window");
            h.assertValueEqual(h.getBlockState(ORIGIN).getValue(FluidTankBlock.SHAPE), FluidTankBlock.Shape.PLAIN, "Corner solid");
            h.assertValueEqual(master.getTankInventory().getCapacity(), 144 * B, "Eighteen cells"); h.succeed();
        });
    }
    @GameTest(maxTicks = 40)
    public void incompleteSquareDoesNotBecomeRectangularTank(GameTestHelper h) {
        tank(h, ORIGIN, false); tank(h, ORIGIN.east(), false); tank(h, ORIGIN.south(), false);
        h.runAfterDelay(10, () -> { h.assertValueEqual(part(h, ORIGIN).getWidth(), 1, "L shape not square"); tank(h, ORIGIN.east().south(), false); });
        h.runAfterDelay(20, () -> { h.assertValueEqual(part(h, ORIGIN).getWidth(), 2, "Final cell completes square"); h.succeed(); });
    }
    @GameTest(maxTicks = 40)
    public void mergingFilledColumnsPreservesAllFluid(GameTestHelper h) {
        var first = tank(h, ORIGIN, false); var second = tank(h, ORIGIN.above(2), false); insert(first.storage(), FluidVariant.of(Fluids.WATER), 5 * B, true); insert(second.storage(), FluidVariant.of(Fluids.WATER), 7 * B, true);
        tank(h, ORIGIN.above(), false);
        h.runAfterDelay(12, () -> { h.assertValueEqual(part(h, ORIGIN).getHeight(), 3, "Bridged column"); h.assertValueEqual(total(h, 1, 3), 12 * B, "No loss while merging"); h.succeed(); });
    }
    @GameTest(maxTicks = 35)
    public void emptyControllerCannotJoinIncompatibleFluids(GameTestHelper h) {
        tank(h, ORIGIN, false); var water = tank(h, ORIGIN.east(), false); var lava = tank(h, ORIGIN.south(), false);
        insert(water.storage(), FluidVariant.of(Fluids.WATER), 3 * B, true); insert(lava.storage(), FluidVariant.of(Fluids.LAVA), 4 * B, true); tank(h, ORIGIN.south().east(), false);
        h.runAfterDelay(12, () -> { h.assertValueEqual(part(h, ORIGIN).getWidth(), 1, "No cross-fluid merge via empty cell"); h.assertValueEqual(total(h, 2, 1), 7 * B, "Both fluids retained"); h.succeed(); });
    }
    @GameTest(maxTicks = 45)
    public void splittingMiddleLayerRedistributesWithoutDuplication(GameTestHelper h) {
        build(h, 1, 3); h.runAfterDelay(8, () -> { insert(part(h, ORIGIN).storage(), FluidVariant.of(Fluids.WATER), 12 * B, true); h.setBlock(ORIGIN.above(), Blocks.AIR); });
        h.runAfterDelay(18, () -> { h.assertValueEqual(part(h, ORIGIN).getHeight(), 1, "Lower part split"); h.assertValueEqual(part(h, ORIGIN.above(2)).getHeight(), 1, "Upper part split"); h.assertValueEqual(total(h, 1, 3), 12 * B, "Twelve buckets retained in two surviving cells"); h.succeed(); });
    }
    @GameTest(maxTicks = 40)
    public void removingControllerMovesFluidToSurvivingCells(GameTestHelper h) {
        build(h, 1, 3); h.runAfterDelay(8, () -> { insert(part(h, ORIGIN).storage(), FluidVariant.of(Fluids.WATER), 5 * B, true); h.setBlock(ORIGIN, Blocks.AIR); });
        h.runAfterDelay(18, () -> { h.assertValueEqual(part(h, ORIGIN.above()).getHeight(), 2, "New controller at surviving bottom"); h.assertValueEqual(total(h, 1, 3), 5 * B, "Partial tank contents survive controller removal"); h.succeed(); });
    }
    @GameTest(maxTicks = 40)
    public void removalDiscardsOnlyFluidExceedingSurvivingCapacity(GameTestHelper h) {
        build(h, 1, 2); h.runAfterDelay(8, () -> { insert(part(h, ORIGIN).storage(), FluidVariant.of(Fluids.WATER), 16 * B, true); h.setBlock(ORIGIN, Blocks.AIR); });
        h.runAfterDelay(18, () -> { h.assertValueEqual(total(h, 1, 2), 8 * B, "One surviving block retains eight buckets"); h.succeed(); });
    }
    @GameTest(maxTicks = 40)
    public void cachedStorageViewFollowsControllerAfterMergeAndSplit(GameTestHelper h) {
        var upper = tank(h, ORIGIN.above(), false); var cached = upper.storage(); var view = cached.iterator().next(); tank(h, ORIGIN, false);
        h.runAfterDelay(10, () -> { h.assertValueEqual(view.getCapacity(), 16 * B, "Old view sees merged capacity"); insert(cached, FluidVariant.of(Fluids.WATER), 12 * B, true); h.setBlock(ORIGIN, Blocks.AIR); });
        h.runAfterDelay(20, () -> { h.assertValueEqual(view.getCapacity(), 8 * B, "Old view sees split capacity"); h.assertValueEqual(view.getAmount(), 8 * B, "View follows surviving reservoir"); h.succeed(); });
    }
    @GameTest(maxTicks = 35)
    public void loadingEveryPartRestoresControllerAndFluidAmount(GameTestHelper h) {
        build(h, 2, 2);
        h.runAfterDelay(10, () -> {
            insert(part(h, ORIGIN).storage(), FluidVariant.of(Fluids.WATER), 27 * B, true);
            var restored = new ArrayList<BlockEntity>();
            for (int y=0;y<2;y++) for(int x=0;x<2;x++) for(int z=0;z<2;z++) { var pos=ORIGIN.offset(x,y,z); var be=part(h,pos); restored.add(BlockEntity.loadStatic(h.absolutePos(pos),be.getBlockState(),be.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess())); }
            restored.forEach(be -> { h.getLevel().removeBlockEntity(be.getBlockPos()); h.getLevel().setBlockEntity(be); });
        });
        h.runAfterDelay(20, () -> { var master = part(h, ORIGIN); h.assertValueEqual(master.getWidth(),2,"Reload width"); h.assertValueEqual(master.getHeight(),2,"Reload height"); h.assertValueEqual(total(h,2,2),27*B,"Fluid restored once"); h.assertTrue(part(h,ORIGIN.offset(1,1,1)).structureLoaded(),"Restored members usable"); h.succeed(); });
    }
    @GameTest(maxTicks = 35)
    public void wrenchWindowToggleAppliesToWholeTank(GameTestHelper h) {
        build(h,2,2); h.runAfterDelay(8, () -> part(h,ORIGIN.offset(1,1,1)).toggleWindows());
        h.runAfterDelay(12, () -> { for(int y=0;y<2;y++) for(int x=0;x<2;x++) for(int z=0;z<2;z++) h.assertValueEqual(h.getBlockState(ORIGIN.offset(x,y,z)).getValue(FluidTankBlock.SHAPE),FluidTankBlock.Shape.PLAIN,"All windows closed"); part(h,ORIGIN).toggleWindows(); });
        h.runAfterDelay(18, () -> { h.assertValueEqual(h.getBlockState(ORIGIN).getValue(FluidTankBlock.SHAPE),FluidTankBlock.Shape.WINDOW_NW,"Windows restored"); h.succeed(); });
    }
    @GameTest(maxTicks = 30)
    public void lavaLightsOnlyFluidHeightAndWindowToggleExtinguishesHull(GameTestHelper h) {
        build(h,1,3); h.runAfterDelay(8, () -> insert(part(h,ORIGIN).storage(),FluidVariant.of(Fluids.LAVA),4*B,true));
        h.runAfterDelay(12, () -> { h.assertValueEqual(h.getBlockState(ORIGIN).getLightEmission(),12,"Lava bright lower layer"); h.assertValueEqual(h.getBlockState(ORIGIN.above(2)).getLightEmission(),1,"Unfilled hull faintly lit"); part(h,ORIGIN).toggleWindows(); });
        h.runAfterDelay(18, () -> { h.assertValueEqual(h.getBlockState(ORIGIN).getLightEmission(),0,"Closed tank not luminous"); h.succeed(); });
    }
    @GameTest(maxTicks = 30)
    public void comparatorReadsSharedFillFromEveryMember(GameTestHelper h) {
        build(h,1,2); h.runAfterDelay(8, () -> { insert(part(h,ORIGIN).storage(),FluidVariant.of(Fluids.WATER),8*B,true);
            for(int y=0;y<2;y++) h.assertValueEqual(h.getBlockState(ORIGIN.above(y)).getAnalogOutputSignal(h.getLevel(),h.absolutePos(ORIGIN.above(y)),Direction.NORTH),8,"Half-full shared comparator"); h.succeed(); });
    }
    @GameTest(maxTicks = 35)
    public void creativeTankSourcesSelectedFluidAndVoidsInsertedFluid(GameTestHelper h) {
        var tank=(CreativeFluidTankBlockEntity)tank(h,ORIGIN,true); var inventory=(CreativeFluidTankBlockEntity.CreativeSmartFluidTank)tank.getTankInventory();
        h.assertValueEqual(insert(tank.storage(),FluidVariant.of(Fluids.LAVA),20*B,true),20*B,"Creative sink accepts all"); h.assertTrue(inventory.isEmpty(),"Automation does not select creative fluid");
        inventory.setContainedFluid(new FluidStack(FluidVariant.of(Fluids.WATER),B));
        h.assertValueEqual(extract(tank.storage(),FluidVariant.of(Fluids.WATER),3*B,true),3*B,"Creative source output"); h.assertValueEqual(inventory.getFluidAmount(),8*B,"Source inexhaustible");
        h.assertValueEqual(extract(tank.storage(),FluidVariant.of(Fluids.LAVA),B,true),0L,"Wrong fluid unavailable"); h.succeed();
    }
    @GameTest(maxTicks = 30)
    public void creativeAndRegularTanksRemainSeparate(GameTestHelper h) {
        tank(h,ORIGIN,false); tank(h,ORIGIN.above(),true); h.runAfterDelay(10, () -> { h.assertTrue(part(h,ORIGIN).isController(),"Regular separate"); h.assertTrue(part(h,ORIGIN.above()).isController(),"Creative separate"); h.succeed(); });
    }
    @GameTest(maxTicks = 50)
    public void deferredSplitPersistsFluidAndRestoresAfterControllerBecomesAvailable(GameTestHelper h) {
        build(h,1,3); final net.minecraft.nbt.CompoundTag[] saved={null};
        h.runAfterDelay(8,()->{
            var master=part(h,ORIGIN);insert(master.storage(),FluidVariant.of(Fluids.WATER),12*B,true);saved[0]=master.saveWithFullMetadata(h.getLevel().registryAccess());
            var followers=new ArrayList<BlockEntity>();
            for(int y=1;y<3;y++){var be=part(h,ORIGIN.above(y));var followerTag=be.saveWithFullMetadata(h.getLevel().registryAccess());followerTag.remove("Size");followerTag.remove("Height");followers.add(BlockEntity.loadStatic(be.getBlockPos(),be.getBlockState(),followerTag,h.getLevel().registryAccess()));}
            h.setBlock(ORIGIN,Blocks.STONE);
            for(var be:followers){h.getLevel().removeBlockEntity(be.getBlockPos());h.getLevel().setBlockEntity(be);}
            h.setBlock(ORIGIN.above(2),Blocks.AIR);
            var data=TankRecoveryData.get(h.getLevel()); h.assertTrue(data.contains(h.absolutePos(ORIGIN)),"Unresolved legacy split stored in world data");
            h.assertValueEqual(insert(part(h,ORIGIN.above()).storage(),FluidVariant.of(Fluids.WATER),B,true),0L,"Transfer waits for controller");
            var ops=h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var nbt=TankRecoveryData.CODEC.encodeStart(ops,data).getOrThrow();
            var loaded=TankRecoveryData.CODEC.parse(ops,nbt).getOrThrow();h.getLevel().getDataStorage().set(TankRecoveryData.TYPE,loaded);
        });
        h.runAfterDelay(16,()->{
            h.setBlock(ORIGIN,AllBlocks.FLUID_TANK);
            var loaded=BlockEntity.loadStatic(h.absolutePos(ORIGIN),h.getBlockState(ORIGIN),saved[0],h.getLevel().registryAccess());h.getLevel().setBlockEntity(loaded);
        });
        h.runAfterDelay(28,()->{h.assertFalse(TankRecoveryData.get(h.getLevel()).contains(h.absolutePos(ORIGIN)),"Recovery completed");h.assertValueEqual(total(h,1,3),12*B,"Legacy follower reconstructs full region and twelve buckets from missing controller");h.assertValueEqual(part(h,ORIGIN).getHeight(),2,"Surviving column rebuilt");h.succeed();});
    }
    @GameTest(maxTicks = 45)
    public void deferredRecoveryOwnsContentsOnceAcrossSerialization(GameTestHelper h) {
        build(h,2,2);
        h.runAfterDelay(8,()->{
            var master=part(h,ORIGIN);insert(master.storage(),FluidVariant.of(Fluids.LAVA),29*B,true);
            var data=TankRecoveryData.get(h.getLevel());data.deferRemoval(master);
            h.assertValueEqual(master.getTankInventory().getFluidAmount(),0L,"Fluid moved to recovery, not copied");
            var ops=h.getLevel().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            h.getLevel().getDataStorage().set(TankRecoveryData.TYPE,TankRecoveryData.CODEC.parse(ops,TankRecoveryData.CODEC.encodeStart(ops,data).getOrThrow()).getOrThrow());h.setBlock(ORIGIN,Blocks.AIR);
        });
        h.runAfterDelay(24,()->{h.assertValueEqual(total(h,2,2),29*B,"Pending contents recovered exactly once");h.succeed();});
    }
    @GameTest(maxTicks = 45)
    public void fluidComponentsSurviveMergeSplitAndBlockEntitySave(GameTestHelper h) {
        var named=FluidVariant.of(Fluids.WATER,net.minecraft.core.component.DataComponentPatch.builder().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Tagged water")).build());
        var tank=tank(h,ORIGIN,false);insert(tank.storage(),named,5*B,true);tank(h,ORIGIN.above(),false);
        h.runAfterDelay(10,()->{h.assertValueEqual(part(h,ORIGIN).getTankInventory().getResource(),named,"Components after merge");h.setBlock(ORIGIN,Blocks.AIR);});
        h.runAfterDelay(20,()->{
            var master=part(h,ORIGIN.above());h.assertValueEqual(master.getTankInventory().getResource(),named,"Components after controller removal");
            var restored=(FluidTankBlockEntity)BlockEntity.loadStatic(master.getBlockPos(),master.getBlockState(),master.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertValueEqual(restored.getTankInventory().getResource(),named,"Components persisted");h.assertValueEqual(restored.getTankInventory().getFluidAmount(),5*B,"Amount persisted");h.succeed();
        });
    }
    @GameTest(maxTicks = 40)
    public void maximumHeightIsThirtyTwoBlocks(GameTestHelper h) {
        build(h,1,33);h.runAfterDelay(15,()->{h.assertValueEqual(part(h,ORIGIN).getHeight(),32,"Height limit");h.assertValueEqual(part(h,ORIGIN.above(32)).getHeight(),1,"Excess cell independent");h.succeed();});
    }
    @GameTest(maxTicks = 35)
    public void creativeTankMergeKeepsSelectedFluidAndInfiniteSupply(GameTestHelper h) {
        var upper=(CreativeFluidTankBlockEntity)tank(h,ORIGIN.above(),true);((CreativeFluidTankBlockEntity.CreativeSmartFluidTank)upper.getTankInventory()).setContainedFluid(new FluidStack(FluidVariant.of(Fluids.WATER),B));tank(h,ORIGIN,true);
        h.runAfterDelay(12,()->{var master=part(h,ORIGIN);h.assertValueEqual(master.getHeight(),2,"Creative column joined");h.assertValueEqual(master.getTankInventory().getFluidAmount(),16*B,"Filled new capacity");
            h.assertValueEqual(extract(master.storage(),FluidVariant.of(Fluids.WATER),10*B,true),10*B,"Supply from new controller");h.assertValueEqual(master.getTankInventory().getFluidAmount(),16*B,"Infinite after merge");h.succeed();});
    }
    @GameTest
    public void survivalBucketDoesNotInteractWithRegularTank(GameTestHelper h) {
        tank(h,ORIGIN,false);var player=h.makeMockServerPlayer(net.minecraft.world.level.GameType.SURVIVAL);var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        var pos=h.absolutePos(ORIGIN);var result=h.getBlockState(ORIGIN).useItemOn(stack,h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
        h.assertValueEqual(result,net.minecraft.world.InteractionResult.PASS,"Original survival interaction rule");h.assertTrue(part(h,ORIGIN).getTankInventory().isEmpty(),"Regular tank unchanged");h.succeed();
    }
    @GameTest
    public void creativePlayerCanFillRegularTankFromBucket(GameTestHelper h) {
        tank(h,ORIGIN,false);var player=h.makeMockServerPlayer(net.minecraft.world.level.GameType.CREATIVE);var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
        var pos=h.absolutePos(ORIGIN);h.getBlockState(ORIGIN).useItemOn(stack,h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
        h.assertValueEqual(part(h,ORIGIN).getTankInventory().getFluidAmount(),B,"Bucket filled tank");h.assertTrue(player.getMainHandItem().is(net.minecraft.world.item.Items.WATER_BUCKET),"Creative bucket retained");h.succeed();
    }
    @GameTest
    public void neoforgeTankNbtConvertsMillibucketsAndWritesFabricUnits(GameTestHelper h) {
        var state=AllBlocks.FLUID_TANK.defaultBlockState();var nbt=new net.minecraft.nbt.CompoundTag();nbt.putString("id","create:fluid_tank");nbt.putInt("Size",1);nbt.putInt("Height",1);nbt.putBoolean("Window",true);
        var fluid=new net.minecraft.nbt.CompoundTag();fluid.putString("id","minecraft:water");fluid.putInt("amount",2500);nbt.put("TankContent",fluid);
        var tank=(FluidTankBlockEntity)BlockEntity.loadStatic(h.absolutePos(ORIGIN),state,nbt,h.getLevel().registryAccess());
        h.assertValueEqual(tank.getTankInventory().getFluidAmount(),2500*81L,"mB converted to Fabric units");h.assertValueEqual(tank.getTankInventory().getResource(),FluidVariant.of(Fluids.WATER),"Original fluid ID read");
        var saved=tank.saveWithFullMetadata(h.getLevel().registryAccess());var restored=(FluidTankBlockEntity)BlockEntity.loadStatic(tank.getBlockPos(),state,saved,h.getLevel().registryAccess());
        h.assertValueEqual(restored.getTankInventory().getFluidAmount(),2500*81L,"Second load does not convert twice");h.succeed();
    }
    @GameTest(maxTicks = 40)
    public void placingTankOnWideColumnCompletesLayerInSurvival(GameTestHelper h) {
        build(h,2,1);
        h.runAfterDelay(10,()->{
            var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var stack=new net.minecraft.world.item.ItemStack(AllBlocks.FLUID_TANK,6);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,stack);
            var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(ORIGIN)).add(0,0.5,0),Direction.UP,h.absolutePos(ORIGIN),false);
            ((net.minecraft.world.item.BlockItem)stack.getItem()).place(new net.minecraft.world.item.context.BlockPlaceContext(player,net.minecraft.world.InteractionHand.MAIN_HAND,stack,hit));
            h.assertValueEqual(stack.getCount(),2,"Four tanks consumed for full square layer");
        });
        h.runAfterDelay(22,()->{h.assertValueEqual(part(h,ORIGIN).getHeight(),2,"Whole upper layer joined");h.assertValueEqual(part(h,ORIGIN.offset(1,1,1)).getController(),h.absolutePos(ORIGIN),"Far corner connected");h.succeed();});
    }
    @GameTest
    public void creativeTankBucketSelectsAndEmptyBucketClearsSource(GameTestHelper h) {
        var tank=tank(h,ORIGIN,true);var player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);var pos=h.absolutePos(ORIGIN);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.NORTH,pos,false);
        var bucket=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,bucket);
        h.getBlockState(ORIGIN).useItemOn(bucket,h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertValueEqual(tank.getTankInventory().getResource(),FluidVariant.of(Fluids.WATER),"Bucket selects source");h.assertTrue(player.getMainHandItem().is(net.minecraft.world.item.Items.BUCKET),"Filled bucket consumed");
        h.getBlockState(ORIGIN).useItemOn(player.getMainHandItem(),h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND,hit);
        h.assertTrue(tank.getTankInventory().isEmpty(),"Extracting with empty bucket clears selected fluid");h.assertTrue(player.getMainHandItem().is(net.minecraft.world.item.Items.WATER_BUCKET),"Bucket filled before source cleared");h.succeed();
    }
}
