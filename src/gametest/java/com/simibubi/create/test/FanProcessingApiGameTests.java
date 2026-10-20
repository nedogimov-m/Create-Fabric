package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllDamageTypes;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.EncasedFanBlock;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** Native world owners, finite original stock, real storage rollback and source API callbacks. */
public final class FanProcessingApiGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos FAN=new BlockPos(2,3,3),DEPOT=FAN.east(2).below();
    private static void power(GameTestHelper h){h.setBlock(FAN.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));}
    private static void fan(GameTestHelper h){power(h);h.setBlock(FAN,AllBlocks.ENCASED_FAN.defaultBlockState().setValue(EncasedFanBlock.FACING,Direction.EAST));}
    private static ItemStack stock(GameTestHelper h,String test,int count){
        var stack=new ItemStack(Items.STICK,count);stack.set(DataComponents.CUSTOM_NAME,Component.literal(FanProcessingApiTestFixtures.PREFIX+test+" "+h.absolutePos(FAN)));return stack;
    }
    private static DepotBlockEntity depot(GameTestHelper h,ItemStack input){
        h.setBlock(DEPOT,AllBlocks.DEPOT);var depot=h.getBlockEntity(DEPOT,DepotBlockEntity.class);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(depot.inventory.automation.insert(ItemVariant.of(input),input.getCount(),tx),(long)input.getCount(),"Actual native depot admits all finite original stock");tx.commit();}return depot;
    }
    private static long count(DepotBlockEntity depot,Item item){
        var held=depot.getHeldItem();return (held.is(item)?held.getCount():0L)+depot.inventory.outputs.stream().map(slot->slot.stack()).filter(stack->stack.is(item)).mapToLong(ItemStack::getCount).sum();
    }
    private static DepotBlockEntity reload(GameTestHelper h,DepotBlockEntity original){
        var saved=original.saveWithFullMetadata(h.getLevel().registryAccess());
        var restored=(DepotBlockEntity)BlockEntity.loadStatic(original.getBlockPos(),original.getBlockState(),saved,h.getLevel().registryAccess());
        h.assertTrue(restored!=null,"Native BlockEntity loader restores actual source owner");
        h.getLevel().removeBlockEntity(original.getBlockPos());h.getLevel().setBlockEntity(restored);return restored;
    }
    private static int calls(ItemStack original,boolean high){return FanProcessingApiTestFixtures.calls(FanProcessingApiTestFixtures.marker(original),high);}

    @GameTest(structure=STRUCTURE,maxTicks=240)
    public void registeredPriorityProcessesActualItemAndCallsActualLivingRecipient(GameTestHelper h){
        fan(h);var input=stock(h,"item-priority",3);
        h.setBlock(FAN.east().below(),Blocks.STONE);h.setBlock(FAN.east().above(2),Blocks.STONE);
        h.setBlock(FAN.east().north(),Blocks.STONE);h.setBlock(FAN.east().south(),Blocks.STONE);h.setBlock(FAN.east(2),Blocks.STONE);
        h.setBlock(FAN.east(),Blocks.TORCH);
        h.assertTrue(FanProcessingType.getAt(h.getLevel(),h.absolutePos(FAN.east()))==FanProcessingApiTestFixtures.HIGH,"Actual catalyst dispatch sorts higher priority registered later");
        var at=Vec3.atCenterOf(h.absolutePos(FAN.east())).add(0,-.3,0);
        var item=new ItemEntity(h.getLevel(),at.x,at.y,at.z,input.copy(),0,0,0);item.setNoGravity(true);
        h.assertTrue(h.getLevel().addFreshEntity(item),"Actual native item owner admitted");
        var pig=EntityTypes.PIG.create(h.getLevel(),EntitySpawnReason.TRIGGERED);h.assertTrue(pig!=null,"Native living recipient constructs");
        pig.setNoAi(true);pig.setPos(at);h.assertTrue(h.getLevel().addFreshEntity(pig),"Actual native living recipient admitted");
        h.succeedWhen(()->{
            h.assertTrue(item.isAlive()&&item.getItem().is(Items.EMERALD)&&item.getItem().getCount()==3,"Actual fan consumes original finite three sticks into exactly three addon emeralds");
            h.assertTrue(item.getItem().get(DataComponents.CUSTOM_NAME).equals(input.get(DataComponents.CUSTOM_NAME)),"Original component marker survives custom result");
            h.assertTrue(pig.hasEffect(MobEffects.GLOWING),"Actual AirCurrent invokes registered addon entity callback");
            h.assertValueEqual(calls(input,true),1,"High priority process callback runs exactly once");h.assertValueEqual(calls(input,false),0,"Lower registered callback never processes original stock");
        });
    }

    @GameTest(structure=STRUCTURE,maxTicks=320)
    public void customDepotProgressReloadsPausesWithoutPowerAndResumesOnce(GameTestHelper h){
        fan(h);h.setBlock(FAN.east().below(),Blocks.STONE);h.setBlock(FAN.east(),Blocks.TORCH);var input=stock(h,"depot-reload",5);depot(h,input);var paused=new int[1];
        h.runAfterDelay(65,()->{
            var current=h.getBlockEntity(DEPOT,DepotBlockEntity.class);var held=current.getTransportedItem();
            h.assertTrue(held.processedBy==FanProcessingApiTestFixtures.HIGH&&held.processingTime>0,"Actual fan starts registered custom type on native depot");paused[0]=held.processingTime;
            var restored=reload(h,current);var recovered=restored.getTransportedItem();
            h.assertTrue(recovered.processedBy==FanProcessingApiTestFixtures.HIGH&&recovered.processingTime==paused[0],"Custom registry identity and remaining timer survive native owner serialization");
            h.assertValueEqual(FanProcessing.id(recovered.processedBy),FanProcessingApiTestFixtures.HIGH_ID.toString(),"Saved strategy uses stable namespaced addon ID");h.setBlock(FAN.west(),Blocks.AIR);
        });
        h.runAfterDelay(85,()->{var current=h.getBlockEntity(DEPOT,DepotBlockEntity.class);paused[0]=current.getTransportedItem().processingTime;h.assertValueEqual(count(current,Items.STICK),5L,"Power loss retains all finite original stock");});
        h.runAfterDelay(115,()->{var current=h.getBlockEntity(DEPOT,DepotBlockEntity.class);h.assertValueEqual(current.getTransportedItem().processingTime,paused[0],"Actual stopped fan freezes remaining progress");h.assertValueEqual(calls(input,true),0,"No callback prepares output while unpaid processing is paused");power(h);});
        h.succeedWhen(()->{
            h.assertTrue(h.getTick()>115,"Wait for actual owner restore and power resume");var current=h.getBlockEntity(DEPOT,DepotBlockEntity.class);
            h.assertValueEqual(count(current,Items.EMERALD),5L,"Resumed native owner pays original five sticks once");h.assertValueEqual(count(current,Items.STICK),0L,"No unpaid input duplicates remain");h.assertValueEqual(calls(input,true),1,"One callback across actual save, pause and resume");
        });
    }

    @GameTest(structure=STRUCTURE,maxTicks=40)
    public void actualBeltOuterAbortRetainsOriginalStockAndCachedResultsUntilCommittedRetry(GameTestHelper h){
        var start=new BlockPos(2,2,3);var end=start.east(4);
        h.setBlock(start,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));h.setBlock(end,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));
        h.assertTrue(BeltConnectorItem.createBelts(h.getLevel(),h.absolutePos(start),h.absolutePos(end)),"Actual source connector creates genuine native belt owners");
        var controller=h.getBlockEntity(start,BeltBlockEntity.class);var segment=h.getBlockEntity(start.east(2),BeltBlockEntity.class);var input=stock(h,"belt-abort",7);var completed=new AtomicInteger();
        try(var tx=Transaction.openOuter()){h.assertValueEqual(controller.getInventory().segment(2,Direction.UP).insert(ItemVariant.of(input),7,tx),7L,"Finite original seven sticks enter actual belt storage");tx.commit();}
        var held=controller.getInventory().getTransportedItems().getFirst();held.processedBy=FanProcessingApiTestFixtures.HIGH;held.processingTime=0;
        try(var outer=Transaction.openOuter()){
            FanProcessing.process(segment,FanProcessingApiTestFixtures.HIGH,completed::incrementAndGet);
            h.assertValueEqual(completed.get(),0,"Nested processing completion waits for outer commit");
            h.assertTrue(controller.getInventory().getTransportedItems().getFirst().stack.is(Items.EMERALD),"Real nested inventory transaction provisionally admits addon output");
            // Outer abort is deliberate; no fabricated Storage or detached transported owner.
        }
        var rolledBack=controller.getInventory().getTransportedItems().getFirst();
        h.assertTrue(rolledBack.stack.is(Items.STICK)&&rolledBack.stack.getCount()==7&&rolledBack.pendingFanResults!=null,"Outer rollback restores all original stock and preserves one rolled custom result");
        h.assertTrue(rolledBack.pendingFanInput.matches(input)&&rolledBack.pendingFanCount==7,"Cached output remains bound to exact original variant and count");
        h.assertValueEqual(calls(input,true),1,"Aborted outer transaction does not re-roll addon output");h.assertValueEqual(completed.get(),0,"Aborted processing never completes");
        h.runAfterDelay(2,()->{
            FanProcessing.process(segment,FanProcessingApiTestFixtures.HIGH,completed::incrementAndGet);
            var results=controller.getInventory().getTransportedItems();h.assertValueEqual(results.size(),1,"Committed retry creates one actual output owner");
            h.assertTrue(results.getFirst().stack.is(Items.EMERALD)&&results.getFirst().stack.getCount()==7,"Committed retry pays original seven items exactly once");
            h.assertValueEqual(calls(input,true),1,"Retry reuses stable cached callback result");h.assertValueEqual(completed.get(),1,"Completion runs exactly once after actual outer commit");h.succeed();
        });
    }

    @GameTest(structure=STRUCTURE,maxTicks=240)
    public void missingAddonProgressReloadsAsOpaqueAndCannotBeConsumedByKnownCatalyst(GameTestHelper h){
        fan(h);h.setBlock(FAN.east().below(),Blocks.STONE);h.setBlock(FAN.east(),Blocks.TORCH);var input=stock(h,"unknown-addon",4);var original=depot(h,input);var held=original.getTransportedItem();
        held.processedBy=null;held.unresolvedFanProcessingType="missing_addon:opaque";held.processingTime=37;
        var output=new ItemStack(Items.DIAMOND,4);output.set(DataComponents.CUSTOM_NAME,input.get(DataComponents.CUSTOM_NAME));
        held.pendingFanResults=List.of(output);held.pendingFanInput=ItemVariant.of(input);held.pendingFanCount=4;original.setChanged();reload(h,original);
        h.runAfterDelay(205,()->{
            var current=h.getBlockEntity(DEPOT,DepotBlockEntity.class);var saved=current.getTransportedItem();
            h.assertTrue(saved.processedBy==null&&"missing_addon:opaque".equals(saved.unresolvedFanProcessingType),"Native owner retains unresolved namespaced strategy without builtin fallback");
            h.assertValueEqual(saved.processingTime,37,"Actual fan leaves unknown strategy timer unchanged");h.assertValueEqual(count(current,Items.STICK),4L,"All original finite unpaid stock remains in native owner");h.assertValueEqual(count(current,Items.EMERALD),0L,"Known catalyst cannot silently consume unknown strategy input");
            h.assertTrue(saved.pendingFanResults!=null&&saved.pendingFanResults.size()==1&&saved.pendingFanResults.getFirst().is(Items.DIAMOND)&&saved.pendingFanResults.getFirst().getCount()==4&&saved.pendingFanInput.matches(input)&&saved.pendingFanCount==4,"Unknown cached output and exact input binding survive native owner reload and 205 actual ticks");
            h.assertValueEqual(calls(input,true),0,"No addon callback re-rolls opaque unpaid progress");h.succeed();
        });
    }

    @GameTest(structure=STRUCTURE)
    public void allFourLegacyAliasesRoundTripThroughCodecAndActualDepotOwner(GameTestHelper h){
        var input=stock(h,"legacy-aliases",2);var owner=depot(h,input);var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for(var type:FanProcessing.Type.values())for(var alias:List.of(type.name(),type.name().toLowerCase(Locale.ROOT),"create:"+type.name().toLowerCase(Locale.ROOT))){
            var transport=new TransportedItemStack(input.copy(),.5f);transport.processedBy=type;transport.processingTime=73;
            transport.pendingFanResults=List.of(new ItemStack(Items.EMERALD,2));transport.pendingFanInput=ItemVariant.of(input);transport.pendingFanCount=2;
            var encoded=(CompoundTag)TransportedItemStack.CODEC.encodeStart(ops,transport).getOrThrow();encoded.putString("FanProcessingType",alias);encoded.remove("PendingFanBinding");
            var decoded=TransportedItemStack.CODEC.parse(ops,encoded).getOrThrow();owner.setTransportedItem(decoded);owner=reload(h,owner);var saved=owner.getTransportedItem();
            h.assertTrue(saved.processedBy==type&&saved.unresolvedFanProcessingType==null&&saved.processingTime==73,"Legacy "+alias+" restores compatible builtin enum identity and timer");
            h.assertTrue(saved.stack.getCount()==2&&saved.stack.get(DataComponents.CUSTOM_NAME).equals(input.get(DataComponents.CUSTOM_NAME))&&saved.pendingFanResults.size()==1&&saved.pendingFanInput.matches(input)&&saved.pendingFanCount==2,"Old pending payload without binding adopts genuine saved original component variant/count");
            var rewritten=(CompoundTag)TransportedItemStack.CODEC.encodeStart(ops,saved).getOrThrow();h.assertValueEqual(rewritten.getStringOr("FanProcessingType",""),FanProcessing.id(type),"Legacy aliases rewrite to stable namespaced registry ID");
        }
        h.succeed();
    }

    @GameTest(structure=STRUCTURE,maxTicks=240)
    public void actualSmokingFanExcludesVanillaRecipeWithManualOnlySuffix(GameTestHelper h){
        var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.SMOKING,new SingleRecipeInput(new ItemStack(Items.PAPER)),h.getLevel());
        h.assertTrue(recipe.isPresent()&&recipe.get().id().identifier().toString().equals("create_test:fan_api_paper_manual_only"),"Actual vanilla recipe manager finds the test manual-only smoking recipe");
        fan(h);h.setBlock(FAN.east(),Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,true));var input=new ItemStack(Items.PAPER,3);var depot=depot(h,input);
        h.runAfterDelay(205,()->{h.assertValueEqual(count(depot,Items.PAPER),3L,"Actual lit-campfire fan retains all original manual-only inputs beyond normal duration");h.assertValueEqual(count(depot,Items.DIAMOND),0L,"No forbidden automation output admitted");h.succeed();});
    }

    @GameTest(structure=STRUCTURE)
    public void builtinEntityCallbacksKeepEndermanExtinguishAndCreateFanDamageSemantics(GameTestHelper h){
        var at=Vec3.atCenterOf(h.absolutePos(FAN));var level=h.getLevel();
        var enderman=EntityTypes.ENDERMAN.create(level,EntitySpawnReason.TRIGGERED);h.assertTrue(enderman!=null,"Actual native Enderman constructs");enderman.setNoAi(true);enderman.setPos(at);h.assertTrue(level.addFreshEntity(enderman),"Native entity manager admits actual Enderman");enderman.igniteForSeconds(10);float health=enderman.getHealth();
        AllFanProcessingTypes.SPLASHING.affectEntity(enderman,level);
        h.assertTrue(!enderman.isOnFire()&&enderman.getHealth()<health&&enderman.getLastDamageSource()!=null&&enderman.getLastDamageSource().is(net.minecraft.world.damagesource.DamageTypes.DROWN),"Source splashing damages actual Enderman with drowning and extinguishes it");
        var smoked=EntityTypes.COW.create(level,EntitySpawnReason.TRIGGERED);var blasted=EntityTypes.COW.create(level,EntitySpawnReason.TRIGGERED);h.assertTrue(smoked!=null&&blasted!=null,"Native damage recipients construct");smoked.setPos(at.add(0,0,3));blasted.setPos(at.add(0,0,6));h.assertTrue(level.addFreshEntity(smoked)&&level.addFreshEntity(blasted),"Native entity manager admits both actual damage recipients");
        AllFanProcessingTypes.SMOKING.affectEntity(smoked,level);AllFanProcessingTypes.BLASTING.affectEntity(blasted,level);
        h.assertTrue(smoked.isOnFire()&&smoked.getLastDamageSource()!=null&&smoked.getLastDamageSource().is(AllDamageTypes.FAN_FIRE),"Smoking uses actual registered Create fan_fire damage type");
        h.assertTrue(blasted.isOnFire()&&blasted.getLastDamageSource()!=null&&blasted.getLastDamageSource().is(AllDamageTypes.FAN_LAVA),"Blasting uses actual registered Create fan_lava damage type");h.succeed();
    }
}
