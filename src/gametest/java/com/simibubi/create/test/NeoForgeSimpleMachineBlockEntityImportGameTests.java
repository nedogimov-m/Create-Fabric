package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.actors.roller.RollerBlockEntity;
import com.simibubi.create.content.decoration.bracket.BracketedBlockEntityBehaviour;
import com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.NeoForgeBlockEntityImport;
import com.simibubi.create.foundation.utility.NeoForgeBlockStateImport;
import com.simibubi.create.foundation.utility.NeoForgeMigrationScope;
import com.simibubi.create.foundation.utility.NeoForgeSavedDataImport;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Actual reviewed small source owner schemas through root full/safe routes and fresh native factories. UNRUN. */
public final class NeoForgeSimpleMachineBlockEntityImportGameTests {
    private static final int VERSION=NeoForgeSavedDataImport.SOURCE_DATA_VERSION;
    private static Identifier id(String value){return Identifier.parse("create:"+value);}
    private static CompoundTag setting(int value){var source=new CompoundTag();source.putInt("ScrollValue",value);return source;}
    private static NeoForgeMigrationScope scope(GameTestHelper h){return NeoForgeMigrationScope.worldBlock(h.getLevel().dimension().identifier(),h.absolutePos(new BlockPos(1,2,1)));}
    private static BlockEntity twice(GameTestHelper h,String type,Block block,CompoundTag source){
        var at=h.absolutePos(new BlockPos(1,2,1));var original=source.copy();original.putString("id",id(type).toString());original.putInt("x",at.getX());original.putInt("y",at.getY());original.putInt("z",at.getZ());var before=original.copy();
        var converted=NeoForgeBlockEntityImport.convert(id(type),original,VERSION,h.getLevel().registryAccess(),scope(h));h.assertTrue(original.equals(before),"Typed migration cannot mutate the caller's actual source save");
        var first=BlockEntity.loadStatic(at,block.defaultBlockState(),converted,h.getLevel().registryAccess());h.assertTrue(first!=null&&first.getLevel()==null,"Actual native registered factory decodes source "+type);
        var second=BlockEntity.loadStatic(at,block.defaultBlockState(),first.saveWithFullMetadata(h.getLevel().registryAccess()),h.getLevel().registryAccess());h.assertTrue(second!=null&&second!=first,"Actual native re-save decodes into a fresh owner for "+type);return second;
    }

    @GameTest public void everyReviewedSmallSourceTypeSurvivesItsActualNativeFactoryAndFreshReload(GameTestHelper h){
        String[] types={"simple_kinetic","gearbox","encased_shaft","encased_cogwheel","encased_large_cogwheel","encased_fan","clutch","gearshift","turntable","gantry_shaft","gantry_pinion","sticker","harvester","mechanical_roller","steam_engine","flywheel","crushing_wheel","sliding_door","item_hatch","fake_track"};
        Block[] blocks={AllBlocks.SHAFT,AllBlocks.GEARBOX,AllBlocks.ANDESITE_ENCASED_SHAFT,AllBlocks.ANDESITE_ENCASED_COGWHEEL,AllBlocks.ANDESITE_ENCASED_LARGE_COGWHEEL,AllBlocks.ENCASED_FAN,AllBlocks.CLUTCH,AllBlocks.GEARSHIFT,AllBlocks.TURNTABLE,AllBlocks.GANTRY_SHAFT,AllBlocks.GANTRY_CARRIAGE,AllBlocks.STICKER,AllBlocks.MECHANICAL_HARVESTER,AllBlocks.MECHANICAL_ROLLER,AllBlocks.STEAM_ENGINE,AllBlocks.FLYWHEEL,AllBlocks.CRUSHING_WHEEL,AllBlocks.ANDESITE_DOOR,AllBlocks.ITEM_HATCH,AllBlocks.FAKE_TRACK};
        for(int index=0;index<types.length;index++)twice(h,types[index],blocks[index],types[index].equals("steam_engine")||types[index].equals("mechanical_roller")?setting(0):new CompoundTag());h.succeed();
    }

