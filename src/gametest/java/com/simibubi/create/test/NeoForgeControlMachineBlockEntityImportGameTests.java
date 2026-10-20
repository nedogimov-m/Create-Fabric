package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crank.HandCrankBlockEntity;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.content.logistics.crate.CreativeCrateBlockEntity;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity;
import com.simibubi.create.foundation.utility.NeoForgeBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Real source control/material/paid-fuel fields through typed root routes and fresh registered native factories. UNRUN. */
public final class NeoForgeControlMachineBlockEntityImportGameTests {
    private static final int VERSION=NeoForgeSavedDataImport.SOURCE_DATA_VERSION;
    private static final UUID SOURCE_FREQ=UUID.fromString("6850247a-a773-4386-b0be-12bad6ee23d4"),SOURCE_OWNER=UUID.fromString("c87b2811-27b2-41d8-848a-2ba9d56d1af8");
    private static Identifier id(String path){return Identifier.parse("create:"+path);}
    private static NeoForgeMigrationScope scope(GameTestHelper h){return NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(new BlockPos(1,2,1)));}
    private static CompoundTag position(BlockPos at){var tag=new CompoundTag();tag.putInt("X",at.getX());tag.putInt("Y",at.getY());tag.putInt("Z",at.getZ());return tag;}
    private static CompoundTag material(String name){var state=new CompoundTag();state.putString("Name",name);return state;}
    private static CompoundTag crank(int remaining){var source=new CompoundTag();source.putInt("InUse",remaining);source.putBoolean("Backwards",true);return source;}
    private static CompoundTag thresholds(){var source=new CompoundTag();source.putFloat("OnAboveAmount",2000);source.putFloat("OffBelowAmount",1500);source.putBoolean("Inverted",true);return source;}
    private static CompoundTag fixture(String type){
        var source=new CompoundTag();switch(type){
            case "adjustable_chain_gearshift"->source.putInt("Signal",7);
            case "hand_crank"->source=crank(0);
            case "valve_handle"->{source=crank(0);source.putInt("TotalUseTicks",0);source.putInt("StartAngle",0);source.putInt("TargetAngle",90);source.putInt("ScrollValue",45);}
            case "packager_link"->{source.store("Freq",UUIDUtil.CODEC,SOURCE_FREQ);source.store("PlacedBy",UUIDUtil.CODEC,SOURCE_OWNER);source.putInt("Power",7);source.putBoolean("Added",true);}
            case "contraption_controls"->{source.putBoolean("Disabled",true);source.putBoolean("Powered",false);}
            case "drill"->{source.putInt("Progress",7);source.putInt("NextTick",31);}
            case "steam_whistle"->source.putInt("Pitch",12);
            case "powered_shaft"->source.putInt("Direction",-1);
            case "water_wheel","large_water_wheel"->{source.putInt("FlowScore",3);source.put("Material",material("minecraft:spruce_planks"));}
            case "blaze_heater"->{source.putInt("fuelLevel",1);source.putInt("burnTimeRemaining",12345);}
            case "speedometer","stressometer"->{source.putFloat("Value",.75f);source.putInt("Color",0x123456);}
            case "nixie_tube"->source.putInt("RedstoneStrength",7);
            case "stockpile_switch"->{source=thresholds();source.putInt("CurrentAmount",1800);source.putInt("CurrentMinAmount",0);source.putInt("CurrentMaxAmount",4000);source.putBoolean("InStacks",false);source.putBoolean("Powered",true);source.putBoolean("PoweredAfterDelay",false);}
            case "content_observer"->source.putInt("TurnOff",3);
            case "cursed_bell"->source.putInt("EffectTicks",7);
            default->{}
        }return source;
    }
    private static BlockEntity twice(GameTestHelper h,String type,Block block,CompoundTag source){
        var at=h.absolutePos(new BlockPos(1,2,1));var original=source.copy();original.putString("id",id(type).toString());original.putInt("x",at.getX());original.putInt("y",at.getY());original.putInt("z",at.getZ());var before=original.copy();
        var converted=NeoForgeBlockEntityImport.convert(id(type),original,VERSION,h.getLevel().registryAccess(),scope(h));h.assertTrue(original.equals(before),"Source owner remains unchanged by typed migration");
        var first=BlockEntity.loadStatic(at,block.defaultBlockState(),converted,h.getLevel().registryAccess());h.assertTrue(first!=null&&first.getLevel()==null,"Actual registered native factory loads original "+type);
        var second=BlockEntity.loadStatic(at,block.defaultBlockState(),first.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(second!=null&&second!=first,"Actual native re-save loads a fresh owner for "+type);return second;
    }

    @GameTest public void allTwentyTwoSourceControlTypesDecodeThroughTheirActualRegisteredNativeOwners(GameTestHelper h){
        String[] types={"adjustable_chain_gearshift","nozzle","hand_crank","valve_handle","cuckoo_clock","packager_link","contraption_controls","drill","steam_whistle","powered_shaft","water_wheel","large_water_wheel","blaze_heater","speedometer","stressometer","nixie_tube","stockpile_switch","creative_crate","content_observer","peculiar_bell","cursed_bell","desk_bell"};
        Block[] blocks={AllBlocks.ADJUSTABLE_CHAIN_GEARSHIFT,AllBlocks.NOZZLE,AllBlocks.HAND_CRANK,AllBlocks.COPPER_VALVE_HANDLE,AllBlocks.CUCKOO_CLOCK,AllBlocks.STOCK_LINK,AllBlocks.CONTRAPTION_CONTROLS,AllBlocks.MECHANICAL_DRILL,AllBlocks.STEAM_WHISTLE,AllBlocks.POWERED_SHAFT,AllBlocks.WATER_WHEEL,AllBlocks.LARGE_WATER_WHEEL,AllBlocks.BLAZE_BURNER,AllBlocks.SPEEDOMETER,AllBlocks.STRESSOMETER,AllBlocks.ORANGE_NIXIE_TUBE,AllBlocks.THRESHOLD_SWITCH,AllBlocks.CREATIVE_CRATE,AllBlocks.SMART_OBSERVER,AllBlocks.PECULIAR_BELL,AllBlocks.HAUNTED_BELL,AllBlocks.DESK_BELL};
        for(int index=0;index<types.length;index++)twice(h,types[index],blocks[index],fixture(types[index]));h.succeed();
    }

    @GameTest public void savedCrankUseAndValveGeometrySurviveUntilAnActualNewUse(GameTestHelper h){
        var crank=(HandCrankBlockEntity)twice(h,"hand_crank",AllBlocks.HAND_CRANK,crank(140));h.assertTrue(crank.inUse==140&&crank.backwards,"Actual saved crank reader domain is not limited to64 remaining ticks");crank.turn(false);h.assertTrue(crank.inUse==10&&!crank.backwards,"An actual new use still applies source10-tick crank policy");
        var source=crank(149);source.putInt("TotalUseTicks",150);source.putInt("StartAngle",1080);source.putInt("TargetAngle",4680);source.putInt("ScrollValue",3600);
        var valve=(ValveHandleBlockEntity)twice(h,"valve_handle",AllBlocks.COPPER_VALVE_HANDLE,source);var saved=valve.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(valve.inUse==149&&valve.getAngleInput()==3600&&saved.getIntOr("TotalUseTicks",0)==150&&saved.getIntOr("StartAngle",0)==1080&&saved.getIntOr("TargetAngle",0)==4680,"Original persisted angle policy, animation endpoints and already consumed use progress retain their actual source values");h.succeed();
    }

    @GameTest public void originalLongDrillCountdownKeepsActualTargetAndAlreadyAccumulatedProgress(GameTestHelper h){
        var source=fixture("drill");source.putInt("NextTick",2500000);var target=h.absolutePos(new BlockPos(2,2,1));source.put("Breaking",position(target));
        var owner=twice(h,"drill",AllBlocks.MECHANICAL_DRILL,source);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.getIntOr("NextTick",0)==2500000&&saved.getIntOr("Progress",0)==7&&BlockPos.CODEC.parse(NbtOps.INSTANCE,saved.get("Breaking")).getOrThrow().equals(target)&&!saved.contains("BreakingState"),"Original long hardness/speed delay and saved target/progress survive; no unstored target-state proof is invented");h.succeed();
    }

    @GameTest public void sourceEngineWarmupAbsenceMeansZeroAndSavedEngineDataKeepsItsActualReaderDomain(GameTestHelper h){
        var idle=(PoweredShaftBlockEntity)twice(h,"powered_shaft",AllBlocks.POWERED_SHAFT,fixture("powered_shaft"));h.assertTrue(idle.initialTicks==0,"Source writer omits completed warmup; native default3 cannot restart it");
        var source=fixture("powered_shaft");source.putInt("Warmup",77);source.put("EnginePos",position(new BlockPos(1,0,0)));source.putFloat("EnginePower",1.5f);source.putString("EngineType","create:steam_engine");
        var owner=(PoweredShaftBlockEntity)twice(h,"powered_shaft",AllBlocks.POWERED_SHAFT,source);
        h.assertTrue(owner.initialTicks==77&&owner.engineEfficiency==1.5f&&owner.enginePos.equals(new BlockPos(1,0,0)),"Actual original relative engine relation and serialized warmup/efficiency survive before the actual loaded engine recalculates current power");h.succeed();
    }

    @GameTest public void sourceWheelMaterialSurvivesAChangedMaterialTagAndSafeWriterAddsNoStock(GameTestHelper h){
        for(String type:new String[]{"water_wheel","large_water_wheel"}){
            var source=fixture(type);source.putInt("FlowScore",200);source.put("Material",material("minecraft:stone"));var block=type.equals("water_wheel")?AllBlocks.WATER_WHEEL:AllBlocks.LARGE_WATER_WHEEL;
            var owner=(WaterWheelBlockEntity)twice(h,type,block,source);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(owner.getMaterial().is(Blocks.STONE)&&saved.getIntOr("FlowScore",0)==200,"Actual saved source material is independent of the current planks edit tag; cached flow is retained until real world resampling");
            var partial=new CompoundTag();partial.put("Material",material("minecraft:stone"));var safe=NeoForgeBlockEntityImport.convertSafe(id(type),partial,VERSION,h.getLevel().registryAccess(),scope(h));
            h.assertTrue(BlockState.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),safe.get("Material")).getOrThrow().is(Blocks.STONE)&&!safe.contains("FlowScore")&&!safe.contains("Item"),"Genuine wheel writeSafe carries appearance only, with no invented material item receipt or flow owner");
        }h.succeed();
    }

    @GameTest public void originalBurnerFuelTimeAndRealCreativeMarkerDoNotProduceExtraFuelItems(GameTestHelper h){
        var source=fixture("blaze_heater");source.putInt("fuelLevel",2);source.putInt("burnTimeRemaining",1500000);source.putBoolean("Goggles",true);source.putBoolean("TrainHat",true);
        var owner=twice(h,"blaze_heater",AllBlocks.BLAZE_BURNER,source);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(saved.getIntOr("fuelLevel",0)==2&&saved.getIntOr("burnTimeRemaining",0)==1500000&&saved.getBooleanOr("Goggles",false)&&saved.getBooleanOr("TrainHat",false)&&!saved.contains("Inventory"),"Already consumed original fuel remains exactly its real timed resource; equipment does not create extra fuel inventory");
        var creative=new CompoundTag();creative.putBoolean("isCreative",true);var infinite=twice(h,"blaze_heater",AllBlocks.BLAZE_BURNER,creative);h.assertTrue(infinite.saveWithoutMetadata(h.getLevel().registryAccess()).getBooleanOr("isCreative",false),"Actual original creative source marker alone authorizes the native creative burner representation");h.succeed();
    }

    @GameTest public void originalGaugeAndPublicObserverCountdownValuesSurviveExactNativeResaves(GameTestHelper h){
        for(String type:new String[]{"speedometer","stressometer"}){var source=fixture(type);source.putFloat("Value",1.5f);source.putInt("Color",0xff123456);var owner=twice(h,type,type.equals("speedometer")?AllBlocks.SPEEDOMETER:AllBlocks.STRESSOMETER,source);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());h.assertTrue(saved.getFloatOr("Value",0)==1.5f&&saved.getIntOr("Color",0)==0xff123456,"Actual source finite dial target and full saved colour integer survive without additional persistence clamps");}
        var pulse=fixture("content_observer");pulse.putInt("TurnOff",80);var observer=twice(h,"content_observer",AllBlocks.SMART_OBSERVER,pulse);h.assertTrue(observer.saveWithoutMetadata(h.getLevel().registryAccess()).getIntOr("TurnOff",0)==80,"Source public activate(int) supports actual pulse lengths beyond the6-tick convenience default");
        var haunting=fixture("cursed_bell");haunting.putInt("EffectTicks",80);var bell=twice(h,"cursed_bell",AllBlocks.HAUNTED_BELL,haunting);h.assertTrue(bell.saveWithoutMetadata(h.getLevel().registryAccess()).getIntOr("EffectTicks",0)==80,"Actual saved public source haunting countdown survives independently of the new-ring default");h.succeed();
    }

    @GameTest public void originalNixieRawAndCachedTextRemainTypedComponentsAndKeepTheirActualPairIndex(GameTestHelper h){
        var source=new CompoundTag();source.putString("RawCustomText","{\"text\":\"ABCDE\",\"bold\":true}");source.putString("CustomText","{\"text\":\"ABCDE\",\"bold\":true}");source.putInt("CustomTextIndex",1);
        var owner=(NixieTubeBlockEntity)twice(h,"nixie_tube",AllBlocks.ORANGE_NIXIE_TUBE,source);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.getFullText().getString().equals("ABCDE")&&owner.getCustomTextIndex()==1&&owner.getDisplayedStrings().first().equals("C")&&owner.getDisplayedStrings().second().equals("D")&&saved.getStringOr("RawCustomText","").contains("ABCDE"),"Both actual original JSON component strings use typed migration; no fake level or selector evaluation supplies cached text");h.succeed();
    }

    @GameTest public void genuineFloatThresholdWriterRetainsOriginalRateUnitsAndOnlyActualPartialSafeFields(GameTestHelper h){
        var owner=(ThresholdSwitchBlockEntity)twice(h,"stockpile_switch",AllBlocks.THRESHOLD_SWITCH,fixture("stockpile_switch"));var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.onWhenAbove==2000&&owner.offWhenBelow==1500&&owner.currentLevel==1800&&!saved.getBooleanOr("SampleReady",true),"Actual source FLOAT thresholds recover integer policy in original mB/custom/item units without81 scaling or invented fresh-target readiness");
        var safe=NeoForgeBlockEntityImport.convertSafe(id("stockpile_switch"),thresholds(),VERSION,h.getLevel().registryAccess(),scope(h));h.assertTrue(safe.getIntOr("OnAboveAmount",0)==2000&&safe.getIntOr("OffBelowAmount",0)==1500&&safe.getBooleanOr("Inverted",false)&&!safe.contains("CurrentAmount")&&!safe.contains("Powered"),"Genuine stockpile writeSafe exports only thresholds/inversion and safe common behaviours");h.succeed();
    }

    @GameTest public void creativeCrateRetainsItsActualTypedGeneratorExemplarWithoutInventingFiniteStock(GameTestHelper h){
        var source=new CompoundTag();var filter=new CompoundTag();filter.putString("id","minecraft:diamond");filter.putInt("count",1);var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Original generator exemplar\"}");filter.put("components",components);source.put("Filter",filter);source.putInt("FilterAmount",0);source.putBoolean("UpTo",true);
        var owner=(CreativeCrateBlockEntity)twice(h,"creative_crate",AllBlocks.CREATIVE_CRATE,source);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(owner.inv.getResource().isOf(Items.DIAMOND)&&owner.inv.getResource().getComponents().get(DataComponents.CUSTOM_NAME).getString().equals("Original generator exemplar")&&owner.inv.getAmount()==64&&!saved.contains("Inventory"),"Genuine source filter remains the actual infinite generator exemplar and its one-stack supply view, with no migrated finite cargo inventory");h.succeed();
    }

    @GameTest public void originalPackagerLinkKeepsOnlyItsProvidedActualNetworkAndPlacementIdentities(GameTestHelper h){
        var original=fixture("packager_link");var owner=twice(h,"packager_link",AllBlocks.STOCK_LINK,original);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());
        h.assertTrue(saved.read("Freq",UUIDUtil.CODEC).orElseThrow().equals(SOURCE_FREQ)&&saved.read("PlacedBy",UUIDUtil.CODEC).orElseThrow().equals(SOURCE_OWNER)&&saved.getIntOr("Power",0)==7,"Actual original supplied network/placer UUID fields are preserved exactly; migration does not manufacture their identities");h.succeed();
    }
}
