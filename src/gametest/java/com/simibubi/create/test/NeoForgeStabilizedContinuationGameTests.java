package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingBlock;
import com.simibubi.create.foundation.utility.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Complete genuine parent/stabilized child shapes and real native continuation; authored3 UNRUN. */
public final class NeoForgeStabilizedContinuationGameTests {
    private static final BlockPos P=new BlockPos(4,3,4);
    private static ListTag rows(Tag... values){var result=new ListTag();for(var value:values)result.add(value);return result;}
    private static ListTag vector(double x,double y,double z){return rows(DoubleTag.valueOf(x),DoubleTag.valueOf(y),DoubleTag.valueOf(z));}
    private static CompoundTag xyz(BlockPos p){var result=new CompoundTag();result.putInt("X",p.getX());result.putInt("Y",p.getY());result.putInt("Z",p.getZ());return result;}
    private static CompoundTag sourceState(BlockState state){var result=new CompoundTag();result.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();state.getValues().forEach(value->properties.putString(value.property().getName(),value.valueName()));if(!properties.isEmpty())result.put("Properties",properties);return result;}
    private static CompoundTag saved(GameTestHelper h,String type,BlockPos pos){var result=new CompoundTag();result.putString("id",type);result.store("UUID",UUIDUtil.CODEC,UUID.randomUUID());var actual=Vec3.atBottomCenterOf(h.absolutePos(pos));result.put("Pos",vector(actual.x,actual.y,actual.z));result.put("Motion",vector(0,0,0));result.put("Rotation",rows(FloatTag.valueOf(0),FloatTag.valueOf(0)));result.putFloat("FallDistance",0);result.putShort("Fire",(short)-1);result.putShort("Air",(short)300);result.putBoolean("OnGround",false);result.putBoolean("Invulnerable",false);result.putBoolean("NoGravity",true);result.putInt("PortalCooldown",0);return result;}
    private static CompoundTag stock(){var item=new CompoundTag();item.putString("id","minecraft:diamond");item.putInt("count",11);return item;}
    private static CompoundTag body(String type,BlockPos anchor,BlockState state,CompoundTag data){
        var result=new CompoundTag();result.putString("Type",type);result.put("Anchor",xyz(anchor));result.putBoolean("Stalled",false);result.putBoolean("BottomlessSupply",false);
        for(String key:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))result.put(key,new ListTag());
        if(com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(state)!=null){var actor=new CompoundTag();actor.put("Pos",xyz(BlockPos.ZERO));actor.put("Motion",vector(0,0,0));actor.put("RelativeMotion",vector(0,0,0));actor.putBoolean("Stall",false);actor.putBoolean("FirstMovement",false);actor.put("Data",new CompoundTag());result.put("Actors",rows(actor));}
        if(com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour.REGISTRY.get(state)!=null){var interaction=new CompoundTag();interaction.put("Pos",xyz(BlockPos.ZERO));result.put("Interactors",rows(interaction));}
        var blocks=new CompoundTag();blocks.put("Palette",rows(sourceState(state)));var block=new CompoundTag();block.putLong("Pos",BlockPos.ZERO.asLong());block.putInt("State",0);block.put("Data",data);blocks.put("BlockList",rows(block));result.put("Blocks",blocks);return result;
    }
    private static CompoundTag source(GameTestHelper h,boolean stalled){
        var parent=saved(h,"create:stationary_contraption",P);var child=saved(h,"create:contraption",P.above());
        var bearing=new CompoundTag();bearing.putString("id","create:mechanical_bearing");bearing.putInt("ScrollValue",0);bearing.putBoolean("Running",false);bearing.putFloat("Angle",0);bearing.putFloat("Speed",0);bearing.putBoolean("NeedsSpeedUpdate",false);
        var parentBody=body("create:bearing",h.absolutePos(P),AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP),bearing);parentBody.putInt("Sails",0);parentBody.putInt("Facing",Direction.UP.get3DDataValue());
        var entry=new CompoundTag();entry.store("Id",UUIDUtil.CODEC,child.read("UUID",UUIDUtil.CODEC).orElseThrow());var location=new CompoundTag();location.put("Pos",xyz(BlockPos.ZERO));location.putString("Face","UP");entry.put("Location",location);parentBody.put("SubContraptions",rows(entry));
        parent.put("Contraption",parentBody);parent.putBoolean("Stalled",stalled);parent.putBoolean("Initialized",true);parent.put("ControllerRelative",xyz(new BlockPos(0,-1,0)));parent.putString("Axis","Y");parent.putFloat("Angle",0);
        var chest=new CompoundTag();chest.putString("id","minecraft:chest");var item=stock();item.putByte("Slot",(byte)26);chest.put("Items",rows(item));var childBody=body("create:stabilized",h.absolutePos(P.above()),Blocks.CHEST.defaultBlockState(),chest);childBody.putInt("Facing",Direction.UP.get3DDataValue());childBody.putBoolean("Stalled",stalled);
        var sparse=new CompoundTag();sparse.put("26",stock());var slots=new CompoundTag();slots.put("items",sparse);slots.putInt("size",27);var storage=new CompoundTag();storage.putString("type","create:chest");storage.put("value",slots);var cargo=new CompoundTag();cargo.put("pos",xyz(BlockPos.ZERO));cargo.put("storage",storage);childBody.put("items",rows(cargo));
        child.put("Contraption",childBody);child.putBoolean("Stalled",stalled);child.putBoolean("Initialized",true);child.putBoolean("Placed",false);child.putString("InitialOrientation","UP");child.putFloat("Yaw",10);child.putFloat("ForceYaw",20);child.putFloat("Pitch",0);child.put("CachedMotion",vector(.4,0,0));parent.put("Passengers",rows(child));return parent;
    }
    private static CompoundTag child(CompoundTag root){return root.getListOrEmpty("Passengers").getCompoundOrEmpty(0);}
    private static CompoundTag converted(GameTestHelper h,CompoundTag source){return NeoForgeStandaloneEntityImport.convertTree(source,3955,h.getLevel().registryAccess());}
    private static CompoundTag save(Entity entity){var problems=new ProblemReporter.Collector(entity.problemPath());var out=TagValueOutput.createWithContext(problems,entity.registryAccess());if(!entity.saveAsPassenger(out)||!problems.isEmpty())throw new AssertionError(problems.getReport());return out.buildResult();}
    private static Entity load(GameTestHelper h,CompoundTag tag){return EntityType.loadEntityRecursive(tag,h.getLevel(),new EntitySpawnRequest(EntitySpawnReason.LOAD,false),EntityProcessor.NOP);}
    private static long stock(OrientedContraptionEntity child){long result=0;for(var view:child.getContraption().getStorage().getAllItems())if(!view.isResourceBlank()&&view.getResource().isOf(Items.DIAMOND))result+=view.getAmount();return result;}
    @GameTest public void actualCompleteSourceParentAndCachedMotionBecomeOneCurrentContinuationPlusInertHistory(GameTestHelper h){
        var original=source(h,true);var before=original.copy();var result=converted(h,original);var actual=child(result);var pending=StabilizedParentMotion.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),actual.get(StabilizedParentMotion.KEY)).getOrThrow();var history=NeoForgeStabilizedSource.read(actual.getCompoundOrEmpty(NeoForgeStabilizedSource.KEY));
        h.assertTrue(original.equals(before)&&pending.parent().equals(original.read("UUID",UUIDUtil.CODEC).orElseThrow())&&pending.motion().equals(new Vec3(.4,0,0))&&actual.getFloat("Yaw").orElseThrow()==290&&history.originalYaw()==10&&history.originalForceYaw().orElseThrow()==20&&history.originalCachedMotion().orElseThrow().equals(new Vec3(.4,0,0))&&!actual.contains("CachedMotion"),"Actual original direct parent identity, exact source load yaw math and genuine saved cache are preserved without inventing a minecart/controller or reactivating inert history");h.succeed();
    }
    @GameTest public void actualNativeParentReceivesOriginalMotionOnceAndNativeReloadCannotReplayHistoricalCache(GameTestHelper h){
        h.setBlock(P.below(),AllBlocks.MECHANICAL_BEARING.defaultBlockState().setValue(BearingBlock.FACING,Direction.UP));var parent=(ControlledContraptionEntity)load(h,converted(h,source(h,true)));h.assertTrue(h.getLevel().tryAddFreshEntityWithPassengers(parent),"Actual native manager admits the genuine source parent/child UUID forest once");var child=(OrientedContraptionEntity)parent.getFirstPassenger();parent.setDeltaMovement(new Vec3(.01,0,0));child.tick();
        h.assertTrue(parent.getDeltaMovement().equals(new Vec3(.4,0,0))&&!save(child).contains(StabilizedParentMotion.KEY)&&stock(child)==11,"Actual native actor release consumes exactly the genuine .4 parent-motion continuation and preserves eleven items in the one real child");
        var saved=save(parent);child.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);parent.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);var restored=(ControlledContraptionEntity)load(h,saved);h.assertTrue(h.getLevel().tryAddFreshEntityWithPassengers(restored),"Real source UUID forest reloads only after complete genuine native unload");var restoredChild=(OrientedContraptionEntity)restored.getFirstPassenger();restored.setDeltaMovement(new Vec3(.12,0,0));restoredChild.tick();
        h.assertTrue(restored.getDeltaMovement().equals(new Vec3(.12,0,0))&&stock(restoredChild)==11&&!save(restoredChild).contains(StabilizedParentMotion.KEY)&&save(restoredChild).contains(NeoForgeStabilizedSource.KEY),"Native consumed continuation remains consumed across actual save/load; inert original .4 history never overwrites current .12 parent motion or duplicates child stock");h.succeed();
    }
    @GameTest public void nonstalledHistoricalCacheNeverCreatesAContinuationAndMissingFrozenCacheRefuses(GameTestHelper h){
        var inactive=converted(h,source(h,false));h.assertTrue(!child(inactive).contains(StabilizedParentMotion.KEY)&&child(inactive).contains(NeoForgeStabilizedSource.KEY)&&child(inactive).getFloat("Yaw").orElseThrow()==290,"Original nonzero historical cache still adjusts load yaw but cannot invent a currently stalled parent-motion journal");
        var broken=source(h,true);child(broken).remove("CachedMotion");var before=broken.copy();boolean refused=false;try{converted(h,broken);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused&&broken.equals(before),"A frozen original stabilized child without genuine saved parent motion refuses before native factories instead of inventing zero motion");h.succeed();
    }
}
