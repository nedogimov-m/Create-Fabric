package com.simibubi.create;

import net.minecraft.core.BlockPos;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class AllBlockEntityTypes {
 public static final BlockEntityType<com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockEntity> CART_ASSEMBLER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("cart_assembler"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockEntity::new,AllBlocks.CART_ASSEMBLER).build());
 public static final BlockEntityType<com.simibubi.create.content.logistics.itemHatch.ItemHatchBlockEntity> ITEM_HATCH=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("item_hatch"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.itemHatch.ItemHatchBlockEntity::new,AllBlocks.ITEM_HATCH).build());
 public static final BlockEntityType<com.simibubi.create.content.kinetics.clock.CuckooClockBlockEntity> CUCKOO_CLOCK=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("cuckoo_clock"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.clock.CuckooClockBlockEntity::new,AllBlocks.CUCKOO_CLOCK,AllBlocks.MYSTERIOUS_CUCKOO_CLOCK).build());

    public static final BlockEntityType<com.simibubi.create.content.equipment.armor.BacktankBlockEntity> BACKTANK=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("backtank"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.equipment.armor.BacktankBlockEntity::new,AllBlocks.COPPER_BACKTANK,AllBlocks.NETHERITE_BACKTANK).build());
    public static final BlockEntityType<com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity> SCHEMATICANNON=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("schematicannon"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity::new,AllBlocks.SCHEMATICANNON).build());
    public static final BlockEntityType<com.simibubi.create.content.equipment.clipboard.ClipboardBlockEntity> CLIPBOARD=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("clipboard"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.equipment.clipboard.ClipboardBlockEntity::new,AllBlocks.CLIPBOARD).build());
    public static final BlockEntityType<com.simibubi.create.content.schematics.table.SchematicTableBlockEntity> SCHEMATIC_TABLE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("schematic_table"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.schematics.table.SchematicTableBlockEntity::new,AllBlocks.SCHEMATIC_TABLE).build());
    public static final BlockEntityType<com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity> TOOLBOX=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("toolbox"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity::new,AllBlocks.TOOLBOXES.values().toArray(Block[]::new)).build());
    public static final BlockEntityType<com.simibubi.create.content.trains.observer.TrackObserverBlockEntity> TRACK_OBSERVER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("track_observer"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.trains.observer.TrackObserverBlockEntity::new,AllBlocks.TRACK_OBSERVER).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity> PACKAGE_POSTBOX=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("package_postbox"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity::new,AllBlocks.POSTBOXES.values().toArray(Block[]::new)).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity> THRESHOLD_SWITCH=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("stockpile_switch"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity::new,AllBlocks.THRESHOLD_SWITCH).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity> DISPLAY_LINK=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("display_link"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockEntity::new,AllBlocks.DISPLAY_LINK).build());
    public static final BlockEntityType<com.simibubi.create.content.trains.signal.SignalBlockEntity> TRACK_SIGNAL=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("track_signal"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.trains.signal.SignalBlockEntity::new,AllBlocks.TRACK_SIGNAL).build());

    public static final BlockEntityType<com.simibubi.create.content.trains.station.StationBlockEntity> TRACK_STATION=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("station"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.trains.station.StationBlockEntity::new,AllBlocks.TRACK_STATION).build());
    public static final BlockEntityType<com.simibubi.create.content.trains.bogey.StandardBogeyBlockEntity> BOGEY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("bogey"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.trains.bogey.StandardBogeyBlockEntity::new,AllBlocks.SMALL_BOGEY,AllBlocks.LARGE_BOGEY).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity> NIXIE_TUBE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("nixie_tube"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity::new,AllBlocks.NIXIE_TUBES.values().toArray(Block[]::new)).build());
    public static final BlockEntityType<com.simibubi.create.content.decoration.placard.PlacardBlockEntity> PLACARD=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("placard"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.decoration.placard.PlacardBlockEntity::new,AllBlocks.PLACARD).build());
    public static final BlockEntityType<com.simibubi.create.content.trains.display.FlapDisplayBlockEntity> FLAP_DISPLAY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("flap_display"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.trains.display.FlapDisplayBlockEntity::new,AllBlocks.DISPLAY_BOARD).build());
    public static final BlockEntityType<com.simibubi.create.content.trains.track.TrackBlockEntity> TRACK=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("track"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.trains.track.TrackBlockEntity::new,com.simibubi.create.content.trains.track.TrackMaterial.allBlocks().stream().map(java.util.function.Supplier::get).toArray(Block[]::new)).build());
    public static final BlockEntityType<com.simibubi.create.content.trains.track.FakeTrackBlockEntity> FAKE_TRACK=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("fake_track"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.trains.track.FakeTrackBlockEntity::new,AllBlocks.FAKE_TRACK).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity> ROPE_PULLEY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("rope_pulley"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity::new,AllBlocks.ROPE_PULLEY).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity> ELEVATOR_PULLEY=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("elevator_pulley"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlockEntity::new,AllBlocks.ELEVATOR_PULLEY).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.elevator.ElevatorContactBlockEntity> ELEVATOR_CONTACT=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("elevator_contact"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.elevator.ElevatorContactBlockEntity::new,AllBlocks.ELEVATOR_CONTACT).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.gantry.GantryShaftBlockEntity> GANTRY_SHAFT=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("gantry_shaft"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.gantry.GantryShaftBlockEntity::new,AllBlocks.GANTRY_SHAFT).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.gantry.GantryCarriageBlockEntity> GANTRY_PINION=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("gantry_pinion"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.gantry.GantryCarriageBlockEntity::new,AllBlocks.GANTRY_CARRIAGE).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.diodes.PulseExtenderBlockEntity> PULSE_EXTENDER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("pulse_extender"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.diodes.PulseExtenderBlockEntity::new,AllBlocks.PULSE_EXTENDER).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.diodes.PulseRepeaterBlockEntity> PULSE_REPEATER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("pulse_repeater"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.diodes.PulseRepeaterBlockEntity::new,AllBlocks.PULSE_REPEATER).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.diodes.PulseTimerBlockEntity> PULSE_TIMER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("pulse_timer"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.diodes.PulseTimerBlockEntity::new,AllBlocks.PULSE_TIMER).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity> ANALOG_LEVER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("analog_lever"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity::new,AllBlocks.ANALOG_LEVER).build());

    public static final BlockEntityType<com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity> MECHANICAL_PISTON=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("mechanical_piston"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.piston.MechanicalPistonBlockEntity::new,AllBlocks.MECHANICAL_PISTON,AllBlocks.STICKY_MECHANICAL_PISTON).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity> SEQUENCED_GEARSHIFT=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("sequenced_gearshift"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlockEntity::new,AllBlocks.SEQUENCED_GEARSHIFT).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity> WINDMILL_BEARING=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("windmill_bearing"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity::new,AllBlocks.WINDMILL_BEARING).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity> CLOCKWORK_BEARING=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("clockwork_bearing"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlockEntity::new,AllBlocks.CLOCKWORK_BEARING).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity> CHASSIS=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("chassis"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.chassis.ChassisBlockEntity::new,AllBlocks.LINEAR_CHASSIS,AllBlocks.SECONDARY_LINEAR_CHASSIS,AllBlocks.RADIAL_CHASSIS).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.chassis.StickerBlockEntity> STICKER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("sticker"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.chassis.StickerBlockEntity::new,AllBlocks.STICKER).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity> REDSTONE_LINK=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("redstone_link"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.link.RedstoneLinkBlockEntity::new,AllBlocks.REDSTONE_LINK).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity> LECTERN_CONTROLLER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("lectern_controller"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.link.controller.LecternControllerBlockEntity::new,AllBlocks.LECTERN_CONTROLLER).build());
    public static final BlockEntityType<com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity> SLIDING_DOOR=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("sliding_door"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlockEntity::new,AllBlocks.ANDESITE_DOOR,AllBlocks.BRASS_DOOR,AllBlocks.COPPER_DOOR,AllBlocks.TRAIN_DOOR,AllBlocks.FRAMED_GLASS_DOOR).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity> CONTRAPTION_CONTROLS=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("contraption_controls"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.actors.contraptionControls.ContraptionControlsBlockEntity::new,AllBlocks.CONTRAPTION_CONTROLS).build());
    public static final BlockEntityType<com.simibubi.create.content.equipment.bell.PeculiarBellBlockEntity> PECULIAR_BELL=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("peculiar_bell"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.equipment.bell.PeculiarBellBlockEntity::new,AllBlocks.PECULIAR_BELL).build());
    public static final BlockEntityType<com.simibubi.create.content.equipment.bell.HauntedBellBlockEntity> HAUNTED_BELL=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("haunted_bell"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.equipment.bell.HauntedBellBlockEntity::new,AllBlocks.HAUNTED_BELL).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.deskBell.DeskBellBlockEntity> DESK_BELL=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("desk_bell"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.deskBell.DeskBellBlockEntity::new,AllBlocks.DESK_BELL).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity> MECHANICAL_ROLLER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("mechanical_roller"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity::new,AllBlocks.MECHANICAL_ROLLER).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.actors.psi.PortableItemInterfaceBlockEntity> PORTABLE_STORAGE_INTERFACE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("portable_storage_interface"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.actors.psi.PortableItemInterfaceBlockEntity::new,AllBlocks.PORTABLE_STORAGE_INTERFACE).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity> PORTABLE_FLUID_INTERFACE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("portable_fluid_interface"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.actors.psi.PortableFluidInterfaceBlockEntity::new,AllBlocks.PORTABLE_FLUID_INTERFACE).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.actors.harvester.HarvesterBlockEntity> HARVESTER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("harvester"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.actors.harvester.HarvesterBlockEntity::new,AllBlocks.MECHANICAL_HARVESTER).build());
    public static final BlockEntityType<com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity> MECHANICAL_BEARING=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("mechanical_bearing"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity::new,AllBlocks.MECHANICAL_BEARING).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity> PACKAGE_FROGPORT=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("package_frogport"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity::new,AllBlocks.PACKAGE_FROGPORT).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity> CHAIN_CONVEYOR=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("chain_conveyor"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity::new,AllBlocks.CHAIN_CONVEYOR).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity> TABLE_CLOTH=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("table_cloth"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.tableCloth.TableClothBlockEntity::new,AllBlocks.tableCloths()).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity> STOCK_TICKER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("stock_ticker"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.stockTicker.StockTickerBlockEntity::new,AllBlocks.STOCK_TICKER).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity> REDSTONE_REQUESTER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("redstone_requester"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity::new,AllBlocks.REDSTONE_REQUESTER).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity> FACTORY_PANEL=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("factory_panel"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlockEntity::new,AllBlocks.FACTORY_GAUGE).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity> PACKAGER_LINK=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("packager_link"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity::new,AllBlocks.STOCK_LINK).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity> ITEM_VAULT=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("item_vault"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity::new,AllBlocks.ITEM_VAULT).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity> CREATIVE_CRATE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("creative_crate"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity::new,AllBlocks.CREATIVE_CRATE).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.packager.PackagerBlockEntity> PACKAGER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("packager"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.packager.PackagerBlockEntity::new,AllBlocks.PACKAGER).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity> REPACKAGER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("repackager"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.packager.repackager.RepackagerBlockEntity::new,AllBlocks.REPACKAGER).build());
    /** Kept for saves created by the initial development build. New blocks use upstream registry IDs. */
    public static final BlockEntityType<KineticBlockEntity> KINETIC = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("kinetic"),
            FabricBlockEntityTypeBuilder.create(AllBlockEntityTypes::legacyKineticOwner,
                    AllBlocks.SHAFT, AllBlocks.COGWHEEL, AllBlocks.LARGE_COGWHEEL,
                    AllBlocks.CREATIVE_MOTOR, AllBlocks.HAND_CRANK, AllBlocks.GEARSHIFT,
                    AllBlocks.CLUTCH, AllBlocks.GEARBOX, AllBlocks.ENCASED_CHAIN_DRIVE,
                    AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity> BRACKETED_KINETIC = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("simple_kinetic"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity::new, AllBlocks.SHAFT, AllBlocks.COGWHEEL, AllBlocks.LARGE_COGWHEEL).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity> MOTOR = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("motor"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity::new,AllBlocks.CREATIVE_MOTOR).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity> HAND_CRANK = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("hand_crank"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity::new,AllBlocks.HAND_CRANK).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.transmission.GearshiftBlockEntity> GEARSHIFT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("gearshift"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.transmission.GearshiftBlockEntity::new, AllBlocks.GEARSHIFT).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity> CLUTCH = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("clutch"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity::new, AllBlocks.CLUTCH).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity> GEARBOX = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("gearbox"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity::new, AllBlocks.GEARBOX).build());
    public static final BlockEntityType<KineticBlockEntity> ENCASED_SHAFT = simple("encased_shaft", AllBlocks.ENCASED_CHAIN_DRIVE, AllBlocks.METAL_GIRDER_ENCASED_SHAFT, AllBlocks.ANDESITE_ENCASED_SHAFT, AllBlocks.BRASS_ENCASED_SHAFT);
    public static final BlockEntityType<com.simibubi.create.content.kinetics.chainDrive.ChainGearshiftBlockEntity> ADJUSTABLE_CHAIN_GEARSHIFT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("adjustable_chain_gearshift"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.chainDrive.ChainGearshiftBlockEntity::new, AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT).build());

    public static final BlockEntityType<com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity> ENCASED_COGWHEEL = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("encased_cogwheel"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity::new, AllBlocks.ANDESITE_ENCASED_COGWHEEL, AllBlocks.BRASS_ENCASED_COGWHEEL).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity> ENCASED_LARGE_COGWHEEL = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("encased_large_cogwheel"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity::new, AllBlocks.ANDESITE_ENCASED_LARGE_COGWHEEL, AllBlocks.BRASS_ENCASED_LARGE_COGWHEEL).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity> VALVE_HANDLE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("valve_handle"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity::new,java.util.stream.Stream.concat(java.util.stream.Stream.of(AllBlocks.COPPER_VALVE_HANDLE),AllBlocks.DYED_VALVE_HANDLES.values().stream()).toArray(Block[]::new)).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.fan.NozzleBlockEntity> NOZZLE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("nozzle"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.fan.NozzleBlockEntity::new,AllBlocks.NOZZLE).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.turntable.TurntableBlockEntity> TURNTABLE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("turntable"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.turntable.TurntableBlockEntity::new,AllBlocks.TURNTABLE).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.flywheel.FlywheelBlockEntity> FLYWHEEL = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("flywheel"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.flywheel.FlywheelBlockEntity::new,AllBlocks.FLYWHEEL).build());

    private static KineticBlockEntity legacyKineticOwner(BlockPos pos,BlockState state){
        if(state.is(AllBlocks.CREATIVE_MOTOR))return new com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity(pos,state);
        if(state.is(AllBlocks.HAND_CRANK))return new com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity(pos,state);
        return createSimpleKineticOwner(pos,state);
    }

    /** Actual placed and legacy owners share the same source factory selection. */
    public static KineticBlockEntity createSimpleKineticOwner(BlockPos pos, BlockState state) {
        var type = simpleType(state);
        if (type == BRACKETED_KINETIC)
            return new com.simibubi.create.content.kinetics.simpleRelays.BracketedKineticBlockEntity(type, pos, state);
        if (type == ENCASED_COGWHEEL || type == ENCASED_LARGE_COGWHEEL)
            return new com.simibubi.create.content.kinetics.simpleRelays.SimpleKineticBlockEntity(type, pos, state);
        if (type == GEARBOX)
            return new com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity(type, pos, state);
        if (type == GEARSHIFT)
            return new com.simibubi.create.content.kinetics.transmission.GearshiftBlockEntity(type, pos, state);
        if (type == CLUTCH)
            return new com.simibubi.create.content.kinetics.transmission.ClutchBlockEntity(type, pos, state);
        if (type == ADJUSTABLE_CHAIN_GEARSHIFT)
            return new com.simibubi.create.content.kinetics.chainDrive.ChainGearshiftBlockEntity(type, pos, state);
        return new KineticBlockEntity(pos, state);
    }

    private static BlockEntityType<KineticBlockEntity> simple(String id, Block... blocks) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource(id),
                FabricBlockEntityTypeBuilder.create(KineticBlockEntity::new, blocks).build());
    }
    public static BlockEntityType<? extends KineticBlockEntity> simpleType(BlockState state) {
        var block = state.getBlock();
        if(block==AllBlocks.WEIGHTED_EJECTOR)return WEIGHTED_EJECTOR;
        if(block==AllBlocks.COPPER_BACKTANK||block==AllBlocks.NETHERITE_BACKTANK)return BACKTANK;
        if (block instanceof com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock) return ENCASED_SHAFT;
        if (block instanceof com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock cog) return cog.isLargeCog() ? ENCASED_LARGE_COGWHEEL : ENCASED_COGWHEEL;
        if (block instanceof com.simibubi.create.content.kinetics.crank.ValveHandleBlock) return VALVE_HANDLE;
        if (block == AllBlocks.TURNTABLE) return TURNTABLE;
        if (block == AllBlocks.FLYWHEEL) return FLYWHEEL;
        if(block==AllBlocks.DISPLAY_BOARD)return FLAP_DISPLAY;
        if (block == AllBlocks.ROPE_PULLEY) return ROPE_PULLEY;
        if (block == AllBlocks.ELEVATOR_PULLEY) return ELEVATOR_PULLEY;
        if (block == AllBlocks.GANTRY_SHAFT) return GANTRY_SHAFT;
        if (block == AllBlocks.GANTRY_CARRIAGE) return GANTRY_PINION;

        if (block == AllBlocks.MECHANICAL_PISTON || block == AllBlocks.STICKY_MECHANICAL_PISTON) return MECHANICAL_PISTON;
        if (block == AllBlocks.SEQUENCED_GEARSHIFT) return SEQUENCED_GEARSHIFT;
        if (block == AllBlocks.CREATIVE_MOTOR) return MOTOR;
        if (block == AllBlocks.HAND_CRANK) return HAND_CRANK;
        if (block == AllBlocks.GEARSHIFT) return GEARSHIFT;
        if (block == AllBlocks.CLUTCH) return CLUTCH;
        if (block == AllBlocks.GEARBOX) return GEARBOX;
        if (block == AllBlocks.ENCASED_CHAIN_DRIVE || block == AllBlocks.METAL_GIRDER_ENCASED_SHAFT) return ENCASED_SHAFT;
        if (block == AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT) return ADJUSTABLE_CHAIN_GEARSHIFT;
        return BRACKETED_KINETIC;
    }

    public static final BlockEntityType<com.simibubi.create.content.logistics.funnel.FunnelBlockEntity> FUNNEL=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("funnel"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.funnel.FunnelBlockEntity::new,AllBlocks.ANDESITE_FUNNEL,AllBlocks.BRASS_FUNNEL,AllBlocks.ANDESITE_BELT_FUNNEL,AllBlocks.BRASS_BELT_FUNNEL).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.tunnel.BeltTunnelBlockEntity> ANDESITE_TUNNEL=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("andesite_tunnel"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.tunnel.BeltTunnelBlockEntity::new,AllBlocks.ANDESITE_TUNNEL).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity> BRASS_TUNNEL=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("brass_tunnel"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.tunnel.BrassTunnelBlockEntity::new,AllBlocks.BRASS_TUNNEL).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.chute.ChuteBlockEntity> CHUTE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("chute"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.chute.ChuteBlockEntity::new,AllBlocks.CHUTE).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.chute.SmartChuteBlockEntity> SMART_CHUTE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("smart_chute"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.chute.SmartChuteBlockEntity::new,AllBlocks.SMART_CHUTE).build());
    public static final BlockEntityType<com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity> SMART_OBSERVER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("content_observer"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.redstone.smartObserver.SmartObserverBlockEntity::new,AllBlocks.SMART_OBSERVER).build());
    public static final BlockEntityType<com.simibubi.create.content.decoration.copycat.CopycatBlockEntity> COPYCAT=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("copycat"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.decoration.copycat.CopycatBlockEntity::new,AllBlocks.COPYCAT_PANEL,AllBlocks.COPYCAT_STEP).build());
    public static void register() {}
    public static final BlockEntityType<com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity> MECHANICAL_ARM=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("mechanical_arm"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.mechanicalArm.ArmBlockEntity::new,AllBlocks.MECHANICAL_ARM).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity> DEPLOYER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("deployer"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity::new,AllBlocks.DEPLOYER).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity> ROTATION_SPEED_CONTROLLER=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("rotation_speed_controller"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity::new,AllBlocks.ROTATION_SPEED_CONTROLLER).build());

    public static final BlockEntityType<com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity> SPEEDOMETER = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("speedometer"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.gauge.SpeedGaugeBlockEntity::new, AllBlocks.SPEEDOMETER).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity> STRESSOMETER = Registry.register(
        BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("stressometer"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.gauge.StressGaugeBlockEntity::new, AllBlocks.STRESSOMETER).build());

    public static final BlockEntityType<MillstoneBlockEntity> MILLSTONE = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("millstone"),
            FabricBlockEntityTypeBuilder.create(MillstoneBlockEntity::new, AllBlocks.MILLSTONE).build());
    public static final BlockEntityType<MechanicalPressBlockEntity> MECHANICAL_PRESS = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("mechanical_press"),
            FabricBlockEntityTypeBuilder.create(MechanicalPressBlockEntity::new, AllBlocks.MECHANICAL_PRESS).build());
    public static final BlockEntityType<com.simibubi.create.content.logistics.depot.EjectorBlockEntity> WEIGHTED_EJECTOR=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("weighted_ejector"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.logistics.depot.EjectorBlockEntity::new,AllBlocks.WEIGHTED_EJECTOR).build());
    public static final BlockEntityType<DepotBlockEntity> DEPOT = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("depot"),
            FabricBlockEntityTypeBuilder.create(DepotBlockEntity::new, AllBlocks.DEPOT).build());
    public static final BlockEntityType<WaterWheelBlockEntity> WATER_WHEEL = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("water_wheel"),
            FabricBlockEntityTypeBuilder.create(WaterWheelBlockEntity::new, AllBlocks.WATER_WHEEL).build());
    public static final BlockEntityType<LargeWaterWheelBlockEntity> LARGE_WATER_WHEEL = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("large_water_wheel"),
            FabricBlockEntityTypeBuilder.create(LargeWaterWheelBlockEntity::new, AllBlocks.LARGE_WATER_WHEEL).build());
    public static final BlockEntityType<BasinBlockEntity> BASIN = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("basin"),
            FabricBlockEntityTypeBuilder.create(BasinBlockEntity::new, AllBlocks.BASIN).build());
    public static final BlockEntityType<MechanicalMixerBlockEntity> MECHANICAL_MIXER = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("mechanical_mixer"),
            FabricBlockEntityTypeBuilder.create(MechanicalMixerBlockEntity::new, AllBlocks.MECHANICAL_MIXER).build());
    public static final BlockEntityType<BlazeBurnerBlockEntity> HEATER = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("blaze_heater"),
            FabricBlockEntityTypeBuilder.create(BlazeBurnerBlockEntity::new, AllBlocks.BLAZE_BURNER).build());
    public static final BlockEntityType<EncasedFanBlockEntity> ENCASED_FAN = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("encased_fan"),
            FabricBlockEntityTypeBuilder.create(EncasedFanBlockEntity::new, AllBlocks.ENCASED_FAN).build());
    public static final BlockEntityType<BeltBlockEntity> BELT = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("belt"),
            FabricBlockEntityTypeBuilder.create(BeltBlockEntity::new, AllBlocks.BELT).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity> CRUSHING_WHEEL = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("crushing_wheel"), FabricBlockEntityTypeBuilder.create(
                    com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity::new, AllBlocks.CRUSHING_WHEEL).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity> CRUSHING_WHEEL_CONTROLLER = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("crushing_wheel_controller"), FabricBlockEntityTypeBuilder.create(
                    com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity::new, AllBlocks.CRUSHING_WHEEL_CONTROLLER).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity> MECHANICAL_CRAFTER = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("mechanical_crafter"), FabricBlockEntityTypeBuilder.create(
                    com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity::new, AllBlocks.MECHANICAL_CRAFTER).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.drill.DrillBlockEntity> DRILL = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("drill"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.drill.DrillBlockEntity::new, AllBlocks.MECHANICAL_DRILL).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.saw.SawBlockEntity> SAW = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("saw"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.saw.SawBlockEntity::new, AllBlocks.MECHANICAL_SAW).build());

    public static final BlockEntityType<com.simibubi.create.content.fluids.tank.FluidTankBlockEntity> FLUID_TANK = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("fluid_tank"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.tank.FluidTankBlockEntity::new, AllBlocks.FLUID_TANK).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity> CREATIVE_FLUID_TANK = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("creative_fluid_tank"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity::new, AllBlocks.CREATIVE_FLUID_TANK).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity> FLUID_PIPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("fluid_pipe"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity::new, AllBlocks.FLUID_PIPE).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity> ENCASED_FLUID_PIPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("encased_fluid_pipe"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.pipes.FluidPipeBlockEntity::new, AllBlocks.ENCASED_FLUID_PIPE).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity> STRAIGHT_PIPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("glass_fluid_pipe"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.pipes.StraightPipeBlockEntity::new, AllBlocks.GLASS_FLUID_PIPE).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.pump.PumpBlockEntity> MECHANICAL_PUMP = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("mechanical_pump"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.pump.PumpBlockEntity::new, AllBlocks.MECHANICAL_PUMP).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlockEntity> SMART_FLUID_PIPE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("smart_fluid_pipe"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlockEntity::new,AllBlocks.SMART_FLUID_PIPE).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity> FLUID_VALVE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("fluid_valve"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity::new,AllBlocks.FLUID_VALVE).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.spout.SpoutBlockEntity> SPOUT = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("spout"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.spout.SpoutBlockEntity::new,AllBlocks.SPOUT).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity> ITEM_DRAIN = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("item_drain"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity::new,AllBlocks.ITEM_DRAIN).build());
    public static final BlockEntityType<com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity> HOSE_PULLEY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Create.asResource("hose_pulley"), FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.fluids.hosePulley.HosePulleyBlockEntity::new,AllBlocks.HOSE_PULLEY).build());
    private AllBlockEntityTypes() {}
    public static final BlockEntityType<com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity> STEAM_WHISTLE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("steam_whistle"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.decoration.steamWhistle.WhistleBlockEntity::new,AllBlocks.STEAM_WHISTLE).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity> STEAM_ENGINE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("steam_engine"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity::new,AllBlocks.STEAM_ENGINE).build());
    public static final BlockEntityType<com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity> POWERED_SHAFT=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Create.asResource("powered_shaft"),FabricBlockEntityTypeBuilder.create(com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity::new,AllBlocks.POWERED_SHAFT).build());
}
