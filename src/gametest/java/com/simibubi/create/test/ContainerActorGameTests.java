package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.fluids.tank.*;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.content.processing.basin.*;
import com.simibubi.create.content.processing.burner.*;
import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.phys.*;

public class ContainerActorGameTests {
    private static final BlockPos P = new BlockPos(3, 5, 4), TARGET = P.north(2);
    private static BlockState funnel(boolean brass, boolean extracting) {
        return (brass ? AllBlocks.BRASS_FUNNEL : AllBlocks.ANDESITE_FUNNEL).defaultBlockState().setValue(FunnelBlock.FACING, Direction.UP).setValue(FunnelBlock.EXTRACTING, extracting);
    }
    private static ControlledContraptionEntity moving(GameTestHelper h, BlockState state, Consumer<BlockEntity> customize) throws Exception {
        h.setBlock(P, state); h.setBlock(P.east(), Blocks.BARREL);
        customize.accept(h.getLevel().getBlockEntity(h.absolutePos(P)));
        h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(P), h.absolutePos(P.east()))));
        var structure = new BearingContraption(false, Direction.UP);
        h.assertTrue(structure.assemble(h.getLevel(), h.absolutePos(P.below())), "Assemble");
        h.assertTrue(structure.tryRemoveBlocksFromWorld(h.getLevel(), BlockPos.ZERO), "Take inventory ownership");
        var owner = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel());
        owner.setContraption(structure); owner.setPos(Vec3.atLowerCornerOf(h.absolutePos(P))); owner.tickActors();
        return owner;
    }
    private static ControlledContraptionEntity moving(GameTestHelper h, BlockState state) throws Exception { return moving(h, state, be -> {}); }
    private static MovementContext ctx(ControlledContraptionEntity owner) { return owner.getContraption().getActorAt(BlockPos.ZERO).right; }
    private static MovementBehaviour behaviour(ControlledContraptionEntity owner) { return MovementBehaviour.REGISTRY.get(ctx(owner).state); }
    private static void stock(ControlledContraptionEntity owner, int slot, ItemStack stack) { owner.getContraption().getStorage().getAllItemStorages().get(new BlockPos(1, 0, 0)).setStackInSlot(slot, stack); }
    private static long count(ControlledContraptionEntity owner, Item item) { long amount = 0; for (var view : owner.getContraption().getStorage().getAllItems()) if (view.getResource().isOf(item)) amount += view.getAmount(); return amount; }
    private static List<ItemEntity> drops(GameTestHelper h) { return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(P)).inflate(5)); }
    private static void visit(GameTestHelper h, ControlledContraptionEntity owner) { ctx(owner).position = Vec3.atCenterOf(h.absolutePos(TARGET)); behaviour(owner).visitNewPosition(ctx(owner), h.absolutePos(TARGET)); }
    private static ItemEntity item(GameTestHelper h, ItemStack stack) { var p = Vec3.atCenterOf(h.absolutePos(TARGET)); var item = new ItemEntity(h.getLevel(), p.x, p.y, p.z, stack); h.getLevel().addFreshEntity(item); return item; }
    private static ItemStack named(Item item, int amount) { var stack = new ItemStack(item, amount); stack.set(DataComponents.CUSTOM_NAME, Component.literal("Mounted cargo")); return stack; }
    private static void upsideDown(ControlledContraptionEntity owner) { owner.setRotationAxis(Direction.Axis.Z); owner.setAngle(180); owner.tickActors(); }
    private static void fillBasin(BlockEntity be) {
        var basin = (BasinBlockEntity) be;
        try (var tx = Transaction.openOuter()) {
            basin.inventory.inputs.getFirst().insert(ItemVariant.of(named(Items.DIAMOND, 1)), 7, tx);
            basin.inventory.outputs.getLast().insert(ItemVariant.of(Items.EMERALD), 11, tx);
            basin.fluids.inputs.getFirst().insert(FluidVariant.of(Fluids.WATER), 17001, tx);
            tx.commit();
        }
    }
    @GameTest public void ordinaryFunnelsAndOnlyFiniteTankRegisterSourceActors(GameTestHelper h) {
        h.assertTrue(MovementBehaviour.REGISTRY.get(AllBlocks.BRASS_FUNNEL) instanceof FunnelMovementBehaviour && MovementBehaviour.REGISTRY.get(AllBlocks.ANDESITE_FUNNEL) instanceof FunnelMovementBehaviour, "Normal funnel actors");
        h.assertTrue(MovementBehaviour.REGISTRY.get(AllBlocks.BRASS_BELT_FUNNEL) == null && MovementBehaviour.REGISTRY.get(AllBlocks.ANDESITE_BELT_FUNNEL) == null, "No EXTRACTING property on belt funnels");
        h.assertTrue(MovementBehaviour.REGISTRY.get(AllBlocks.FLUID_TANK) instanceof FluidTankMovementBehavior && MovementBehaviour.REGISTRY.get(AllBlocks.CREATIVE_FLUID_TANK) == null, "Source finite/creative registration"); h.succeed();
    }
    @GameTest public void andesiteEjectsOneItemWithZeroMotionAndPickupDelay(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(false, true)); stock(owner, 0, new ItemStack(Items.DIAMOND, 9)); visit(h, owner);
        var item = drops(h).getFirst(); h.assertValueEqual(count(owner, Items.DIAMOND), 8L, "Exactly one extracted"); h.assertTrue(item.getItem().getCount() == 1 && item.getDeltaMovement().equals(Vec3.ZERO) && item.hasPickUpDelay(), "Source ejection properties"); h.assertTrue(item.position().equals(ctx(owner).position.add(0, -.5, 0)), "Source non-down point"); h.succeed();
    }
    @GameTest public void brassExactFailureRollsBackAndCanChooseALaterMatchingVariant(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(true, true)); ctx(owner).blockEntityData.putInt("FilterAmount", 8); ctx(owner).blockEntityData.putBoolean("UpTo", false);
        stock(owner, 0, new ItemStack(Items.DIRT, 3)); visit(h, owner); h.assertTrue(drops(h).isEmpty() && count(owner, Items.DIRT) == 3, "Insufficient exact batch rolled back");
        stock(owner, 1, named(Items.DIAMOND, 8)); visit(h, owner); h.assertTrue(drops(h).size() == 1 && drops(h).getFirst().getItem().getCount() == 8 && drops(h).getFirst().getItem().has(DataComponents.CUSTOM_NAME), "Later component-bearing exact batch"); h.assertValueEqual(count(owner, Items.DIRT), 3L, "Failed candidate retained"); h.succeed();
    }
    @GameTest public void brassUpToCombinesSlotsWithoutCombiningComponents(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(true, true)); ctx(owner).blockEntityData.putInt("FilterAmount", 9); ctx(owner).blockEntityData.putBoolean("UpTo", true);
        stock(owner, 0, named(Items.DIAMOND, 2)); stock(owner, 1, named(Items.DIAMOND, 3)); stock(owner, 2, new ItemStack(Items.DIAMOND, 4)); visit(h, owner);
        h.assertTrue(drops(h).getFirst().getItem().getCount() == 5 && drops(h).getFirst().getItem().has(DataComponents.CUSTOM_NAME), "Same components combine"); h.assertValueEqual(count(owner, Items.DIAMOND), 4L, "Other component variant retained"); h.succeed();
    }
    @GameTest public void movingFunnelHonorsCapturedFilter(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(true, true), be -> ((FunnelBlockEntity) be).filtering.setFilter(new ItemStack(Items.EMERALD)));
        stock(owner, 0, new ItemStack(Items.DIAMOND, 9)); stock(owner, 1, new ItemStack(Items.EMERALD, 2)); visit(h, owner);
        h.assertTrue(drops(h).getFirst().getItem().is(Items.EMERALD) && count(owner, Items.DIAMOND) == 9, "BE filter follows actor"); h.succeed();
    }
    @GameTest public void occupiedDropCellAndSolidWorkCellDoNotConsumeStock(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(false, true)); stock(owner, 0, new ItemStack(Items.DIAMOND, 9)); var blocking = item(h, new ItemStack(Items.DIRT)); visit(h, owner);
        h.assertValueEqual(count(owner, Items.DIAMOND), 9L, "Item in drop cell blocks ejection"); blocking.discard(); h.setBlock(TARGET, Blocks.STONE); visit(h, owner);
        h.assertTrue(drops(h).isEmpty() && count(owner, Items.DIAMOND) == 9, "Collision shape blocks ejection"); h.succeed();
    }
    @GameTest public void downwardExtractionRetainsFullActorPoint(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(false, true).setValue(FunnelBlock.FACING, Direction.DOWN)); stock(owner, 0, new ItemStack(Items.DIAMOND)); visit(h, owner);
        h.assertTrue(drops(h).getFirst().position().equals(ctx(owner).position), "Down does not subtract .5"); h.succeed();
    }
    @GameTest public void collectionRetainsExactComponentRemainderAndCannotCollectTwice(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(false, false)); for (int i = 0; i < 27; i++) stock(owner, i, new ItemStack(Items.DIRT, 64)); stock(owner, 0, named(Items.DIAMOND, 62));
        var entity = item(h, named(Items.DIAMOND, 7)); visit(h, owner); visit(h, owner);
        h.assertTrue(entity.isAlive() && entity.getItem().getCount() == 5 && entity.getItem().has(DataComponents.CUSTOM_NAME), "Only two fit and remainder keeps components"); h.assertValueEqual(count(owner, Items.DIAMOND), 64L, "No double collection"); h.succeed();
    }
    @GameTest public void filteredCollectionKeepsOtherWorldItemsAlive(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(true, false), be -> ((FunnelBlockEntity) be).filtering.setFilter(new ItemStack(Items.EMERALD)));
        var other = item(h, new ItemStack(Items.DIAMOND, 7)); var matching = item(h, new ItemStack(Items.EMERALD, 3)); visit(h, owner);
        h.assertTrue(other.isAlive() && !matching.isAlive() && count(owner, Items.EMERALD) == 3 && count(owner, Items.DIAMOND) == 0, "Filter before transaction/entity mutation"); h.succeed();
    }
    @GameTest public void packageCollectionPreservesPayloadAndDiscardsPhysicalBoxOnce(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(false, false)); var box = PackageItem.containingSlots(List.of(named(Items.DIAMOND, 61), new ItemStack(Items.EMERALD, 47)));
        var packageEntity = PackageEntity.fromItemStack(h.getLevel(), Vec3.atCenterOf(h.absolutePos(TARGET)), box); h.getLevel().addFreshEntity(packageEntity); visit(h, owner); visit(h, owner);
        var mounted = owner.getContraption().getStorage().getAllItemStorages().get(new BlockPos(1, 0, 0));
        h.assertTrue(packageEntity.isRemoved() && ItemStack.isSameItemSameComponents(mounted.getStackInSlot(0), box) && mounted.getStackInSlot(0).getCount() == 1, "One intact package, no duplicate"); h.succeed();
    }
    @GameTest public void fullStorageDoesNotDestroyPhysicalPackage(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(false, false)); for (int i = 0; i < 27; i++) stock(owner, i, new ItemStack(Items.DIRT, 64));
        var box = PackageItem.containingSlots(List.of(new ItemStack(Items.DIAMOND, 63))); var entity = PackageEntity.fromItemStack(h.getLevel(), Vec3.atCenterOf(h.absolutePos(TARGET)), box); h.getLevel().addFreshEntity(entity); visit(h, owner);
        h.assertTrue(entity.isAlive() && ItemStack.isSameItemSameComponents(box, entity.getBox()), "Blocked package keeps payload"); h.succeed();
    }
    @GameTest public void unloadedFunnelCellDoesNotGenerateChunkOrMutateItems(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(false, true)); stock(owner, 0, new ItemStack(Items.DIAMOND, 9)); var far = new BlockPos(2000000, 60, 2000000); ctx(owner).position = Vec3.atCenterOf(far); behaviour(owner).visitNewPosition(ctx(owner), far);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null && count(owner, Items.DIAMOND) == 9, "Loaded-only extraction"); h.succeed();
    }
    @GameTest public void disabledFunnelDoesNotExtractWhenOwnerMoves(GameTestHelper h) throws Exception {
        var owner = moving(h, funnel(false, true)); stock(owner, 0, new ItemStack(Items.DIAMOND, 9)); owner.getContraption().setActorsActive(new ItemStack(AllBlocks.ANDESITE_FUNNEL), false); owner.setPos(owner.position().add(0, 0, 2)); owner.tickActors();
        h.assertTrue(drops(h).isEmpty() && count(owner, Items.DIAMOND) == 9, "Controls suppress transfers"); h.succeed();
    }
    @GameTest public void basinCaptureKeepsItemsInPayloadWithoutWorldDropsOrMountedCopy(GameTestHelper h) throws Exception {
        var owner = moving(h, AllBlocks.BASIN.defaultBlockState(), ContainerActorGameTests::fillBasin);
        h.assertTrue(drops(h).isEmpty() && count(owner, Items.DIAMOND) == 0 && !owner.getContraption().getStorage().getAllItemStorages().containsKey(BlockPos.ZERO), "Capture suppresses BE removal drops and no second owner");
        h.assertTrue(!ctx(owner).blockEntityData.getCompoundOrEmpty("Inventory").isEmpty(), "Native inventory retained"); h.succeed();
    }
    @GameTest public void upsideDownBasinDumpsInputAndNinthOutputOnceAndKeepsFluids(GameTestHelper h) throws Exception {
        var owner = moving(h, AllBlocks.BASIN.defaultBlockState(), ContainerActorGameTests::fillBasin); var fluids = ctx(owner).blockEntityData.getCompoundOrEmpty("Fluids").copy(); upsideDown(owner); for (int i = 0; i < 8; i++) owner.tickActors();
        var drops = drops(h); h.assertTrue(drops.size() == 2 && drops.stream().mapToInt(e -> e.getItem().getCount()).sum() == 18, "Both native inventories dumped once");
        h.assertTrue(drops.stream().filter(e -> e.getItem().is(Items.DIAMOND)).allMatch(e -> e.getItem().has(DataComponents.CUSTOM_NAME)), "Item components retained");
        h.assertTrue(ctx(owner).blockEntityData.getCompoundOrEmpty("Inventory").isEmpty() && ctx(owner).blockEntityData.getCompoundOrEmpty("Fluids").equals(fluids), "Only items cleared");
        h.assertTrue(drops.stream().allMatch(e -> e.getDeltaMovement().distanceTo(new Vec3(0, -.05, 0)) < 1e-6), "Rotated drop velocity"); h.succeed();
    }
    @GameTest public void sidewaysBasinAndDisabledInvertedBasinRetainContents(GameTestHelper h) throws Exception {
        var owner = moving(h, AllBlocks.BASIN.defaultBlockState(), ContainerActorGameTests::fillBasin); owner.setRotationAxis(Direction.Axis.Z); owner.setAngle(90); owner.tickActors(); h.assertTrue(drops(h).isEmpty(), "Sideways does not spill");
        owner.getContraption().setActorsActive(new ItemStack(AllBlocks.BASIN), false); upsideDown(owner); h.assertTrue(drops(h).isEmpty(), "Disabled inverted basin retains inventory");
        owner.getContraption().setActorsActive(new ItemStack(AllBlocks.BASIN), true); owner.tickActors(); h.assertValueEqual(drops(h).size(), 2, "Reenabled inverted basin spills once"); h.succeed();
    }
    @GameTest public void basinReloadBeforeDumpKeepsInventoryAndAfterDumpCannotRepeat(GameTestHelper h) throws Exception {
        var owner = moving(h, AllBlocks.BASIN.defaultBlockState(), ContainerActorGameTests::fillBasin);
        var restored = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel()); restored.setContraption(Contraption.fromNBT(h.getLevel(), owner.getContraption().writeNBT(h.getLevel().registryAccess(), false), false)); restored.setPos(owner.position()); upsideDown(restored);
        h.assertValueEqual(drops(h).size(), 2, "Saved native inventory dumps"); var twice = new ControlledContraptionEntity(AllEntityTypes.CONTROLLED_CONTRAPTION, h.getLevel()); twice.setContraption(Contraption.fromNBT(h.getLevel(), restored.getContraption().writeNBT(h.getLevel().registryAccess(), false), false)); twice.setPos(owner.position()); upsideDown(twice);
        h.assertTrue(drops(h).size() == 2 && ctx(twice).data.getBooleanOr(BasinMovementBehaviour.DUMPED, false), "Persistent dump state prevents repeat"); h.succeed();
    }
    @GameTest public void dumpedBasinPreviewClearsItemsAndDoesNotPublishPrivatePayload(GameTestHelper h) throws Exception {
        var owner = moving(h, AllBlocks.BASIN.defaultBlockState(), ContainerActorGameTests::fillBasin); ctx(owner).blockEntityData.putString("OnlyServer", "private"); upsideDown(owner);
        var snapshot = owner.getContraption().writeNBT(h.getLevel().registryAccess(), true); var remote = Contraption.fromNBT(h.getLevel(), snapshot, true); var payload = remote.getBlocks().get(BlockPos.ZERO).nbt();
        h.assertTrue(payload.getCompoundOrEmpty("Inventory").isEmpty() && !payload.contains("OnlyServer") && !payload.getCompoundOrEmpty("Fluids").isEmpty(), "Tracking contains current public preview only"); h.succeed();
    }
    @GameTest public void dumpedBasinDisassemblyRestoresEmptyItemsAndExactFluidUnits(GameTestHelper h) throws Exception {
        var owner = moving(h, AllBlocks.BASIN.defaultBlockState(), ContainerActorGameTests::fillBasin); upsideDown(owner); owner.setAngle(0); h.assertTrue(owner.disassemble(), "Disassemble"); var basin = h.getBlockEntity(P, BasinBlockEntity.class);
        h.assertTrue(basin.allItems().stream().allMatch(slot -> slot.isResourceBlank()), "Dumped items do not return"); h.assertValueEqual(basin.fluids.inputs.getFirst().getAmount(), 17001L, "Fluid remains exact"); h.succeed();
    }
    @GameTest public void uprightBasinDisassemblyReturnsAllCapturedItems(GameTestHelper h) throws Exception {
        var owner = moving(h, AllBlocks.BASIN.defaultBlockState(), ContainerActorGameTests::fillBasin); h.assertTrue(owner.disassemble(), "Disassemble"); var basin = h.getBlockEntity(P, BasinBlockEntity.class);
        h.assertTrue(basin.inventory.inputs.getFirst().stack().getCount() == 7 && basin.inventory.inputs.getFirst().stack().has(DataComponents.CUSTOM_NAME) && basin.inventory.outputs.getLast().stack().getCount() == 11 && drops(h).isEmpty(), "No spill or capture duplication"); h.succeed();
    }
    @GameTest public void movingBurnerIgnoresControlsAndDoesNotConsumeFuel(GameTestHelper h) throws Exception {
        var state = AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED);
        var owner = moving(h, state, be -> { var burner = (BlazeBurnerBlockEntity) be; burner.tryFuel(new ItemStack(Items.COAL), false, false); burner.goggles = true; burner.trainHat = true; });
        var fuel = ctx(owner).blockEntityData.getIntOr("burnTimeRemaining", 0); owner.getContraption().setActorsActive(ItemStack.EMPTY, false); for (int i = 0; i < 80; i++) owner.tickActors();
        h.assertTrue(!ctx(owner).disabled && fuel > 0 && ctx(owner).blockEntityData.getIntOr("burnTimeRemaining", 0) == fuel && behaviour(owner).canBeDisabledVia(ctx(owner)) == null, "Server moving burner fuel is frozen and cannot be disabled");
        h.assertTrue(owner.disassemble(), "Disassemble"); var burner = h.getBlockEntity(P, BlazeBurnerBlockEntity.class); h.assertTrue(burner.getRemainingBurnTime() == fuel && burner.goggles && burner.trainHat, "Fuel and accessories restored"); h.succeed();
    }
    @GameTest public void angularHeadChaseTakesShortestRouteAcrossSeam(GameTestHelper h) {
        var angle = new BlazeBurnerMovementBehaviour.HeadAngle(179); angle.tick(-179);
        h.assertTrue(angle.getValue(1) > 179 && angle.getValue(1) < 181 && angle.getValue(.5f) > 179, "No full-circle chase across seam"); h.succeed();
    }
    @GameTest public void burnerHeadUsesSourceHalfDistanceChaseWithFiveDegreeTickLimit(GameTestHelper h) {
        var angle = new BlazeBurnerMovementBehaviour.HeadAngle(0); angle.tick(90); h.assertValueEqual(angle.getValue(1), 5f, "Large turn capped at five degrees");
        angle.tick(9); h.assertValueEqual(angle.getValue(1), 7f, "Short turn covers half the remaining angle"); h.assertValueEqual(angle.getValue(.5f), 6f, "Partial tick interpolation"); h.succeed();
    }
    @GameTest public void disabledMovingTankNeverRunsServerConnectivityOrBoilerTick(GameTestHelper h) throws Exception {
        var owner = moving(h, AllBlocks.FLUID_TANK.defaultBlockState(), be -> { var tank = (FluidTankBlockEntity) be; tank.getTankInventory().setFluid(new com.simibubi.create.foundation.fluid.FluidStack(FluidVariant.of(Fluids.WATER), 17001)); });
        var captured = ctx(owner).blockEntityData.copy(); owner.getContraption().setActorsActive(new ItemStack(AllBlocks.FLUID_TANK), false); for (int i = 0; i < 80; i++) owner.tickActors();
        h.assertTrue(ctx(owner).disabled && behaviour(owner).mustTickWhileDisabled() && captured.equals(ctx(owner).blockEntityData), "Disabled actor only requests safe client ticking");
        h.assertTrue(owner.disassemble(), "Disassemble"); h.assertValueEqual(h.getBlockEntity(P, FluidTankBlockEntity.class).getTankInventory().getFluidAmount(), 17001L, "Tank amount unchanged"); h.succeed();
    }
}
