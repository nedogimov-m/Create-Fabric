package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.crusher.*;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.kinetics.fan.*;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.saw.*;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.item.ProcessingInventory;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Narrow regressions for seven original events. Authored only; runtime UNRUN. */
public final class ProcessingAdvancementGameTests {
    private static final BlockPos P = new BlockPos(4, 3, 4);
    private static final BlockPos FAN = new BlockPos(2, 3, 3);

    private static NativeSurvivalTestPlayer join(GameTestHelper h) {
        return NativeSurvivalTestPlayer.join(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 8))));
    }

    private static void placed(GameTestHelper h, BlockPos pos, ServerPlayer player) {
        GameplayAdvancementGameTests.placed(h, pos, player);
    }

    private static boolean done(ServerPlayer player, String id) {
        return GameplayAdvancementGameTests.done(player, id);
    }

    private static long count(ProcessingInventory inventory, Item item) {
        return Stream.concat(inventory.inputs.stream(), inventory.outputs.stream())
            .filter(slot -> slot.stack().is(item)).mapToLong(slot -> slot.getAmount()).sum();
    }

    private static void insert(GameTestHelper h, ProcessingInventory inventory, Item item, int amount) {
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(inventory.automation.insert(ItemVariant.of(item), amount, tx), (long) amount,
                "Finite original items enter the actual native inventory");
            tx.commit();
        }
    }

    private static EncasedFanBlockEntity fan(GameTestHelper h, ServerPlayer owner) {
        h.setBlock(FAN, AllBlocks.ENCASED_FAN.defaultBlockState().setValue(EncasedFanBlock.FACING, Direction.EAST));
        if (owner != null) placed(h, FAN, owner);
        h.setBlock(FAN.west(), AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        h.getBlockEntity(FAN.west(), KineticBlockEntity.class).setGeneratedSpeed(128);
        return h.getBlockEntity(FAN, EncasedFanBlockEntity.class);
    }

    @GameTest
    public void realNonzeroAirflowAwardsOnlyActualPlacedFanOwner(GameTestHelper h) {
        h.setBlock(FAN, AllBlocks.ENCASED_FAN.defaultBlockState().setValue(EncasedFanBlock.FACING, Direction.EAST));
        try (var owner = join(h); var observer = join(h)) {
            placed(h, FAN, owner.player());
            var fan = h.getBlockEntity(FAN, EncasedFanBlockEntity.class);
            GameplayAdvancementGameTests.drive(fan, 0);
            fan.getAirCurrent().tick();
            h.assertFalse(done(owner.player(), "encased_fan"), "Stopped real fan has no positive source airflow");
            GameplayAdvancementGameTests.drive(fan, 128);
            fan.getAirCurrent().tick();
            h.assertTrue(fan.getAirCurrent().maxDistance > 0, "Actual loaded air geometry has positive range");
            h.assertTrue(done(owner.player(), "encased_fan") && !done(observer.player(), "encased_fan"),
                "Source nonzero-flow event awards only the literal registered placing player");
        }
        h.succeed();
    }

    @GameTest(maxTicks = 240)
    public void actualDepotWashingPaysThreeFlourAndAwardsFanOwner(GameTestHelper h) {
        var owner = join(h); var observer = join(h);
        fan(h, owner.player());
        h.setBlock(FAN.east(), Blocks.WATER);
        var pos = FAN.east(2).below(); h.setBlock(pos, AllBlocks.DEPOT);
        var depot = h.getBlockEntity(pos, DepotBlockEntity.class);
        insert(h, depot.inventory, AllItems.WHEAT_FLOUR, 3);
        h.runAfterDelay(200, () -> {
            try {
                h.assertValueEqual(count(depot.inventory, AllItems.WHEAT_FLOUR), 0L, "All three original flour are spent");
                h.assertValueEqual(count(depot.inventory, AllItems.DOUGH), 3L, "Exactly three real dough own the output");
                h.assertTrue(done(owner.player(), "fan_processing") && !done(observer.player(), "fan_processing"),
                    "Completed native depot conversion reaches only the original fan owner");
                h.succeed();
            } finally { owner.close(); observer.close(); }
        });
    }

    @GameTest(maxTicks = 240)
    public void actualDroppedItemWashingAwardsFanOwnerAfterConversion(GameTestHelper h) {
        var owner = join(h); var observer = join(h);
        fan(h, owner.player());
        h.setBlock(FAN.east().below(), Blocks.STONE); h.setBlock(FAN.east().above(), Blocks.STONE);
        h.setBlock(FAN.east().north(), Blocks.STONE); h.setBlock(FAN.east().south(), Blocks.STONE);
        h.setBlock(FAN.east(2), Blocks.STONE); h.setBlock(FAN.east(), Blocks.WATER);
        var pos = h.absolutePos(FAN.east());
        var item = new ItemEntity(h.getLevel(), pos.getX() + .5, pos.getY() + .2, pos.getZ() + .5,
            new ItemStack(AllItems.WHEAT_FLOUR, 2), 0, 0, 0);
        h.assertTrue(h.getLevel().addFreshEntity(item), "One actual two-flour entity is admitted");
        h.runAfterDelay(200, () -> {
            try {
                h.assertTrue(item.isAlive() && item.getItem().is(AllItems.DOUGH) && item.getItem().getCount() == 2,
                    "The original entity now owns exactly two converted dough");
                h.assertTrue(done(owner.player(), "fan_processing") && !done(observer.player(), "fan_processing"),
                    "Only completed original item conversion emits the actual fan owner event");
                h.succeed();
            } finally { owner.close(); observer.close(); }
        });
    }

    @GameTest(maxTicks = 240)
    public void actualStationaryBeltWashingAwardsOnlyAfterCommittedReplacement(GameTestHelper h) {
        var owner = join(h); var observer = join(h);
        fan(h, owner.player()); h.setBlock(FAN.east(), Blocks.WATER);
        var start = FAN.east(2).below(); var end = start.east(3);
        var shaft = AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z);
        h.setBlock(start, shaft); h.setBlock(end, shaft);
        h.assertTrue(BeltConnectorItem.createBelts(h.getLevel(), h.absolutePos(start), h.absolutePos(end)),
            "A genuine four-segment horizontal belt is created");
        var belt = h.getBlockEntity(start, BeltBlockEntity.class);
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(belt.storage(Direction.UP).insert(ItemVariant.of(AllItems.WHEAT_FLOUR), 3, tx), 3L,
                "Actual belt admits three originals inside an aborted insertion");
        }
        h.assertTrue(belt.getInventory().getTransportedItems().isEmpty() && !done(owner.player(), "fan_processing"),
            "Aborted belt stock cannot convert or award the fan owner");
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(belt.storage(Direction.UP).insert(ItemVariant.of(AllItems.WHEAT_FLOUR), 3, tx), 3L,
                "Three actual flour enter the canonical belt inventory"); tx.commit();
        }
        h.runAfterDelay(200, () -> {
            try {
                var items = belt.getInventory().getTransportedItems();
                h.assertValueEqual(items.stream().filter(item -> item.stack.is(AllItems.DOUGH)).mapToInt(item -> item.stack.getCount()).sum(),
                    3, "Committed replacement preserves exactly three actual dough");
                h.assertTrue(items.stream().noneMatch(item -> item.stack.is(AllItems.WHEAT_FLOUR)), "Original belt flour are debited");
                h.assertTrue(done(owner.player(), "fan_processing") && !done(observer.player(), "fan_processing"),
                    "Committed belt conversion reaches only the original fan owner");
                h.succeed();
            } finally { owner.close(); observer.close(); }
        });
    }

    @GameTest
    public void millAwardsOnlyAfterRealRecipeCanCommitItsPaidInputAndOutput(GameTestHelper h) {
        h.setBlock(P, AllBlocks.MILLSTONE);
        try (var owner = join(h); var observer = join(h)) {
            placed(h, P, owner.player());
            var mill = h.getBlockEntity(P, MillstoneBlockEntity.class);
            try (var tx = Transaction.openOuter()) {
                h.assertValueEqual(mill.inventory.automation.insert(ItemVariant.of(Items.GRAVEL), 1, tx), 1L, "Aborted input is simulated only");
            }
            h.assertTrue(mill.inventory.inputs.getFirst().isResourceBlank() && !done(owner.player(), "millstone"),
                "Aborted native insertion neither pays nor awards");
            insert(h, mill.inventory, Items.GRAVEL, 1);
            try (var tx = Transaction.openOuter()) {
                for (var slot : mill.inventory.outputs) slot.insert(ItemVariant.of(Items.DIAMOND), 64, tx);
                tx.commit();
            }
            for (int i = 0; i < 40; i++) {
                GameplayAdvancementGameTests.drive(mill, 256);
                MillstoneBlockEntity.tick(h.getLevel(), mill.getBlockPos(), mill.getBlockState(), mill);
            }
            h.assertTrue(count(mill.inventory, Items.GRAVEL) == 1 && !done(owner.player(), "millstone"),
                "Blocked real outputs roll back completion and retain the original gravel and progress");
            try (var tx = Transaction.openOuter()) {
                long removed = 0;
                for (var slot : mill.inventory.outputs) removed += slot.extract(ItemVariant.of(Items.DIAMOND), 64, tx);
                h.assertValueEqual(removed, 576L, "All nine original filled output slots are recovered exactly"); tx.commit();
            }
            for (int i = 0; i < 40 && !mill.inventory.inputs.getFirst().isResourceBlank(); i++) {
                GameplayAdvancementGameTests.drive(mill, 256);
                MillstoneBlockEntity.tick(h.getLevel(), mill.getBlockPos(), mill.getBlockState(), mill);
            }
            h.assertTrue(count(mill.inventory, Items.GRAVEL) == 0 && count(mill.inventory, Items.FLINT) == 1,
                "One actual committed recipe pays one gravel and owns one flint");
            h.assertTrue(done(owner.player(), "millstone") && !done(observer.player(), "millstone"),
                "Committed milling awards only its exact persisted real owner");
        }
        h.succeed();
    }

    @GameTest
    public void sawAwardsOwnerAfterOneActualAlloyBecomesSixRealShafts(GameTestHelper h) {
        h.setBlock(P, AllBlocks.MECHANICAL_SAW.defaultBlockState().setValue(SawBlock.FACING, Direction.UP));
        try (var owner = join(h); var observer = join(h)) {
            placed(h, P, owner.player()); var saw = h.getBlockEntity(P, SawBlockEntity.class);
            saw.setFilter(new ItemStack(AllBlocks.SHAFT));
            try (var tx = Transaction.openOuter()) {
                h.assertValueEqual(saw.inventory.insert(ItemVariant.of(AllItems.ANDESITE_ALLOY), 1, tx), 1L, "Native simulated saw input accepts one alloy");
            }
            h.assertTrue(saw.inventory.isEmpty() && !done(owner.player(), "saw_processing"), "Aborted saw input creates neither items nor progress");
            try (var tx = Transaction.openOuter()) {
                h.assertValueEqual(saw.inventory.insert(ItemVariant.of(AllItems.ANDESITE_ALLOY), 1, tx), 1L, "One finite alloy enters the actual saw"); tx.commit();
            }
            h.assertValueEqual(saw.inventory.selectedRecipe(), Create.asResource("cutting/andesite_alloy"), "Real output filter selects the pinned recipe");
            for (int i = 0; i < 100 && !saw.inventory.appliedRecipe; i++) {
                GameplayAdvancementGameTests.drive(saw, 256);
                SawBlockEntity.tick(h.getLevel(), saw.getBlockPos(), saw.getBlockState(), saw);
            }
            h.assertTrue(saw.inventory.input().isEmpty() && saw.inventory.outputs().stream().allMatch(item -> item.is(AllBlocks.SHAFT.asItem()))
                && saw.inventory.outputs().stream().mapToInt(ItemStack::getCount).sum() == 6, "One paid native alloy becomes exactly six shafts");
            h.assertTrue(done(owner.player(), "saw_processing") && !done(observer.player(), "saw_processing"),
                "Only the original actual saw owner earns the completed cutting event");
        }
        h.succeed();
    }

    private static CrushingWheelControllerBlockEntity crusher(GameTestHelper h, ServerPlayer owner, ServerPlayer otherOwner) {
        var state = AllBlocks.CRUSHING_WHEEL.defaultBlockState().setValue(CrushingWheelBlock.AXIS, Direction.Axis.Z);
        h.setBlock(P.west(), state); h.setBlock(P.east(), state);
        placed(h, P.west(), owner); placed(h, P.east(), otherOwner);
        CrushingWheelBlock.updateControllers(h.getLevel(), h.absolutePos(P.west()), state);
        return h.getBlockEntity(P, CrushingWheelControllerBlockEntity.class);
    }

    @GameTest
    public void ordinaryCrusherAwardRequiresActualOpposedRotatingPair(GameTestHelper h) {
        try (var owner = join(h); var observer = join(h)) {
            var controller = crusher(h, owner.player(), observer.player());
            var left = h.getBlockEntity(P.west(), CrushingWheelBlockEntity.class);
            var right = h.getBlockEntity(P.east(), CrushingWheelBlockEntity.class);
            GameplayAdvancementGameTests.drive(left, 128); GameplayAdvancementGameTests.drive(right, 128); controller.refreshDrive();
            h.assertTrue(controller.getCrushingSpeed() == 0 && !done(owner.player(), "crushing_wheel"), "Same-direction invalid pair has no source working-wheel event");
            GameplayAdvancementGameTests.drive(right, -128); controller.refreshDrive();
            h.assertTrue(controller.getCrushingSpeed() == 128f / 50 && done(owner.player(), "crushing_wheel") && !done(observer.player(), "crushing_wheel"),
                "Actual valid working pair awards the source-selected first wheel owner only");
        }
        h.succeed();
    }

    @GameTest
    public void crusherMaxAwardPreservesOriginalStrictConfiguredSpeedThreshold(GameTestHelper h) {
        try (var owner = join(h); var observer = join(h)) {
            var controller = crusher(h, owner.player(), observer.player());
            var left = h.getBlockEntity(P.west(), CrushingWheelBlockEntity.class);
            var right = h.getBlockEntity(P.east(), CrushingWheelBlockEntity.class);
            float max = com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.maxRotationSpeed.get();
            GameplayAdvancementGameTests.drive(left, max - 1); GameplayAdvancementGameTests.drive(right, -(max - 1)); controller.refreshDrive();
            h.assertFalse(done(owner.player(), "crusher_maxed_0000"), "Exact max-minus-one cannot pass the source strict greater-than predicate");
            GameplayAdvancementGameTests.drive(left, -max); GameplayAdvancementGameTests.drive(right, max); controller.refreshDrive();
            h.assertTrue(controller.getCrushingSpeed() == max / 50 && done(owner.player(), "crusher_maxed_0000") && !done(observer.player(), "crusher_maxed_0000"),
                "Genuine reversed max-speed working pair awards only the original selected wheel owner");
        }
        h.succeed();
    }

    @GameTest
    public void deployerRecipePaysOriginalQuartzAndToolBeforeOwnerAward(GameTestHelper h) {
        h.setBlock(P, AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING, Direction.DOWN));
        h.setBlock(P.below(2), AllBlocks.DEPOT);
        try (var owner = join(h); var observer = join(h)) {
            placed(h, P, owner.player()); var deployer = h.getBlockEntity(P, DeployerBlockEntity.class);
            var depot = h.getBlockEntity(P.below(2), DepotBlockEntity.class);
            deployer.setHeldItem(new ItemStack(AllItems.SAND_PAPER));
            GameplayAdvancementGameTests.drive(deployer, 256);
            try (var tx = Transaction.openOuter()) {
                h.assertValueEqual(depot.inventory.automation.insert(ItemVariant.of(AllItems.ROSE_QUARTZ), 1, tx), 1L, "Actual simulated source quartz insertion");
            }
            for (int i = 0; i < 8; i++) {
                DepotBlockEntity.tick(h.getLevel(), depot.getBlockPos(), depot.getBlockState(), depot);
                GameplayAdvancementGameTests.drive(deployer, 256);
                DeployerBlockEntity.tick(h.getLevel(), deployer.getBlockPos(), deployer.getBlockState(), deployer);
            }
            h.assertTrue(deployer.getHeldItem().getDamageValue() == 0 && !done(owner.player(), "deployer"), "Aborted surface input cannot pay a tool or earn a recipe event");
            insert(h, depot.inventory, AllItems.ROSE_QUARTZ, 1);
            for (int i = 0; i < 80 && count(depot.inventory, AllItems.POLISHED_ROSE_QUARTZ) == 0; i++) {
                DepotBlockEntity.tick(h.getLevel(), depot.getBlockPos(), depot.getBlockState(), depot);
                GameplayAdvancementGameTests.drive(deployer, 256);
                DeployerBlockEntity.tick(h.getLevel(), deployer.getBlockPos(), deployer.getBlockState(), deployer);
            }
            h.assertTrue(count(depot.inventory, AllItems.ROSE_QUARTZ) == 0 && count(depot.inventory, AllItems.POLISHED_ROSE_QUARTZ) == 1
                && deployer.getHeldItem().getDamageValue() == 1, "Actual committed polishing debits one quartz and one native paper durability: input="
                    + count(depot.inventory, AllItems.ROSE_QUARTZ) + ", output=" + count(depot.inventory, AllItems.POLISHED_ROSE_QUARTZ)
                    + ", damage=" + deployer.getHeldItem().getDamageValue());
            h.assertTrue(done(owner.player(), "deployer") && !done(observer.player(), "deployer") && owner.player().getUUID().equals(deployer.owner),
                "Recipe award targets the genuine owner and preserves durable deployer authority");
        }
        h.succeed();
    }

    @GameTest
    public void deployerWorldHandlerPaysOneRealStoneBeforeOriginalOwnerEvent(GameTestHelper h) {
        h.setBlock(P, AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING, Direction.DOWN));
        h.setBlock(P.below(3), Blocks.STONE);
        try (var owner = join(h); var observer = join(h)) {
            placed(h, P, owner.player()); var deployer = h.getBlockEntity(P, DeployerBlockEntity.class);
            deployer.setHeldItem(new ItemStack(Items.STONE, 2)); GameplayAdvancementGameTests.drive(deployer, 256);
            for (int i = 0; i < 80 && h.getBlockState(P.below(2)).isAir(); i++) {
                GameplayAdvancementGameTests.drive(deployer, 256);
                DeployerBlockEntity.tick(h.getLevel(), deployer.getBlockPos(), deployer.getBlockState(), deployer);
            }
            h.assertTrue(h.getBlockState(P.below(2)).is(Blocks.STONE) && deployer.getHeldItem().getCount() == 1,
                "Original native survival handler really places and pays one stone");
            h.assertTrue(done(owner.player(), "deployer") && !done(observer.player(), "deployer") && owner.player().getUUID().equals(deployer.owner),
                "Original after-handler event awards only the real owner without clearing actor authority");
        }
        h.succeed();
    }
}
