package com.simibubi.create;

import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.model.loading.v1.ExtraModelKey;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.SimpleUnbakedExtraModel;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.RandomSource;
import org.joml.Matrix4f;

/** Standalone models are baked on every resource reload through Fabric's model API. */
public final class AllPartialModels {

  private static final Map<String, ExtraModelKey<BlockStateModel>> MODELS = new LinkedHashMap<>();
  public static final ExtraModelKey<BlockStateModel> EJECTOR_TOP=partial("weighted_ejector/top");
 public static final ExtraModelKey<BlockStateModel> CUCKOO_HOUR_HAND=partial("cuckoo_clock/hour_hand"),CUCKOO_MINUTE_HAND=partial("cuckoo_clock/minute_hand"),CUCKOO_LEFT_DOOR=partial("cuckoo_clock/left_door"),CUCKOO_RIGHT_DOOR=partial("cuckoo_clock/right_door"),CUCKOO_PIG=partial("cuckoo_clock/pig"),CUCKOO_CREEPER=partial("cuckoo_clock/creeper");

  public static final ExtraModelKey<BlockStateModel> COPPER_BACKTANK_BODY=partial("copper_backtank/block"),COPPER_BACKTANK_COGS=partial("copper_backtank/block_cogs"),COPPER_BACKTANK_SHAFT=partial("copper_backtank/block_shaft_input"),NETHERITE_BACKTANK_BODY=partial("netherite_backtank/block"),NETHERITE_BACKTANK_COGS=partial("netherite_backtank/block_cogs"),NETHERITE_BACKTANK_SHAFT=partial("netherite_backtank/block_shaft_input");
  public static final ExtraModelKey<BlockStateModel> TOOLBOX_DRAWER=partial("toolbox/drawer");
  public static final Map<net.minecraft.world.item.DyeColor,ExtraModelKey<BlockStateModel>> TOOLBOX_LIDS=toolboxModels(true),TOOLBOX_BODIES=toolboxModels(false);
  private static Map<net.minecraft.world.item.DyeColor,ExtraModelKey<BlockStateModel>> toolboxModels(boolean lid){var result=new java.util.EnumMap<net.minecraft.world.item.DyeColor,ExtraModelKey<BlockStateModel>>(net.minecraft.world.item.DyeColor.class);for(var color:net.minecraft.world.item.DyeColor.values())result.put(color,partial(lid?"toolbox/lid/"+color.getSerializedName():color.getSerializedName()+"_toolbox"));return java.util.Map.copyOf(result);}

  public static final ExtraModelKey<BlockStateModel>
      TRAIN_CONTROLS_COVER = partial("controls/train/cover"),
      TRAIN_CONTROLS_LEVER = partial("controls/train/lever");
  public static final ExtraModelKey<BlockStateModel> BOGEY_FRAME =
      partial("track/bogey/bogey_frame");
  public static final ExtraModelKey<BlockStateModel> SMALL_BOGEY_WHEELS =
      partial("track/bogey/bogey_wheel");
  public static final ExtraModelKey<BlockStateModel> BOGEY_PIN =
      partial("track/bogey/bogey_drive_wheel_pin");
  public static final ExtraModelKey<BlockStateModel> BOGEY_PISTON =
      partial("track/bogey/bogey_drive_piston");
  public static final ExtraModelKey<BlockStateModel> BOGEY_DRIVE =
      partial("track/bogey/bogey_drive");
  public static final ExtraModelKey<BlockStateModel> LARGE_BOGEY_WHEELS =
      partial("track/bogey/bogey_drive_wheel");
  public static final ExtraModelKey<BlockStateModel> BOGEY_DRIVE_BELT =
      partial("track/bogey/bogey_drive_belt");
  public static final ExtraModelKey<BlockStateModel> ROPE_COIL = partial("rope_pulley/rope_coil");
  public static final ExtraModelKey<BlockStateModel> ROPE_HALF = partial("rope_pulley/rope_half");
  public static final ExtraModelKey<BlockStateModel> ROPE_HALF_MAGNET =
      partial("rope_pulley/rope_half_magnet");
  public static final ExtraModelKey<BlockStateModel> ELEVATOR_COIL =
      partial("elevator_pulley/rope_coil");
  public static final ExtraModelKey<BlockStateModel> ELEVATOR_MAGNET =
      partial("elevator_pulley/pulley_magnet");
  public static final ExtraModelKey<BlockStateModel> ELEVATOR_BELT =
      partial("elevator_pulley/rope");
  public static final ExtraModelKey<BlockStateModel> ELEVATOR_BELT_HALF =
      partial("elevator_pulley/rope_half");
  public static final ExtraModelKey<BlockStateModel> FLEXPEATER_INDICATOR =
      partial("diodes/indicator");
  public static final ExtraModelKey<BlockStateModel> ANALOG_LEVER_HANDLE =
      partial("analog_lever/handle");
  public static final ExtraModelKey<BlockStateModel> ANALOG_LEVER_INDICATOR =
      partial("analog_lever/indicator");

