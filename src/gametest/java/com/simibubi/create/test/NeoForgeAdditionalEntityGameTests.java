package com.simibubi.create.test;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.NeoForgeStandaloneEntityImport;
import com.simibubi.create.content.equipment.blueprint.BlueprintEntity;
import com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.foundation.utility.NeoForgePackageHistory;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
/** Complete real3955 additional schemas and actual native factories/codecs; all5 cases UNRUN. */
public final class NeoForgeAdditionalEntityGameTests {
    private static ListTag rows(Tag... values){var result=new ListTag();for(var value:values)result.add(value);return result;}
    private static CompoundTag xyz(){var tag=new CompoundTag();tag.putInt("X",0);tag.putInt("Y",0);tag.putInt("Z",0);return tag;}
    private static CompoundTag originalPacked(){
        var state=AllBlocks.MINECART_ANCHOR.defaultBlockState();var sourceState=new CompoundTag();sourceState.putString("Name",BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());var properties=new CompoundTag();state.getValues().forEach(value->properties.putString(value.property().getName(),value.valueName()));if(!properties.isEmpty())sourceState.put("Properties",properties);
        var row=new CompoundTag();row.putLong("Pos",BlockPos.ZERO.asLong());row.putInt("State",0);var blocks=new CompoundTag();blocks.put("Palette",rows(sourceState));blocks.put("BlockList",rows(row));
        var body=new CompoundTag();body.putString("Type","create:mounted");body.putString("RotationMode","ROTATE");body.putString("InitialOrientation","EAST");body.put("Anchor",xyz());body.putBoolean("Stalled",false);body.putBoolean("BottomlessSupply",false);body.put("Blocks",blocks);
        for(String key:Set.of("CapturedMultiblocks","Actors","DisabledActors","Interactors","Superglue","Seats","Passengers","SubContraptions"))body.put(key,new ListTag());
        var components=new CompoundTag();components.put("create:minecart_contraption_data",body);var item=new CompoundTag();item.putString("id","create:minecart_contraption");item.putInt("count",1);item.put("components",components);return item;
    }

