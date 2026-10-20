package com.simibubi.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkBlock;
import com.simibubi.create.content.redstone.displayLink.source.*;
import java.util.*;
import net.minecraft.core.Registry;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

/** Real source identifiers and concrete available native device associations. Missing consumers are not dummy sources. */
public final class AllDisplaySources {
 public static final TimeOfDayDisplaySource TIME_OF_DAY=register("time_of_day",new TimeOfDayDisplaySource());
 public static final StopWatchDisplaySource STOPWATCH=register("stopwatch",new StopWatchDisplaySource());

    public static final ObservedTrainNameSource OBSERVED_TRAIN_NAME=register("observed_train_name",new ObservedTrainNameSource());
    public static final StationSummaryDisplaySource STATION_SUMMARY=register("station_summary",new StationSummaryDisplaySource());
    public static final TrainStatusDisplaySource TRAIN_STATUS=register("train_status",new TrainStatusDisplaySource());
    public static final DeathCounterDisplaySource DEATH_COUNT=register("death_count",new DeathCounterDisplaySource());
    public static final ScoreboardDisplaySource SCOREBOARD=register("scoreboard",new ScoreboardDisplaySource());
    public static final EnchantPowerDisplaySource ENCHANT_POWER=register("enchant_power",new EnchantPowerDisplaySource());
    public static final RedstonePowerDisplaySource REDSTONE_POWER=register("redstone_power",new RedstonePowerDisplaySource());
    public static final NixieTubeDisplaySource NIXIE_TUBE=register("nixie_tube",new NixieTubeDisplaySource());
    public static final ItemNameDisplaySource ITEM_NAMES=register("item_names",new ItemNameDisplaySource());
    public static final BoilerDisplaySource BOILER=register("boiler",new BoilerDisplaySource());
    public static final CurrentFloorDisplaySource CURRENT_FLOOR=register("current_floor",new CurrentFloorDisplaySource());
    public static final FactoryGaugeDisplaySource GAUGE_STATUS=register("gauge_status",new FactoryGaugeDisplaySource());
    public static final EntityNameDisplaySource ENTITY_NAME=register("entity_name",new EntityNameDisplaySource());
    public static final KineticSpeedDisplaySource KINETIC_SPEED=register("kinetic_speed",new KineticSpeedDisplaySource());
    public static final KineticStressDisplaySource KINETIC_STRESS=register("kinetic_stress",new KineticStressDisplaySource());
    public static final AccumulatedItemCountDisplaySource ACCUMULATE_ITEMS=register("accumulate_items",new AccumulatedItemCountDisplaySource());
    public static final ItemThroughputDisplaySource ITEM_THROUGHPUT=register("item_throughput",new ItemThroughputDisplaySource());
    public static final ItemCountDisplaySource COUNT_ITEMS=register("count_items",new ItemCountDisplaySource());
    public static final ItemListDisplaySource LIST_ITEMS=register("list_items",new ItemListDisplaySource());
    public static final FluidAmountDisplaySource COUNT_FLUIDS=register("count_fluids",new FluidAmountDisplaySource());
    public static final FluidListDisplaySource LIST_FLUIDS=register("list_fluids",new FluidListDisplaySource());
    public static final PackageAddressDisplaySource READ_PACKAGE_ADDRESS=register("read_package_address",new PackageAddressDisplaySource());
    public static final FillLevelDisplaySource FILL_LEVEL=register("fill_level",new FillLevelDisplaySource());
    public static final Map<String,DisplaySource> LEGACY_NAMES=legacy();
    private static <T extends DisplaySource>T register(String id,T source){return Registry.register(CreateBuiltInRegistries.DISPLAY_SOURCE,Create.asResource(id),source);}
    private static Map<String,DisplaySource> legacy(){var names=new LinkedHashMap<String,DisplaySource>();names.put("track_observer_source_observed_train_name",OBSERVED_TRAIN_NAME);names.put("track_station_source_station_summary",STATION_SUMMARY);names.put("track_station_source_train_status",TRAIN_STATUS);names.put("cuckoo_clock_source_time_of_day",TIME_OF_DAY);names.put("cuckoo_clock_source_stop_watch",STOPWATCH);names.put("death_count_display_source",DEATH_COUNT);names.put("scoreboard_display_source",SCOREBOARD);names.put("enchant_power_display_source",ENCHANT_POWER);names.put("redstone_power_display_source",REDSTONE_POWER);names.put("nixie_tube_source",NIXIE_TUBE);names.put("belt_source_combine_item_names",ITEM_NAMES);names.put("depot_source_combine_item_names",ITEM_NAMES);names.put("weighted_ejector_source_combine_item_names",ITEM_NAMES);names.put("speedometer_source_kinetic_speed",KINETIC_SPEED);names.put("stressometer_source_kinetic_stress",KINETIC_STRESS);names.put("fluid_tank_source_boiler_status",BOILER);names.put("elevator_contact_source_current_floor",CURRENT_FLOOR);for(var name:List.of("andesite_tunnel","brass_tunnel")){names.put(name+"_source_accumulate_items",ACCUMULATE_ITEMS);names.put(name+"_source_item_throughput",ITEM_THROUGHPUT);}names.put("content_observer_source_count_items",COUNT_ITEMS);names.put("content_observer_source_list_items",LIST_ITEMS);names.put("content_observer_source_count_fluids",COUNT_FLUIDS);names.put("content_observer_source_list_fluids",LIST_FLUIDS);names.put("factory_gauge_source_gauge_status",GAUGE_STATUS);names.put("stockpile_switch_source_fill_level",FILL_LEVEL);for(var dye:DyeColor.values())names.put(dye.getSerializedName()+"_seat_source_entity_name",ENTITY_NAME);return Collections.unmodifiableMap(names);}
    private static boolean associated;
    public static void register(){
        if(associated)return;associated=true;DisplaySource.BY_BLOCK.add(AllBlocks.CUCKOO_CLOCK,TIME_OF_DAY);DisplaySource.BY_BLOCK.add(AllBlocks.CUCKOO_CLOCK,STOPWATCH);com.simibubi.create.content.redstone.displayLink.DisplayLinkAdvancements.register();com.simibubi.create.content.redstone.displayLink.StationDepartureUpdates.register();DisplaySource.BY_BLOCK.add(AllBlocks.TRACK_OBSERVER,OBSERVED_TRAIN_NAME);DisplaySource.BY_BLOCK.add(AllBlocks.TRACK_STATION,STATION_SUMMARY);DisplaySource.BY_BLOCK.add(AllBlocks.TRACK_STATION,TRAIN_STATUS);DisplaySource.BY_BLOCK.add(Blocks.RESPAWN_ANCHOR,DEATH_COUNT);DisplaySource.BY_BLOCK_ENTITY.add(BlockEntityTypes.COMMAND_BLOCK,SCOREBOARD);DisplaySource.BY_BLOCK_ENTITY.add(BlockEntityTypes.ENCHANTING_TABLE,ENCHANT_POWER);DisplaySource.BY_BLOCK.add(Blocks.TARGET,REDSTONE_POWER);
        DisplaySource.BY_BLOCK_ENTITY.add(AllBlockEntityTypes.NIXIE_TUBE,NIXIE_TUBE);DisplaySource.BY_BLOCK.add(AllBlocks.BELT,ITEM_NAMES);DisplaySource.BY_BLOCK.add(AllBlocks.DEPOT,ITEM_NAMES);DisplaySource.BY_BLOCK.add(AllBlocks.WEIGHTED_EJECTOR,ITEM_NAMES);DisplaySource.BY_BLOCK.add(AllBlocks.PLACARD,ITEM_NAMES);DisplaySource.BY_BLOCK.add(AllBlocks.SPEEDOMETER,KINETIC_SPEED);DisplaySource.BY_BLOCK.add(AllBlocks.STRESSOMETER,KINETIC_STRESS);DisplaySource.BY_BLOCK.add(AllBlocks.FLUID_TANK,BOILER);DisplaySource.BY_BLOCK.add(AllBlocks.ELEVATOR_CONTACT,CURRENT_FLOOR);DisplaySource.BY_BLOCK.add(AllBlocks.FACTORY_GAUGE,GAUGE_STATUS);for(var seat:AllBlocks.SEATS.values())DisplaySource.BY_BLOCK.add(seat,ENTITY_NAME);for(var tunnel:List.of(AllBlocks.ANDESITE_TUNNEL,AllBlocks.BRASS_TUNNEL)){DisplaySource.BY_BLOCK.add(tunnel,ACCUMULATE_ITEMS);DisplaySource.BY_BLOCK.add(tunnel,ITEM_THROUGHPUT);}for(var source:List.of(COUNT_ITEMS,LIST_ITEMS,COUNT_FLUIDS,LIST_FLUIDS,READ_PACKAGE_ADDRESS))DisplaySource.BY_BLOCK.add(AllBlocks.SMART_OBSERVER,source);
        DisplaySource.BY_BLOCK.add(AllBlocks.THRESHOLD_SWITCH,FILL_LEVEL);
        ElevatorContactBlockEntity.registerFloorDisplayListener(contact->{if(contact.getLevel()!=null)DisplayLinkBlock.notifyGatherers(contact.getLevel(),contact.getBlockPos());});
    }
}
