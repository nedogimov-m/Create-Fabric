package com.simibubi.create.test;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
/** Real source fallback freeze vs genuine normal-controller state; all3 authored cases UNRUN. */
public final class NeoForgeMountedContinuationGameTests {
    private static final BlockPos CHEST=new BlockPos(1,1,0),P=new BlockPos(4,3,4);
    private static ListTag rows(Tag... values){var result=new ListTag();for(var value:values)result.add(value);return result;}
    private static CompoundTag xyz(BlockPos p){var tag=new CompoundTag();tag.putInt("X",p.getX());tag.putInt("Y",p.getY());tag.putInt("Z",p.getZ());return tag;}
    private static CompoundTag sourceState(BlockState state){
        var tag=new CompoundTag();tag.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();
        state.getValues().forEach(entry->properties.putString(entry.property().getName(),entry.valueName()));if(!properties.isEmpty())tag.put("Properties",properties);return tag;
    }
    private static CompoundTag diamonds(){var tag=new CompoundTag();tag.putString("id","minecraft:diamond");tag.putInt("count",11);var components=new CompoundTag();components.putString("minecraft:custom_name","{\"text\":\"Original eleven\"}");tag.put("components",components);return tag;}
    private static CompoundTag body(){
        var tag=new CompoundTag();tag.putString("Type","create:mounted");tag.putString("RotationMode","ROTATE");tag.putString("InitialOrientation","EAST");tag.put("Anchor",xyz(BlockPos.ZERO));tag.putBoolean("Stalled",false);tag.putBoolean("BottomlessSupply",false);
        var palette=rows(sourceState(AllBlocks.MINECART_ANCHOR.defaultBlockState()),sourceState(Blocks.CHEST.defaultBlockState()));
        var anchor=new CompoundTag();anchor.putLong("Pos",BlockPos.ZERO.asLong());anchor.putInt("State",0);
        var chest=new CompoundTag();chest.putLong("Pos",CHEST.asLong());chest.putInt("State",1);var be=new CompoundTag();be.putString("id","minecraft:chest");var sourceItem=diamonds();sourceItem.putByte("Slot",(byte)26);be.put("Items",rows(sourceItem));chest.put("Data",be);chest.put("UpdateTag",new CompoundTag());
        var blocks=new CompoundTag();blocks.put("Palette",palette);blocks.put("BlockList",rows(anchor,chest));tag.put("Blocks",blocks);
        for(String key:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))tag.put(key,new ListTag());
        var slots=new CompoundTag();var sparse=new CompoundTag();sparse.put("26",diamonds());slots.put("items",sparse);slots.putInt("size",27);var storage=new CompoundTag();storage.putString("type","create:chest");storage.put("value",slots);var row=new CompoundTag();row.put("pos",xyz(CHEST));row.put("storage",storage);tag.put("items",rows(row));return tag;
    }

    private static CompoundTag saved(GameTestHelper h,String type){var source=new CompoundTag();source.putString("id",type);source.store("UUID",UUIDUtil.CODEC,UUID.randomUUID());var pos=Vec3.atBottomCenterOf(h.absolutePos(P));source.put("Pos",rows(DoubleTag.valueOf(pos.x),DoubleTag.valueOf(pos.y),DoubleTag.valueOf(pos.z)));source.put("Motion",rows(DoubleTag.valueOf(0),DoubleTag.valueOf(0),DoubleTag.valueOf(0)));source.put("Rotation",rows(FloatTag.valueOf(0),FloatTag.valueOf(0)));source.putFloat("FallDistance",0);source.putShort("Fire",(short)-1);source.putShort("Air",(short)300);source.putBoolean("OnGround",false);source.putBoolean("Invulnerable",false);source.putInt("PortalCooldown",0);source.putBoolean("NoGravity",true);return source;}
    private static CompoundTag source(GameTestHelper h,String kind){
        var cart=saved(h,"minecraft:minecart");var root=saved(h,"create:contraption");var body=body();body.remove("InitialOrientation");body.putBoolean("Stalled",true);root.put("Contraption",body);root.putBoolean("Stalled",true);root.putBoolean("Initialized",true);root.putBoolean("Placed",false);root.putString("InitialOrientation","EAST");root.putFloat("Yaw",10);root.putFloat("ForceYaw",20);root.putFloat("Pitch",0);root.put("CachedMotion",rows(DoubleTag.valueOf(.4),DoubleTag.valueOf(0),DoubleTag.valueOf(0)));cart.put("Passengers",rows(root));
        if(!kind.equals("ABSENT")){var old=new CompoundTag();old.putString("Type",kind);if(kind.equals("NORMAL")){var stall=new CompoundTag();stall.put("Pos",cart.get("Pos").copy());stall.put("Motion",rows(DoubleTag.valueOf(.2),DoubleTag.valueOf(0),DoubleTag.valueOf(0)));stall.putFloat("Yaw",0);stall.putFloat("Pitch",0);old.put("StallData",stall);}var attachments=new CompoundTag();attachments.put("create:minecart_controller",old);cart.put("neoforge:attachments",attachments);}return cart;
    }
    private static CompoundTag convert(GameTestHelper h,CompoundTag source){return NeoForgeStandaloneEntityImport.convertTree(source,3955,h.getLevel().registryAccess());}
    private static CompoundTag controller(CompoundTag cart){return cart.getCompoundOrEmpty("fabric:attachments").getCompoundOrEmpty("create:minecart_controller");}
    private static CompoundTag child(CompoundTag cart){return cart.getListOrEmpty("Passengers").getCompoundOrEmpty(0);}
    @GameTest public void genuineAbsentAndEmptyControllerProduceNewMotionOnlyContinuationFromActualSavedParentAndCache(GameTestHelper h){
        for(var kind:List.of("ABSENT","EMPTY")){var source=source(h,kind);var before=source.copy();var actual=convert(h,source);var root=child(actual);var data=controller(actual);var stall=data.getCompoundOrEmpty("StallData");var history=NeoForgeOrientedSource.read(root.getCompoundOrEmpty(NeoForgeOrientedSource.KEY));
            h.assertTrue(source.equals(before)&&data.read("MountedRoot",UUIDUtil.CODEC).orElseThrow().equals(root.read("UUID",UUIDUtil.CODEC).orElseThrow())&&!data.getBooleanOr("LegacyEmpty",false)&&stall.getBooleanOr("MotionOnly",false)&&stall.get("Pos").equals(source.get("Pos"))&&stall.read("Motion",Vec3.CODEC).orElseThrow().equals(new Vec3(.4,0,0)),"Actual saved direct cart/child relationship supplies a new native current continuation, never an invented original controller journal");
            h.assertTrue(root.getFloat("Yaw").orElseThrow()==290&&history.sourceControllerKind().equals(kind)&&history.originalYaw()==10&&history.originalForceYaw().orElseThrow()==20&&history.originalCachedMotion().orElseThrow().equals(new Vec3(.4,0,0))&&!data.contains("PackedReceipt")&&!data.contains("PendingRefunds"),"Exact original load yaw math and source scalar provenance survive without any paid receipt or refund");
        }h.succeed();
    }
    private static Entity load(GameTestHelper h,CompoundTag actual){return EntityType.loadEntityRecursive(actual,h.getLevel(),new EntitySpawnRequest(EntitySpawnReason.LOAD,false),EntityProcessor.NOP);}
    private static CompoundTag save(Entity actual){var problems=new ProblemReporter.Collector(actual.problemPath());var output=TagValueOutput.createWithContext(problems,actual.registryAccess());if(!actual.saveAsPassenger(output)||!problems.isEmpty())throw new AssertionError(problems.getReport());return output.buildResult();}
    private static long stock(OrientedContraptionEntity actual){long count=0;for(var view:actual.getContraption().getStorage().getAllItems())if(!view.isResourceBlank()&&view.getResource().isOf(Items.DIAMOND))count+=view.getAmount();return count;}
    @GameTest public void actualNativeActorReleaseUsesOriginalCacheOnceAndNativeResaveNeverReplaysInertHistory(GameTestHelper h){
        h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,Blocks.RAIL);var original=source(h,"EMPTY");var cart=(AbstractMinecart)load(h,convert(h,original));h.assertTrue(h.getLevel().tryAddFreshEntityWithPassengers(cart),"Actual native manager admits the genuine original cart and mounted source body once");var root=(OrientedContraptionEntity)cart.getFirstPassenger();var control=MinecartController.get(cart);
        cart.setYRot(23);control.tick();h.assertTrue(cart.getDeltaMovement().equals(Vec3.ZERO)&&cart.getYRot()==23,"Legacy fallback continuation freezes actual native motion only, preserving the source EMPTY-controller angle behavior");root.tick();
        h.assertTrue(cart.getDeltaMovement().equals(new Vec3(.4,0,0))&&!controller(save(cart)).contains("StallData")&&stock(root)==11,"The actual native actor transition releases the original .4 motion once and retains exactly eleven real source items");
        var saved=save(cart);root.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);cart.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);var restored=(AbstractMinecart)load(h,saved);h.assertTrue(h.getLevel().tryAddFreshEntityWithPassengers(restored),"Actual native save/load restores the same source UUID tree after complete genuine unload");var restoredRoot=(OrientedContraptionEntity)restored.getFirstPassenger();restored.setDeltaMovement(new Vec3(.12,0,0));restoredRoot.tick();var next=save(restored);
        h.assertTrue(restored.getDeltaMovement().equals(new Vec3(.12,0,0))&&!controller(next).contains("StallData")&&stock(restoredRoot)==11&&child(next).contains(NeoForgeOrientedSource.KEY)&&!child(next).contains("CachedMotion"),"Consumed native continuation stays consumed; retained historical .4 cache never overwrites current .12 motion or duplicates stock on native reload");h.succeed();
    }
    @GameTest public void genuineNormalControllerJournalWinsAndMissingFallbackCacheRefusesBeforeFactories(GameTestHelper h){
        var source=source(h,"NORMAL");var actual=convert(h,source);var original=source.getCompoundOrEmpty("neoforge:attachments").getCompoundOrEmpty("create:minecart_controller").getCompoundOrEmpty("StallData");var converted=controller(actual).getCompoundOrEmpty("StallData");
        h.assertTrue(converted.read("Motion",Vec3.CODEC).orElseThrow().equals(new Vec3(.2,0,0))&&!converted.getBooleanOr("MotionOnly",false)&&original.get("Pos").equals(converted.get("Pos")),"Actual source NORMAL controller's own .2 journal is never overwritten with its body's independent .4 cache");var incomplete=source(h,"EMPTY");child(incomplete).remove("CachedMotion");var before=incomplete.copy();boolean refused=false;try{convert(h,incomplete);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused&&incomplete.equals(before),"A missing genuine saved fallback motion record never becomes a manufactured zero continuation");h.succeed();
    }
}
