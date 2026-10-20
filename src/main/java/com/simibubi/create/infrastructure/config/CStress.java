package com.simibubi.create.infrastructure.config;

import java.util.*;
import java.util.function.DoubleSupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/** Exact pinned source v2 per-block stress defaults and real native provider values. */
public final class CStress extends NativeConfigBase {
 private final Map<Identifier,ConfigDouble> impacts=new LinkedHashMap<>(),capacities=new LinkedHashMap<>();
 public CStress(){
  impacts.put(Identifier.fromNamespaceAndPath("create","shaft"),new ConfigDouble("shaft",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","cogwheel"),new ConfigDouble("cogwheel",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","large_cogwheel"),new ConfigDouble("large_cogwheel",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","gearbox"),new ConfigDouble("gearbox",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","clutch"),new ConfigDouble("clutch",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","gearshift"),new ConfigDouble("gearshift",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","encased_chain_drive"),new ConfigDouble("encased_chain_drive",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","adjustable_chain_gearshift"),new ConfigDouble("adjustable_chain_gearshift",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","belt"),new ConfigDouble("belt",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","chain_conveyor"),new ConfigDouble("chain_conveyor",1.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","encased_fan"),new ConfigDouble("encased_fan",2.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","turntable"),new ConfigDouble("turntable",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","millstone"),new ConfigDouble("millstone",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","crushing_wheel"),new ConfigDouble("crushing_wheel",8.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mechanical_press"),new ConfigDouble("mechanical_press",8.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mechanical_mixer"),new ConfigDouble("mechanical_mixer",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","weighted_ejector"),new ConfigDouble("weighted_ejector",2.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","speedometer"),new ConfigDouble("speedometer",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","stressometer"),new ConfigDouble("stressometer",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mechanical_pump"),new ConfigDouble("mechanical_pump",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","hose_pulley"),new ConfigDouble("hose_pulley",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","gantry_shaft"),new ConfigDouble("gantry_shaft",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mechanical_bearing"),new ConfigDouble("mechanical_bearing",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","clockwork_bearing"),new ConfigDouble("clockwork_bearing",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","rope_pulley"),new ConfigDouble("rope_pulley",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","elevator_pulley"),new ConfigDouble("elevator_pulley",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mechanical_drill"),new ConfigDouble("mechanical_drill",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mechanical_saw"),new ConfigDouble("mechanical_saw",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","deployer"),new ConfigDouble("deployer",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mechanical_crafter"),new ConfigDouble("mechanical_crafter",2.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","sequenced_gearshift"),new ConfigDouble("sequenced_gearshift",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","flywheel"),new ConfigDouble("flywheel",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","rotation_speed_controller"),new ConfigDouble("rotation_speed_controller",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mechanical_arm"),new ConfigDouble("mechanical_arm",2.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","display_board"),new ConfigDouble("display_board",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","andesite_encased_shaft"),new ConfigDouble("andesite_encased_shaft",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","brass_encased_shaft"),new ConfigDouble("brass_encased_shaft",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","andesite_encased_cogwheel"),new ConfigDouble("andesite_encased_cogwheel",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","brass_encased_cogwheel"),new ConfigDouble("brass_encased_cogwheel",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","andesite_encased_large_cogwheel"),new ConfigDouble("andesite_encased_large_cogwheel",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","brass_encased_large_cogwheel"),new ConfigDouble("brass_encased_large_cogwheel",0.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","cuckoo_clock"),new ConfigDouble("cuckoo_clock",1.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mysterious_cuckoo_clock"),new ConfigDouble("mysterious_cuckoo_clock",1.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","mechanical_piston"),new ConfigDouble("mechanical_piston",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","sticky_mechanical_piston"),new ConfigDouble("sticky_mechanical_piston",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","copper_backtank"),new ConfigDouble("copper_backtank",4.0));
  impacts.put(Identifier.fromNamespaceAndPath("create","netherite_backtank"),new ConfigDouble("netherite_backtank",4.0));
  capacities.put(Identifier.fromNamespaceAndPath("create","creative_motor"),new ConfigDouble("creative_motor",16384.0));
  capacities.put(Identifier.fromNamespaceAndPath("create","water_wheel"),new ConfigDouble("water_wheel",32.0));
  capacities.put(Identifier.fromNamespaceAndPath("create","large_water_wheel"),new ConfigDouble("large_water_wheel",128.0));
  capacities.put(Identifier.fromNamespaceAndPath("create","hand_crank"),new ConfigDouble("hand_crank",8.0));
  capacities.put(Identifier.fromNamespaceAndPath("create","copper_valve_handle"),new ConfigDouble("copper_valve_handle",8.0));
  capacities.put(Identifier.fromNamespaceAndPath("create","steam_engine"),new ConfigDouble("steam_engine",1024.0));
  capacities.put(Identifier.fromNamespaceAndPath("create","windmill_bearing"),new ConfigDouble("windmill_bearing",512.0));
 }
 @Override public String getName(){return "stressValues.v2";}
 @Override public void registerAll(NativeConfigStore.Builder builder){builder.push("impact");impacts.values().forEach(value->value.register(builder));builder.pop(1);builder.push("capacity");capacities.values().forEach(value->value.register(builder));builder.pop(1);}
 public DoubleSupplier getImpact(Block block){var value=impacts.get(BuiltInRegistries.BLOCK.getKey(block));return value==null?null:value::get;}
 public DoubleSupplier getCapacity(Block block){var value=capacities.get(BuiltInRegistries.BLOCK.getKey(block));return value==null?null:value::get;}
}