  public static final ExtraModelKey<BlockStateModel> CONTRAPTION_CONTROLS_BUTTON =
      partial("contraption_controls/button");
  public static final java.util.List<ExtraModelKey<BlockStateModel>>
      CONTRAPTION_CONTROLS_INDICATOR =
          java.util.stream.IntStream.range(0, 8)
              .mapToObj(i -> partial("contraption_controls/indicator_" + i))
              .toList();
  private static final Map<String, ExtraModelKey<BlockStateModel>> ENTITIES = new LinkedHashMap<>();
  public static final ExtraModelKey<BlockStateModel> PECULIAR_BELL = partial("peculiar_bell"),
      HAUNTED_BELL = partial("haunted_bell"),
      DESK_BELL_PLUNGER = partial("desk_bell/plunger"),
      DESK_BELL_BELL = partial("desk_bell/bell");
  public static final ExtraModelKey<BlockStateModel> BEARING_TOP = partial("bearing/top"),
      BEARING_TOP_WOODEN = partial("bearing/top_wooden");

  private record CachedMesh(BlockStateModel model, Mesh mesh) {}

  private static final Map<Object, CachedMesh> MESHES = new LinkedHashMap<>();
  public static final ExtraModelKey<BlockStateModel>
      TABLE_CLOTH_PRICE_SIDE = partial("table_cloth/price_tag_side"),
      TABLE_CLOTH_PRICE_TOP = partial("table_cloth/price_tag_top");
  public static final ExtraModelKey<BlockStateModel>
      CHAIN_CONVEYOR_WHEEL = partial("chain_conveyor/wheel"),
      CHAIN_CONVEYOR_SHAFT = partial("chain_conveyor/shaft"),
      CHAIN_CONVEYOR_GUARD = partial("chain_conveyor/guard");
  public static final ExtraModelKey<BlockStateModel>
      FROGPORT_BODY = partial("package_frogport/body"),
      FROGPORT_HEAD = partial("package_frogport/head"),
      FROGPORT_HEAD_GOGGLES = partial("package_frogport/head_goggles"),
      FROGPORT_TONGUE = partial("package_frogport/tongue");
  public static final ExtraModelKey<BlockStateModel> FACTORY_PANEL = partial("factory_gauge/panel"),
      FACTORY_PANEL_WITH_BULB = partial("factory_gauge/panel_with_bulb"),
      FACTORY_PANEL_RESTOCKER = partial("factory_gauge/panel_restocker"),
      FACTORY_PANEL_RESTOCKER_WITH_BULB = partial("factory_gauge/panel_restocker_with_bulb"),
      FACTORY_PANEL_LIGHT = partial("factory_gauge/bulb_light"),
      FACTORY_PANEL_RED_LIGHT = partial("factory_gauge/bulb_red");
  public static final Map<net.minecraft.core.Direction, ExtraModelKey<BlockStateModel>>
      FACTORY_PANEL_ARROWS = new java.util.EnumMap<>(net.minecraft.core.Direction.class),
      FACTORY_PANEL_LINES = new java.util.EnumMap<>(net.minecraft.core.Direction.class);
  public static final Map<net.minecraft.core.Direction, ExtraModelKey<BlockStateModel>>
      FACTORY_PANEL_ANIMATED_ARROWS = new java.util.EnumMap<>(net.minecraft.core.Direction.class),
      FACTORY_PANEL_ANIMATED_LINES = new java.util.EnumMap<>(net.minecraft.core.Direction.class);
  public static final ExtraModelKey<BlockStateModel>
      DISPLAY_LINK_TUBE = partial("display_link/tube"),
      DISPLAY_LINK_GLOW = partial("display_link/glow");
  public static final ExtraModelKey<BlockStateModel> PACKAGER_TRAY = partial("packager/tray"),
      REPACKAGER_TRAY = partial("repackager/tray"),
      PACKAGER_HATCH_OPEN = partial("packager/hatch_open"),
      PACKAGER_HATCH_CLOSED = partial("packager/hatch_closed");
  public static final ExtraModelKey<BlockStateModel> BELT_TUNNEL_FLAP = partial("belt_tunnel/flap");
  public static final ExtraModelKey<BlockStateModel> FUNNEL_FLAP = partial("funnel/flap"),
      BELT_FUNNEL_FLAP = partial("belt_funnel/flap");
  public static final ExtraModelKey<BlockStateModel> SHAFT_HALF = partial("shaft_half");
  public static final ExtraModelKey<BlockStateModel> SPEED_CONTROLLER_BRACKET =
      partial("rotation_speed_controller/bracket");
  public static final ExtraModelKey<BlockStateModel> ARM_COG=partial("mechanical_arm/cog"),ARM_BASE=partial("mechanical_arm/base"),ARM_LOWER_BODY=partial("mechanical_arm/lower_body"),ARM_UPPER_BODY=partial("mechanical_arm/upper_body"),ARM_CLAW_BASE=partial("mechanical_arm/claw_base"),ARM_CLAW_BASE_GOGGLES=partial("mechanical_arm/claw_base_goggles"),ARM_CLAW_GRIP_UPPER=partial("mechanical_arm/upper_claw_grip"),ARM_CLAW_GRIP_LOWER=partial("mechanical_arm/lower_claw_grip");
  public static final ExtraModelKey<BlockStateModel> DEPLOYER_POLE = partial("deployer/pole"),
      DEPLOYER_HAND_POINTING = partial("deployer/hand_pointing"),
      DEPLOYER_HAND_HOLDING = partial("deployer/hand_holding"),
      DEPLOYER_HAND_PUNCHING = partial("deployer/hand_punching");
  public static final ExtraModelKey<BlockStateModel>
      GAUGE_HEAD_SPEED = partial("gauge/speedometer/head"),
      GAUGE_HEAD_STRESS = partial("gauge/stressometer/head"),
      GAUGE_DIAL = partial("gauge/dial");
  public static final ExtraModelKey<BlockStateModel>
      WHISTLE_MOUTH_SMALL = partial("steam_whistle/small_mouth"),
      WHISTLE_MOUTH_MEDIUM = partial("steam_whistle/medium_mouth"),
      WHISTLE_MOUTH_LARGE = partial("steam_whistle/large_mouth");
  public static final ExtraModelKey<BlockStateModel> ENGINE_PISTON = partial("steam_engine/piston"),
      ENGINE_LINKAGE = partial("steam_engine/linkage"),
      ENGINE_CONNECTOR = partial("steam_engine/shaft_connector"),
      BOILER_GAUGE = partial("steam_engine/gauge"),
      BOILER_GAUGE_DIAL = partial("steam_engine/gauge_dial");
  public static final ExtraModelKey<BlockStateModel>
      PORTABLE_STORAGE_INTERFACE_MIDDLE = partial("portable_storage_interface/block_middle"),
      PORTABLE_STORAGE_INTERFACE_MIDDLE_POWERED =
          partial("portable_storage_interface/block_middle_powered"),
      PORTABLE_STORAGE_INTERFACE_TOP = partial("portable_storage_interface/block_top");
  public static final ExtraModelKey<BlockStateModel>
      PORTABLE_FLUID_INTERFACE_MIDDLE = partial("portable_fluid_interface/block_middle"),
      PORTABLE_FLUID_INTERFACE_MIDDLE_POWERED =
          partial("portable_fluid_interface/block_middle_powered"),
      PORTABLE_FLUID_INTERFACE_TOP = partial("portable_fluid_interface/block_top");
  public static final ExtraModelKey<BlockStateModel>
      ROLLER_WHEEL = partial("mechanical_roller/wheel"),
      ROLLER_FRAME = partial("mechanical_roller/frame");
  public static final java.util.List<ExtraModelKey<BlockStateModel>> ROLLER_MODE_ICONS =
      java.util.List.of(
          partial("mechanical_roller/mode_tunnel_pave"),
          partial("mechanical_roller/mode_straight_fill"),
          partial("mechanical_roller/mode_wide_fill"));
  public static final ExtraModelKey<BlockStateModel> HARVESTER_BLADE =
      partial("mechanical_harvester/blade");
  public static final ExtraModelKey<BlockStateModel> DRILL_HEAD = partial("mechanical_drill/head");
  public static final ExtraModelKey<BlockStateModel> FLUID_VALVE_POINTER =
      partial("fluid_valve/pointer");
  public static final ExtraModelKey<BlockStateModel> SPOUT_TOP = partial("spout/top"),
      SPOUT_MIDDLE = partial("spout/middle"),
      SPOUT_BOTTOM = partial("spout/bottom");
  public static final ExtraModelKey<BlockStateModel> HOSE_COIL = partial("hose_pulley/hose_coil"),
      HOSE = partial("hose_pulley/rope"),
      HOSE_HALF = partial("hose_pulley/rope_half"),
      HOSE_MAGNET = partial("hose_pulley/pulley_magnet"),
      HOSE_HALF_MAGNET = partial("hose_pulley/rope_half_magnet");
  public static final ExtraModelKey<BlockStateModel> MECHANICAL_PUMP_COG =
      partial("mechanical_pump/cog");
  public static final ExtraModelKey<BlockStateModel> FLUID_PIPE_CASING =
      partial("fluid_pipe/casing");
  public static final Map<String, Map<net.minecraft.core.Direction, ExtraModelKey<BlockStateModel>>>
      PIPE_ATTACHMENTS = new LinkedHashMap<>();
  public static final ExtraModelKey<BlockStateModel> SAW_HORIZONTAL_ACTIVE =
      partial("mechanical_saw/blade_horizontal_active");
  public static final ExtraModelKey<BlockStateModel> SAW_HORIZONTAL_INACTIVE =
      partial("mechanical_saw/blade_horizontal_inactive");
  public static final ExtraModelKey<BlockStateModel> SAW_HORIZONTAL_REVERSED =
      partial("mechanical_saw/blade_horizontal_reversed");
  public static final ExtraModelKey<BlockStateModel> SAW_VERTICAL_ACTIVE =
      partial("mechanical_saw/blade_vertical_active");
  public static final ExtraModelKey<BlockStateModel> SAW_VERTICAL_INACTIVE =
      partial("mechanical_saw/blade_vertical_inactive");
  public static final ExtraModelKey<BlockStateModel> SAW_VERTICAL_REVERSED =
      partial("mechanical_saw/blade_vertical_reversed");
  public static final ExtraModelKey<BlockStateModel> HAND_CRANK_HANDLE =
      partial("hand_crank/handle");
  public static final ExtraModelKey<BlockStateModel> MILLSTONE_COG = partial("millstone/inner");
  public static final ExtraModelKey<BlockStateModel> MECHANICAL_PRESS_HEAD =
      partial("mechanical_press/head");
  public static final ExtraModelKey<BlockStateModel> SHAFTLESS_LARGE_COGWHEEL = partial("large_cogwheel_shaftless");
  public static final ExtraModelKey<BlockStateModel> SHAFTLESS_COGWHEEL =
      partial("cogwheel_shaftless");
  public static final ExtraModelKey<BlockStateModel> MIXER_POLE = partial("mechanical_mixer/pole");
  public static final ExtraModelKey<BlockStateModel> MIXER_HEAD = partial("mechanical_mixer/head");
  public static final ExtraModelKey<BlockStateModel> BLAZE_CAGE = partial("blaze_burner/block");
  public static final ExtraModelKey<BlockStateModel> BLAZE_INERT =
      partial("blaze_burner/blaze/inert");
  public static final ExtraModelKey<BlockStateModel>
      BLAZE_GOGGLES = partial("blaze_burner/goggles"),
      BLAZE_GOGGLES_SMALL = partial("blaze_burner/goggles_small");
  public static final ExtraModelKey<BlockStateModel> BLAZE_IDLE =
      partial("blaze_burner/blaze/idle");
  public static final ExtraModelKey<BlockStateModel> BLAZE_ACTIVE =
      partial("blaze_burner/blaze/active");
  public static final ExtraModelKey<BlockStateModel> BLAZE_SUPER =
      partial("blaze_burner/blaze/super");
  public static final ExtraModelKey<BlockStateModel> BLAZE_SUPER_ACTIVE =
      partial("blaze_burner/blaze/super_active");
  public static final ExtraModelKey<BlockStateModel> BLAZE_RODS =
      partial("blaze_burner/rods_small");
  public static final ExtraModelKey<BlockStateModel> BLAZE_RODS_LARGE =
      partial("blaze_burner/rods_large");
  public static final ExtraModelKey<BlockStateModel> BLAZE_SUPER_RODS =
      partial("blaze_burner/superheated_rods_small");
  public static final ExtraModelKey<BlockStateModel> BLAZE_SUPER_RODS_LARGE =
      partial("blaze_burner/superheated_rods_large");
  public static final ExtraModelKey<BlockStateModel> BLAZE_FLAME = partial("blaze_burner/flame");
  public static final ExtraModelKey<BlockStateModel> FAN_PROPELLER =
      partial("encased_fan/propeller");
  public static final ExtraModelKey<BlockStateModel> CRUSHING_WHEEL =
      partial("crushing_wheel/block");
  public static final ExtraModelKey<BlockStateModel> MECHANICAL_CRAFTER_LID =
      partial("mechanical_crafter/lid");
  public static final ExtraModelKey<BlockStateModel> MECHANICAL_CRAFTER_ARROW =
      partial("mechanical_crafter/arrow");
  public static final ExtraModelKey<BlockStateModel> MECHANICAL_CRAFTER_BELT_FRAME =
      partial("mechanical_crafter/belt");
  public static final ExtraModelKey<BlockStateModel> MECHANICAL_CRAFTER_BELT =
      partial("mechanical_crafter/belt_animated");
  public static final ExtraModelKey<BlockStateModel> BELT_PULLEY = partial("belt_pulley");
  public static final ExtraModelKey<BlockStateModel> BELT_START = partial("belt/start");
  public static final ExtraModelKey<BlockStateModel> BELT_MIDDLE = partial("belt/middle");
  public static final ExtraModelKey<BlockStateModel> BELT_END = partial("belt/end");
  public static final ExtraModelKey<BlockStateModel> BELT_START_BOTTOM =
      partial("belt/start_bottom");
  public static final ExtraModelKey<BlockStateModel> BELT_MIDDLE_BOTTOM =
      partial("belt/middle_bottom");
  public static final ExtraModelKey<BlockStateModel> BELT_END_BOTTOM = partial("belt/end_bottom");
  public static final ExtraModelKey<BlockStateModel> BELT_DIAGONAL_START =
      partial("belt/diagonal_start");
  public static final ExtraModelKey<BlockStateModel> BELT_DIAGONAL_MIDDLE =
      partial("belt/diagonal_middle");
  public static final ExtraModelKey<BlockStateModel> BELT_DIAGONAL_END =
      partial("belt/diagonal_end");
  public static final ExtraModelKey<BlockStateModel> ANDESITE_BELT_COVER_X =
      partial("belt_cover/andesite_belt_cover_x");
  public static final ExtraModelKey<BlockStateModel> ANDESITE_BELT_COVER_Z =
      partial("belt_cover/andesite_belt_cover_z");
  public static final ExtraModelKey<BlockStateModel> BRASS_BELT_COVER_X =
      partial("belt_cover/brass_belt_cover_x");
  public static final ExtraModelKey<BlockStateModel> BRASS_BELT_COVER_Z =
      partial("belt_cover/brass_belt_cover_z");
  private static final Map<String, ExtraModelKey<BlockStateModel>> WATER_WHEELS =
      new LinkedHashMap<>();
  private static final Map<String, ExtraModelKey<BlockStateModel>> LARGE_WHEELS =
      new LinkedHashMap<>();
  private static final Map<String, ExtraModelKey<BlockStateModel>> LARGE_EXTENSIONS =
      new LinkedHashMap<>();

