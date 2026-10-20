package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.actors.contraptionControls.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import org.apache.commons.lang3.tuple.MutablePair;

/** Native controls persistence/input and source global-to-specific actor inversion. */
public class ControlsGameTests {
    private static final BlockPos P = new BlockPos(4, 4, 4), CONTROL = BlockPos.ZERO, DRILL = new BlockPos(1, 0, 0), SAW = new BlockPos(2, 0, 0);
    private static final class Actors extends BearingContraption {
        Actors() { super(false, Direction.UP); bounds = new AABB(0, 0, 0, 4, 1, 1); }
        void add(BlockPos pos, net.minecraft.world.level.block.state.BlockState state, CompoundTag tag) {
            var info = new StructureBlockInfo(pos, state, tag); blocks.put(pos, info); actors.add(MutablePair.of(info, null));
        }
    }
    private static CompoundTag controlsTag(GameTestHelper h, ItemStack filter, boolean disabled) {
        var out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, h.getLevel().registryAccess()); out.store("Filter", ItemStack.OPTIONAL_CODEC, filter); out.putBoolean("Disabled", disabled); return out.buildResult();
    }
    private static Actors actors(GameTestHelper h, ItemStack filter, boolean disabled) {
        var result = new Actors(); result.add(CONTROL, AllBlocks.CONTRAPTION_CONTROLS.defaultBlockState(), controlsTag(h, filter, disabled));
        result.add(DRILL, AllBlocks.MECHANICAL_DRILL.defaultBlockState(), new CompoundTag()); result.add(SAW, AllBlocks.MECHANICAL_SAW.defaultBlockState(), new CompoundTag()); result.startMoving(h.getLevel()); return result;
    }
    private static MovementContext actor(Contraption contraption, BlockPos pos) { return contraption.getActorAt(pos).right; }
    private static ContraptionControlsBlockEntity placed(GameTestHelper h) { h.setBlock(P, AllBlocks.CONTRAPTION_CONTROLS); return h.getBlockEntity(P, ContraptionControlsBlockEntity.class); }
    private static BlockHitResult button(GameTestHelper h) { var pos = h.absolutePos(P); return new BlockHitResult(Vec3.atLowerCornerOf(pos).add(.15, .45, .45), Direction.NORTH, pos, false); }
    private static void click(GameTestHelper h) { h.getBlockState(P).useWithoutItem(h.getLevel(), h.makeMockPlayer(GameType.SURVIVAL), button(h)); }
    @GameTest public void disabledUnfilteredControlInitializesAllActorTypes(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, true);
        h.assertTrue(actor(c, DRILL).disabled && actor(c, SAW).disabled && !actor(c, CONTROL).disabled, "Source global disable excludes controls themselves");
        h.assertTrue(c.getDisabledActors().size() == 1 && c.getDisabledActors().getFirst().isEmpty(), "Global sentinel is retained"); h.succeed();
    }
    @GameTest public void disabledFilteredControlOnlyInitializesItsOwnType(GameTestHelper h) {
        var c = actors(h, new ItemStack(AllBlocks.MECHANICAL_DRILL), true);
        h.assertTrue(actor(c, DRILL).disabled && !actor(c, SAW).disabled && !actor(c, CONTROL).disabled, "Type selection at assembly"); h.succeed();
    }
    @GameTest public void multipleInitiallyDisabledControlsCombineWithoutDuplicates(GameTestHelper h) {
        var c = actors(h, new ItemStack(AllBlocks.MECHANICAL_DRILL), true);
        c.add(new BlockPos(3, 0, 0), AllBlocks.CONTRAPTION_CONTROLS.defaultBlockState(), controlsTag(h, new ItemStack(AllBlocks.MECHANICAL_DRILL), true));
        c.startMoving(h.getLevel()); h.assertTrue(c.getDisabledActors().size() == 1 && actor(c, DRILL).disabled && !actor(c, SAW).disabled, "Repeated controls deduplicate filters after every context exists"); h.succeed();
    }
    @GameTest public void globalControlTogglesAllActorsAndLeavesItselfUsable(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, false);
        h.assertTrue(ContraptionControlsMovingInteraction.toggle(c, ItemStack.EMPTY), "First click disables");
        h.assertTrue(actor(c, DRILL).disabled && actor(c, SAW).disabled && !actor(c, CONTROL).disabled, "Only disableable actors disabled");
        h.assertFalse(ContraptionControlsMovingInteraction.toggle(c, ItemStack.EMPTY), "Second click enables");
        h.assertTrue(!actor(c, DRILL).disabled && !actor(c, SAW).disabled && c.getDisabledActors().isEmpty(), "Global enable removes sentinel"); h.succeed();
    }
    @GameTest public void specificControlAfterGlobalEnablesOnlyItsOwnType(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, true);
        h.assertFalse(ContraptionControlsMovingInteraction.toggle(c, new ItemStack(AllBlocks.MECHANICAL_DRILL)), "Source inversion enables selected type");
        h.assertTrue(!actor(c, DRILL).disabled && actor(c, SAW).disabled && c.getDisabledActors().size() == 1 && c.getDisabledActors().getFirst().is(AllBlocks.MECHANICAL_SAW.asItem()), "Remaining types explicitly stay disabled"); h.succeed();
    }
    @GameTest public void globalClickReplacesSpecificSelectionsWithTheGlobalSentinel(GameTestHelper h) {
        var c = actors(h, new ItemStack(AllBlocks.MECHANICAL_DRILL), true);
        ContraptionControlsMovingInteraction.toggle(c, ItemStack.EMPTY);
        h.assertTrue(c.getDisabledActors().size() == 1 && c.getDisabledActors().getFirst().isEmpty() && actor(c, DRILL).disabled && actor(c, SAW).disabled, "Source global click clears individual filters"); h.succeed();
    }
    @GameTest public void disablingOneTypeDoesNotDisableOtherTypes(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, false); ContraptionControlsMovingInteraction.toggle(c, new ItemStack(AllBlocks.MECHANICAL_DRILL));
        h.assertTrue(actor(c, DRILL).disabled && !actor(c, SAW).disabled, "Selected actor type only");
        ContraptionControlsMovingInteraction.toggle(c, new ItemStack(AllBlocks.MECHANICAL_DRILL, 17));
        h.assertTrue(!actor(c, DRILL).disabled && c.getDisabledActors().isEmpty(), "Sample count does not change type matching"); h.succeed();
    }
    @GameTest public void filtersCompareItemComponentsAndIgnoreSampleCounts(GameTestHelper h) {
        var plain = new ItemStack(AllBlocks.MECHANICAL_DRILL); var named = plain.copy(); named.set(DataComponents.CUSTOM_NAME, Component.literal("Special drill"));
        h.assertFalse(ContraptionControlsMovement.isSameFilter(plain, named), "Component-bearing filter differs");
        h.assertTrue(ContraptionControlsMovement.isSameFilter(named, named.copyWithCount(8)) && ContraptionControlsMovement.isSameFilter(ItemStack.EMPTY, ItemStack.EMPTY), "Source count-independent equality");
        var c = actors(h, named, true); h.assertTrue(!actor(c, DRILL).disabled && c.getDisabledActors().size() == 1, "Components are not discarded when initial filters are stored"); h.succeed();
    }
    @GameTest public void disablingDrillCancelsItsActualPendingBreakerWork(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, false); var ctx = actor(c, DRILL); ctx.stall = true; ctx.data.store("BreakingPos", BlockPos.CODEC, h.absolutePos(P)); ctx.data.putInt("Progress", 4); ctx.data.putInt("WaitingTicks", 7);
        ContraptionControlsMovingInteraction.toggle(c, new ItemStack(AllBlocks.MECHANICAL_DRILL));
        h.assertTrue(ctx.disabled && !ctx.stall && !ctx.data.contains("BreakingPos") && !ctx.data.contains("Progress") && !ctx.data.contains("WaitingTicks"), "Source onDisabledByControls cancels native breaker/stall"); h.succeed();
    }
    @GameTest public void stopWritesCurrentDisabledStateInsteadOfTheAssemblySnapshot(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, false); ContraptionControlsMovingInteraction.toggle(c, ItemStack.EMPTY); c.stop(h.getLevel());
        h.assertTrue(actor(c, CONTROL).blockEntityData.getBooleanOr("Disabled", false), "Disassembly restores current disabled state"); h.succeed();
    }
    @GameTest public void filteredControlRestoresDisabledWhenGlobalControlIsOff(GameTestHelper h) {
        var c = actors(h, new ItemStack(AllBlocks.MECHANICAL_DRILL), false); ContraptionControlsMovingInteraction.toggle(c, ItemStack.EMPTY); c.stop(h.getLevel());
        h.assertTrue(actor(c, CONTROL).blockEntityData.getBooleanOr("Disabled", false), "Global sentinel takes precedence over the local filter on restoration"); h.succeed();
    }
    @GameTest public void reenabledControlRestoresEnabledInsteadOfItsOldDisabledFlag(GameTestHelper h) {
        var c = actors(h, new ItemStack(AllBlocks.MECHANICAL_DRILL), true); ContraptionControlsMovingInteraction.toggle(c, new ItemStack(AllBlocks.MECHANICAL_DRILL)); c.stop(h.getLevel());
        h.assertFalse(actor(c, CONTROL).blockEntityData.getBooleanOr("Disabled", true), "Current control state survives disassembly"); h.succeed();
    }
    @GameTest public void nativeActorSnapshotRestoresGlobalDisableAndAllowsSpecificInversion(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, true); var tag = new CompoundTag(); c.writeActors(tag, h.getLevel().registryAccess());
        var loaded = actors(h, ItemStack.EMPTY, false); loaded.readActors(h.getLevel(), tag);
        h.assertTrue(actor(loaded, DRILL).disabled && actor(loaded, SAW).disabled, "Global sentinel applied after native actor decoding");
        ContraptionControlsMovingInteraction.toggle(loaded, new ItemStack(AllBlocks.MECHANICAL_DRILL));
        h.assertTrue(!actor(loaded, DRILL).disabled && actor(loaded, SAW).disabled, "Snapshot still supports source inversion"); h.succeed();
    }
    @GameTest public void fullContraptionPersistencePreservesDynamicEnabledState(GameTestHelper h) {
        var c = actors(h, new ItemStack(AllBlocks.MECHANICAL_DRILL), true); ContraptionControlsMovingInteraction.toggle(c, new ItemStack(AllBlocks.MECHANICAL_DRILL));
        var restored = new BearingContraption(); restored.readNBT(h.getLevel(), c.writeNBT(h.getLevel().registryAccess(), false), false);
        h.assertTrue(!actor(restored, DRILL).disabled && restored.getDisabledActors().isEmpty() && actor(restored, CONTROL).blockEntityData.getBooleanOr("Disabled", false), "Actor snapshot is authoritative while moving; old stationary NBT cannot disable a reenabled type"); h.succeed();
    }
    @GameTest public void nativeBlockEntityLoadBeforeLevelPreservesFlagsAndFilterComponents(GameTestHelper h) {
        var controls = placed(h); var sample = new ItemStack(AllBlocks.MECHANICAL_DRILL); sample.set(DataComponents.CUSTOM_NAME, Component.literal("Stored type")); controls.filtering.setFilter(sample); controls.disabled = true; controls.powered = true;
        var loaded = (ContraptionControlsBlockEntity) BlockEntity.loadStatic(h.absolutePos(P), h.getBlockState(P), controls.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(loaded.getLevel() == null && loaded.disabled && loaded.powered && ItemStack.isSameItemSameComponents(sample, loaded.filtering.getFilter()), "Native load occurs before setLevel and retains exact source settings");
        loaded.setLevel(h.getLevel()); h.assertTrue(loaded.disabled && loaded.powered, "setLevel alone cannot overwrite saved toggles"); h.succeed();
    }
    @GameTest public void nativeEmptyHandClickTogglesAndSyncTagContainsTheCurrentState(GameTestHelper h) {
        var controls = placed(h); click(h); var tag = controls.getUpdateTag(h.getLevel().registryAccess());
        h.assertTrue(controls.disabled && tag.getBooleanOr("Disabled", false) && tag.getLongOr("ButtonSequence", 0) == 1, "Native toggle produces a current BE update tag");
        click(h); h.assertFalse(controls.disabled, "Second native click reenables"); h.succeed();
    }
    @GameTest public void redstoneTransitionsOverwriteToggleOnlyWhenPowerChanges(GameTestHelper h) {
        var controls = placed(h); h.setBlock(P.east(), Blocks.REDSTONE_BLOCK); controls.updatePoweredState();
        h.assertTrue(controls.powered && controls.disabled, "Rising edge disables"); click(h); controls.updatePoweredState();
        h.assertTrue(controls.powered && !controls.disabled, "Manual override remains until a signal transition");
        h.setBlock(P.east(), Blocks.AIR); controls.updatePoweredState(); h.assertTrue(!controls.powered && !controls.disabled, "Falling edge enables"); h.succeed();
    }
    @GameTest public void filteringUsesActualNativeHitAndNeverConsumesAnOrdinaryActorSample(GameTestHelper h) {
        var controls = placed(h); var player = h.makeMockPlayer(GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.MECHANICAL_DRILL, 9));
        var pos = h.absolutePos(P); var hit = new BlockHitResult(Vec3.atLowerCornerOf(pos).add(ContraptionControlsBlock.filterPosition(h.getBlockState(P))), Direction.UP, pos, false);
        h.getBlockState(P).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(controls.filtering.getFilter().is(AllBlocks.MECHANICAL_DRILL.asItem()) && player.getMainHandItem().getCount() == 9 && !controls.disabled, "Source filter route keeps ordinary sample and does not press button");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND)); h.getBlockState(P).useItemOn(player.getMainHandItem(), h.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        h.assertTrue(controls.filtering.getFilter().is(AllBlocks.MECHANICAL_DRILL.asItem()) && !controls.disabled, "Invalid item cannot silently toggle controls");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); h.getBlockState(P).useWithoutItem(h.getLevel(), player, hit); h.assertTrue(controls.filtering.getFilter().isEmpty() && !controls.disabled, "Empty-hand sample clears filter"); h.succeed();
    }
    @GameTest public void ordinaryBlockItemsCannotBecomeActorFilters(GameTestHelper h) {
        var controls = placed(h); h.assertFalse(controls.filtering.setFilter(new ItemStack(Items.DIAMOND)), "Source controlled-items tag is enforced");
        for (var block : List.of(AllBlocks.MECHANICAL_DRILL, AllBlocks.MECHANICAL_SAW, AllBlocks.REDSTONE_CONTACT)) h.assertTrue(controls.filtering.setFilter(new ItemStack(block)), "Ported source actor tag includes " + block);
        controls.clearContent(); h.assertTrue(controls.filtering.getFilter().isEmpty(), "Clearable removes only the sample"); h.succeed();
    }
    @GameTest public void controlsExposeOriginalWaterloggedPropertiesAndHorizontalShapes(GameTestHelper h) {
        for (var direction : Direction.Plane.HORIZONTAL) {
            var state = AllBlocks.CONTRAPTION_CONTROLS.defaultBlockState().setValue(ContraptionControlsBlock.FACING, direction).setValue(ContraptionControlsBlock.WATERLOGGED, true);
            h.assertTrue(state.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER) && !state.getCollisionShape(h.getLevel(), h.absolutePos(P)).isEmpty() && !state.getShape(h.getLevel(), h.absolutePos(P)).isEmpty(), "Original controls shape/water " + direction);
            h.assertTrue(!state.getValue(ContraptionControlsBlock.OPEN) && !state.getValue(ContraptionControlsBlock.VIRTUAL), "Source stationary default flags");
        } h.succeed();
    }
    @GameTest public void directGlobalDisableStoresTheGlobalSentinelAndKeepsControlsActive(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, false); c.setActorsActive(ItemStack.EMPTY, false);
        h.assertTrue(c.getDisabledActors().size() == 1 && c.getDisabledActors().getFirst().isEmpty() && actor(c, DRILL).disabled && actor(c, SAW).disabled && !actor(c, CONTROL).disabled, "Direct API uses the same global filter as the physical control"); h.succeed();
    }
    @GameTest public void directGlobalEnableClearsEveryPerTypeFilter(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, false); c.setActorsActive(new ItemStack(AllBlocks.MECHANICAL_DRILL), false); c.setActorsActive(new ItemStack(AllBlocks.MECHANICAL_SAW), false);
        c.setActorsActive(new ItemStack(Blocks.BELL), false); c.setActorsActive(ItemStack.EMPTY, true);
        h.assertTrue(c.getDisabledActors().isEmpty() && !actor(c, DRILL).disabled && !actor(c, SAW).disabled, "Enable all clears filters for present and absent types");
        var tag = new CompoundTag(); c.writeActors(tag, h.getLevel().registryAccess()); var restored = actors(h, ItemStack.EMPTY, false); restored.readActors(h.getLevel(), tag);
        h.assertTrue(restored.getDisabledActors().isEmpty() && !actor(restored, DRILL).disabled && !actor(restored, SAW).disabled, "Native snapshot cannot resurrect a cleared type"); h.succeed();
    }
    @GameTest public void directTypeEnableUnderGlobalDisableSurvivesNativeSnapshot(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, true); c.setActorsActive(new ItemStack(AllBlocks.MECHANICAL_DRILL), true);
        h.assertTrue(!actor(c, DRILL).disabled && actor(c, SAW).disabled && !c.isActorTypeDisabled(ItemStack.EMPTY), "Selected type enabled and global sentinel expanded");
        var restored = new BearingContraption(); restored.readNBT(h.getLevel(), c.writeNBT(h.getLevel().registryAccess(), false), false);
        h.assertTrue(!actor(restored, DRILL).disabled && actor(restored, SAW).disabled && restored.getDisabledActors().size() == 1 && restored.isActorTypeDisabled(new ItemStack(AllBlocks.MECHANICAL_SAW)), "Saving no longer re-disables the enabled type through a stale EMPTY sentinel"); h.succeed();
    }
    @GameTest public void directTypeDisableWhileGloballyDisabledDoesNotExpandOrDuplicateFilters(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, true); c.setActorsActive(new ItemStack(AllBlocks.MECHANICAL_DRILL), false); c.setActorsActive(new ItemStack(AllBlocks.MECHANICAL_DRILL, 17), false);
        h.assertTrue(c.getDisabledActors().size() == 1 && c.getDisabledActors().getFirst().isEmpty(), "Already globally disabled type adds no concrete duplicate");
        c.setActorsActive(ItemStack.EMPTY, true); h.assertTrue(c.getDisabledActors().isEmpty() && !actor(c, DRILL).disabled && !actor(c, SAW).disabled, "Global enable clears the canonical sentinel"); h.succeed();
    }
    @GameTest public void repeatedDirectTypeDisableDeduplicatesCountsButRetainsComponentDistinctFilters(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, false); var plain = new ItemStack(AllBlocks.MECHANICAL_DRILL); var named = plain.copy(); named.set(DataComponents.CUSTOM_NAME, Component.literal("Named type"));
        c.setActorsActive(plain, false); c.setActorsActive(plain.copyWithCount(17), false); c.setActorsActive(named, false);
        h.assertTrue(c.getDisabledActors().size() == 2 && actor(c, DRILL).disabled, "Counts deduplicate, components remain distinct");
        c.setActorsActive(named, true); h.assertTrue(c.getDisabledActors().size() == 1 && actor(c, DRILL).disabled, "Enabling a component-distinct filter cannot enable the ordinary drill");
        c.setActorsActive(plain, true); h.assertTrue(c.getDisabledActors().isEmpty() && !actor(c, DRILL).disabled, "Exact type can be enabled independently"); h.succeed();
    }
    @GameTest public void directEnableOfAbsentTypeUnderGlobalDisableKeepsAllPresentTypesDisabled(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, true); c.setActorsActive(new ItemStack(Blocks.BELL), true);
        h.assertTrue(!c.isActorTypeDisabled(ItemStack.EMPTY) && actor(c, DRILL).disabled && actor(c, SAW).disabled && !actor(c, CONTROL).disabled, "Absent selected type does not enable other present actor types");
        h.assertTrue(c.getDisabledActors().size() == 2 && c.isActorTypeDisabled(new ItemStack(AllBlocks.MECHANICAL_DRILL)) && c.isActorTypeDisabled(new ItemStack(AllBlocks.MECHANICAL_SAW)), "Remaining live types have explicit filters"); h.succeed();
    }
    @GameTest public void physicalControlAndDirectApiShareGlobalAndSpecificState(GameTestHelper h) {
        var c = actors(h, ItemStack.EMPTY, false); ContraptionControlsMovingInteraction.toggle(c, ItemStack.EMPTY); c.setActorsActive(new ItemStack(AllBlocks.MECHANICAL_DRILL), true);
        h.assertTrue(!actor(c, DRILL).disabled && actor(c, SAW).disabled, "Direct API can invert a physical global control");
        ContraptionControlsMovingInteraction.toggle(c, ItemStack.EMPTY); h.assertTrue(actor(c, DRILL).disabled && actor(c, SAW).disabled && c.isActorTypeDisabled(ItemStack.EMPTY), "Physical global control replaces the direct per-type state");
        c.setActorsActive(ItemStack.EMPTY, true); h.assertTrue(!actor(c, DRILL).disabled && !actor(c, SAW).disabled && c.getDisabledActors().isEmpty(), "Direct global enable also clears physical control state"); h.succeed();
    }
}
