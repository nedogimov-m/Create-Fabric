package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

public class SeatGameTests {
    private static final BlockPos P = new BlockPos(3, 2, 3);
    private static SeatBlock place(GameTestHelper h) { var seat = AllBlocks.SEATS.get(DyeColor.RED); h.setBlock(P, seat); return seat; }
    private static InteractionResult click(GameTestHelper h, Player player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var pos = h.absolutePos(P);
        var state = h.getBlockState(P);
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return stack.isEmpty() ? state.useWithoutItem(h.getLevel(), player, hit) : state.useItemOn(stack, h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }
    @GameTest public void emptyHandSeatsPlayerAndSecondPlayerCannotEjectThem(GameTestHelper h) {
        place(h); var player = h.makeMockPlayer(GameType.SURVIVAL);
        h.assertTrue(click(h, player, ItemStack.EMPTY).consumesAction(), "Empty hand mounts");
        h.assertTrue(player.getVehicle() instanceof SeatEntity, "Real vehicle");
        var other = h.makeMockPlayer(GameType.SURVIVAL); click(h, other, ItemStack.EMPTY);
        h.assertFalse(other.isPassenger(), "Occupied player cannot be replaced");
        h.assertTrue(player.getVehicle().hasPassenger(player), "Original player remains seated"); h.succeed();
    }
    @GameTest public void leashedMobIsSeatedThenReplacedByClickingPlayer(GameTestHelper h) {
        place(h); var player = h.makeMockPlayer(GameType.SURVIVAL); player.setPos(Vec3.atCenterOf(h.absolutePos(P)));
        var cow = h.spawnWithNoFreeWill(EntityTypes.COW, P.east(2)); cow.setLeashedTo(player, false);
        click(h, player, ItemStack.EMPTY);
        h.assertTrue(cow.getVehicle() instanceof SeatEntity, "Leashed mob uses seat"); h.assertFalse(player.isPassenger(), "Player remains standing");
        var vehicle = cow.getVehicle(); click(h, player, ItemStack.EMPTY);
        h.assertFalse(cow.isPassenger(), "Mob ejected"); h.assertTrue(player.getVehicle() == vehicle, "Same seat now carries player"); h.succeed();
    }
    @GameTest public void dyeChangesColorWithoutConsumingDyeOrRemovingWaterAndRider(GameTestHelper h) {
        var block = place(h); h.setBlock(P, block.defaultBlockState().setValue(SeatBlock.WATERLOGGED, true));
        var cow = h.spawnWithNoFreeWill(EntityTypes.COW, P); SeatBlock.sitDown(h.getLevel(), h.absolutePos(P), cow);
        var vehicle = cow.getVehicle(); var player = h.makeMockPlayer(GameType.SURVIVAL); var dye = new ItemStack(Items.DYE.blue(), 3);
        click(h, player, dye);
        h.assertTrue(h.getBlockState(P).is(AllBlocks.SEATS.get(DyeColor.BLUE)), "Recolored block"); h.assertTrue(h.getBlockState(P).getFluidState().isSourceOfType(Fluids.WATER), "Water retained");
        h.assertValueEqual(dye.getCount(), 3, "Source recoloring does not consume dye"); vehicle.tick(); h.assertTrue(cow.getVehicle() == vehicle && !vehicle.isRemoved(), "Passenger survives recoloring"); h.succeed();
    }
    @GameTest public void sneakingAndDeployerCannotUseSeat(GameTestHelper h) {
        place(h); var player = h.makeMockPlayer(GameType.SURVIVAL); player.setShiftKeyDown(true); click(h, player, new ItemStack(Items.DYE.blue()));
        h.assertFalse(player.isPassenger(), "Sneak passes"); h.assertTrue(h.getBlockState(P).is(AllBlocks.SEATS.get(DyeColor.RED)), "Sneak does not recolor");
        var fake = new DeployerFakePlayer(h.getLevel(), null); click(h, fake, ItemStack.EMPTY); SeatBlock.sitDown(h.getLevel(), h.absolutePos(P), fake);
        h.assertFalse(fake.isPassenger() || SeatBlock.isSeatOccupied(h.getLevel(), h.absolutePos(P)), "Deployer never creates a seat vehicle"); h.succeed();
    }
    @GameTest public void tamedAnimalsRestorePoseOnDismountAndDestroyedSeatEjectsPassengers(GameTestHelper h) {
        place(h); var cat = h.spawnWithNoFreeWill(EntityTypes.CAT, P); cat.setTame(true, false); SeatBlock.sitDown(h.getLevel(), h.absolutePos(P), cat);
        h.assertTrue(cat.isInSittingPose(), "Animal sits"); var seat = (SeatEntity)cat.getVehicle(); cat.stopRiding();
        h.assertFalse(cat.isInSittingPose(), "Pose reset"); seat.tick(); h.assertTrue(seat.isRemoved(), "Empty vehicle removed");
        SeatBlock.sitDown(h.getLevel(), h.absolutePos(P), cat); var replacement = (SeatEntity)cat.getVehicle(); h.setBlock(P, Blocks.AIR); replacement.tick();
        h.assertFalse(cat.isPassenger() || cat.isInSittingPose(), "Destroyed seat ejects and resets pose"); h.assertTrue(replacement.isRemoved(), "Broken seat has no orphan entity"); h.succeed();
    }
    @GameTest public void actualFallingMobBecomesPassenger(GameTestHelper h) {
        place(h); var cow = h.spawnWithNoFreeWill(EntityTypes.COW, 3.5f, 5, 3.5f); cow.setNoGravity(false);
        h.succeedWhen(() -> { h.assertTrue(cow.getVehicle() instanceof SeatEntity, "Native fall/bounce callback captured mob"); h.assertTrue(cow.getRootVehicle().blockPosition().equals(h.absolutePos(P)), "Seat under falling mob"); });
    }
    @GameTest public void realItemCollisionUsesNativeBounceAndSneakingSuppressesBounce(GameTestHelper h) {
        place(h); var level = h.getLevel(); var at = Vec3.atLowerCornerOf(h.absolutePos(P)).add(.5, .7, .5);
        var item = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.APPLE)); item.setDeltaMovement(0, -1, 0); item.move(MoverType.SELF, new Vec3(0, -1, 0));
        h.assertValueEqual(item.getY(), h.absolutePos(P).getY() + 6 / 16d, "Ordinary entity collision height");
        h.assertTrue(item.getDeltaMovement().y > .3 && item.getDeltaMovement().y < .66, "26.3 physics applies reduced nonliving restitution");
        h.assertFalse(item.isPassenger(), "Items bounce without mounting");
        var player = h.makeMockPlayer(GameType.SURVIVAL); player.setShiftKeyDown(true); player.setPos(at); player.setDeltaMovement(0, -1, 0); player.move(MoverType.SELF, new Vec3(0, -1, 0));
        h.assertValueEqual(player.getY(), h.absolutePos(P).getY() + 3 / 16d, "Player collision height");
        h.assertValueEqual(player.getDeltaMovement().y, 0d, "Sneaking suppresses native restitution"); h.succeed();
    }
    @GameTest public void seatReducesActualFallDamageByHalf(GameTestHelper h) {
        var seat = place(h); var cow = h.spawnWithNoFreeWill(EntityTypes.COW, P); float initial = cow.getHealth();
        seat.fallOn(h.getLevel(), seat.defaultBlockState(), h.absolutePos(P), cow, 10);
        h.assertValueEqual(cow.getHealth(), initial - 2, "Ten block fall uses distance five before vanilla three block grace"); h.succeed();
    }
    @GameTest public void nativeSaveReloadRestoresPassengerAndSeatedAnimal(GameTestHelper h) {
        place(h); var cat = h.spawnWithNoFreeWill(EntityTypes.CAT, P); cat.setTame(true, false); SeatBlock.sitDown(h.getLevel(), h.absolutePos(P), cat); var seat = cat.getVehicle();
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess()); h.assertTrue(seat.saveAsPassenger(output), "Vehicle serializable");
        var restored = EntityType.loadEntityRecursive(output.buildResult(), h.getLevel(), new EntitySpawnRequest(EntitySpawnReason.LOAD, false), EntityProcessor.NOP);
        h.assertTrue(restored instanceof SeatEntity && restored.getPassengers().size() == 1, "Native recursive passenger load");
        var passenger = (TamableAnimal)restored.getFirstPassenger(); h.assertTrue(passenger.isInSittingPose() && passenger.getVehicle() == restored, "Saved animal still sits");
        restored.tick(); h.assertFalse(restored.isRemoved(), "Restored seat recognized"); restored.ejectPassengers(); h.assertFalse(passenger.isInSittingPose(), "Reload retains pose cleanup"); h.succeed();
    }
    @GameTest public void failedDuplicatePlacementNeverCreatesAnotherVehicle(GameTestHelper h) {
        place(h); var first = h.spawnWithNoFreeWill(EntityTypes.COW, P); var second = h.spawnWithNoFreeWill(EntityTypes.PIG, P.east());
        SeatBlock.sitDown(h.getLevel(), h.absolutePos(P), first); SeatBlock.sitDown(h.getLevel(), h.absolutePos(P), second);
        h.assertTrue(first.isPassenger() && !second.isPassenger(), "One passenger owner"); h.assertValueEqual(h.getLevel().getEntitiesOfClass(SeatEntity.class, new AABB(h.absolutePos(P))).size(), 1, "One seat vehicle");
        var seat = first.getVehicle(); var before = seat.position(); seat.setDeltaMovement(new Vec3(1, 1, 1)); seat.tick(); h.assertValueEqual(seat.position(), before, "Seat cannot drift"); h.succeed();
    }
    @GameTest public void waterloggedSeatPlacementAndBucketRemovalUseNativeFluidApi(GameTestHelper h) {
        h.setBlock(P, Blocks.WATER); var player = h.makeMockPlayer(GameType.SURVIVAL); var stack = new ItemStack(AllBlocks.SEATS.get(DyeColor.RED)); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var pos = h.absolutePos(P); var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false); var block = AllBlocks.SEATS.get(DyeColor.RED);
        h.assertTrue(block.asItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Survival block item placed");
        h.assertTrue(h.getBlockState(P).getValue(SeatBlock.WATERLOGGED), "Seat contains source water"); h.assertTrue(block.pickupBlock(player, h.getLevel(), pos, h.getBlockState(P)).is(Items.WATER_BUCKET), "Native bucket pickup"); h.assertFalse(h.getBlockState(P).getValue(SeatBlock.WATERLOGGED), "Bucket drains seat"); h.succeed();
    }
    @GameTest public void mobNavigationAvoidsSeatsAndShulkersAreNotPickedUp(GameTestHelper h) {
        place(h); var cow = h.spawnWithNoFreeWill(EntityTypes.COW, P.east());
        h.assertValueEqual(net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(cow, h.absolutePos(P)), net.minecraft.world.level.pathfinder.PathType.RAIL, "Source seat navigation category");
        var shulker = h.spawnWithNoFreeWill(EntityTypes.SHULKER, P.above()); h.assertFalse(SeatBlock.canBePickedUp(shulker), "Shulkers cannot be seated by falling or a leash"); h.succeed();
    }
    @GameTest public void originalSurvivalAndRecolorRecipesWorkForEveryColor(GameTestHelper h) {
        var recipes = h.getLevel().getServer().getRecipeManager().getRecipes();
        for (var color : DyeColor.values()) {
            var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(new ItemStack(Items.WOOL.pick(color)), new ItemStack(Items.OAK_SLAB), ItemStack.EMPTY, ItemStack.EMPTY));
            var recolor = net.minecraft.world.item.crafting.CraftingInput.of(2, 2, java.util.List.of(new ItemStack(AllBlocks.SEATS.get(color == DyeColor.WHITE ? DyeColor.BLACK : DyeColor.WHITE)), new ItemStack(Items.DYE.pick(color)), ItemStack.EMPTY, ItemStack.EMPTY));
            for (boolean conversion : java.util.List.of(false, true)) {
                String name = "crafting/kinetics/" + color.getSerializedName() + "_seat" + (conversion ? "_from_other_seat" : "");
                var recipe = (net.minecraft.world.item.crafting.CraftingRecipe)recipes.stream().filter(holder -> holder.id().identifier().equals(Create.asResource(name))).findFirst().orElseThrow().value();
                var grid = conversion ? recolor : input; h.assertTrue(recipe.matches(grid, h.getLevel()), "Original recipe matches: " + name);
                var result = recipe.assemble(grid); h.assertTrue(result.is(AllBlocks.SEATS.get(color).asItem()) && result.getCount() == 1, "Exactly one correct color seat");
            }
        }
        h.succeed();
    }
}