  public static final ExtraModelKey<BlockStateModel> CRAFTING_BLUEPRINT_1x1=entity("crafting_blueprint_small"),
      CRAFTING_BLUEPRINT_2x2=entity("crafting_blueprint_medium"),CRAFTING_BLUEPRINT_3x3=entity("crafting_blueprint_large");

  public static final ExtraModelKey<BlockStateModel> LOGISTICS_HAT = entity("logistics_hat");
  public static final ExtraModelKey<BlockStateModel> TRAIN_HAT = entity("train_hat");

  private static ExtraModelKey<BlockStateModel> entity(String path) {
    var key = ExtraModelKey.<BlockStateModel>create(() -> "create:entity/" + path);
    ENTITIES.put(path, key);
    return key;
  }

  private static ExtraModelKey<BlockStateModel> partial(String path) {
    var key = ExtraModelKey.<BlockStateModel>create(() -> "create:block/" + path);
    MODELS.put(path, key);
    return key;
  }

  public static void register() {
    for (var side : net.minecraft.core.Direction.Plane.HORIZONTAL) {
      FACTORY_PANEL_ARROWS.put(side, partial("factory_gauge/connections/arrow_" + side.getName()));
      FACTORY_PANEL_LINES.put(side, partial("factory_gauge/connections/line_" + side.getName()));
      FACTORY_PANEL_ANIMATED_ARROWS.put(
          side, partial("factory_gauge/connections/arrow_" + side.getName() + "_animated"));
      FACTORY_PANEL_ANIMATED_LINES.put(
          side, partial("factory_gauge/connections/line_" + side.getName() + "_animated"));
    }
    for (String kind : new String[] {"connection", "rim_connector", "rim", "drain"}) {
      var directions =
          new java.util.EnumMap<net.minecraft.core.Direction, ExtraModelKey<BlockStateModel>>(
              net.minecraft.core.Direction.class);
      for (var side : net.minecraft.core.Direction.values())
        directions.put(side, partial("fluid_pipe/" + kind + "/" + side.getName()));
      PIPE_ATTACHMENTS.put(kind, directions);
    }
    for (String wood :
        new String[] {
          "oak",
          "spruce",
          "birch",
          "jungle",
          "acacia",
          "dark_oak",
          "mangrove",
          "cherry",
          "pale_oak",
          "poplar",
          "bamboo",
          "crimson",
          "warped"
        }) {
      WATER_WHEELS.put(wood, partial("water_wheel/material/" + wood));
      LARGE_WHEELS.put(wood, partial("large_water_wheel/material/" + wood));
      LARGE_EXTENSIONS.put(wood, partial("large_water_wheel/material/" + wood + "_extension"));
    }
    ModelLoadingPlugin.register(
        context ->
            MODELS.forEach(
                (path, key) ->
                    context.addModel(
                        key,
                        SimpleUnbakedExtraModel.blockStateModel(
                            Create.asResource("block/" + path)))));
    ModelLoadingPlugin.register(
        context ->
            ENTITIES.forEach(
                (path, key) ->
                    context.addModel(
                        key,
                        SimpleUnbakedExtraModel.blockStateModel(
                            Create.asResource("entity/" + path)))));
  }

