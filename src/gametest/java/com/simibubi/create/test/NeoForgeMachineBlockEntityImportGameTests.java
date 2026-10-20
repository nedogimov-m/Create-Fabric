package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.IControlContraption;
import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity;
import com.simibubi.create.content.contraptions.pulley.PulleyMirrorRestoration;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity;
import com.simibubi.create.content.redstone.diodes.BrassDiodeBlockEntity;
import com.simibubi.create.foundation.utility.NeoForgeBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMachineBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.material.Fluids;

/** Actual pinned original3955 schema -> real root dispatcher -> two registered native owners. UNRUN. */
public final class NeoForgeMachineBlockEntityImportGameTests {
    private static final int VERSION = NeoForgeSavedDataImport.SOURCE_DATA_VERSION;
    private static Identifier id(String path) { return Identifier.parse("create:" + path); }
    private static CompoundTag setting(int value) { var result = new CompoundTag(); result.putInt("ScrollValue", value); return result; }
    private static NeoForgeMigrationScope scope(GameTestHelper h) { return NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(), h.absolutePos(new BlockPos(1, 2, 1))); }
    private static BlockEntity twice(GameTestHelper h, String type, Block block, CompoundTag source) {
        var at = h.absolutePos(new BlockPos(1, 2, 1)); var original = source.copy(); original.putString("id", id(type).toString()); original.putInt("x", at.getX()); original.putInt("y", at.getY()); original.putInt("z", at.getZ());
        var before = original.copy(); var converted = NeoForgeBlockEntityImport.convert(id(type), original, VERSION, h.getLevel().registryAccess(), scope(h));
        h.assertTrue(original.equals(before), "Root typed migration cannot alter caller's original source save");
        var first = BlockEntity.loadStatic(at, block.defaultBlockState(), converted, h.getLevel().registryAccess());
        h.assertTrue(first != null && first.getLevel() == null, "Actual registered native block-entity factory decodes the source migration");
        var second = BlockEntity.loadStatic(at, block.defaultBlockState(), first.saveWithFullMetadata(h.getLevel().registryAccess()), h.getLevel().registryAccess());
        h.assertTrue(second != null && second != first, "Fresh actual native owner decodes the actual native re-save"); return second;
    }
    private static CompoundTag tank() {
        var result = new CompoundTag(); result.putInt("Size", 1); result.putInt("Height", 1); result.putBoolean("Window", true); result.putInt("Luminosity", 0);
        var fluid = new CompoundTag(); fluid.putString("id", "minecraft:water"); fluid.putInt("amount", 750);
        var components = new CompoundTag(); components.putString("minecraft:custom_name", "{\"text\":\"Original fluid\"}"); fluid.put("components", components); var wrapper = new CompoundTag(); wrapper.put("Fluid", fluid); result.put("TankContent", wrapper);
        var boiler = new CompoundTag(); boiler.putFloat("Supply", 12.5f); boiler.putInt("ActiveHeat", 27); boiler.putBoolean("PassiveHeat", false); boiler.putInt("Engines", 0); boiler.putInt("Whistles", 0); boiler.putBoolean("Update", false); result.put("Boiler", boiler); return result;
    }

    @GameTest public void originalTankKeepsActualNamedFluidAndBoilerUsesItsOriginalRateUnits(GameTestHelper h) {
        var owner = (FluidTankBlockEntity) twice(h, "fluid_tank", AllBlocks.FLUID_TANK, tank());
        h.assertTrue(owner.getTankInventory().getFluidAmount() == 750L * 81 && owner.getTankInventory().getResource().getFluid() == Fluids.WATER,
            "Finite source fluid stock converts millibuckets to native droplets exactly once");
        h.assertTrue(owner.getTankInventory().getResource().getComponents().get(DataComponents.CUSTOM_NAME).getString().equals("Original fluid"),
            "Genuine original fluid component patches use typed text migration without a carrier item");
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.getCompound("Boiler").orElseThrow().getFloatOr("Supply", -1) == 12.5f
            && saved.getCompound("Boiler").orElseThrow().getIntOr("ActiveHeat", -1) == 27 && !saved.getBooleanOr("Uninitialized", true),
            "Boiler measured mB/tick and genuine theoretical heater sum retain source units; effective power caps do not truncate saved heat"); h.succeed();
    }

    @GameTest public void creativeTankRetainsItsActualInfiniteExemplarWithoutClaimingFiniteStock(GameTestHelper h) {
        var owner = (CreativeFluidTankBlockEntity) twice(h, "creative_fluid_tank", AllBlocks.CREATIVE_FLUID_TANK, tank());
        h.assertTrue(owner.getTankInventory().getResource().getFluid() == Fluids.WATER
            && owner.getTankInventory().getFluidAmount() == owner.getTankInventory().getCapacity(), "Native creative generator retains the actual source exemplar and its actual infinite-source representation"); h.succeed();
    }

    @GameTest public void sourceSettingsAndEveryDiodeKeepActualPolicyAndCountdown(GameTestHelper h) {
        var motor = (CreativeMotorBlockEntity) twice(h, "motor", AllBlocks.CREATIVE_MOTOR, setting(-64));
        var controller = (SpeedControllerBlockEntity) twice(h, "rotation_speed_controller", AllBlocks.ROTATION_SPEED_CONTROLLER, setting(-96));
        h.assertTrue(motor.generatedSpeed.getValue() == -64 && controller.getTargetSpeed() == -96, "Original source ScrollValue is the actual native control setting");
        String[] types = {"pulse_extender", "pulse_repeater", "pulse_timer"}; Block[] blocks = {AllBlocks.PULSE_EXTENDER, AllBlocks.PULSE_REPEATER, AllBlocks.PULSE_TIMER};
        for (int index = 0; index < types.length; index++) {
            var source = setting(600); source.putInt("State", 299); var diode = (BrassDiodeBlockEntity) twice(h, types[index], blocks[index], source);
            h.assertTrue(diode.getMaxState() == 600 && diode.getState() == 299, "Actual native diode retains source interval/countdown for " + types[index]);
        }
        h.succeed();
    }

    @GameTest public void savedSpeedSettingsSurviveLoweredLimitsButAnActualNewEditUsesCurrentBounds(GameTestHelper h) {
        var option = AllConfigs.server().kinetics.maxRotationSpeed; int previous = option.getServer();
        try {
            option.set(64);
            var motor = (CreativeMotorBlockEntity) twice(h, "motor", AllBlocks.CREATIVE_MOTOR, setting(256));
            var controller = (SpeedControllerBlockEntity) twice(h, "rotation_speed_controller", AllBlocks.ROTATION_SPEED_CONTROLLER, setting(-192));
            h.assertTrue(motor.generatedSpeed.getValue() == 256 && controller.getTargetSpeed() == -192,
                "Actual saved source speed policy survives a subsequent reduction of the native admission setting");
            motor.generatedSpeed.setValue(256); controller.setTargetSpeed(-192);
            h.assertTrue(motor.generatedSpeed.getValue() == 64 && controller.getTargetSpeed() == -64,
                "Actual new speed setting edits follow the current native configuration bounds");
        } finally { option.set(previous); }
        h.succeed();
    }

    @GameTest public void persistedDiodePolicyUsesTheOriginalReaderDomainRatherThanTheNewEditorBounds(GameTestHelper h) {
        var source = setting(100000); source.putInt("State", 90000);
        var owner = (BrassDiodeBlockEntity) twice(h, "pulse_repeater", AllBlocks.PULSE_REPEATER, source);
        h.assertTrue(owner.getMaxState() == 100000 && owner.getState() == 90000,
            "Source saved integer policy/countdown survive; source GUI limits are not a persistence schema restriction");
        owner.setMaxState(600); h.assertTrue(owner.getMaxState() == 600 && owner.getState() == 600,
            "A real new diode edit still applies its native editor bounds and clips progress to the chosen interval"); h.succeed();
    }

    @GameTest public void savedChassisRangeSurvivesReducedAdmissionLimitUntilAnActualNewEdit(GameTestHelper h) {
        var option = AllConfigs.server().kinetics.maxChassisRange; int previous = option.getServer();
        try {
            option.set(4); var owner = (ChassisBlockEntity) twice(h, "chassis", AllBlocks.LINEAR_CHASSIS, setting(13));
            h.assertTrue(owner.getRange() == 13 && owner.currentlySelectedRange == 13, "Original loaded source range remains authoritative after the construction limit is lowered");
            owner.setRange(13); h.assertTrue(owner.getRange() == 4, "An actual new setting edit still follows the current source configuration limit");
        } finally { option.set(previous); }
        h.succeed();
    }

    @GameTest public void bearingModeAndOriginalTypedErrorSurviveWithoutAnInventedBodyUuid(GameTestHelper h) {
        var source = setting(2); source.putBoolean("Running", false); source.putFloat("Angle", 23.5f);
        var exception = new CompoundTag(); exception.putString("Component", "{\"text\":\"Original assembly error\"}"); exception.putLong("Position", new BlockPos(4, 5, 6).asLong()); source.put("LastException", exception);
        var owner = (MechanicalBearingBlockEntity) twice(h, "mechanical_bearing", AllBlocks.MECHANICAL_BEARING, source);
        h.assertTrue(owner.getMovementMode() == IControlContraption.RotationMode.ROTATE_NEVER_PLACE && owner.getInterpolatedAngle(1) == 23.5f,
            "Source ScrollValue movement policy and stored angle use the actual native fields");
        var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess()); var error = saved.getCompound("AssemblyError").orElseThrow().getCompound("LastException").orElseThrow();
        h.assertTrue(error.getStringOr("Component", "").contains("Original assembly error") && error.getLongOr("Position", 0) == new BlockPos(4, 5, 6).asLong()
            && !saved.contains("MovedContraption"), "Original error text/typed position survive; no source body identity is invented"); h.succeed();
    }

    @GameTest public void actualSequencerProgramAndActiveOriginalProgressSurviveNativeReload(GameTestHelper h) {
        var source = new CompoundTag(); var rows = new ListTag(); var turn = new CompoundTag(); turn.putString("Type", "TURN_DISTANCE"); turn.putString("Modifier", "BACK_FAST"); turn.putInt("Value", 64); rows.add(turn);
        var end = new CompoundTag(); end.putString("Type", "END"); end.putString("Modifier", "FORWARD"); end.putInt("Value", 1); rows.add(end); source.put("Instructions", rows);
        source.putInt("InstructionIndex", 0); source.putInt("InstructionDuration", 1024); source.putFloat("InstructionProgress", 12.5f); source.putInt("Timer", 511); source.putBoolean("PrevPowered", true);
        var owner = twice(h, "sequenced_gearshift", AllBlocks.SEQUENCED_GEARSHIFT, source); var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.getIntOr("InstructionIndex", -1) == 0 && saved.getIntOr("Timer", -1) == 511 && saved.getFloatOr("InstructionProgress", -1) == 12.5f
            && ((CompoundTag) saved.getListOrEmpty("Instructions").get(0)).getStringOr("Modifier", "").equals("BACK_FAST"), "The actual native producer retains original ordered program and already consumed progress"); h.succeed();
    }

    private static CompoundTag originalPos(BlockPos pos) { var result = new CompoundTag(); result.putInt("X", pos.getX()); result.putInt("Y", pos.getY()); result.putInt("Z", pos.getZ()); return result; }
    private static CompoundTag pulley(float offset, int initial) {
        var result = setting(0); result.putFloat("Offset", offset); result.putInt("InitialOffset", initial); result.putBoolean("Running", true); result.putBoolean("Waiting", false); return result;
    }
    private static PulleyBlockEntity installOriginalPulley(GameTestHelper h, BlockPos relative, CompoundTag source) {
        var at = h.absolutePos(relative); var original = source.copy(); original.putString("id", "create:rope_pulley"); original.putInt("x", at.getX()); original.putInt("y", at.getY()); original.putInt("z", at.getZ());
        var migrated = NeoForgeBlockEntityImport.convert(id("rope_pulley"), original, VERSION, h.getLevel().registryAccess(), NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(), at));
        var result = (PulleyBlockEntity) BlockEntity.loadStatic(at, h.getBlockState(relative), migrated, h.getLevel().registryAccess()); result.setLevel(h.getLevel()); h.getLevel().setBlockEntity(result); return result;
    }

    @GameTest public void originalPulleyMirrorKeepsPositionalEvidenceUntilTheActualOwnerExists(GameTestHelper h) {
        var source = pulley(91, 2); var parent = h.absolutePos(new BlockPos(5, 2, 1)); source.put("MirrorParent", originalPos(parent));
        var owner = (PulleyBlockEntity) twice(h, "rope_pulley", AllBlocks.ROPE_PULLEY, source); var saved = owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.getMirrorParent().equals(parent) && owner.getMirrorOwner() == null && owner.getMovedContraption() == null && owner.offset == 0,
            "Actual original reader resets passive offset while native pending evidence contains no invented entity owner");
        h.assertTrue(saved.contains("OriginalMirrorParent") && !saved.contains("MirrorOwner") && !saved.contains("MovedEntity"),
            "Source positional evidence survives native re-save before actual loaded reciprocal controller/body reconciliation"); h.succeed();
    }

    @GameTest public void originalPulleyOffsetSurvivesLowerCurrentConstructionLimit(GameTestHelper h) {
        int previous = com.simibubi.create.content.contraptions.pulley.PulleySettings.maxRopeLength;
        try {
            com.simibubi.create.content.contraptions.pulley.PulleySettings.maxRopeLength = 4;
            var owner = (PulleyBlockEntity) twice(h, "rope_pulley", AllBlocks.ROPE_PULLEY, pulley(31.5f, 2));
            h.assertTrue(owner.offset == 31.5f && owner.getInitialRopeOffset() == 2,
                "Saved actual extension and initial body anchor survive a reduced new-assembly rope limit");
        } finally { com.simibubi.create.content.contraptions.pulley.PulleySettings.maxRopeLength = previous; }
        h.succeed();
    }

    @GameTest public void sourcePulleyMirrorReconcilesOnlyTheRealLoadedReciprocalBodyAndRestoresItsCargoOnce(GameTestHelper h) throws Exception {
        var at = new BlockPos(3, 6, 3); var mirrorAt = at.east(2); var bodyAt = at.below(3);
        h.setBlock(at, AllBlocks.ROPE_PULLEY); h.setBlock(mirrorAt, AllBlocks.ROPE_PULLEY);
        for (var column : new BlockPos[]{at, mirrorAt}) { h.setBlock(column.below(), AllBlocks.ROPE); h.setBlock(column.below(2), AllBlocks.PULLEY_MAGNET); }
        h.setBlock(bodyAt, Blocks.BARREL); h.setBlock(bodyAt.east(), Blocks.STONE); h.setBlock(bodyAt.east(2), Blocks.STONE);
        var cargo = new ItemStack(Items.DIAMOND, 7); cargo.set(DataComponents.CUSTOM_NAME, Component.literal("Original pulley cargo")); h.getBlockEntity(bodyAt, BarrelBlockEntity.class).setItem(0, cargo.copy());
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(), SuperGlueEntity.span(h.absolutePos(bodyAt), h.absolutePos(bodyAt.east(2))))), "Actual source body glue is admitted");
        var initial = h.getBlockEntity(at, PulleyBlockEntity.class); initial.setNetworkState(16, 16, false, new KineticGraph.Stress(512, 64, false), Map.of(Direction.WEST, 16f));
        h.assertTrue(initial.assemble(), "Actual native assembly produces a real owner for the loaded-body reconciliation boundary");
        var body = initial.getMovedContraption(); var actualUuid = body.getUUID();
        var parentSource = pulley(initial.offset, initial.getInitialRopeOffset()); var children = new ListTag(); var childRow = new CompoundTag(); childRow.put("Pos", originalPos(h.absolutePos(mirrorAt))); children.add(childRow); parentSource.put("MirrorChildren", children);
        var childSource = pulley(0, 0); childSource.put("MirrorParent", originalPos(h.absolutePos(at)));
        var parent = installOriginalPulley(h, at, parentSource); var child = installOriginalPulley(h, mirrorAt, childSource);
        h.assertTrue(child.getMirrorOwner() == null && parent.getMovedContraption() == null, "Original BE saves do not manufacture the original entity UUID");
        parent.attach(body); PulleyBlockEntity.tick(h.getLevel(), h.absolutePos(at), h.getBlockState(at), parent);
        h.assertTrue(parent.getMovedContraption() == body && child.getAttachedContraption() == body && actualUuid.equals(child.getMirrorOwner()),
            "Only actual loaded reciprocal source rows, controller position, body anchor and matching body collider authorize the native mirror relation");
        var saved = child.saveWithFullMetadata(h.getLevel().registryAccess()); var reloaded = (PulleyBlockEntity) BlockEntity.loadStatic(h.absolutePos(mirrorAt), h.getBlockState(mirrorAt), saved, h.getLevel().registryAccess()); reloaded.setLevel(h.getLevel()); h.getLevel().setBlockEntity(reloaded);
        h.assertTrue(reloaded.getAttachedContraption() == body && !saved.contains("OriginalMirrorParent") && !saved.contains("MovedEntity"), "Verified native relation survives fresh load without copying body ownership");
        h.assertTrue(parent.disassemble(), "Actual original cargo body can now restore with its verified passive mirror"); PulleyMirrorRestoration.tick(h.getLevel());
        h.assertTrue(body.isRemoved() && !reloaded.isRunning() && reloaded.getMirrorParent() == null && ItemStack.isSameItemSameComponents(h.getBlockEntity(bodyAt, BarrelBlockEntity.class).getItem(0), cargo)
            && h.getBlockEntity(bodyAt, BarrelBlockEntity.class).getItem(0).getCount() == 7, "Real body and physical mirror ropes retire once, preserving the seven original named diamonds");
        h.assertBlockPresent(AllBlocks.ROPE, mirrorAt.below()); h.assertBlockPresent(AllBlocks.PULLEY_MAGNET, mirrorAt.below(2)); h.succeed();
    }

    @GameTest public void ordinaryTankSafeWriterDoesNotInventFluidOrBoilerOwners(GameTestHelper h) {
        var source = new CompoundTag(); source.putInt("Size", 2); source.putInt("Height", 3); source.putBoolean("Window", false);
        var converted = NeoForgeMachineBlockEntityImport.convertSafe(id("fluid_tank"), source, VERSION, h.getLevel().registryAccess(), scope(h)).orElseThrow();
        h.assertTrue(converted.equals(source) && !converted.contains("TankContent") && !converted.contains("Boiler"), "Actual ordinary-tank partial safe data remains geometry/window only"); h.succeed();
    }
}