    @GameTest public void originalPhysicalBracketSurvivesFullAndGenuineSafeBehaviourMigration(GameTestHelper h){
        var source=new CompoundTag();var bracket=new CompoundTag();bracket.putString("Name","create:metal_bracket");source.put("Bracket",bracket);
        var owner=twice(h,"simple_kinetic",AllBlocks.SHAFT,source);var behaviour=BlockEntityBehaviour.get(owner,BracketedBlockEntityBehaviour.TYPE);
        h.assertTrue(behaviour!=null&&behaviour.isBracketPresent()&&behaviour.getBracket().is(AllBlocks.METAL_BRACKET),"Actual original attached material is retained through typed vanilla block-state migration");
        var safe=NeoForgeBlockEntityImport.convertSafe(id("simple_kinetic"),source,VERSION,h.getLevel().registryAccess(),scope(h));
        h.assertTrue(BlockState.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),safe.get("Bracket")).getOrThrow().is(AllBlocks.METAL_BRACKET)&&!safe.contains("Inventory"),"Actual source BracketedBehaviour.isSafeNBT=true exports one bracket requirement, with no invented physical inventory");h.succeed();
    }

    @GameTest public void actualSteamAndRollerBehaviourPolicySurvivesFullAndSafeSourceRoutes(GameTestHelper h){
        var engine=(SteamEngineBlockEntity)twice(h,"steam_engine",AllBlocks.STEAM_ENGINE,setting(1));h.assertTrue(engine.isCounterClockwise(),"Original actual ScrollValue1 maps native steam MovementDirection1");
        var safe=NeoForgeBlockEntityImport.convertSafe(id("steam_engine"),setting(1),VERSION,h.getLevel().registryAccess(),scope(h));h.assertTrue(safe.getIntOr("MovementDirection",-1)==1,"Partial genuine safe source steam setting uses its native alias too");
        var source=setting(2);var filter=new CompoundTag();filter.putString("id","minecraft:stone");filter.putInt("count",1);var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Original paving sample\"}");filter.put("components",components);source.put("Filter",filter);source.putInt("FilterAmount",9);source.putBoolean("UpTo",true);
        var roller=(RollerBlockEntity)twice(h,"mechanical_roller",AllBlocks.MECHANICAL_ROLLER,source);
        h.assertTrue(roller.getMode()==RollerBlockEntity.RollingMode.WIDE_FILL&&roller.filtering.getFilter().get(DataComponents.CUSTOM_NAME).getString().equals("Original paving sample"),"Original roller mode and typed filter sample survive without inventing material stock");h.succeed();
    }

    @GameTest public void originalGantryErrorKeepsTypedTextAndActualPackedPositionWithoutABodyReceipt(GameTestHelper h){
        var source=new CompoundTag();var error=new CompoundTag();error.putString("Component","{\"text\":\"Original pinion error\"}");long at=new BlockPos(9,17,-4).asLong();error.putLong("Position",at);source.put("LastException",error);
        var owner=twice(h,"gantry_pinion",AllBlocks.GANTRY_CARRIAGE,source);var saved=owner.saveWithoutMetadata(h.getLevel().registryAccess());var restored=saved.getCompound("AssemblyError").orElseThrow().getCompound("LastException").orElseThrow();
        h.assertTrue(restored.getStringOr("Component","").contains("Original pinion error")&&restored.getLongOr("Position",0)==at&&!saved.contains("MovedEntity")&&!saved.contains("MovedContraption"),"Source exception survives native wrapper/text encoding; actual moving gantry body remains a separate real entity owner");h.succeed();
    }

    @GameTest public void unknownOriginalMaterialAndSilentPropertyLossAreRefusedBeforeAStockOwnerLoads(GameTestHelper h){
        var missing=new CompoundTag();missing.putString("Name","create:missing_original_material");boolean unknown=false;
        try{NeoForgeBlockStateImport.convert(missing,VERSION,h.getLevel().registryAccess());}catch(RuntimeException expected){unknown=true;}
        var property=new CompoundTag();property.putString("Name","minecraft:stone");var values=new CompoundTag();values.putString("nonexistent_source_property","x");property.put("Properties",values);boolean lost=false;
        try{NeoForgeBlockStateImport.convert(property,VERSION,h.getLevel().registryAccess());}catch(RuntimeException expected){lost=true;}
        h.assertTrue(unknown&&lost,"Registry fallback to air and dropped original properties cannot silently change the actual source material");h.succeed();
    }
}