  public static ExtraModelKey<BlockStateModel> waterWheel(
      net.minecraft.world.level.block.state.BlockState material) {
    return waterWheel(material, false, false);
  }

  public static ExtraModelKey<BlockStateModel> waterWheel(
      net.minecraft.world.level.block.state.BlockState material, boolean large, boolean extension) {
    String path =
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(material.getBlock()).getPath();
    var variants = large ? extension ? LARGE_EXTENSIONS : LARGE_WHEELS : WATER_WHEELS;
    return variants.getOrDefault(path.replace("_planks", ""), variants.get("spruce"));
  }

  public static void extract(ExtraModelKey<BlockStateModel> key, BlockModelRenderState state) {
    BlockStateModel model = Minecraft.getInstance().getModelManager().getModel(key);
    if (model == null) {
      throw new IllegalStateException("Partial model has not been baked: " + key);
    }
    extract(key, model, state);
  }

  public static void extract(
      net.minecraft.world.level.block.state.BlockState block, PartialModelRenderState state) {
    extract(
        block, Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(block), state);
  }

  private static void extract(Object key, BlockStateModel model, BlockModelRenderState state) {
    state.clear();
    var parts = state.setupModel(new Matrix4f(), model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT));
    model.collectParts(RandomSource.create(42), parts);
    if (state instanceof PartialModelRenderState partial) {
      // Baked model identity changes on reload. Each key retains only its current immutable mesh.
      var cached = MESHES.get(key);
      if (cached == null || cached.model() != model) {
        var mesh = Renderer.get().mutableMesh();
        var emitter = mesh.emitter();
        parts.forEach(part -> part.emitQuads(emitter, direction -> false));
        cached = new CachedMesh(model, mesh.immutableCopy());
        MESHES.put(key, cached);
      }
      partial.mesh = cached.mesh();
      partial.translucent = model.hasMaterialFlag(BakedQuad.FLAG_TRANSLUCENT);
    }
  }

  private AllPartialModels() {}
}
