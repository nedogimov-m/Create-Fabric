package com.simibubi.create;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.kinetics.chainDrive.ChainGearshiftBlock;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlock;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlock;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlock;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.transmission.ClutchBlock;
import com.simibubi.create.content.kinetics.transmission.GearshiftBlock;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelStructuralBlock;
import com.simibubi.create.content.logistics.depot.DepotBlock;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class AllBlocks {
 public static final com.simibubi.create.content.kinetics.clock.CuckooClockBlock CUCKOO_CLOCK=register("cuckoo_clock",com.simibubi.create.content.kinetics.clock.CuckooClockBlock::regular,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STRIPPED_SPRUCE_WOOD).mapColor(MapColor.TERRACOTTA_YELLOW));
 public static final com.simibubi.create.content.kinetics.clock.CuckooClockBlock MYSTERIOUS_CUCKOO_CLOCK=register("mysterious_cuckoo_clock",com.simibubi.create.content.kinetics.clock.CuckooClockBlock::mysterious,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STRIPPED_SPRUCE_WOOD).mapColor(MapColor.TERRACOTTA_YELLOW));

  public static final com.simibubi.create.content.equipment.armor.BacktankBlock COPPER_BACKTANK=register("copper_backtank",com.simibubi.create.content.equipment.armor.BacktankBlock::new,false,BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3,6).sound(SoundType.COPPER).requiresCorrectToolForDrops().noOcclusion());
  public static final com.simibubi.create.content.equipment.armor.BacktankBlock NETHERITE_BACKTANK=register("netherite_backtank",com.simibubi.create.content.equipment.armor.BacktankBlock::new,false,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.NETHERITE_BLOCK).noOcclusion());
  public static final com.simibubi.create.content.schematics.cannon.SchematicannonBlock SCHEMATICANNON=register("schematicannon",com.simibubi.create.content.schematics.cannon.SchematicannonBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DISPENSER).mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops());
  public static final com.simibubi.create.content.equipment.clipboard.ClipboardBlock CLIPBOARD=register("clipboard",com.simibubi.create.content.equipment.clipboard.ClipboardBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STRIPPED_SPRUCE_WOOD).forceSolidOn());
  public static final com.simibubi.create.content.schematics.table.SchematicTableBlock SCHEMATIC_TABLE=register("schematic_table",com.simibubi.create.content.schematics.table.SchematicTableBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LECTERN).mapColor(MapColor.PODZOL).forceSolidOn());
  public static final java.util.Map<net.minecraft.world.item.DyeColor,com.simibubi.create.content.equipment.toolbox.ToolboxBlock> TOOLBOXES=registerToolboxes();
  private static java.util.Map<net.minecraft.world.item.DyeColor,com.simibubi.create.content.equipment.toolbox.ToolboxBlock> registerToolboxes(){
    var values=new java.util.EnumMap<net.minecraft.world.item.DyeColor,com.simibubi.create.content.equipment.toolbox.ToolboxBlock>(net.minecraft.world.item.DyeColor.class);
    for(var color:net.minecraft.world.item.DyeColor.values()){
      var id=Create.asResource(color.getSerializedName()+"_toolbox");var blockKey=ResourceKey.create(Registries.BLOCK,id);var itemKey=ResourceKey.create(Registries.ITEM,id);
      var block=Registry.register(BuiltInRegistries.BLOCK,blockKey,new com.simibubi.create.content.equipment.toolbox.ToolboxBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.OAK_PLANKS).setId(blockKey).mapColor(color).noOcclusion(),color));
      Registry.register(BuiltInRegistries.ITEM,itemKey,new com.simibubi.create.content.equipment.toolbox.ToolboxBlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));values.put(color,block);
    }return java.util.Collections.unmodifiableMap(values);
  }

  public static final com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock
      TRAIN_CONTROLS =
          register(
              "controls",
              p ->
                  new com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock(
                      p.strength(1.5f, 6)
                          .mapColor(MapColor.PODZOL)
                          .sound(SoundType.WOOD)
                          .noOcclusion()));
  public static final com.simibubi.create.content.trains.observer.TrackObserverBlock
      TRACK_OBSERVER =
          register(
              "track_observer",
              com.simibubi.create.content.trains.observer.TrackObserverBlock::new,
              true,
              BlockBehaviour.Properties.ofFullCopy(
                      net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
                  .sound(SoundType.NETHERITE_BLOCK)
                  .noOcclusion());
  public static final java.util.Map<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock>
      POSTBOXES = registerPostboxes();
  public static final java.util.Map<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock>
      PACKAGE_POSTBOXES = POSTBOXES;

  private static java.util.Map<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock>
      registerPostboxes() {
    var blocks =
        new java.util.EnumMap<
            net.minecraft.world.item.DyeColor,
            com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock>(
            net.minecraft.world.item.DyeColor.class);
    for (var color : net.minecraft.world.item.DyeColor.values())
      blocks.put(
          color,
          register(
              color.getSerializedName() + "_postbox",
              properties ->
                  new com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock(
                      properties, color),
              true,
              BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.BARREL)
                  .mapColor(color)
                  .noOcclusion()));
    return java.util.Collections.unmodifiableMap(blocks);
  }

  public static final com.simibubi.create.content.trains.signal.SignalBlock TRACK_SIGNAL =
      register(
          "track_signal",
          com.simibubi.create.content.trains.signal.SignalBlock::new,
          true,
          BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
              .mapColor(MapColor.PODZOL)
              .noOcclusion()
              .sound(SoundType.NETHERITE_BLOCK));
  public static final com.simibubi.create.content.trains.station.StationBlock TRACK_STATION =
      register(
          "track_station",
          com.simibubi.create.content.trains.station.StationBlock::new,
          true,
          BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
              .mapColor(MapColor.PODZOL)
              .sound(SoundType.NETHERITE_BLOCK)
              .requiresCorrectToolForDrops()
              .noOcclusion());
  public static final com.simibubi.create.content.trains.bogey.StandardBogeyBlock SMALL_BOGEY =
      register(
          "small_bogey",
          p ->
              new com.simibubi.create.content.trains.bogey.StandardBogeyBlock(
                  p, com.simibubi.create.content.trains.bogey.BogeySizes.SMALL),
          false,
          BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
              .mapColor(MapColor.PODZOL)
              .sound(SoundType.NETHERITE_BLOCK)
              .noOcclusion());
  public static final com.simibubi.create.content.trains.bogey.StandardBogeyBlock LARGE_BOGEY =
      register(
          "large_bogey",
          p ->
              new com.simibubi.create.content.trains.bogey.StandardBogeyBlock(
                  p, com.simibubi.create.content.trains.bogey.BogeySizes.LARGE),
          false,
          BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
              .mapColor(MapColor.PODZOL)
              .sound(SoundType.NETHERITE_BLOCK)
              .noOcclusion());

  static {
    com.simibubi.create.content.trains.bogey.AbstractBogeyBlock.registerStandardBogey(
        Create.asResource("small_bogey"));
    com.simibubi.create.content.trains.bogey.AbstractBogeyBlock.registerStandardBogey(
        Create.asResource("large_bogey"));
  }

  public static final com.simibubi.create.content.decoration.girder.GirderBlock METAL_GIRDER =
      register(
          "metal_girder",
          com.simibubi.create.content.decoration.girder.GirderBlock::new,
          true,
          BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
              .mapColor(MapColor.COLOR_GRAY)
              .sound(SoundType.NETHERITE_BLOCK));
  public static final com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock
      METAL_GIRDER_ENCASED_SHAFT =
          register(
              "metal_girder_encased_shaft",
              com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock::new,
              false,
              BlockBehaviour.Properties.ofFullCopy(
                      net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
                  .mapColor(MapColor.COLOR_GRAY)
                  .sound(SoundType.NETHERITE_BLOCK));
  public static final java.util.Map<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock>
      NIXIE_TUBES = new java.util.EnumMap<>(net.minecraft.world.item.DyeColor.class);
  public static final com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock
      ORANGE_NIXIE_TUBE =
          register(
              "nixie_tube",
              p ->
                  new com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock(
                      p, net.minecraft.world.item.DyeColor.ORANGE),
              true,
              BlockBehaviour.Properties.ofFullCopy(
                      net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
                  .lightLevel(state -> 5)
                  .mapColor(net.minecraft.world.item.DyeColor.ORANGE)
                  .forceSolidOn());

  static {
    for (var color : net.minecraft.world.item.DyeColor.values())
      NIXIE_TUBES.put(
          color,
          color == net.minecraft.world.item.DyeColor.ORANGE
              ? ORANGE_NIXIE_TUBE
              : register(
                  color.getSerializedName() + "_nixie_tube",
                  p -> new com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock(p, color),
                  false,
                  BlockBehaviour.Properties.ofFullCopy(
                          net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
                      .lightLevel(state -> 5)
                      .mapColor(color)
                      .forceSolidOn()));
  }

  public static final com.simibubi.create.content.decoration.placard.PlacardBlock PLACARD =
      register(
          "placard",
          com.simibubi.create.content.decoration.placard.PlacardBlock::new,
          true,
          BlockBehaviour.Properties.ofFullCopy(
                  net.minecraft.world.level.block.Blocks.COPPER_BLOCK.weathering().unaffected())
              .forceSolidOn());
  public static final com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock
      DISPLAY_LINK =
          register(
              "display_link",
              com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock::new,
              true,
              BlockBehaviour.Properties.ofFullCopy(
                      net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
                  .mapColor(MapColor.TERRACOTTA_BROWN));
  public static final com.simibubi.create.content.trains.display.FlapDisplayBlock DISPLAY_BOARD =
      register(
          "display_board",
          com.simibubi.create.content.trains.display.FlapDisplayBlock::new,
          true,
          BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
              .mapColor(MapColor.COLOR_GRAY));
  public static final com.simibubi.create.content.trains.track.TrackBlock TRACK =
      register(
          "track", com.simibubi.create.content.trains.track.TrackMaterial.ANDESITE::createBlock);
  public static final com.simibubi.create.content.trains.track.FakeTrackBlock FAKE_TRACK =
      register(
          "fake_track",
          p ->
              new com.simibubi.create.content.trains.track.FakeTrackBlock(
                  p.mapColor(MapColor.METAL).replaceable()),
          false);
  public static final com.simibubi.create.content.decoration.encasing.CasingBlock RAILWAY_CASING =
      register(
          "railway_casing",
          p ->
              new com.simibubi.create.content.decoration.encasing.CasingBlock(
                  p.strength(1.5f, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.NETHERITE_BLOCK)
                      .mapColor(MapColor.TERRACOTTA_CYAN)));
  public static final com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock
      FRAMED_GLASS =
          register(
              "framed_glass",
              p ->
                  new com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock(
                      p.strength(.3f).sound(SoundType.GLASS).mapColor(MapColor.NONE)));

  public static final com.simibubi.create.content.contraptions.pulley.PulleyBlock ROPE_PULLEY =
      register(
          "rope_pulley",
          p ->
              new com.simibubi.create.content.contraptions.pulley.PulleyBlock(
                  p.strength(1.5f, 6).sound(SoundType.STONE).mapColor(MapColor.PODZOL)));
  public static final com.simibubi.create.content.contraptions.pulley.PulleyBlock.RopeBlock ROPE =
      register(
          "rope",
          p ->
              new com.simibubi.create.content.contraptions.pulley.PulleyBlock.RopeBlock(
                  p.strength(0).sound(SoundType.WOOL).mapColor(MapColor.COLOR_BROWN)),
          false);
  public static final com.simibubi.create.content.contraptions.pulley.PulleyBlock.MagnetBlock
      PULLEY_MAGNET =
          register(
              "pulley_magnet",
              p ->
                  new com.simibubi.create.content.contraptions.pulley.PulleyBlock.MagnetBlock(
                      p.strength(1.5f, 6).sound(SoundType.STONE).mapColor(MapColor.STONE)),
              false);
  public static final com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlock
      ELEVATOR_PULLEY =
          register(
              "elevator_pulley",
              p ->
                  new com.simibubi.create.content.contraptions.elevator.ElevatorPulleyBlock(
                      p.strength(3, 6).sound(SoundType.METAL).mapColor(MapColor.TERRACOTTA_BROWN)));
  public static final com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock
      ELEVATOR_CONTACT =
          register(
              "elevator_contact",
              p ->
                  new com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock(
                      p.strength(3, 6)
                          .sound(SoundType.METAL)
                          .mapColor(MapColor.TERRACOTTA_YELLOW)));
  public static final com.simibubi.create.content.kinetics.gantry.GantryShaftBlock GANTRY_SHAFT =
      register(
          "gantry_shaft",
          p ->
              new com.simibubi.create.content.kinetics.gantry.GantryShaftBlock(
                  p.strength(1.5f, 6)
                      .sound(SoundType.STONE)
                      .mapColor(MapColor.NETHER)
                      .forceSolidOn()));
  public static final com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock
      GANTRY_CARRIAGE =
          register(
              "gantry_carriage",
              p ->
                  new com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock(
                      p.strength(1.5f, 6).sound(SoundType.STONE).mapColor(MapColor.PODZOL)));
  public static final com.simibubi.create.content.redstone.diodes.BrassDiodeBlock PULSE_EXTENDER =
      register(
          "pulse_extender",
          p ->
              new com.simibubi.create.content.redstone.diodes.BrassDiodeBlock(
                  p.strength(0)
                      .noCollision()
                      .sound(SoundType.WOOD)
                      .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));
  public static final com.simibubi.create.content.redstone.diodes.BrassDiodeBlock PULSE_REPEATER =
      register(
          "pulse_repeater",
          p ->
              new com.simibubi.create.content.redstone.diodes.BrassDiodeBlock(
                  p.strength(0)
                      .noCollision()
                      .sound(SoundType.WOOD)
                      .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));
  public static final com.simibubi.create.content.redstone.diodes.BrassDiodeBlock PULSE_TIMER =
      register(
          "pulse_timer",
          p ->
              new com.simibubi.create.content.redstone.diodes.BrassDiodeBlock(
                  p.strength(0)
                      .noCollision()
                      .sound(SoundType.WOOD)
                      .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));
  public static final com.simibubi.create.content.redstone.diodes.PoweredLatchBlock POWERED_LATCH =
      register(
          "powered_latch",
          p ->
              new com.simibubi.create.content.redstone.diodes.PoweredLatchBlock(
                  p.strength(0)
                      .noCollision()
                      .sound(SoundType.WOOD)
                      .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));
  public static final com.simibubi.create.content.redstone.diodes.ToggleLatchBlock
      POWERED_TOGGLE_LATCH =
          register(
              "powered_toggle_latch",
              p ->
                  new com.simibubi.create.content.redstone.diodes.ToggleLatchBlock(
                      p.strength(0)
                          .noCollision()
                          .sound(SoundType.WOOD)
                          .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));
  public static final com.simibubi.create.content.redstone.analogLever.AnalogLeverBlock
      ANALOG_LEVER =
          register(
              "analog_lever",
              p ->
                  new com.simibubi.create.content.redstone.analogLever.AnalogLeverBlock(
                      p.strength(.5f)
                          .noCollision()
                          .sound(SoundType.WOOD)
                          .pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)));

  public static final com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock
      MECHANICAL_PISTON =
          register(
              "mechanical_piston",
              com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock::normal);
  public static final com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock
      STICKY_MECHANICAL_PISTON =
          register(
              "sticky_mechanical_piston",
              com.simibubi.create.content.contraptions.piston.MechanicalPistonBlock::sticky);
  public static final com.simibubi.create.content.contraptions.piston.PistonExtensionPoleBlock
      PISTON_EXTENSION_POLE =
          register(
              "piston_extension_pole",
              com.simibubi.create.content.contraptions.piston.PistonExtensionPoleBlock::new);
  public static final com.simibubi.create.content.contraptions.piston.MechanicalPistonHeadBlock
      MECHANICAL_PISTON_HEAD =
          register(
              "mechanical_piston_head",
              com.simibubi.create.content.contraptions.piston.MechanicalPistonHeadBlock::new,
              false);
  public static final com.simibubi.create.content.kinetics.transmission.sequencer
          .SequencedGearshiftBlock
      SEQUENCED_GEARSHIFT =
          register(
              "sequenced_gearshift",
              p ->
                  new com.simibubi.create.content.kinetics.transmission.sequencer
                      .SequencedGearshiftBlock(p.strength(1.5f, 6).sound(SoundType.STONE)));

  public static final com.simibubi.create.content.contraptions.bearing.WindmillBearingBlock
      WINDMILL_BEARING =
          register(
              "windmill_bearing",
              p ->
                  new com.simibubi.create.content.contraptions.bearing.WindmillBearingBlock(
                      p.strength(1.5f, 6).sound(SoundType.STONE).mapColor(MapColor.PODZOL)));
  public static final com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlock
      CLOCKWORK_BEARING =
          register(
              "clockwork_bearing",
              p ->
                  new com.simibubi.create.content.contraptions.bearing.ClockworkBearingBlock(
                      p.strength(1.5f, 6)
                          .sound(SoundType.STONE)
                          .mapColor(MapColor.TERRACOTTA_BROWN)));
  public static final com.simibubi.create.content.contraptions.bearing.SailBlock SAIL_FRAME =
      register(
          "sail_frame",
          p ->
              com.simibubi.create.content.contraptions.bearing.SailBlock.frame(
                  p.strength(2).sound(SoundType.SCAFFOLDING).mapColor(MapColor.DIRT)));
  public static final com.simibubi.create.content.contraptions.bearing.SailBlock SAIL =
      register(
          "white_sail",
          p ->
              com.simibubi.create.content.contraptions.bearing.SailBlock.withCanvas(
                  p.strength(2).sound(SoundType.SCAFFOLDING).mapColor(MapColor.SNOW),
                  net.minecraft.world.item.DyeColor.WHITE));
  public static final java.util.EnumMap<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.contraptions.bearing.SailBlock>
      DYED_SAILS = registerSails();

  private static java.util.EnumMap<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.contraptions.bearing.SailBlock>
      registerSails() {
    var sails =
        new java.util.EnumMap<
            net.minecraft.world.item.DyeColor,
            com.simibubi.create.content.contraptions.bearing.SailBlock>(
            net.minecraft.world.item.DyeColor.class);
    for (var color : net.minecraft.world.item.DyeColor.values())
      sails.put(
          color,
          color == net.minecraft.world.item.DyeColor.WHITE
              ? SAIL
              : register(
                  color.getName() + "_sail",
                  p ->
                      com.simibubi.create.content.contraptions.bearing.SailBlock.withCanvas(
                          p.strength(2).sound(SoundType.SCAFFOLDING).mapColor(color.getMapColor()),
                          color),
                  false));
    return sails;
  }

  static {
    com.simibubi.create.content.contraptions.bearing.BlankSailBlockItem.registerColoredAliases();
  }

  public static final com.simibubi.create.content.contraptions.chassis.LinearChassisBlock
      LINEAR_CHASSIS =
          register(
              "linear_chassis",
              p ->
                  new com.simibubi.create.content.contraptions.chassis.LinearChassisBlock(
                      p.mapColor(MapColor.TERRACOTTA_BROWN)));
  public static final com.simibubi.create.content.contraptions.chassis.LinearChassisBlock
      SECONDARY_LINEAR_CHASSIS =
          register(
              "secondary_linear_chassis",
              p ->
                  new com.simibubi.create.content.contraptions.chassis.LinearChassisBlock(
                      p.mapColor(MapColor.PODZOL)));
  public static final com.simibubi.create.content.contraptions.chassis.RadialChassisBlock
      RADIAL_CHASSIS =
          register(
              "radial_chassis",
              p ->
                  new com.simibubi.create.content.contraptions.chassis.RadialChassisBlock(
                      p.mapColor(MapColor.DIRT)));
  public static final com.simibubi.create.content.contraptions.chassis.StickerBlock STICKER =
      register(
          "sticker",
          p ->
              new com.simibubi.create.content.contraptions.chassis.StickerBlock(
                  p.mapColor(MapColor.STONE)));
  public static final com.simibubi.create.content.redstone.link.RedstoneLinkBlock REDSTONE_LINK =
      register(
          "redstone_link",
          p ->
              new com.simibubi.create.content.redstone.link.RedstoneLinkBlock(
                  p.strength(2)
                      .sound(SoundType.WOOD)
                      .mapColor(MapColor.TERRACOTTA_BROWN)
                      .forceSolidOn()));
  public static final com.simibubi.create.content.redstone.link.controller.LecternControllerBlock
      LECTERN_CONTROLLER =
          register(
              "lectern_controller",
              p ->
                  new com.simibubi.create.content.redstone.link.controller.LecternControllerBlock(
                      p.strength(2.5f).sound(SoundType.WOOD)),
              false);

  public static final com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock
      ANDESITE_DOOR =
          register(
              "andesite_door",
              p ->
                  com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock.stone(
                      p.strength(3).mapColor(MapColor.STONE), true)),
      BRASS_DOOR =
          register(
              "brass_door",
              p ->
                  com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock.stone(
                      p.strength(3).mapColor(MapColor.TERRACOTTA_YELLOW), false)),
      COPPER_DOOR =
          register(
              "copper_door",
              p ->
                  com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock.stone(
                      p.strength(3).mapColor(MapColor.COLOR_ORANGE), true)),
      TRAIN_DOOR =
          register(
              "train_door",
              p ->
                  com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock.metal(
                      p.strength(3), false)),
      FRAMED_GLASS_DOOR =
          register(
              "framed_glass_door",
              p ->
                  com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock.glass(
                      p.strength(3), false));
  public static final com.simibubi
          .create
          .content
          .contraptions
          .actors
          .contraptionControls
          .ContraptionControlsBlock
      CONTRAPTION_CONTROLS =
          register(
              "contraption_controls",
              com.simibubi
                      .create
                      .content
                      .contraptions
                      .actors
                      .contraptionControls
                      .ContraptionControlsBlock
                  ::new);
  public static final com.simibubi.create.content.equipment.bell.PeculiarBellBlock PECULIAR_BELL =
      register(
          "peculiar_bell",
          p ->
              new com.simibubi.create.content.equipment.bell.PeculiarBellBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.ANVIL)
                      .mapColor(MapColor.GOLD)
                      .forceSolidOn()));
  public static final com.simibubi.create.content.equipment.bell.HauntedBellBlock HAUNTED_BELL =
      register(
          "haunted_bell",
          p ->
              new com.simibubi.create.content.equipment.bell.HauntedBellBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.ANVIL)
                      .mapColor(MapColor.SAND)
                      .forceSolidOn()));
  public static final com.simibubi.create.content.redstone.deskBell.DeskBellBlock DESK_BELL =
      register(
          "desk_bell",
          p ->
              new com.simibubi.create.content.redstone.deskBell.DeskBellBlock(
                  p.mapColor(MapColor.SAND)));
  public static final com.simibubi.create.content.redstone.contact.RedstoneContactBlock
      REDSTONE_CONTACT =
          register(
              "redstone_contact",
              p ->
                  new com.simibubi.create.content.redstone.contact.RedstoneContactBlock(
                      p.sound(SoundType.STONE)
                          .strength(1.5f, 6)
                          .requiresCorrectToolForDrops()
                          .mapColor(MapColor.COLOR_GRAY)));
  public static final com.simibubi.create.content.contraptions.actors.roller.RollerBlock
      MECHANICAL_ROLLER =
          register(
              "mechanical_roller",
              com.simibubi.create.content.contraptions.actors.roller.RollerBlock::new);
  public static final com.simibubi.create.content.contraptions.actors.psi
          .PortableStorageInterfaceBlock
      PORTABLE_STORAGE_INTERFACE =
          register(
              "portable_storage_interface",
              com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock
                  ::forItems),
      PORTABLE_FLUID_INTERFACE =
          register(
              "portable_fluid_interface",
              com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlock
                  ::forFluids);
  public static final com.simibubi.create.content.contraptions.actors.harvester.HarvesterBlock
      MECHANICAL_HARVESTER =
          register(
              "mechanical_harvester",
              com.simibubi.create.content.contraptions.actors.harvester.HarvesterBlock::new);
  public static final com.simibubi.create.content.contraptions.actors.plough.PloughBlock
      MECHANICAL_PLOUGH =
          register(
              "mechanical_plough",
              com.simibubi.create.content.contraptions.actors.plough.PloughBlock::new);
  public static final com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlock
      MECHANICAL_BEARING =
          register(
              "mechanical_bearing",
              com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlock::new);
  public static final com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlock
      PACKAGE_FROGPORT =
          register(
              "package_frogport",
              com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlock::new);
  public static final com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock
      CHAIN_CONVEYOR =
          register(
              "chain_conveyor",
              com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlock::new);
  public static final java.util.Map<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.logistics.tableCloth.TableClothBlock>
      TABLE_CLOTHS = registerTableCloths();
  public static final com.simibubi.create.content.logistics.tableCloth.TableClothBlock
      ANDESITE_TABLE_CLOTH =
          register(
              "andesite_table_cloth",
              p ->
                  new com.simibubi.create.content.logistics.tableCloth.TableClothBlock(
                      p.strength(1.5f, 6).sound(SoundType.STONE).mapColor(MapColor.STONE), null)),
      BRASS_TABLE_CLOTH =
          register(
              "brass_table_cloth",
              p ->
                  new com.simibubi.create.content.logistics.tableCloth.TableClothBlock(
                      p.strength(3, 6).mapColor(MapColor.TERRACOTTA_YELLOW), null)),
      COPPER_TABLE_CLOTH =
          register(
              "copper_table_cloth",
              p ->
                  new com.simibubi.create.content.logistics.tableCloth.TableClothBlock(
                      p.strength(3, 6).sound(SoundType.COPPER).mapColor(MapColor.COLOR_ORANGE),
                      null));

  public static Block[] tableCloths() {
    var result = new java.util.ArrayList<Block>(TABLE_CLOTHS.values());
    result.addAll(java.util.List.of(ANDESITE_TABLE_CLOTH, BRASS_TABLE_CLOTH, COPPER_TABLE_CLOTH));
    return result.toArray(Block[]::new);
  }

  private static java.util.Map<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.logistics.tableCloth.TableClothBlock>
      registerTableCloths() {
    var result =
        new java.util.EnumMap<
            net.minecraft.world.item.DyeColor,
            com.simibubi.create.content.logistics.tableCloth.TableClothBlock>(
            net.minecraft.world.item.DyeColor.class);
    for (var color : net.minecraft.world.item.DyeColor.values())
      result.put(
          color,
          register(
              color.getSerializedName() + "_table_cloth",
              p -> new com.simibubi.create.content.logistics.tableCloth.TableClothBlock(p, color)));
    return java.util.Collections.unmodifiableMap(result);
  }

  public static final com.simibubi.create.content.logistics.stockTicker.StockTickerBlock
      STOCK_TICKER =
          register(
              "stock_ticker",
              com.simibubi.create.content.logistics.stockTicker.StockTickerBlock::new);
  public static final java.util.Map<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.contraptions.actors.seat.SeatBlock>
      SEATS = registerSeats();

  private static java.util.Map<
          net.minecraft.world.item.DyeColor,
          com.simibubi.create.content.contraptions.actors.seat.SeatBlock>
      registerSeats() {
    var seats =
        new java.util.EnumMap<
            net.minecraft.world.item.DyeColor,
            com.simibubi.create.content.contraptions.actors.seat.SeatBlock>(
            net.minecraft.world.item.DyeColor.class);
    for (var color : net.minecraft.world.item.DyeColor.values())
      seats.put(
          color,
          register(
              color.getSerializedName() + "_seat",
              properties ->
                  new com.simibubi.create.content.contraptions.actors.seat.SeatBlock(
                      properties, color)));
    return java.util.Collections.unmodifiableMap(seats);
  }

  public static final com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlock
      REDSTONE_REQUESTER =
          register(
              "redstone_requester",
              com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlock::new);
  public static final com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock
      FACTORY_GAUGE =
          register(
              "factory_gauge",
              com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock::new);
  public static final com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock
      STOCK_LINK =
          register(
              "stock_link",
              com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock::new);
  public static final com.simibubi.create.content.logistics.vault.ItemVaultBlock ITEM_VAULT =
      register("item_vault", com.simibubi.create.content.logistics.vault.ItemVaultBlock::new);
  public static final com.simibubi.create.content.logistics.crate.CreativeCrateBlock
      CREATIVE_CRATE =
          register(
              "creative_crate",
              com.simibubi.create.content.logistics.crate.CreativeCrateBlock::new);
  public static final com.simibubi.create.content.decoration.CardboardBlock CARDBOARD_BLOCK =
      register("cardboard_block", com.simibubi.create.content.decoration.CardboardBlock::new);
  public static final com.simibubi.create.content.logistics.packager.PackagerBlock PACKAGER =
      register("packager", com.simibubi.create.content.logistics.packager.PackagerBlock::new);
  public static final com.simibubi.create.content.logistics.packager.repackager.RepackagerBlock
      REPACKAGER =
          register(
              "repackager",
              com.simibubi.create.content.logistics.packager.repackager.RepackagerBlock::new);
  public static final ShaftBlock SHAFT = register("shaft", ShaftBlock::new);
  public static final CogWheelBlock COGWHEEL =
      register("cogwheel", p -> new CogWheelBlock(p, false));
  public static final CogWheelBlock LARGE_COGWHEEL =
      register("large_cogwheel", p -> new CogWheelBlock(p, true));
  public static final CreativeMotorBlock CREATIVE_MOTOR =
      register("creative_motor", CreativeMotorBlock::new);
  public static final com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock MECHANICAL_ARM=register("mechanical_arm",com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).mapColor(MapColor.TERRACOTTA_YELLOW).noOcclusion());
  public static final HandCrankBlock HAND_CRANK = register("hand_crank", HandCrankBlock::new);
  public static final com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock
      ANDESITE_ENCASED_SHAFT = register("andesite_encased_shaft", p -> new com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock(p, () -> AllBlocks.ANDESITE_CASING),true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.PODZOL).noOcclusion().isRedstoneConductor((state,level,pos)->false)),
      BRASS_ENCASED_SHAFT = register("brass_encased_shaft", p -> new com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedShaftBlock(p, () -> AllBlocks.BRASS_CASING),true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.TERRACOTTA_BROWN).noOcclusion().isRedstoneConductor((state,level,pos)->false));
  public static final com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock
      ANDESITE_ENCASED_COGWHEEL = register("andesite_encased_cogwheel", p -> new com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock(p, false, () -> AllBlocks.ANDESITE_CASING),true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.PODZOL).noOcclusion()),
      BRASS_ENCASED_COGWHEEL = register("brass_encased_cogwheel", p -> new com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock(p, false, () -> AllBlocks.BRASS_CASING),true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.TERRACOTTA_BROWN).noOcclusion()),
      ANDESITE_ENCASED_LARGE_COGWHEEL = register("andesite_encased_large_cogwheel", p -> new com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock(p, true, () -> AllBlocks.ANDESITE_CASING),true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.PODZOL).noOcclusion()),
      BRASS_ENCASED_LARGE_COGWHEEL = register("brass_encased_large_cogwheel", p -> new com.simibubi.create.content.kinetics.simpleRelays.encased.EncasedCogwheelBlock(p, true, () -> AllBlocks.BRASS_CASING),true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.TERRACOTTA_BROWN).noOcclusion());
  public static final com.simibubi.create.content.kinetics.crank.ValveHandleBlock COPPER_VALVE_HANDLE =
      register("copper_valve_handle", com.simibubi.create.content.kinetics.crank.ValveHandleBlock::copper,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.COPPER_BLOCK.weathering().unaffected()).noOcclusion());
  public static final java.util.Map<net.minecraft.world.item.DyeColor,com.simibubi.create.content.kinetics.crank.ValveHandleBlock> DYED_VALVE_HANDLES = registerValveHandles();
  private static java.util.Map<net.minecraft.world.item.DyeColor,com.simibubi.create.content.kinetics.crank.ValveHandleBlock> registerValveHandles() {
    var blocks = new java.util.EnumMap<net.minecraft.world.item.DyeColor,com.simibubi.create.content.kinetics.crank.ValveHandleBlock>(net.minecraft.world.item.DyeColor.class);
    for (var color : net.minecraft.world.item.DyeColor.values()) blocks.put(color,register(color.getSerializedName()+"_valve_handle",p -> com.simibubi.create.content.kinetics.crank.ValveHandleBlock.dyed(p,color),true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.COPPER_BLOCK.weathering().unaffected()).mapColor(color).noOcclusion()));
    return java.util.Collections.unmodifiableMap(blocks);
  }
  public static final com.simibubi.create.content.kinetics.fan.NozzleBlock NOZZLE = register("nozzle", com.simibubi.create.content.kinetics.fan.NozzleBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.COLOR_LIGHT_GRAY).noOcclusion());
  public static final com.simibubi.create.content.kinetics.turntable.TurntableBlock TURNTABLE = register("turntable", com.simibubi.create.content.kinetics.turntable.TurntableBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STRIPPED_SPRUCE_WOOD).mapColor(MapColor.PODZOL).noOcclusion());
  public static final com.simibubi.create.content.kinetics.flywheel.FlywheelBlock FLYWHEEL = register("flywheel", com.simibubi.create.content.kinetics.flywheel.FlywheelBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).mapColor(MapColor.TERRACOTTA_YELLOW).noOcclusion());

  public static final com.simibubi.create.content.kinetics.deployer.DeployerBlock DEPLOYER =
      register("deployer", com.simibubi.create.content.kinetics.deployer.DeployerBlock::new);
  public static final com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock
      ROTATION_SPEED_CONTROLLER =
          register(
              "rotation_speed_controller",
              com.simibubi.create.content.kinetics.speedController.SpeedControllerBlock::new);
  public static final com.simibubi.create.content.kinetics.gauge.GaugeBlock SPEEDOMETER =
      register("speedometer", com.simibubi.create.content.kinetics.gauge.GaugeBlock::speed);
  public static final com.simibubi.create.content.kinetics.gauge.GaugeBlock STRESSOMETER =
      register("stressometer", com.simibubi.create.content.kinetics.gauge.GaugeBlock::stress);
  public static final GearshiftBlock GEARSHIFT = register("gearshift", GearshiftBlock::new);
  public static final ClutchBlock CLUTCH = register("clutch", ClutchBlock::new);
  public static final GearboxBlock GEARBOX = register("gearbox", GearboxBlock::new);
  public static final MillstoneBlock MILLSTONE = register("millstone", MillstoneBlock::new);
  public static final Block ANDESITE_CASING = register("andesite_casing", Block::new);
  public static final Block COPPER_CASING =
      register(
          "copper_casing",
          p -> new Block(p.mapColor(MapColor.TERRACOTTA_LIGHT_GRAY).sound(SoundType.COPPER)));
  public static final Block BRASS_CASING = register("brass_casing", Block::new);
  public static final ChainDriveBlock ENCASED_CHAIN_DRIVE =
      register("encased_chain_drive", ChainDriveBlock::new);
  public static final ChainGearshiftBlock ADJUSTABLE_CHAIN_GEARSHIFT =
      register("adjustable_chain_gearshift", ChainGearshiftBlock::new);
  public static final MechanicalPressBlock MECHANICAL_PRESS =
      register("mechanical_press", MechanicalPressBlock::new);
  public static final DepotBlock DEPOT = register("depot", DepotBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.COLOR_GRAY));
  public static final com.simibubi.create.content.logistics.depot.EjectorBlock WEIGHTED_EJECTOR = register("weighted_ejector",com.simibubi.create.content.logistics.depot.EjectorBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).noOcclusion().mapColor(MapColor.COLOR_GRAY));
  public static final WaterWheelBlock WATER_WHEEL = register("water_wheel", WaterWheelBlock::new);
  public static final LargeWaterWheelBlock LARGE_WATER_WHEEL =
      register("large_water_wheel", LargeWaterWheelBlock::new);
  public static final WaterWheelStructuralBlock WATER_WHEEL_STRUCTURAL =
      register("water_wheel_structure", WaterWheelStructuralBlock::new, false);
  public static final BasinBlock BASIN = register("basin", BasinBlock::new);
  public static final MechanicalMixerBlock MECHANICAL_MIXER =
      register("mechanical_mixer", MechanicalMixerBlock::new);
  public static final BlazeBurnerBlock BLAZE_BURNER =
      register("blaze_burner", BlazeBurnerBlock::new, false);
  public static final com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock
      LIT_BLAZE_BURNER =
          register(
              "lit_blaze_burner",
              com.simibubi.create.content.processing.burner.LitBlazeBurnerBlock::new,
              false);
  public static final Block ZINC_ORE =
      register(
          "zinc_ore",
          p -> new Block(p.strength(3).sound(SoundType.STONE).requiresCorrectToolForDrops()));
  public static final Block DEEPSLATE_ZINC_ORE =
      register(
          "deepslate_zinc_ore",
          p ->
              new Block(p.strength(4.5f).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
  public static final Block ZINC_BLOCK = register("zinc_block", Block::new);
  public static final Block RAW_ZINC_BLOCK = register("raw_zinc_block", Block::new);
  public static final Block BRASS_BLOCK = register("brass_block", Block::new);
  public static final EncasedFanBlock ENCASED_FAN = register("encased_fan", EncasedFanBlock::new);
  public static final BeltBlock BELT = register("belt", BeltBlock::new, false);
  public static final com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock
      CRUSHING_WHEEL =
          register(
              "crushing_wheel",
              com.simibubi.create.content.kinetics.crusher.CrushingWheelBlock::new);
  public static final com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock
      CRUSHING_WHEEL_CONTROLLER =
          register(
              "crushing_wheel_controller",
              com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlock::new,
              false);
  public static final Block ASURINE =
      register("asurine", Block::new,true,com.simibubi.create.content.decoration.palettes.PaletteStoneRegistry.properties("asurine"));
  public static final Block CRIMSITE =
      register("crimsite", Block::new,true,com.simibubi.create.content.decoration.palettes.PaletteStoneRegistry.properties("crimsite"));
  public static final Block OCHRUM =
      register("ochrum", Block::new,true,com.simibubi.create.content.decoration.palettes.PaletteStoneRegistry.properties("ochrum"));
  public static final Block VERIDIUM =
      register("veridium", Block::new,true,com.simibubi.create.content.decoration.palettes.PaletteStoneRegistry.properties("veridium"));
  public static final com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock
      MECHANICAL_CRAFTER =
          register(
              "mechanical_crafter",
              com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock::new);
  public static final com.simibubi.create.content.kinetics.drill.DrillBlock MECHANICAL_DRILL =
      register("mechanical_drill", com.simibubi.create.content.kinetics.drill.DrillBlock::new);
  public static final com.simibubi.create.content.kinetics.saw.SawBlock MECHANICAL_SAW =
      register("mechanical_saw", com.simibubi.create.content.kinetics.saw.SawBlock::new);

  public static final com.simibubi.create.content.fluids.tank.FluidTankBlock FLUID_TANK =
      register(
          "fluid_tank",
          p ->
              new com.simibubi.create.content.fluids.tank.FluidTankBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.COPPER)
                      .mapColor(MapColor.COLOR_ORANGE)
                      .isRedstoneConductor((state, level, pos) -> true),
                  false));
  public static final com.simibubi.create.content.fluids.tank.FluidTankBlock CREATIVE_FLUID_TANK =
      register(
          "creative_fluid_tank",
          p ->
              new com.simibubi.create.content.fluids.tank.FluidTankBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.COPPER)
                      .mapColor(MapColor.COLOR_PURPLE),
                  true));

  public static final com.simibubi.create.content.fluids.pipes.FluidPipeBlock FLUID_PIPE =
      register(
          "fluid_pipe",
          p ->
              new com.simibubi.create.content.fluids.pipes.FluidPipeBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .forceSolidOff()
                      .sound(SoundType.COPPER)
                      .mapColor(MapColor.COLOR_ORANGE)));
  public static final com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock
      GLASS_FLUID_PIPE =
          register(
              "glass_fluid_pipe",
              p ->
                  new com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock(
                      p.strength(3, 6)
                          .requiresCorrectToolForDrops()
                          .sound(SoundType.COPPER)
                          .mapColor(MapColor.COLOR_ORANGE)),
              false);
  public static final com.simibubi.create.content.fluids.pipes.EncasedPipeBlock ENCASED_FLUID_PIPE =
      register(
          "encased_fluid_pipe",
          p ->
              new com.simibubi.create.content.fluids.pipes.EncasedPipeBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.COPPER)
                      .mapColor(MapColor.TERRACOTTA_LIGHT_GRAY)),
          false);
  public static final com.simibubi.create.content.fluids.pump.PumpBlock MECHANICAL_PUMP =
      register(
          "mechanical_pump",
          p ->
              new com.simibubi.create.content.fluids.pump.PumpBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.COPPER)
                      .mapColor(MapColor.STONE)));

  public static final com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock
      SMART_FLUID_PIPE =
          register(
              "smart_fluid_pipe",
              p ->
                  new com.simibubi.create.content.fluids.pipes.SmartFluidPipeBlock(
                      p.strength(3, 6)
                          .requiresCorrectToolForDrops()
                          .sound(SoundType.COPPER)
                          .mapColor(MapColor.TERRACOTTA_YELLOW)));
  public static final com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock FLUID_VALVE =
      register(
          "fluid_valve",
          p ->
              new com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.COPPER)
                      .mapColor(MapColor.COLOR_ORANGE)));
  public static final com.simibubi.create.content.fluids.spout.SpoutBlock SPOUT =
      register(
          "spout",
          p ->
              new com.simibubi.create.content.fluids.spout.SpoutBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.COPPER)
                      .mapColor(MapColor.COLOR_ORANGE)));
  public static final com.simibubi.create.content.fluids.drain.ItemDrainBlock ITEM_DRAIN =
      register(
          "item_drain",
          p ->
              new com.simibubi.create.content.fluids.drain.ItemDrainBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.COPPER)
                      .mapColor(MapColor.COLOR_ORANGE)));
  public static final com.simibubi.create.content.fluids.hosePulley.HosePulleyBlock HOSE_PULLEY =
      register(
          "hose_pulley",
          p ->
              new com.simibubi.create.content.fluids.hosePulley.HosePulleyBlock(
                  p.strength(3, 6)
                      .requiresCorrectToolForDrops()
                      .sound(SoundType.COPPER)
                      .mapColor(MapColor.COLOR_ORANGE)));
  public static final com.simibubi.create.content.logistics.funnel.FunnelBlock ANDESITE_FUNNEL =
      register(
          "andesite_funnel",
          p -> new com.simibubi.create.content.logistics.funnel.FunnelBlock(p, false));
  public static final com.simibubi.create.content.logistics.funnel.FunnelBlock BRASS_FUNNEL =
      register(
          "brass_funnel",
          p -> new com.simibubi.create.content.logistics.funnel.FunnelBlock(p, true));
  public static final com.simibubi.create.content.logistics.funnel.BeltFunnelBlock
      ANDESITE_BELT_FUNNEL =
          register(
              "andesite_belt_funnel",
              p -> new com.simibubi.create.content.logistics.funnel.BeltFunnelBlock(p, false),
              false);
  public static final com.simibubi.create.content.logistics.funnel.BeltFunnelBlock
      BRASS_BELT_FUNNEL =
          register(
              "brass_belt_funnel",
              p -> new com.simibubi.create.content.logistics.funnel.BeltFunnelBlock(p, true),
              false);
  public static final com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock ANDESITE_TUNNEL =
      register(
          "andesite_tunnel",
          p -> new com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock(p, false));
  public static final com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock BRASS_TUNNEL =
      register(
          "brass_tunnel",
          p -> new com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock(p, true));
  public static final com.simibubi.create.content.logistics.chute.ChuteBlock CHUTE =
      register("chute", p -> new com.simibubi.create.content.logistics.chute.ChuteBlock(p));
  public static final com.simibubi.create.content.logistics.chute.SmartChuteBlock SMART_CHUTE =
      register(
          "smart_chute", p -> new com.simibubi.create.content.logistics.chute.SmartChuteBlock(p));
  public static final Block INDUSTRIAL_IRON_BLOCK =
      register(
          "industrial_iron_block", p -> new Block(p.strength(5, 6).requiresCorrectToolForDrops()));
  public static final com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock
      THRESHOLD_SWITCH =
          register(
              "stockpile_switch",
              p ->
                  new com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlock(
                      p.strength(1.5f, 6)
                          .requiresCorrectToolForDrops()
                          .mapColor(MapColor.TERRACOTTA_BROWN)
                          .sound(SoundType.STONE)
                          .isRedstoneConductor((state, level, pos) -> false)));
  public static final com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock
      SMART_OBSERVER =
          register(
              "content_observer",
              p ->
                  new com.simibubi.create.content.redstone.smartObserver.SmartObserverBlock(
                      p.strength(1.5f, 6)
                          .requiresCorrectToolForDrops()
                          .mapColor(MapColor.TERRACOTTA_BROWN)
                          .sound(SoundType.STONE)
                          .isRedstoneConductor((state, level, pos) -> false)));
  public static final Block HONEY =
      register(
          "honey",
          p ->
              new com.simibubi.create.content.fluids.CreateLiquidBlock(
                  AllFluids.HONEY,
                  p.noCollision()
                      .strength(100)
                      .noLootTable()
                      .replaceable()
                      .liquid()
                      .mapColor(MapColor.TERRACOTTA_YELLOW)),
          false);
  public static final Block CHOCOLATE =
      register(
          "chocolate",
          p ->
              new com.simibubi.create.content.fluids.CreateLiquidBlock(
                  AllFluids.CHOCOLATE,
                  p.noCollision()
                      .strength(100)
                      .noLootTable()
                      .replaceable()
                      .liquid()
                      .mapColor(MapColor.TERRACOTTA_BROWN)),
          false);
  public static final Block LIMESTONE =
      register("limestone", Block::new,true,com.simibubi.create.content.decoration.palettes.PaletteStoneRegistry.properties("limestone"));
  public static final Block SCORCHIA=register("scorchia",Block::new,true,com.simibubi.create.content.decoration.palettes.PaletteStoneRegistry.properties("scorchia"));
  public static final Block SCORIA =
      register("scoria", Block::new,true,com.simibubi.create.content.decoration.palettes.PaletteStoneRegistry.properties("scoria"));
  public static final com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock
      STEAM_ENGINE =
          register(
              "steam_engine",
              p ->
                  new com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock(
                      p.strength(3, 6).requiresCorrectToolForDrops().sound(SoundType.COPPER)));
  public static final com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock
      POWERED_SHAFT =
          register(
              "powered_shaft",
              com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlock::new,
              false);
  public static final com.simibubi.create.content.decoration.steamWhistle.WhistleBlock
      STEAM_WHISTLE =
          register(
              "steam_whistle",
              p ->
                  new com.simibubi.create.content.decoration.steamWhistle.WhistleBlock(
                      p.sound(SoundType.COPPER).requiresCorrectToolForDrops()));
  public static final com.simibubi.create.content.decoration.steamWhistle.WhistleExtenderBlock
      STEAM_WHISTLE_EXTENSION =
          register(
              "steam_whistle_extension",
              p ->
                  new com.simibubi.create.content.decoration.steamWhistle.WhistleExtenderBlock(
                      p.sound(SoundType.COPPER).requiresCorrectToolForDrops()),
              false);

  public static final Block COPYCAT_BASE=register("copycat_base",Block::new,false,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).mapColor(MapColor.GLOW_LICHEN));
  public static final com.simibubi.create.content.decoration.copycat.CopycatPanelBlock COPYCAT_PANEL=register("copycat_panel",com.simibubi.create.content.decoration.copycat.CopycatPanelBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).noOcclusion().mapColor(MapColor.NONE).isValidSpawn((state,level,pos,type)->false));
  public static final com.simibubi.create.content.decoration.copycat.CopycatStepBlock COPYCAT_STEP=register("copycat_step",com.simibubi.create.content.decoration.copycat.CopycatStepBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).noOcclusion().mapColor(MapColor.NONE).forceSolidOn().isValidSpawn((state,level,pos,type)->false));
  public static final com.simibubi.create.foundation.block.WrenchableDirectionalBlock COPYCAT_BARS=register("copycat_bars",com.simibubi.create.foundation.block.WrenchableDirectionalBlock::new,false,BlockBehaviour.Properties.of());
  public static final com.simibubi.create.content.redstone.RoseQuartzLampBlock ROSE_QUARTZ_LAMP=register("rose_quartz_lamp",com.simibubi.create.content.redstone.RoseQuartzLampBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.REDSTONE_LAMP).mapColor(MapColor.TERRACOTTA_PINK));

  // Remaining standalone source decorations; legacy casings retain their source creative exclusion.
  public static final Block ANDESITE_ALLOY_BLOCK=register("andesite_alloy_block",Block::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.STONE).requiresCorrectToolForDrops());
  public static final com.simibubi.create.content.decoration.CardboardBlock BOUND_CARDBOARD_BLOCK=register("bound_cardboard_block",com.simibubi.create.content.decoration.CardboardBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.MUSHROOM_STEM));
  public static final com.simibubi.create.content.materials.ExperienceBlock EXPERIENCE_BLOCK=register("experience_block",com.simibubi.create.content.materials.ExperienceBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).mapColor(MapColor.PLANT).requiresCorrectToolForDrops().lightLevel(state->15).sound(new SoundType(1,.5f,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_STEP,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_PLACE,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT,net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_FALL)));
  public static final net.minecraft.world.level.block.RotatedPillarBlock ROSE_QUARTZ_BLOCK=register("rose_quartz_block",net.minecraft.world.level.block.RotatedPillarBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.AMETHYST_BLOCK).mapColor(MapColor.TERRACOTTA_PINK).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE));
  public static final Block ROSE_QUARTZ_TILES=register("rose_quartz_tiles",Block::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DEEPSLATE).mapColor(MapColor.TERRACOTTA_PINK).requiresCorrectToolForDrops());
  public static final Block SMALL_ROSE_QUARTZ_TILES=register("small_rose_quartz_tiles",Block::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.DEEPSLATE).mapColor(MapColor.TERRACOTTA_PINK).requiresCorrectToolForDrops());
  public static final com.simibubi.create.content.decoration.bracket.BracketBlock WOODEN_BRACKET=register("wooden_bracket",com.simibubi.create.content.decoration.bracket.BracketBlock::new,true,BlockBehaviour.Properties.of().sound(SoundType.SCAFFOLDING));
  public static final com.simibubi.create.content.decoration.bracket.BracketBlock METAL_BRACKET=register("metal_bracket",com.simibubi.create.content.decoration.bracket.BracketBlock::new,true,BlockBehaviour.Properties.of().sound(SoundType.NETHERITE_BLOCK));
  public static final com.simibubi.create.content.decoration.encasing.CasingBlock SHADOW_STEEL_CASING=register("shadow_steel_casing",com.simibubi.create.content.decoration.encasing.CasingBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.COLOR_BLACK).sound(SoundType.WOOD));
  public static final com.simibubi.create.content.decoration.encasing.CasingBlock REFINED_RADIANCE_CASING=register("refined_radiance_casing",com.simibubi.create.content.decoration.encasing.CasingBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.ANDESITE).mapColor(MapColor.SNOW).sound(SoundType.WOOD).lightLevel(state->12));
  public static final com.simibubi.create.content.decoration.TrainTrapdoorBlock TRAIN_TRAPDOOR=register("train_trapdoor",com.simibubi.create.content.decoration.TrainTrapdoorBlock::metal,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).mapColor(MapColor.TERRACOTTA_CYAN));
  public static final com.simibubi.create.content.decoration.TrainTrapdoorBlock FRAMED_GLASS_TRAPDOOR=register("framed_glass_trapdoor",com.simibubi.create.content.decoration.TrainTrapdoorBlock::glass,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).mapColor(MapColor.NONE).noOcclusion());
  public static final com.simibubi.create.content.decoration.MetalLadderBlock ANDESITE_LADDER=register("andesite_ladder",com.simibubi.create.content.decoration.MetalLadderBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LADDER).mapColor(MapColor.STONE).sound(SoundType.COPPER));
  public static final net.minecraft.world.level.block.IronBarsBlock ANDESITE_BARS=register("andesite_bars",net.minecraft.world.level.block.IronBarsBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BARS).mapColor(MapColor.STONE).sound(SoundType.COPPER));
  public static final com.simibubi.create.content.decoration.MetalScaffoldingBlock ANDESITE_SCAFFOLD=register("andesite_scaffolding",com.simibubi.create.content.decoration.MetalScaffoldingBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SCAFFOLDING).mapColor(MapColor.STONE).sound(SoundType.COPPER));
  public static final com.simibubi.create.content.decoration.MetalLadderBlock BRASS_LADDER=register("brass_ladder",com.simibubi.create.content.decoration.MetalLadderBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LADDER).mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.COPPER));
  public static final net.minecraft.world.level.block.IronBarsBlock BRASS_BARS=register("brass_bars",net.minecraft.world.level.block.IronBarsBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BARS).mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.COPPER));
  public static final com.simibubi.create.content.decoration.MetalScaffoldingBlock BRASS_SCAFFOLD=register("brass_scaffolding",com.simibubi.create.content.decoration.MetalScaffoldingBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SCAFFOLDING).mapColor(MapColor.TERRACOTTA_YELLOW).sound(SoundType.COPPER));
  public static final com.simibubi.create.content.decoration.MetalLadderBlock COPPER_LADDER=register("copper_ladder",com.simibubi.create.content.decoration.MetalLadderBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.LADDER).mapColor(MapColor.COLOR_ORANGE).sound(SoundType.COPPER));
  public static final net.minecraft.world.level.block.IronBarsBlock COPPER_BARS=register("copper_bars",net.minecraft.world.level.block.IronBarsBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.IRON_BARS).mapColor(MapColor.COLOR_ORANGE).sound(SoundType.COPPER));
  public static final com.simibubi.create.content.decoration.MetalScaffoldingBlock COPPER_SCAFFOLD=register("copper_scaffolding",com.simibubi.create.content.decoration.MetalScaffoldingBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SCAFFOLDING).mapColor(MapColor.COLOR_ORANGE).sound(SoundType.COPPER));

  public static final com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock CART_ASSEMBLER=register("cart_assembler",com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE).noOcclusion().mapColor(MapColor.COLOR_GRAY).pushReaction(net.minecraft.world.level.material.PushReaction.IMMOVEABLE));
  public static final com.simibubi.create.content.redstone.rail.ControllerRailBlock CONTROLLER_RAIL=register("controller_rail",com.simibubi.create.content.redstone.rail.ControllerRailBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.POWERED_RAIL));
  public static final com.simibubi.create.content.contraptions.mounted.MinecartAnchorBlock MINECART_ANCHOR=register("minecart_anchor",com.simibubi.create.content.contraptions.mounted.MinecartAnchorBlock::new,false,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.STONE));

  private static <T extends Block> T register(
      String path, Function<BlockBehaviour.Properties, T> factory) {
    return register(path, factory, true);
  }

  private static <T extends Block> T register(
      String path, Function<BlockBehaviour.Properties, T> factory, boolean hasItem) {
    return register(
        path,
        factory,
        hasItem,
        BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL)
            .strength(1.5f)
            .sound(SoundType.METAL)
            .noOcclusion());
  }

  private static <T extends Block> T register(
      String path,
      Function<BlockBehaviour.Properties, T> factory,
      boolean hasItem,
      BlockBehaviour.Properties properties) {
    var id = Create.asResource(path);
    var blockKey = ResourceKey.create(Registries.BLOCK, id);
    T block =
        Registry.register(
            BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));
    if (hasItem) {
      var itemKey = ResourceKey.create(Registries.ITEM, id);
      Registry.register(
          BuiltInRegistries.ITEM,
          itemKey,
          block instanceof com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock
              ? new com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
              :
          block instanceof com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock
              ? new com.simibubi.create.content.kinetics.simpleRelays.CogwheelBlockItem((com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock)block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
              :
          block.getClass() == com.simibubi.create.content.redstone.contact.RedstoneContactBlock.class
              ? new com.simibubi.create.content.redstone.contact.RedstoneContactItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
              :
          block instanceof com.simibubi.create.content.kinetics.press.MechanicalPressBlock
                  || block instanceof com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlock
                  || block instanceof com.simibubi.create.content.fluids.spout.SpoutBlock
                  || block instanceof com.simibubi.create.content.kinetics.deployer.DeployerBlock
              ? new com.simibubi.create.content.processing.AssemblyOperatorBlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
              : block instanceof com.simibubi.create.content.contraptions.mounted.CartAssemblerBlock
              ? new com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
              : block instanceof com.simibubi.create.content.decoration.bracket.BracketBlock
              ? new com.simibubi.create.content.decoration.bracket.BracketBlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
              : block instanceof com.simibubi.create.content.decoration.MetalScaffoldingBlock
              ? new com.simibubi.create.content.decoration.MetalScaffoldingBlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
              : block instanceof com.simibubi.create.content.materials.ExperienceBlock
              ? new BlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().rarity(net.minecraft.world.item.Rarity.UNCOMMON))
              : block instanceof com.simibubi.create.content.equipment.clipboard.ClipboardBlock
              ? new com.simibubi.create.content.equipment.clipboard.ClipboardBlockItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().stacksTo(1))
              : block instanceof com.simibubi.create.content.logistics.depot.EjectorBlock
              ? new com.simibubi.create.content.logistics.depot.EjectorItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
              : block instanceof com.simibubi.create.content.kinetics.mechanicalArm.ArmBlock
              ? new com.simibubi.create.content.kinetics.mechanicalArm.ArmItem(block,new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
              : block instanceof com.simibubi.create.content.trains.observer.TrackObserverBlock
              ? new com.simibubi.create.content.trains.track.TrackTargetingBlockItem(
                  block,
                  new Item.Properties().setId(itemKey).useBlockDescriptionPrefix(),
                  com.simibubi.create.content.trains.observer.ObserverEdgePoints.OBSERVER)
              : block instanceof com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock
                  ? new com.simibubi.create.content.redstone.displayLink.DisplayLinkBlockItem(
                      block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
                  : block instanceof com.simibubi.create.content.trains.signal.SignalBlock
                      ? new com.simibubi.create.content.trains.track.TrackTargetingBlockItem(
                          block,
                          new Item.Properties().setId(itemKey).useBlockDescriptionPrefix(),
                          com.simibubi.create.content.trains.signal.SignalEdgePoints.SIGNAL)
                      : block instanceof com.simibubi.create.content.trains.station.StationBlock
                          ? new com.simibubi.create.content.trains.track.TrackTargetingBlockItem(
                              block,
                              new Item.Properties().setId(itemKey).useBlockDescriptionPrefix(),
                              com.simibubi.create.content.trains.station.StationEdgePoints.STATION)
                          : block instanceof com.simibubi.create.content.trains.track.TrackBlock
                              ? new com.simibubi.create.content.trains.track.TrackBlockItem(
                                  block,
                                  new Item.Properties().setId(itemKey).useBlockDescriptionPrefix())
                              : block
                                          instanceof
                                          com.simibubi.create.content.contraptions.bearing.SailBlock
                                              sail
                                      && !sail.isFrame()
                                  ? new com.simibubi.create.content.contraptions.bearing
                                      .BlankSailBlockItem(
                                      block,
                                      new Item.Properties()
                                          .setId(itemKey)
                                          .useBlockDescriptionPrefix())
                                  : block
                                          instanceof
                                          com.simibubi
                                              .create
                                              .content
                                              .decoration
                                              .slidingDoor
                                              .SlidingDoorBlock
                                      ? new net.minecraft.world.item.DoubleHighBlockItem(
                                          block,
                                          new Item.Properties()
                                              .setId(itemKey)
                                              .useBlockDescriptionPrefix())
                                      : block
                                              instanceof
                                              com.simibubi.create.content.contraptions.actors.roller
                                                  .RollerBlock
                                          ? new com.simibubi.create.content.contraptions.actors
                                              .roller.RollerBlockItem(
                                              block,
                                              new Item.Properties()
                                                  .setId(itemKey)
                                                  .useBlockDescriptionPrefix())
                                          : block
                                                  instanceof
                                                  com.simibubi
                                                      .create
                                                      .content
                                                      .logistics
                                                      .tableCloth
                                                      .TableClothBlock
                                              ? new com.simibubi
                                                  .create
                                                  .content
                                                  .logistics
                                                  .tableCloth
                                                  .TableClothBlockItem(
                                                  block,
                                                  new Item.Properties()
                                                      .setId(itemKey)
                                                      .useBlockDescriptionPrefix())
                                              : block
                                                      instanceof
                                                      com.simibubi
                                                          .create
                                                          .content
                                                          .logistics
                                                          .factoryBoard
                                                          .FactoryPanelBlock
                                                  ? new com.simibubi
                                                      .create
                                                      .content
                                                      .logistics
                                                      .factoryBoard
                                                      .FactoryPanelBlockItem(
                                                      block,
                                                      new Item.Properties()
                                                          .setId(itemKey)
                                                          .useBlockDescriptionPrefix())
                                                  : block
                                                          instanceof
                                                          com.simibubi
                                                              .create
                                                              .content
                                                              .logistics
                                                              .redstoneRequester
                                                              .RedstoneRequesterBlock
                                                      ? new com.simibubi
                                                          .create
                                                          .content
                                                          .logistics
                                                          .redstoneRequester
                                                          .RedstoneRequesterBlockItem(
                                                          block,
                                                          new Item.Properties()
                                                              .setId(itemKey)
                                                              .useBlockDescriptionPrefix())
                                                      : block
                                                                  instanceof
                                                                  com.simibubi
                                                                      .create
                                                                      .content
                                                                      .logistics
                                                                      .packagerLink
                                                                      .PackagerLinkBlock
                                                              || block
                                                                  instanceof
                                                                  com.simibubi
                                                                      .create
                                                                      .content
                                                                      .logistics
                                                                      .stockTicker
                                                                      .StockTickerBlock
                                                          ? new com.simibubi
                                                              .create
                                                              .content
                                                              .logistics
                                                              .packagerLink
                                                              .LogisticallyLinkedBlockItem(
                                                              block,
                                                              new Item.Properties()
                                                                  .setId(itemKey)
                                                                  .useBlockDescriptionPrefix())
                                                          : block
                                                                  instanceof
                                                                  com.simibubi.create.content
                                                                      .logistics.vault
                                                                      .ItemVaultBlock
                                                              ? new com.simibubi.create.content
                                                                  .logistics.vault.ItemVaultItem(
                                                                  block,
                                                                  new Item.Properties()
                                                                      .setId(itemKey)
                                                                      .useBlockDescriptionPrefix())
                                                              : block
                                                                      instanceof
                                                                      com.simibubi.create.content
                                                                          .logistics.crate
                                                                          .CreativeCrateBlock
                                                                  ? new BlockItem(
                                                                      block,
                                                                      new Item.Properties()
                                                                          .setId(itemKey)
                                                                          .useBlockDescriptionPrefix()
                                                                          .rarity(
                                                                              net.minecraft.world
                                                                                  .item.Rarity
                                                                                  .EPIC))
                                                                  : block
                                                                          instanceof
                                                                          com.simibubi.create
                                                                              .content.decoration
                                                                              .CardboardBlock
                                                                      ? new BlockItem(
                                                                          block,
                                                                          new Item.Properties()
                                                                              .setId(itemKey)
                                                                              .useBlockDescriptionPrefix()
                                                                              .component(
                                                                                  net.minecraft.core
                                                                                      .component
                                                                                      .DataComponents
                                                                                      .COOKING_FUEL,
                                                                                  com.simibubi.create.foundation.item.CreateCookingFuels.component("cardboard_block")))
                                                                      : block
                                                                              instanceof
                                                                              com.simibubi.create
                                                                                      .content
                                                                                      .logistics
                                                                                      .chute
                                                                                      .ChuteBlock
                                                                                  chute
                                                                          ? new com.simibubi.create
                                                                              .content.logistics
                                                                              .chute.ChuteItem(
                                                                              chute,
                                                                              new Item.Properties()
                                                                                  .setId(itemKey)
                                                                                  .useBlockDescriptionPrefix())
                                                                          : block
                                                                                  instanceof
                                                                                  com.simibubi
                                                                                          .create
                                                                                          .content
                                                                                          .logistics
                                                                                          .tunnel
                                                                                          .BeltTunnelBlock
                                                                                      tunnel
                                                                              ? new com.simibubi
                                                                                  .create.content
                                                                                  .logistics.tunnel
                                                                                  .BeltTunnelItem(
                                                                                  tunnel,
                                                                                  new Item
                                                                                          .Properties()
                                                                                      .setId(
                                                                                          itemKey)
                                                                                      .useBlockDescriptionPrefix())
                                                                              : block
                                                                                      instanceof
                                                                                      com.simibubi
                                                                                              .create
                                                                                              .content
                                                                                              .logistics
                                                                                              .funnel
                                                                                              .FunnelBlock
                                                                                          funnel
                                                                                  ? new com.simibubi
                                                                                      .create
                                                                                      .content
                                                                                      .logistics
                                                                                      .funnel
                                                                                      .FunnelItem(
                                                                                      funnel,
                                                                                      new Item
                                                                                              .Properties()
                                                                                          .setId(
                                                                                              itemKey)
                                                                                          .useBlockDescriptionPrefix())
                                                                                  : block
                                                                                          instanceof
                                                                                          com
                                                                                                  .simibubi
                                                                                                  .create
                                                                                                  .content
                                                                                                  .kinetics
                                                                                                  .saw
                                                                                                  .SawBlock
                                                                                              saw
                                                                                      ? new com
                                                                                          .simibubi
                                                                                          .create
                                                                                          .content
                                                                                          .kinetics
                                                                                          .saw
                                                                                          .SawBlockItem(
                                                                                          saw,
                                                                                          new Item
                                                                                                  .Properties()
                                                                                              .setId(
                                                                                                  itemKey)
                                                                                              .useBlockDescriptionPrefix())
                                                                                      : block
                                                                                              instanceof
                                                                                              com
                                                                                                  .simibubi
                                                                                                  .create
                                                                                                  .content
                                                                                                  .fluids
                                                                                                  .tank
                                                                                                  .FluidTankBlock
                                                                                          ? new com
                                                                                              .simibubi
                                                                                              .create
                                                                                              .content
                                                                                              .fluids
                                                                                              .tank
                                                                                              .FluidTankItem(
                                                                                              block,
                                                                                              new Item
                                                                                                      .Properties()
                                                                                                  .setId(
                                                                                                      itemKey)
                                                                                                  .useBlockDescriptionPrefix()
                                                                                                  .rarity(
                                                                                                      ((com
                                                                                                                      .simibubi
                                                                                                                      .create
                                                                                                                      .content
                                                                                                                      .fluids
                                                                                                                      .tank
                                                                                                                      .FluidTankBlock)
                                                                                                                  block)
                                                                                                              .creative
                                                                                                          ? net
                                                                                                              .minecraft
                                                                                                              .world
                                                                                                              .item
                                                                                                              .Rarity
                                                                                                              .EPIC
                                                                                                          : net
                                                                                                              .minecraft
                                                                                                              .world
                                                                                                              .item
                                                                                                              .Rarity
                                                                                                              .COMMON))
                                                                                          : new BlockItem(
                                                                                              block,
                                                                                              new Item
                                                                                                      .Properties()
                                                                                                  .setId(
                                                                                                      itemKey)
                                                                                                  .useBlockDescriptionPrefix()));
    }
    return block;
  }

  public static final com.simibubi.create.foundation.block.CopperBlockSet COPPER_SHINGLES=new com.simibubi.create.foundation.block.CopperBlockSet("copper_shingles","copper_roof_top"),COPPER_TILES=new com.simibubi.create.foundation.block.CopperBlockSet("copper_tiles","copper_roof_top");
  public static final Block WEATHERED_IRON_BLOCK=register("weathered_iron_block",Block::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).mapColor(MapColor.COLOR_GRAY).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops());

  public static final com.simibubi.create.content.logistics.itemHatch.ItemHatchBlock ITEM_HATCH=register("item_hatch",com.simibubi.create.content.logistics.itemHatch.ItemHatchBlock::new,true,BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.GOLD_BLOCK).mapColor(MapColor.TERRACOTTA_BLUE).sound(SoundType.NETHERITE_BLOCK));

  public static void register() {
    com.simibubi.create.content.decoration.palettes.AllPaletteBlocks.register();
    BlockStressValues.registerImpact(CUCKOO_CLOCK,1);BlockStressValues.registerImpact(MYSTERIOUS_CUCKOO_CLOCK,1);
    BlockStressValues.registerImpact(COPPER_BACKTANK,4);BlockStressValues.registerImpact(NETHERITE_BACKTANK,4);
    com.simibubi.create.content.decoration.palettes.AllPaletteStoneTypes.register();
    BlockStressValues.registerImpact(DISPLAY_BOARD, 0);
    BlockStressValues.registerImpact(MECHANICAL_BEARING, 4);
    BlockStressValues.registerImpact(MECHANICAL_PISTON, 4);
    BlockStressValues.registerImpact(STICKY_MECHANICAL_PISTON, 4);
    BlockStressValues.registerImpact(SEQUENCED_GEARSHIFT, 0);
    BlockStressValues.registerImpact(ROPE_PULLEY, 4);
    BlockStressValues.registerImpact(ELEVATOR_PULLEY, 4);
    BlockStressValues.registerImpact(GANTRY_SHAFT, 0);
    BlockStressValues.registerImpact(GANTRY_CARRIAGE, 0);
    BlockStressValues.registerImpact(CLOCKWORK_BEARING, 4);
    BlockStressValues.registerCapacity(WINDMILL_BEARING, 512);
    AllFluids.HONEY_FAMILY.block = HONEY;
    AllFluids.CHOCOLATE_FAMILY.block = CHOCOLATE;
    BlockStressValues.registerCapacity(CREATIVE_MOTOR, 16384);
    BlockStressValues.registerCapacity(HAND_CRANK, 8);
    BlockStressValues.registerCapacity(COPPER_VALVE_HANDLE, 8);
    DYED_VALVE_HANDLES.values().forEach(block -> BlockStressValues.registerCapacity(block,8));
    BlockStressValues.registerImpact(FLYWHEEL,0);
    BlockStressValues.registerImpact(TURNTABLE,4);
    com.simibubi.create.content.decoration.encasing.EncasingRegistry.addVariant(SHAFT,ANDESITE_ENCASED_SHAFT);
    com.simibubi.create.content.decoration.encasing.EncasingRegistry.addVariant(SHAFT,BRASS_ENCASED_SHAFT);
    com.simibubi.create.content.decoration.encasing.EncasingRegistry.addVariant(COGWHEEL,ANDESITE_ENCASED_COGWHEEL);
    com.simibubi.create.content.decoration.encasing.EncasingRegistry.addVariant(COGWHEEL,BRASS_ENCASED_COGWHEEL);
    com.simibubi.create.content.decoration.encasing.EncasingRegistry.addVariant(LARGE_COGWHEEL,ANDESITE_ENCASED_LARGE_COGWHEEL);
    com.simibubi.create.content.decoration.encasing.EncasingRegistry.addVariant(LARGE_COGWHEEL,BRASS_ENCASED_LARGE_COGWHEEL);
    BlockStressValues.registerImpact(MILLSTONE, 4);
    BlockStressValues.registerImpact(MECHANICAL_PRESS, 8);
    BlockStressValues.registerCapacity(WATER_WHEEL, 32);
    BlockStressValues.registerCapacity(LARGE_WATER_WHEEL, 128);
    BlockStressValues.registerImpact(MECHANICAL_MIXER, 4);
    BlockStressValues.registerImpact(ENCASED_FAN, 2);
    BlockStressValues.registerImpact(CRUSHING_WHEEL, 8);
    BlockStressValues.registerImpact(MECHANICAL_CRAFTER, 2);
    BlockStressValues.registerImpact(MECHANICAL_PUMP, 4);
    BlockStressValues.registerImpact(HOSE_PULLEY, 4);
    BlockStressValues.registerCapacity(STEAM_ENGINE, 1024);
    BlockStressValues.registerImpact(MECHANICAL_DRILL, 4);
    BlockStressValues.registerImpact(MECHANICAL_SAW, 4);
    BlockStressValues.registerImpact(DEPLOYER, 4);
    BlockStressValues.registerImpact(MECHANICAL_ARM,2);
    BlockStressValues.registerImpact(WEIGHTED_EJECTOR,2);
    // Exact pinned source tooltip metadata; actual generator behavior remains in its native owner.
    BlockStressValues.setGeneratorSpeed(256,true).accept(CREATIVE_MOTOR);
    BlockStressValues.setGeneratorSpeed(8).accept(WATER_WHEEL);
    BlockStressValues.setGeneratorSpeed(4).accept(LARGE_WATER_WHEEL);
    BlockStressValues.setGeneratorSpeed(32).accept(HAND_CRANK);
    BlockStressValues.setGeneratorSpeed(32).accept(COPPER_VALVE_HANDLE);
    DYED_VALVE_HANDLES.values().forEach(BlockStressValues.setGeneratorSpeed(32));
    BlockStressValues.setGeneratorSpeed(64,true).accept(STEAM_ENGINE);
    BlockStressValues.setGeneratorSpeed(16,true).accept(WINDMILL_BEARING);
    Registry.register(
        BuiltInRegistries.CREATIVE_MODE_TAB,
        Create.asResource("base"),
        FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.create.base"))
            .icon(() -> new ItemStack(COGWHEEL))
            .displayItems(
                (context, output) -> {
                  output.accept(AllItems.EMPTY_SCHEMATIC);
                  output.accept(AllItems.SCHEMATIC_AND_QUILL);
                  output.accept(SCHEMATIC_TABLE);
                  output.accept(SCHEMATICANNON);
                  output.accept(CLIPBOARD);
                  output.accept(MECHANICAL_BEARING);
                  output.accept(CONTRAPTION_CONTROLS);
                  output.accept(TRACK_OBSERVER);
                  output.accept(ANDESITE_DOOR);
                  output.accept(BRASS_DOOR);
                  output.accept(COPPER_DOOR);
                  output.accept(TRAIN_DOOR);
                  output.accept(FRAMED_GLASS_DOOR);
                  output.accept(COPYCAT_PANEL);
                  output.accept(COPYCAT_STEP);
                  output.accept(ROSE_QUARTZ_LAMP);
                  POSTBOXES.values().forEach(output::accept);
                  output.accept(AllItems.WAND_OF_SYMMETRY);
                  output.accept(AllItems.WORLDSHAPER);
                  for (var item : java.util.List.of(AllItems.COPPER_BACKTANK, AllItems.NETHERITE_BACKTANK)) {
                    var stack = new ItemStack(item);
                    stack.set(AllDataComponents.BACKTANK_AIR, com.simibubi.create.content.equipment.armor.BacktankUtil.maxAirWithoutEnchants());
                    output.accept(stack);
                  }
                  output.accept(AllItems.COPPER_DIVING_HELMET);
                  output.accept(AllItems.NETHERITE_DIVING_HELMET);
                  output.accept(AllItems.COPPER_DIVING_BOOTS);
                  output.accept(AllItems.NETHERITE_DIVING_BOOTS);
                  output.accept(AllItems.CARDBOARD_HELMET);
                  output.accept(AllItems.CARDBOARD_CHESTPLATE);
                  output.accept(AllItems.CARDBOARD_LEGGINGS);
                  output.accept(AllItems.CARDBOARD_BOOTS);
                  output.accept(METAL_GIRDER);
                  output.accept(ORANGE_NIXIE_TUBE);
                  output.accept(PLACARD);
                  output.accept(DISPLAY_BOARD);
                  output.accept(CUCKOO_CLOCK);output.accept(MYSTERIOUS_CUCKOO_CLOCK);
                  output.accept(DISPLAY_LINK);
                  output.accept(TRAIN_CONTROLS);
                  output.accept(TRACK);
                  output.accept(TRACK_STATION);
                  output.accept(TRACK_SIGNAL);
                  output.accept(RAILWAY_CASING);
                  output.accept(FRAMED_GLASS);
                  output.accept(CART_ASSEMBLER);output.accept(CONTROLLER_RAIL);output.accept(AllItems.MINECART_COUPLING);
                  output.accept(ROPE_PULLEY);
                  output.accept(ELEVATOR_PULLEY);
                  output.accept(ELEVATOR_CONTACT);
                  output.accept(GANTRY_SHAFT);
                  output.accept(GANTRY_CARRIAGE);
                  output.accept(PULSE_EXTENDER);
                  output.accept(PULSE_REPEATER);
                  output.accept(PULSE_TIMER);
                  output.accept(POWERED_LATCH);
                  output.accept(POWERED_TOGGLE_LATCH);
                  output.accept(ANALOG_LEVER);
                  output.accept(MECHANICAL_PISTON);
                  output.accept(STICKY_MECHANICAL_PISTON);
                  output.accept(PISTON_EXTENSION_POLE);
                  output.accept(SEQUENCED_GEARSHIFT);
                  output.accept(WINDMILL_BEARING);
                  output.accept(CLOCKWORK_BEARING);
                  output.accept(SAIL_FRAME);
                  output.accept(SAIL);
                  output.accept(LINEAR_CHASSIS);
                  output.accept(SECONDARY_LINEAR_CHASSIS);
                  output.accept(RADIAL_CHASSIS);
                  output.accept(STICKER);
                  output.accept(REDSTONE_LINK);
                  output.accept(AllItems.LINKED_CONTROLLER);output.accept(AllItems.SCHEDULE);
                  output.accept(AllItems.POTATO_CANNON);output.accept(AllItems.EXTENDO_GRIP);
                  for (var cloth : tableCloths()) output.accept(cloth);
                  output.accept(AllItems.SHOPPING_LIST);
                  SEATS.values().forEach(output::accept);
                  output.accept(PACKAGE_FROGPORT);
                  output.accept(CHAIN_CONVEYOR);
                  output.accept(STOCK_TICKER);
                  output.accept(REDSTONE_REQUESTER);
                  output.accept(FACTORY_GAUGE);
                  output.accept(PECULIAR_BELL);
                  output.accept(HAUNTED_BELL);
                  TOOLBOXES.values().forEach(output::accept);
                  output.accept(DESK_BELL);
                  output.accept(REDSTONE_CONTACT);
                  output.accept(SHAFT);
                  output.accept(COGWHEEL);
                  output.accept(LARGE_COGWHEEL);
                  output.accept(CREATIVE_MOTOR);
                  output.accept(HAND_CRANK);
                  output.accept(COPPER_VALVE_HANDLE);
                  DYED_VALVE_HANDLES.values().forEach(block -> output.accept(block,net.minecraft.world.item.CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY));
                  output.accept(NOZZLE); output.accept(TURNTABLE); output.accept(FLYWHEEL);
                  output.accept(DEPLOYER);
                  output.accept(MECHANICAL_ARM);
                  output.accept(MECHANICAL_ROLLER);
                  output.accept(PORTABLE_STORAGE_INTERFACE);
                  output.accept(PORTABLE_FLUID_INTERFACE);
                  output.accept(MECHANICAL_HARVESTER);
                  output.accept(MECHANICAL_PLOUGH);
                  output.accept(AllItems.BRASS_HAND);
                  output.accept(AllItems.STURDY_SHEET);
                  output.accept(ROTATION_SPEED_CONTROLLER);
                  output.accept(AllItems.PRECISION_MECHANISM);
                  output.accept(SPEEDOMETER);
                  output.accept(STRESSOMETER);
                  output.accept(GEARSHIFT);
                  output.accept(CLUTCH);
                  output.accept(GEARBOX);
                  output.accept(MILLSTONE);
                  output.accept(ANDESITE_CASING);
                  output.accept(BRASS_CASING);
                  output.accept(COPPER_CASING);
                  output.accept(ENCASED_CHAIN_DRIVE);
                  output.accept(ADJUSTABLE_CHAIN_GEARSHIFT);
                  output.accept(MECHANICAL_PRESS);
                  output.accept(DEPOT);
                  output.accept(WEIGHTED_EJECTOR);
                  output.accept(ANDESITE_FUNNEL);
                  output.accept(BRASS_FUNNEL);
                  output.accept(ANDESITE_TUNNEL);
                  output.accept(BRASS_TUNNEL);
                  output.accept(CHUTE);
                  output.accept(SMART_CHUTE);
                  output.accept(INDUSTRIAL_IRON_BLOCK);
                  output.accept(SMART_OBSERVER);
                  output.accept(THRESHOLD_SWITCH);
                  output.accept(PACKAGER);
                  output.accept(REPACKAGER);
                  output.accept(STOCK_LINK);
                  output.accept(AllItems.TRANSMITTER);
                  output.accept(CARDBOARD_BLOCK);
                  output.accept(ANDESITE_ALLOY_BLOCK);
                  output.accept(BOUND_CARDBOARD_BLOCK);
                  output.accept(EXPERIENCE_BLOCK);
                  output.accept(ROSE_QUARTZ_BLOCK);
                  output.accept(ROSE_QUARTZ_TILES);
                  output.accept(SMALL_ROSE_QUARTZ_TILES);
                  output.accept(WOODEN_BRACKET);
                  output.accept(METAL_BRACKET);
                  output.accept(TRAIN_TRAPDOOR);
                  output.accept(FRAMED_GLASS_TRAPDOOR);
                  output.accept(ANDESITE_LADDER);
                  output.accept(ANDESITE_BARS);
                  output.accept(ANDESITE_SCAFFOLD);
                  output.accept(BRASS_LADDER);
                  output.accept(BRASS_BARS);
                  output.accept(BRASS_SCAFFOLD);
                  output.accept(COPPER_LADDER);
                  output.accept(COPPER_BARS);
                  output.accept(COPPER_SCAFFOLD);

                  output.accept(ITEM_VAULT);
                  output.accept(CREATIVE_CRATE);
                  output.accept(WATER_WHEEL);
                  output.accept(LARGE_WATER_WHEEL);
                  output.accept(BASIN);
                  output.accept(MECHANICAL_MIXER);
                  output.accept(AllItems.WHISK);
                  output.accept(AllItems.DOUGH);
                  output.accept(AllItems.BLAZE_BURNER);
                  output.accept(AllItems.EMPTY_BLAZE_BURNER);
                  output.accept(AllItems.BLAZE_CAKE);
                  output.accept(AllItems.CREATIVE_BLAZE_CAKE);
                  output.accept(AllItems.ZINC_INGOT);
                  output.accept(AllItems.ZINC_NUGGET);
                  output.accept(AllItems.BRASS_NUGGET);
                  output.accept(AllItems.RAW_ZINC);
                  output.accept(ZINC_ORE);
                  output.accept(DEEPSLATE_ZINC_ORE);
                  output.accept(ZINC_BLOCK);
                  output.accept(RAW_ZINC_BLOCK);
                  output.accept(BRASS_BLOCK);
                  output.accept(ENCASED_FAN);
                  output.accept(AllItems.PROPELLER);
                  output.accept(AllItems.BELT_CONNECTOR);
                  output.accept(CRUSHING_WHEEL);
                  output.accept(MECHANICAL_CRAFTER);
                  output.accept(AllItems.CRAFTER_SLOT_COVER);
                  output.accept(MECHANICAL_DRILL);
                  output.accept(MECHANICAL_SAW);
                  output.accept(FLUID_TANK);
                  output.accept(CREATIVE_FLUID_TANK);
                  output.accept(FLUID_PIPE);
                  output.accept(MECHANICAL_PUMP);
                  output.accept(SMART_FLUID_PIPE);
                  output.accept(FLUID_VALVE);
                  output.accept(SPOUT);
                  output.accept(ITEM_DRAIN);
                  output.accept(HOSE_PULLEY);
                  output.accept(STEAM_ENGINE);
                  output.accept(STEAM_WHISTLE);
                  output.accept(AllItems.HONEY_BUCKET);
                  output.accept(AllItems.CHOCOLATE_BUCKET);
                  output.accept(AllItems.BAR_OF_CHOCOLATE);
                  output.accept(AllItems.SWEET_ROLL);
                  output.accept(AllItems.CHOCOLATE_BERRIES);
                  output.accept(AllItems.HONEYED_APPLE);
                  output.accept(AllItems.BUILDERS_TEA);
                  output.accept(AllItems.BLAZE_CAKE_BASE);
                  output.accept(ASURINE);
                  output.accept(CRIMSITE);
                  output.accept(OCHRUM);
                  output.accept(VERIDIUM);
                  output.accept(AllItems.CRUSHED_IRON);
                  output.accept(AllItems.CRUSHED_GOLD);
                  output.accept(AllItems.CRUSHED_COPPER);
                  output.accept(AllItems.CRUSHED_ZINC);
                  AllItems.COMPAT_CRUSHED_ORES.stream().filter(item -> !item.shouldHide()).forEach(output::accept);
                  output.accept(AllItems.COPPER_NUGGET);
                  output.accept(AllItems.EXP_NUGGET);
                  output.accept(AllItems.POWDERED_OBSIDIAN);
                  output.accept(AllItems.CINDER_FLOUR);
                  output.accept(AllItems.WHEAT_FLOUR);
                  output.accept(AllItems.VERTICAL_GEARBOX);
                  output.accept(AllItems.WRENCH);
                  output.accept(AllItems.GOGGLES);
                  output.accept(AllItems.FILTER);
                  output.accept(AllItems.ATTRIBUTE_FILTER);
                  output.accept(AllItems.PACKAGE_FILTER);
                  com.simibubi.create.content.logistics.box.PackageStyles.ALL_BOXES.forEach(
                      output::accept);
                  output.accept(AllItems.GOLDEN_SHEET);
                  output.accept(AllItems.IRON_SHEET);
                  output.accept(AllItems.COPPER_SHEET);
                  output.accept(AllItems.BRASS_SHEET);
                  output.accept(AllItems.BRASS_INGOT);
                  output.accept(AllItems.ELECTRON_TUBE);
                  output.accept(AllItems.POLISHED_ROSE_QUARTZ);
                  output.accept(AllItems.ROSE_QUARTZ);
                  output.accept(AllItems.PULP);
                  output.accept(AllItems.CARDBOARD);
                  output.accept(AllItems.SAND_PAPER);
                  output.accept(AllItems.RED_SAND_PAPER);
                  output.accept(AllItems.ANDESITE_ALLOY);
                  output.accept(AllItems.SUPER_GLUE);
                })
            .build());
  }

  private AllBlocks() {}
}
