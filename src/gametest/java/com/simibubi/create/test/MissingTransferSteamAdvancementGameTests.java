package com.simibubi.create.test;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.item.PlayerInventoryStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Five missing source-hook regressions. No direct advancement award or fabricated progress. UNRUN. */
public final class MissingTransferSteamAdvancementGameTests {
    private static final BlockPos P = new BlockPos(4, 3, 4);

    private static ServerPlayer player(GameTestHelper h) {
        var joined = NativeSurvivalTestPlayer.join(h, Vec3.atBottomCenterOf(h.absolutePos(P.north(2))));
        var closed = new AtomicBoolean();
        Runnable cleanup = () -> { if (closed.compareAndSet(false, true)) joined.close(); };
        ((com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor) (Object) h)
            .create$getActualNativeTestInfo().addListener(new net.minecraft.gametest.framework.GameTestListener() {
                public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) {}
                public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
                public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
                public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo old, net.minecraft.gametest.framework.GameTestInfo next, net.minecraft.gametest.framework.GameTestRunner runner) { cleanup.run(); }
            });
        return joined.player();
    }

    private static boolean done(ServerPlayer player, String id) {
        var entry = player.level().getServer().getAdvancements().get(Create.asResource(id));
        if (entry == null) throw new AssertionError("Missing genuine source advancement: " + id);
        return player.getAdvancements().getOrStartProgress(entry).isDone();
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block, ServerPlayer player, BlockPos support, Direction face) {
        var stack = new ItemStack(block);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var at = h.absolutePos(support);
        var hit = new BlockHitResult(Vec3.atCenterOf(at).add(face.getUnitVec3().scale(.499)), face, at, false);
        h.assertTrue(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
            && stack.isEmpty() && h.getBlockState(pos).is(block), "Physical native survival placement pays the one source item");
        var be = (SmartBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        h.assertTrue(player.getUUID().equals(be.advancementOwnerStorage().getPlayerId()), "Actual BlockItem callback records the real placed owner");
    }

    private static <T extends SmartBlockEntity> T reload(GameTestHelper h, T original, Class<T> type, ServerPlayer player) {
        var saved = original.saveWithFullMetadata(h.getLevel().registryAccess());
        var decoded = type.cast(BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(), saved, h.getLevel().registryAccess()));
        h.assertTrue(decoded != null && decoded.getLevel() == null
            && player.getUUID().equals(decoded.advancementOwnerStorage().getPlayerId()), "Original UUID survives actual native pre-level decode");
        h.getLevel().removeBlockEntity(original.getBlockPos());
        h.getLevel().setBlockEntity(decoded);
        h.assertTrue(h.getLevel().getBlockEntity(decoded.getBlockPos()) == decoded, "Only the fresh native owner is installed");
        return decoded;
    }

    private static void connector(GameTestHelper h, ServerPlayer player, BlockPos pos, boolean succeeds) {
        var at = h.absolutePos(pos);
        var hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
        var result = player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(result.consumesAction() == succeeds, "Native connector result matches actual path admission");
    }

    @GameTest public void beltAwardRequiresCompletedRealConnectorAndNeverInvalidFirstSelectionOrFakePlayer(GameTestHelper h) {
        var owner = player(h); var observer = player(h);
        var first = new BlockPos(2, 3, 2); var second = first.east(4);
        for (var pos : java.util.List.of(first, second)) h.setBlock(pos, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.BELT_CONNECTOR, 2));
        connector(h, owner, first, true);
        h.assertFalse(done(owner, "belt"), "First actual pulley selection is not a connected belt");
        h.setBlock(first.east(2), Blocks.STONE); connector(h, owner, second, false);
        h.assertTrue(!done(owner, "belt") && owner.getMainHandItem().getCount() == 2, "Obstructed path awards nothing and pays no connector");
        h.setBlock(first.east(2), Blocks.AIR); connector(h, owner, second, true);
        h.assertTrue(done(owner, "belt") && !done(observer, "belt") && owner.getMainHandItem().getCount() == 1,
            "Completed native belt awards exactly its real connector interactor and consumes once");
        var fake = FakePlayer.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "belt_hook_fake"));
        var fakeFirst = first.south(4); var fakeSecond = fakeFirst.east(4);
        for (var pos : java.util.List.of(fakeFirst, fakeSecond)) h.setBlock(pos, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        fake.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.BELT_CONNECTOR, 2));
        connector(h, fake, fakeFirst, true); connector(h, fake, fakeSecond, true);
        h.assertFalse(done(fake, "belt"), "Even a successful actual fake-player connector cannot earn a player advancement");
        h.succeed();
    }

    @GameTest public void funnelOwnerSurvivesNativeReloadAndAwardsOnlyAfterPaidOuterCommit(GameTestHelper h) {
        var owner = player(h); var observer = player(h);
        h.setBlock(P.south(), Blocks.BARREL);
        owner.setYRot(0); owner.setXRot(0); owner.setYHeadRot(0); owner.setShiftKeyDown(true);
        place(h, P, AllBlocks.ANDESITE_FUNNEL, owner, P.south(), Direction.NORTH);
        owner.setShiftKeyDown(false);
        h.assertTrue(h.getBlockState(P).getValue(FunnelBlock.FACING) == Direction.NORTH
            && !h.getBlockState(P).getValue(FunnelBlock.EXTRACTING), "Physical source funnel points away from actual receipt barrel");
        var funnel = reload(h, h.getBlockEntity(P, FunnelBlockEntity.class), FunnelBlockEntity.class, owner);
        var receipt = h.getBlockEntity(P.south(), BarrelBlockEntity.class);
        for (int slot = 0; slot < receipt.getContainerSize(); slot++) receipt.setItem(slot, new ItemStack(Items.STONE, 64));
        h.assertTrue(funnel.insert(new ItemStack(Items.APPLE, 7), false).getCount() == 7 && !done(owner, "funnel"), "Full native destination never invents transfer or award");
        receipt.setItem(0, ItemStack.EMPTY);
        h.assertTrue(funnel.insert(new ItemStack(Items.APPLE, 7), true).isEmpty() && receipt.getItem(0).isEmpty()
            && !done(owner, "funnel"), "Accepted simulation neither changes the actual barrel nor awards its owner");
        owner.getInventory().setItem(0, new ItemStack(Items.APPLE, 7));
        var source = PlayerInventoryStorage.of(owner).getSlot(0); var apple = ItemVariant.of(Items.APPLE);
        try (var outer = Transaction.openOuter()) {
            try (var inner = outer.openNested()) {
                h.assertTrue(source.extract(apple, 7, inner) == 7 && funnel.insert(apple, 7, true, inner) == 7, "Seven actual apples stage in nested transfer");
                inner.commit();
            }
        }
        h.assertTrue(source.getAmount() == 7 && receipt.getItem(0).isEmpty() && !done(owner, "funnel"), "Outer abort restores both real owners and awards nothing");
        try (var tx = Transaction.openOuter()) {
            h.assertTrue(source.extract(apple, 7, tx) == 7 && funnel.insert(apple, 7, true, tx) == 7, "Same finite original stock pays the real receipt");
            h.assertFalse(done(owner, "funnel"), "No source progress before the outer transfer closes committed");
            tx.commit();
        }
        h.assertTrue(source.isResourceBlank() && receipt.getItem(0).is(Items.APPLE) && receipt.getItem(0).getCount() == 7
            && done(owner, "funnel") && !done(observer, "funnel"), "Actual committed source event awards only the persisted placer and conserves7 apples");
        h.succeed();
    }

    @GameTest public void chuteOwnerReceivesOnlyCommittedNativeCargoAfterReloadAndNestedAbort(GameTestHelper h) {
        var owner = player(h); var observer = player(h); h.setBlock(P.below(), Blocks.STONE);
        place(h, P, AllBlocks.CHUTE, owner, P.below(), Direction.UP);
        var chute = reload(h, h.getBlockEntity(P, ChuteBlockEntity.class), ChuteBlockEntity.class, owner);
        owner.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 7));
        var source = PlayerInventoryStorage.of(owner).getSlot(0); var diamond = ItemVariant.of(Items.DIAMOND);
        try (var outer = Transaction.openOuter()) {
            try (var inner = outer.openNested()) {
                h.assertTrue(source.extract(diamond, 7, inner) == 7 && chute.itemHandler.insert(diamond, 7, inner) == 7, "Real native stock can stage in the chute");
                h.assertValueEqual(chute.itemHandler.insert(diamond, 1, inner), 0L, "Occupied chute refuses another insertion even within the transaction");
                inner.commit();
            }
            h.assertFalse(done(owner, "chute"), "Nested commit cannot grant progress before its real outer owner commits");
        }
        h.assertTrue(source.getAmount() == 7 && chute.getItem().isEmpty() && !done(owner, "chute"), "Aborted native insertion restores original diamonds and criterion");
        try (var tx = Transaction.openOuter()) {
            h.assertTrue(source.extract(diamond, 7, tx) == 7 && chute.itemHandler.insert(diamond, 7, tx) == 7,
                "Actual stock can visit the chute within a still-reversible transaction");
            h.assertTrue(chute.itemHandler.extract(diamond, 7, tx) == 7 && source.insert(diamond, 7, tx) == 7,
                "The same seven originals return to their original native owner before commit");
            tx.commit();
        }
        h.assertTrue(source.getAmount() == 7 && chute.getItem().isEmpty() && !done(owner, "chute"),
            "A committed net-unchanged item round trip cannot invent an advancement from changed insertion position");
        try (var tx = Transaction.openOuter()) {
            h.assertTrue(source.extract(diamond, 7, tx) == 7 && chute.itemHandler.insert(diamond, 7, tx) == 7, "Seven actual original diamonds enter the genuine chute");
            h.assertFalse(done(owner, "chute"), "No award while the paid insertion remains uncommitted"); tx.commit();
        }
        h.assertTrue(source.isResourceBlank() && chute.getItem().is(Items.DIAMOND) && chute.getItem().getCount() == 7
            && done(owner, "chute") && !done(observer, "chute"), "Committed real cargo awards the original saved placer and conserves all7 diamonds");
        h.succeed();
    }

    @GameTest public void originalWhistleCriterionRequiresNativeRedstonePowerButNotHotBoilerOrSound(GameTestHelper h) {
        var owner = player(h); var observer = player(h); h.setBlock(P, AllBlocks.FLUID_TANK);
        var pos = P.above(); place(h, pos, AllBlocks.STEAM_WHISTLE, owner, P, Direction.UP);
        var whistle = reload(h, h.getBlockEntity(pos, WhistleBlockEntity.class), WhistleBlockEntity.class, owner);
        WhistleBlockEntity.tick(h.getLevel(), whistle.getBlockPos(), whistle.getBlockState(), whistle);
        h.assertTrue(!whistle.isPowered() && !done(owner, "steam_whistle"), "An actual unpowered placed whistle awards nothing");
        h.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
        WhistleBlockEntity.tick(h.getLevel(), whistle.getBlockPos(), h.getBlockState(pos), whistle);
        h.assertTrue(whistle.isPowered() && !whistle.isSounding() && whistle.getTank().boiler.activeHeat == 0 && !whistle.getTank().boiler.passiveHeat
            && done(owner, "steam_whistle") && !done(observer, "steam_whistle"), "Pinned source awards real redstone power even on a cold silent tank, only to its persisted owner");
        h.succeed();
    }

    @GameTest(maxTicks = 120) public void steamEngineAwardsSavedOwnerOnlyAfterFiniteWaterCommitsAndActualPositiveEfficiency(GameTestHelper h) {
        var owner = player(h); var observer = player(h);
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            h.setBlock(P.offset(x, 0, z), AllBlocks.FLUID_TANK); h.setBlock(P.offset(x, -1, z), Blocks.MAGMA_BLOCK);
        }
        var enginePos = P.west(); var shaftPos = enginePos.west(2); var reservePos = P.offset(4, 0, 4);
        h.setBlock(shaftPos, AllBlocks.SHAFT.defaultBlockState().setValue(PoweredShaftBlock.AXIS, Direction.Axis.Z));
        owner.setYRot(-90); owner.setXRot(0); owner.setYHeadRot(-90);
        place(h, enginePos, AllBlocks.STEAM_ENGINE, owner, P, Direction.WEST);
        reload(h, h.getBlockEntity(enginePos, SteamEngineBlockEntity.class), SteamEngineBlockEntity.class, owner);
        h.setBlock(reservePos, AllBlocks.FLUID_TANK); var water = FluidVariant.of(Fluids.WATER);
        try (var tx = Transaction.openOuter()) {
            h.assertValueEqual(h.getBlockEntity(reservePos, FluidTankBlockEntity.class).storage().insert(water, 81000, tx), 81000L, "One finite original bucket is stored separately from the real boiler"); tx.commit();
        }
        h.onEachTick(() -> {
            var tank = h.getBlockEntity(P, FluidTankBlockEntity.class).getControllerBE();
            var reserve = h.getBlockEntity(reservePos, FluidTankBlockEntity.class).getControllerBE();
            if (tank == null || reserve == null || !tank.boiler.isActive()) return;
            try (var tx = Transaction.openOuter()) {
                long moved = reserve.storage().extract(water, 810, tx);
                h.assertValueEqual(tank.storage().insert(water, moved, tx), moved, "Only original finite reserve water enters native boiler supply");
                if (h.getTick() > 30) tx.commit();
            }
        });
        h.runAfterDelay(28, () -> {
            var tank = h.getBlockEntity(P, FluidTankBlockEntity.class).getControllerBE();
            h.assertTrue(tank.boiler.waterSupply == 0 && !done(owner, "steam_engine")
                && h.getBlockEntity(reservePos, FluidTankBlockEntity.class).getTankInventory().getAmount() == 81000,
                "Actual aborted supply leaves zero efficiency, no award and the complete original bucket");
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 35, "Native initial abort phase must complete");
            var tank = h.getBlockEntity(P, FluidTankBlockEntity.class).getControllerBE();
            var shaft = h.getBlockEntity(shaftPos, PoweredShaftBlockEntity.class);
            h.assertTrue(tank.getTotalTankSize() == 4 && tank.boiler.getEngineEfficiency(4) > 0 && shaft.getSpeed() == 16
                && h.getBlockEntity(reservePos, FluidTankBlockEntity.class).getTankInventory().getAmount() < 81000
                && done(owner, "steam_engine") && !done(observer, "steam_engine"),
                "Real committed water powers the native16rpm engine and awards only its genuine persisted placer");
        });
    }
}