    private static CompoundTag item(String id,int count){var item=new CompoundTag();item.putString("id",id);item.putInt("count",count);return item;}
    private static CompoundTag source(GameTestHelper h,String id){var tag=new CompoundTag();tag.putString("id",id);tag.store("UUID",UUIDUtil.CODEC,UUID.randomUUID());var pos=h.absolutePos(new BlockPos(3,4,3));tag.put("Pos",rows(DoubleTag.valueOf(pos.getX()+.5),DoubleTag.valueOf(pos.getY()+.5),DoubleTag.valueOf(pos.getZ()+.5)));tag.put("Motion",rows(DoubleTag.valueOf(0),DoubleTag.valueOf(0),DoubleTag.valueOf(0)));tag.put("Rotation",rows(FloatTag.valueOf(0),FloatTag.valueOf(0)));tag.putFloat("FallDistance",0);tag.putShort("Fire",(short)0);tag.putShort("Air",(short)300);tag.putBoolean("OnGround",false);tag.putBoolean("Invulnerable",false);tag.putInt("PortalCooldown",0);return tag;}
    private static CompoundTag convert(GameTestHelper h,CompoundTag source){return NeoForgeStandaloneEntityImport.convertTree(source,3955,h.getLevel().registryAccess());}
    private static Entity load(GameTestHelper h,CompoundTag actual){return EntityType.loadEntityRecursive(actual,h.getLevel(),new EntitySpawnRequest(EntitySpawnReason.LOAD,false),EntityProcessor.NOP);}
    private static CompoundTag save(Entity actual){var problems=new ProblemReporter.Collector(actual.problemPath());var output=TagValueOutput.createWithContext(problems,actual.registryAccess());if(!actual.saveAsPassenger(output)||!problems.isEmpty())throw new AssertionError("Actual native complete entity save: "+problems.getReport());return output.buildResult();}
    private static CompoundTag blueprint(GameTestHelper h){var tag=source(h,"create:crafting_blueprint");var anchor=h.absolutePos(new BlockPos(3,4,3));tag.putInt("TileX",anchor.getX());tag.putInt("TileY",anchor.getY());tag.putInt("TileZ",anchor.getZ());tag.putByte("Facing",(byte)Direction.NORTH.get3DDataValue());tag.putByte("Orientation",(byte)Direction.DOWN.get3DDataValue());tag.putInt("Size",1);var pos=Vec3.atCenterOf(anchor).subtract(Direction.NORTH.getUnitVec3().scale(.46875));tag.put("Pos",rows(DoubleTag.valueOf(pos.x),DoubleTag.valueOf(pos.y),DoubleTag.valueOf(pos.z)));var icon=originalPacked();icon.putInt("Slot",9);var sample=item("minecraft:diamond",11);sample.putInt("Slot",10);var section=new CompoundTag();section.putInt("Size",11);section.put("Items",rows(icon,sample));var recipes=new CompoundTag();recipes.put("0",section);recipes.putBoolean("InferredIcon",true);var own=new CompoundTag();own.put("Recipes",recipes);tag.put("NeoForgeData",own);return tag;}
    @GameTest public void originalBlueprintGhostSlotsAndAnchorSurviveActualNativeSaveLoad(GameTestHelper h){
        var original=blueprint(h);var before=original.copy();var nativeTag=convert(h,original);var actual=load(h,nativeTag);h.assertTrue(actual instanceof BlueprintEntity,"Actual registered native blueprint factory receives its migrated complete original");var blueprint=(BlueprintEntity)actual;var section=blueprint.getSection(0);var samples=section.getItems();
        h.assertTrue(samples.getItem(9).is(AllItems.MINECART_CONTRAPTION)&&samples.getItem(10).is(Items.DIAMOND)&&samples.getItem(10).getCount()==11&&section.inferredIcon(),"Actual native section retains a source packed ghost icon, eleven-item ghost sample and genuine global inferred-icon state");
        var saved=save(actual);var restored=(BlueprintEntity)load(h,saved);h.assertTrue(restored.getSection(0).getItems().getItem(10).getCount()==11&&restored.getSection(0).inferredIcon()&&original.equals(before)&&!saved.contains("NeoForgeData"),"Source recipes become actual tracked native recipes and native save/reload never turns ghost samples into physical stock");h.succeed();
    }
    private static CompoundTag potato(GameTestHelper h){var tag=source(h,"create:potato_projectile");tag.put("Item",item("minecraft:potato",1));tag.putFloat("AdditionalDamage",1.25f);tag.putFloat("AdditionalKnockback",-.25f);tag.putFloat("Recovery",1.5f);tag.putDouble("acceleration_power",.125);tag.putBoolean("HasBeenShot",true);tag.putBoolean("LeftOwner",true);return tag;}
    @GameTest public void originalPotatoSavedAccelerationAndFiniteModifiersReachActualNativeContinuation(GameTestHelper h){
        var original=potato(h);var before=original.copy();var actual=load(h,convert(h,original));h.assertTrue(actual instanceof PotatoProjectileEntity&&((PotatoProjectileEntity)actual).getItem().is(Items.POTATO),"Actual native potato factory retains its source item owner");var saved=save(actual);var again=save(load(h,saved));
        h.assertTrue(saved.getDouble("acceleration_power").orElseThrow()==.125&&again.getDouble("acceleration_power").orElseThrow()==.125&&again.getFloat("AdditionalDamage").orElseThrow()==1.25f&&again.getFloat("AdditionalKnockback").orElseThrow()==-.25f&&again.getFloat("Recovery").orElseThrow()==1.5f&&original.equals(before),"Actual original acceleration and finite signed/large source modifiers are retained through native load/save twice");
        h.assertTrue(!saved.contains("StuckEntity")&&!saved.contains("Owner"),"No missing historical stuck-body or owner reference is invented");h.succeed();
    }
    private static CompoundTag parcel(GameTestHelper h){
        var tag=source(h,"create:package");tag.putFloat("Health",4);tag.putShort("HurtTime",(short)3);tag.putInt("HurtByTimestamp",17);tag.putShort("DeathTime",(short)0);tag.putFloat("AbsorptionAmount",0);tag.putBoolean("FallFlying",false);var health=new CompoundTag();health.putString("id","minecraft:generic.max_health");health.putDouble("base",5);var speed=new CompoundTag();speed.putString("id","minecraft:generic.movement_speed");speed.putDouble("base",1);tag.put("attributes",rows(health,speed));var brain=new CompoundTag();brain.put("memories",new CompoundTag());tag.put("Brain",brain);
        var box=item("create:cardboard_package_12x12",1);var row=new CompoundTag();row.putInt("slot",0);row.put("item",item("minecraft:diamond",11));var components=new CompoundTag();components.put("create:package_contents",rows(row));box.put("components",components);tag.put("Box",box);return tag;
    }
    @GameTest public void originalPackageHasOneBoxOwnerWithRealLivingStateAndInertHistoricalTimestamp(GameTestHelper h){
        var original=parcel(h);var before=original.copy();var actual=load(h,convert(h,original));h.assertTrue(actual instanceof PackageEntity,"Actual registered native package factory receives typed source living/base state");var parcel=(PackageEntity)actual;h.assertTrue(parcel.getHealth()==4&&PackageItem.getContents(parcel.getBox()).getFirst().getCount()==11,"Actual original box retains precisely eleven source diamonds and saved health4");var saved=save(actual);var restored=(PackageEntity)load(h,saved);var next=save(restored);
        h.assertTrue(PackageItem.getContents(restored.getBox()).getFirst().getCount()==11&&NeoForgePackageHistory.read(next.getCompoundOrEmpty(NeoForgePackageHistory.KEY)).originalHurtByTimestamp()==17&&!next.contains("last_hurt_by_mob")&&!next.contains("equipment")&&original.equals(before),"Native re-save retains source scalar history without inventing a current attacker/tick or duplicate mainhand material owner");h.succeed();
    }
    @GameTest public void originalCountedSavedBoxIsNotClampedByTheNativePaidOneGameplaySetter(GameTestHelper h){
        var original=parcel(h);original.getCompoundOrEmpty("Box").putInt("count",2);var actual=(PackageEntity)load(h,convert(h,original));var saved=save(actual);h.assertTrue(actual.getBox().getCount()==2&&saved.getCompoundOrEmpty("Box").getInt("count").orElseThrow()==2,"Actual serialized source box count2 remains2; paid-one gameplay factory remains a separate actual contract");h.succeed();
    }
    @GameTest public void incompleteOrUnknownAdditionalOwnersRefuseBeforeAnyNativeFactory(GameTestHelper h){
        var missing=potato(h);missing.remove("acceleration_power");refuses(h,missing);var duplicate=blueprint(h);var section=duplicate.getCompoundOrEmpty("NeoForgeData").getCompoundOrEmpty("Recipes").getCompoundOrEmpty("0");section.getListOrEmpty("Items").add(section.getListOrEmpty("Items").getFirst().copy());refuses(h,duplicate);var unknown=parcel(h);unknown.putInt("FuturePaidStock",11);refuses(h,unknown);var cure=parcel(h);var effect=new CompoundTag();effect.putString("id","minecraft:speed");effect.put("neoforge:cures",rows(StringTag.valueOf("unknown_custom_cure")));cure.put("active_effects",rows(effect));refuses(h,cure);h.succeed();
    }
    private static void refuses(GameTestHelper h,CompoundTag original){var before=original.copy();boolean refused=false;try{convert(h,original);}catch(RuntimeException expected){refused=true;}h.assertTrue(refused&&original.equals(before),"Incomplete/unknown source additional owner is refused without changing the genuine input tree");}
}
