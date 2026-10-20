package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.foundation.utility.*;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Genuine complete source player schema and its own RootVehicle; no player entity/fake level is made. Cases UNRUN. */
public final class NeoForgePlayerVehicleGameTests {
    private static final UUID PLAYER=UUID.fromString("b81d43b2-39b4-4f74-82f1-7386515e3f3a");
    private static final UUID SOURCE=UUID.fromString("355b916c-8da5-4bb3-afac-6c4f0ee90c1e"),CART=UUID.fromString("47f6c7c1-d1dd-4320-94a3-8af48645e411");
    private static ListTag rows(Tag... values){var tag=new ListTag();for(var value:values)tag.add(value);return tag;}
    private static ListTag vector(double x,double y,double z){return rows(DoubleTag.valueOf(x),DoubleTag.valueOf(y),DoubleTag.valueOf(z));}
    private static CompoundTag xyz(BlockPos p){var tag=new CompoundTag();tag.putInt("X",p.getX());tag.putInt("Y",p.getY());tag.putInt("Z",p.getZ());return tag;}
    private static CompoundTag saved(String type,UUID id){
        var tag=new CompoundTag();tag.putString("id",type);tag.store("UUID",UUIDUtil.CODEC,id);tag.put("Pos",vector(-.25,128,3.75));tag.put("Motion",vector(0,0,0));tag.put("Rotation",rows(FloatTag.valueOf(0),FloatTag.valueOf(0)));
        tag.putFloat("FallDistance",0);tag.putShort("Fire",(short)-1);tag.putShort("Air",(short)300);tag.putBoolean("OnGround",false);tag.putBoolean("Invulnerable",false);tag.putInt("PortalCooldown",0);return tag;
    }
    private static CompoundTag sourceState(BlockState state){var tag=new CompoundTag();tag.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();state.getValues().forEach(value->properties.putString(value.property().getName(),value.valueName()));if(!properties.isEmpty())tag.put("Properties",properties);return tag;}
    private static CompoundTag body(String type){
        var tag=new CompoundTag();tag.putString("Type",type);tag.put("Anchor",xyz(new BlockPos(-4,131,7)));tag.putBoolean("Stalled",false);tag.putBoolean("BottomlessSupply",false);
        for(String field:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))tag.put(field,new ListTag());
        var palette=rows(sourceState(type.equals("create:mounted")?AllBlocks.MINECART_ANCHOR.defaultBlockState():Blocks.STONE.defaultBlockState()),sourceState(Blocks.STONE.defaultBlockState()));var a=new CompoundTag();a.putLong("Pos",BlockPos.ZERO.asLong());a.putInt("State",0);var b=new CompoundTag();b.putLong("Pos",BlockPos.ZERO.above().asLong());b.putInt("State",1);var blocks=new CompoundTag();blocks.put("Palette",palette);blocks.put("BlockList",rows(a,b));tag.put("Blocks",blocks);
        if(type.equals("create:bearing")){tag.putInt("Sails",0);tag.putInt("Facing",Direction.UP.get3DDataValue());}if(type.equals("create:mounted"))tag.putString("RotationMode","ROTATE");return tag;
    }
    private static CompoundTag controlled(){var tag=saved("create:stationary_contraption",SOURCE);tag.put("Contraption",body("create:bearing"));tag.putBoolean("Stalled",false);tag.putBoolean("Initialized",true);tag.put("ControllerRelative",xyz(new BlockPos(-3,2,4)));tag.putString("Axis","Y");tag.putFloat("Angle",45);return tag;}

    private static CompoundTag player(){
        var actual=saved("minecraft:player",PLAYER);actual.remove("id");actual.putInt("DataVersion",3955);actual.putString("Dimension","minecraft:overworld");
        var originalItem=new CompoundTag();originalItem.putString("id","minecraft:diamond");originalItem.putInt("count",11);originalItem.putByte("Slot",(byte)0);actual.put("Inventory",rows(originalItem));actual.put("EnderItems",new ListTag());
        for(String field:Set.of("SelectedItemSlot","XpLevel","XpTotal","XpSeed","Score","foodTickTimer","playerGameType"))actual.putInt(field,0);actual.putInt("foodLevel",20);
        for(String field:Set.of("XpP","foodExhaustionLevel"))actual.putFloat(field,0);actual.putFloat("Health",20);actual.putFloat("foodSaturationLevel",5);actual.putShort("SleepTimer",(short)0);actual.putBoolean("seenCredits",false);
        var abilities=new CompoundTag();for(String field:Set.of("invulnerable","flying","mayfly","instabuild"))abilities.putBoolean(field,false);abilities.putBoolean("mayBuild",true);abilities.putFloat("flySpeed",.05f);abilities.putFloat("walkSpeed",.1f);actual.put("abilities",abilities);
        var root=controlled();var body=root.getCompoundOrEmpty("Contraption");var seatPosition=new CompoundTag();seatPosition.put("Pos",xyz(BlockPos.ZERO));body.put("Seats",rows(seatPosition));var seat=new CompoundTag();seat.store("Id",UUIDUtil.CODEC,PLAYER);seat.putInt("Seat",0);body.put("Passengers",rows(seat));
        var vehicle=new CompoundTag();vehicle.store("Attach",UUIDUtil.CODEC,SOURCE);vehicle.put("Entity",root);actual.put("RootVehicle",vehicle);return actual;
    }
    private static void refuses(GameTestHelper h,Runnable operation,String description){boolean refused=false;try{operation.run();}catch(RuntimeException expected){refused=true;}h.assertTrue(refused,description);}
    @GameTest public void ownCompleteSavedPlayerRootVehicleAllowsActualSeatReferenceWithoutCreatingPlayerBody(GameTestHelper h){
        var source=player();var before=source.copy();var converted=NeoForgeStandaloneEntityImport.playerRootVehicle(source,3955,h.getLevel().registryAccess());var root=converted.getCompoundOrEmpty("Entity");
        h.assertTrue(source.equals(before)&&converted.read("Attach",UUIDUtil.CODEC).orElseThrow().equals(SOURCE)&&root.read("UUID",UUIDUtil.CODEC).orElseThrow().equals(SOURCE)&&!root.contains("Inventory")&&!root.contains("Passengers"),"Actual complete source player retains its own eleven diamonds; returned vehicle contains no replacement player, copied player inventory, or fabricated saved passenger");
        var loaded=EntityType.loadEntityRecursive(root,h.getLevel(),new EntitySpawnRequest(EntitySpawnReason.LOAD,false),EntityProcessor.NOP);
        h.assertTrue(loaded instanceof ControlledContraptionEntity&&((ControlledContraptionEntity)loaded).getContraption().getSeatMapping().get(PLAYER)==0&&loaded.getPassengers().isEmpty(),"Real native vehicle factory retains actual player seat UUID as an inert mapping until the genuine player admission path attaches it");h.succeed();
    }
    @GameTest public void bareUuidOrDifferentAttachDimensionSnapshotAndIncompletePlayerCannotAuthorizeAnExternalSeat(GameTestHelper h){
        var source=player();var root=source.getCompoundOrEmpty("RootVehicle").getCompoundOrEmpty("Entity");var before=source.copy();
        refuses(h,()->NeoForgeStandaloneEntityImport.convertTree(root,3955,h.getLevel().registryAccess()),"Generic source region conversion cannot invent a missing real saved player owner");
        var wrong=source.copy();wrong.getCompoundOrEmpty("RootVehicle").store("Attach",UUIDUtil.CODEC,CART);refuses(h,()->NeoForgeStandaloneEntityImport.playerRootVehicle(wrong,3955,h.getLevel().registryAccess()),"Actual RootVehicle.Attach must name a genuine saved tree node");
        var players=rows(source);var context=NeoForgePlayerVehicleContext.fromOriginalPlayers(players,net.minecraft.resources.Identifier.parse("minecraft:overworld"));
        refuses(h,()->NeoForgeStandaloneEntityImport.convertRoots(rows(root),3955,h.getLevel().registryAccess(),context,net.minecraft.resources.Identifier.parse("minecraft:the_nether")),"Source context is tied to its real saved player dimension");
        var changed=root.copy();changed.putFloat("Angle",46);refuses(h,()->NeoForgeStandaloneEntityImport.convertRoots(rows(changed),3955,h.getLevel().registryAccess(),context,net.minecraft.resources.Identifier.parse("minecraft:overworld")),"Same loose UUID with a different original attached body snapshot is not actual saved player context");
        var incomplete=source.copy();incomplete.remove("Inventory");refuses(h,()->NeoForgeStandaloneEntityImport.playerRootVehicle(incomplete,3955,h.getLevel().registryAccess()),"A RootVehicle and UUID fragment never substitutes for complete actual source player data");
        var current=source.copy();current.putInt("DataVersion",net.minecraft.SharedConstants.getCurrentVersion().dataVersion().version());refuses(h,()->NeoForgeStandaloneEntityImport.playerRootVehicle(current,3955,h.getLevel().registryAccess()),"An already-native current player file cannot reactivate a historical original-player snapshot");h.assertTrue(source.equals(before),"All refusal paths leave actual source data unpublished and unchanged");h.succeed();
    }
    @GameTest public void actualPlayerCannotOwnTwoVehicleSnapshotsOrBeDuplicatedAsAnEntityInTheImportedForest(GameTestHelper h){
        var source=player();var other=source.copy();other.store("UUID",UUIDUtil.CODEC,CART);refuses(h,()->NeoForgePlayerVehicleContext.fromOriginalPlayers(rows(source,other),net.minecraft.resources.Identifier.parse("minecraft:overworld")),"Source exactly-one-player RootVehicle invariant rejects two complete player records claiming the same vehicle body");
        var context=NeoForgePlayerVehicleContext.fromOriginalPlayers(rows(source),net.minecraft.resources.Identifier.parse("minecraft:overworld"));var root=source.getCompoundOrEmpty("RootVehicle").getCompoundOrEmpty("Entity");
        refuses(h,()->NeoForgeStandaloneEntityImport.convertRoots(rows(root,saved("create:seat",PLAYER)),3955,h.getLevel().registryAccess(),context,net.minecraft.resources.Identifier.parse("minecraft:overworld")),"Real external player seat UUID can never also become an actual serialized entity body owner");
        var duplicate=player();var map=duplicate.getCompoundOrEmpty("RootVehicle").getCompoundOrEmpty("Entity").getCompoundOrEmpty("Contraption").getListOrEmpty("Passengers");map.add(map.getFirst().copy());refuses(h,()->NeoForgeStandaloneEntityImport.playerRootVehicle(duplicate,3955,h.getLevel().registryAccess()),"One actual source seat/player mapping cannot become two native seat owners");h.succeed();
    }
}
