package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour;
import com.simibubi.create.content.kinetics.press.PressingWorldOutputRecovery;
import com.simibubi.create.foundation.mixin.accessor.EntitySectionVisibilityAccessor;
import com.simibubi.create.foundation.mixin.accessor.ServerLevelEntityManagerAccessor;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.ConfigScope;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.phys.Vec3;

/** Real RecipeManager resources, source ItemEntities, native section hooks and actual world SavedData. UNRUN. */
public final class PressingWorldOutputGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos P=new BlockPos(6,6,6);
    private record Fixture(MechanicalPressBlockEntity press,ItemEntity input,Vec3 position){}
    private static Fixture fixture(GameTestHelper h,Item item,int amount){
        h.setBlock(P.below(2),Blocks.STONE);
        h.setBlock(P,AllBlocks.MECHANICAL_PRESS.defaultBlockState().setValue(MechanicalPressBlock.FACING,Direction.EAST));
        h.setBlock(P.west(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.EAST));
        h.getBlockEntity(P.west(),KineticBlockEntity.class).setGeneratedSpeed(64);
        var press=h.getBlockEntity(P,MechanicalPressBlockEntity.class);press.initialize();KineticNetworks.tick(h.getLevel());
        var position=Vec3.atLowerCornerOf(h.absolutePos(P.below())).add(.5,.1,.5);
        var input=new ItemEntity(h.getLevel(),position.x,position.y,position.z,new ItemStack(item,amount),0,0,0);
        input.setOnGround(true);input.setNoGravity(true);
        h.assertTrue(h.getLevel().addFreshEntity(input),"The real original physical input is natively admitted before processing");
        var cycle=press.getPressingBehaviour();cycle.start(PressingBehaviour.Mode.WORLD);cycle.runningTicks=cycle.prevRunningTicks=120;
        return new Fixture(press,input,position);
    }
    private static ItemEntity blocker(GameTestHelper h,Fixture f){
        var p=f.position.add(2,0,0);var item=new ItemEntity(h.getLevel(),p.x,p.y,p.z,new ItemStack(Items.STONE,7),0,0,0);item.setNoGravity(true);
        h.assertTrue(h.getLevel().addFreshEntity(item),"A separate finite actual native UUID owner exists");return item;
    }
    private static long physical(GameTestHelper h,Item item){
        return h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds().inflate(1)).stream().filter(entity->entity.getItem().is(item)).mapToLong(entity->entity.getItem().getCount()).sum();
    }
    private static CompoundTag journal(GameTestHelper h){
        var data=h.getLevel().getDataStorage().computeIfAbsent(PressingWorldOutputRecovery.TYPE);
        return (CompoundTag)PressingWorldOutputRecovery.CODEC.encodeStart(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),data).getOrThrow();
    }
    private static List<CompoundTag> journalAt(GameTestHelper h,Fixture f){
        var result=new ArrayList<CompoundTag>();var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        for(var tag:journal(h).getListOrEmpty("Pending"))if(tag instanceof CompoundTag entry
            &&Vec3.CODEC.parse(ops,entry.get("Position")).getOrThrow().distanceToSqr(f.position)<1e-10)result.add(entry);
        return result;
    }
    @FunctionalInterface private interface Checked {void run()throws Exception;}
    private static void single(Checked body)throws Exception{
        var setting=AllConfigs.server().recipes.bulkPressing;boolean previous=setting.get();
        AllConfigs.store(ConfigScope.SERVER).importValues(Map.of(setting.key(),false),false);
        try{body.run();}finally{AllConfigs.store(ConfigScope.SERVER).importValues(Map.of(setting.key(),previous),false);}
    }

    @GameTest(structure=STRUCTURE) public void actualWorldPaymentAndImpactAreSavedBeforeNativeCallbackReentryAndOwnerReload(GameTestHelper h)throws Exception{
        single(()->{
            var f=fixture(h,Items.IRON_INGOT,3);var restored=new AtomicReference<MechanicalPressBlockEntity>();
            try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity.level()==h.getLevel()&&entity.position().distanceToSqr(f.position)<1e-10&&entity instanceof ItemEntity item&&item.getItem().is(AllItems.IRON_SHEET),entity->{
                h.assertTrue(NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(entity),"Payment is witnessed by actual native section insertion before onCreated");
                var saved=f.press.saveWithFullMetadata(h.getLevel().registryAccess());
                h.assertTrue(f.input.getItem().getCount()==2&&saved.getBooleanOr("Pressed",false)&&!saved.contains("PendingOutputs"),"Exact real source debit, impact and unpaid-roll revocation precede observable callback serialization");
                h.assertFalse(f.press.tryProcessInWorld(f.input,false),"Same-source callback reentry cannot debit the original physical remainder again");
                var loaded=(MechanicalPressBlockEntity)BlockEntity.loadStatic(f.press.getBlockPos(),f.press.getBlockState(),saved,h.getLevel().registryAccess());
                h.getLevel().removeBlockEntity(f.press.getBlockPos());h.getLevel().setBlockEntity(loaded);loaded.initialize();restored.set(loaded);
            })){
                h.assertTrue(f.press.tryProcessInWorld(f.input,false),"The original paid world action reports genuine historical admission despite owner reload");
            }
            h.assertTrue(restored.get()!=null&&physical(h,AllItems.IRON_SHEET)==1&&f.input.getItem().getCount()==2,"One real output and two original ingots survive callback reload");
            var live=restored.get();live.getPressingBehaviour().runningTicks=120;
            MechanicalPressBlockEntity.tick(h.getLevel(),live.getBlockPos(),live.getBlockState(),live);
            h.assertTrue(live.getPressingBehaviour().hasAppliedImpact()&&physical(h,AllItems.IRON_SHEET)==1&&f.input.getItem().getCount()==2,"Actual reloaded midpoint cannot replay the already-paid impact");
        });h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualNativeUuidRefusalKeepsPhysicalInputAndExactlyTheSameChanceRolls(GameTestHelper h)throws Exception{
        single(()->{
            var f=fixture(h,Items.BEETROOT,3);var blocker=blocker(h,f);h.getLevel().getRandom().setSeed(1234);
            h.assertTrue(f.press.tryProcessInWorld(f.input,true)&&!f.press.saveWithFullMetadata(h.getLevel().registryAccess()).contains("PendingOutputs"),"Genuine simulation performs no actual recipe roll or source debit");
            CompoundTag previous=null;
            for(int attempt=0;attempt<2;attempt++){
                try(var refusal=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity.level()==h.getLevel()&&entity.position().distanceToSqr(f.position)<1e-10&&entity instanceof ItemEntity item&&item.getItem().is(Items.DIAMOND),entity->entity.setUUID(blocker.getUUID()))){
                    h.assertFalse(f.press.tryProcessInWorld(f.input,false),"The real native duplicate-UUID check refuses the prospective output");
                }
                var saved=f.press.saveWithFullMetadata(h.getLevel().registryAccess());
                h.assertTrue(f.input.getItem().getCount()==3&&!f.press.getPressingBehaviour().hasAppliedImpact()&&journalAt(h,f).isEmpty()&&physical(h,Items.DIAMOND)==0&&physical(h,Items.EMERALD)==0,"No source payment or paid journal is manufactured on full native refusal");
                if(previous!=null)h.assertValueEqual(saved.getListOrEmpty("PendingOutputs"),previous.getListOrEmpty("PendingOutputs"),"Actual persisted chance results are not re-rolled while native admission refuses");
                previous=saved;
            }
            var rolled=previous.getListOrEmpty("PendingOutputs").stream().map(tag->ItemStack.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE),tag).getOrThrow()).filter(stack->stack.is(Items.DIAMOND)).mapToInt(ItemStack::getCount).sum();
            blocker.discard();h.assertTrue(f.press.tryProcessInWorld(f.input,false),"The same real source can retry after native refusal clears");
            h.assertTrue(rolled>0&&physical(h,Items.DIAMOND)==rolled&&physical(h,Items.EMERALD)==3&&f.input.getItem().getCount()==2,"Exactly the original rolled outputs are delivered for one finite original input");
        });h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualSourceComponentMutationBetweenNativeHeadAndSectionCannotDebitOrRestoreACopy(GameTestHelper h)throws Exception{
        single(()->{
            var f=fixture(h,Items.IRON_INGOT,3);var original=f.input.getItem();
            try(var mutation=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity.level()==h.getLevel()&&entity.position().distanceToSqr(f.position)<1e-10&&entity instanceof ItemEntity item&&item.getItem().is(AllItems.IRON_SHEET),entity->{
                original.setCount(5);original.set(DataComponents.CUSTOM_NAME,Component.literal("Actual callback-owned remainder"));
            })){
                h.assertFalse(f.press.tryProcessInWorld(f.input,false),"Final actual pre-section source validation rejects changed count/components");
            }
            h.assertTrue(f.input.getItem()==original&&original.getCount()==5&&original.getHoverName().getString().equals("Actual callback-owned remainder")&&physical(h,AllItems.IRON_SHEET)==0&&journalAt(h,f).isEmpty(),"Refusal preserves the actual callback mutation and never restores a stale original copy");
            h.assertTrue(f.press.tryProcessInWorld(f.input,false)&&original.getCount()==4&&physical(h,AllItems.IRON_SHEET)==1,"A fresh original witness processes the current physical source exactly once");
        });h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualSourceStackReplacementBetweenNativeHeadAndSectionCannotPayFromTheDetachedOldObject(GameTestHelper h)throws Exception{
        single(()->{
            var f=fixture(h,Items.IRON_INGOT,3);var old=f.input.getItem();var replacement=new ItemStack(Items.IRON_INGOT,9);
            try(var mutation=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity.level()==h.getLevel()&&entity.position().distanceToSqr(f.position)<1e-10&&entity instanceof ItemEntity item&&item.getItem().is(AllItems.IRON_SHEET),entity->f.input.setItem(replacement))){
                h.assertFalse(f.press.tryProcessInWorld(f.input,false),"Actual source stack identity replacement refuses prepared native payment");
            }
            h.assertTrue(old.getCount()==3&&f.input.getItem()==replacement&&replacement.getCount()==9&&physical(h,AllItems.IRON_SHEET)==0&&journalAt(h,f).isEmpty(),"Neither detached old object nor actual replacement is debited, copied back or dropped");
            h.assertTrue(f.press.tryProcessInWorld(f.input,false)&&replacement.getCount()==8&&physical(h,AllItems.IRON_SHEET)==1,"Actual current replacement pays one later output");
        });h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualNativeReadinessLossDuringAdmissionLeavesOriginalInputAndCanRetryAfterVisibilityReturns(GameTestHelper h)throws Exception{
        single(()->{
            var f=fixture(h,Items.IRON_INGOT,3);var manager=((ServerLevelEntityManagerAccessor)h.getLevel()).create$getEntityManager();
            var visibility=((EntitySectionVisibilityAccessor)manager).create$getChunkVisibility();var key=ChunkPos.pack(f.input.blockPosition());var original=visibility.get(key);
            h.assertTrue(original!=null&&original.isAccessible(),"Actual native source entity chunk begins accessible");
            try{
                try(var mutation=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity.level()==h.getLevel()&&entity.position().distanceToSqr(f.position)<1e-10&&entity instanceof ItemEntity item&&item.getItem().is(AllItems.IRON_SHEET),entity->manager.updateChunkStatus(ChunkPos.unpack(key),Visibility.HIDDEN))){
                    h.assertFalse(f.press.tryProcessInWorld(f.input,false),"Native section readiness change vetoes payment before actual output insertion");
                }
                h.assertTrue(f.input.getItem().getCount()==3&&journalAt(h,f).isEmpty()&&!f.press.getPressingBehaviour().hasAppliedImpact(),"Hidden data cannot create a paid output or consume the physical source");
            }finally{manager.updateChunkStatus(ChunkPos.unpack(key),original);}
            h.assertTrue(h.getLevel().getEntity(f.input.getUUID())==f.input&&physical(h,AllItems.IRON_SHEET)==0,"Actual original native identity and query visibility return without a fabricated output");
            h.assertTrue(f.press.tryProcessInWorld(f.input,false)&&f.input.getItem().getCount()==2&&physical(h,AllItems.IRON_SHEET)==1,"Actual source retries once ready without any chunk ticket or refund");
        });h.succeed();
    }

    @GameTest(structure=STRUCTURE,maxTicks=80) public void actualPartialMultiOutputSurvivesNativeRefusalPressRemovalAndRealSavedDataDiskReload(GameTestHelper h)throws Exception{
        var held=new AtomicReference<Fixture>();
        single(()->{
            var f=fixture(h,Items.CARROT,2);held.set(f);var blocker=blocker(h,f);var refusal=new AtomicReference<AutoCloseable>();
            try{
                try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity.level()==h.getLevel()&&entity.position().distanceToSqr(f.position)<1e-10&&entity instanceof ItemEntity item&&item.getItem().is(Items.DIAMOND),entity->{
                    h.assertTrue(NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(entity)&&f.input.getItem().getCount()==1&&journalAt(h,f).size()==1,"First actual admission atomically owns one paid pending endpoint before callback removal");
                    refusal.set(CartAcceptanceTestFixtures.beforeNativeUuidCheck(candidate->candidate.level()==h.getLevel()&&candidate.position().distanceToSqr(f.position)<1e-10&&candidate instanceof ItemEntity item&&item.getItem().is(Items.EMERALD),candidate->candidate.setUUID(blocker.getUUID())));
                    h.getLevel().destroyBlock(f.press.getBlockPos(),false);
                })){
                    h.assertTrue(f.press.tryProcessInWorld(f.input,false),"Actual first accepted output pays the finite batch even when later native output refuses");
                }
            }finally{if(refusal.get()!=null)refusal.get().close();}
            h.assertTrue(f.press.isRemoved()&&f.input.getItem().getCount()==1&&physical(h,Items.DIAMOND)==2&&physical(h,Items.EMERALD)==0&&journalAt(h,f).size()==1,"Only the unaccepted paid remainder is retained after native source removal");
            h.getLevel().getDataStorage().saveAndJoin();var dimension=h.getLevel().dimension().identifier();
            var path=h.getLevel().getServer().getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(dimension.getNamespace()).resolve(dimension.getPath()).resolve("data");
            try(var disk=new SavedDataStorage(path,net.minecraft.util.datafix.DataFixers.getDataFixer(),h.getLevel().registryAccess())){
                var restored=disk.get(PressingWorldOutputRecovery.TYPE);h.assertTrue(restored!=null,"Actual native SavedDataStorage reads paid pending output from disk");
                h.getLevel().getDataStorage().set(PressingWorldOutputRecovery.TYPE,restored);
            }
            h.assertTrue(journalAt(h,f).size()==1&&physical(h,Items.DIAMOND)==2&&physical(h,Items.EMERALD)==0,"Exact rejected endpoint survives actual storage reload without republishing accepted output");
            blocker.discard();
        });
        h.succeedWhen(()->{
            var f=held.get();h.assertTrue(f.input.getItem().getCount()==1&&physical(h,Items.DIAMOND)==2&&physical(h,Items.EMERALD)==3&&journalAt(h,f).isEmpty(),"Actual end-level-tick recovery delivers precisely the paid missing endpoint after source removal and disk reload");
        });
    }

    @GameTest(structure=STRUCTURE,maxTicks=80) public void actualJournalReplacementInsideNativeCallbackStopsOldPublisherAndKeepsTheCurrentPaidOwner(GameTestHelper h)throws Exception{
        var held=new AtomicReference<Fixture>();
        single(()->{
            var f=fixture(h,Items.CARROT,2);held.set(f);var original=h.getLevel().getDataStorage().computeIfAbsent(PressingWorldOutputRecovery.TYPE);
            try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity.level()==h.getLevel()&&entity.position().distanceToSqr(f.position)<1e-10&&entity instanceof ItemEntity item&&item.getItem().is(Items.DIAMOND),entity->{
                var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
                var loaded=PressingWorldOutputRecovery.CODEC.parse(ops,journal(h).copy()).getOrThrow();
                h.getLevel().getDataStorage().set(PressingWorldOutputRecovery.TYPE,loaded);
                h.assertTrue(loaded!=original&&journalAt(h,f).size()==1&&f.press.saveWithFullMetadata(h.getLevel().registryAccess()).getBooleanOr("Pressed",false),"Actual canonical SavedData owner is reloaded inside the observable paid callback");
            })){
                h.assertTrue(f.press.tryProcessInWorld(f.input,false),"Historical first native acceptance remains paid after canonical owner replacement");
            }
            h.assertTrue(physical(h,Items.DIAMOND)==2&&physical(h,Items.EMERALD)==0&&journalAt(h,f).size()==1,"Old live journal cannot publish its detached pending endpoints after callback replacement");
            h.getLevel().destroyBlock(f.press.getBlockPos(),false);
        });
        h.succeedWhen(()->{
            var f=held.get();h.assertTrue(f.input.getItem().getCount()==1&&physical(h,Items.DIAMOND)==2&&physical(h,Items.EMERALD)==3&&journalAt(h,f).isEmpty(),"Only the current real loaded paid journal publishes the missing endpoint");
        });
    }

    @GameTest(structure=STRUCTURE) public void actualAcceptedEndpointConsumedInsideNativeCallbackIsNeverRefundedOrReissued(GameTestHelper h)throws Exception{
        single(()->{
            var f=fixture(h,Items.IRON_INGOT,3);h.setBlock(P.east(2),Blocks.BARREL);var barrel=h.getBlockEntity(P.east(2),BarrelBlockEntity.class);
            try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity.level()==h.getLevel()&&entity.position().distanceToSqr(f.position)<1e-10&&entity instanceof ItemEntity item&&item.getItem().is(AllItems.IRON_SHEET),entity->{
                var endpoint=(ItemEntity)entity;h.assertTrue(NativeEntityAcceptance.isAcceptedSourceInCurrentAdd(endpoint),"Endpoint really belongs to the native section before its actual material is consumed");
                barrel.setItem(0,endpoint.getItem());endpoint.setItem(ItemStack.EMPTY);endpoint.discard();
            })){
                h.assertTrue(f.press.tryProcessInWorld(f.input,false),"Actual historical insertion wins over later endpoint removal and return-value heuristics");
            }
            PressingWorldOutputRecovery.tick(h.getLevel());
            h.assertTrue(f.input.getItem().getCount()==2&&barrel.getItem(0).is(AllItems.IRON_SHEET)&&barrel.getItem(0).getCount()==1&&physical(h,AllItems.IRON_SHEET)==0&&journalAt(h,f).isEmpty(),"One actual barrel owns the accepted output; source and recovery cannot duplicate or refund it");
        });h.succeed();
    }

    @GameTest(structure=STRUCTURE) public void actualEmptyChanceOutputPaysOneOriginalInputWithoutManufacturingAnEndpoint(GameTestHelper h)throws Exception{
        single(()->{
            var f=fixture(h,Items.POTATO,3);var random=h.getLevel().getRandom();random.setSeed(601);
            h.assertTrue(random.nextFloat()>0,"Actual controlled native RNG roll produces no zero-chance output");random.setSeed(601);
            h.assertTrue(f.press.tryProcessInWorld(f.input,false),"Actual native empty-output recipe still consumes its one finite original input");
            h.assertTrue(f.input.getItem().getCount()==2&&physical(h,Items.DIAMOND)==0&&journalAt(h,f).isEmpty()&&f.press.getPressingBehaviour().hasAppliedImpact(),"No fake endpoint, paid lost-stock cache or uncommitted source impact exists for an empty result");
        });h.succeed();
    }

    @GameTest(structure=STRUCTURE,maxTicks=80) public void actualRejectedPaidEndpointRetainsOnlyItsCurrentRemainderAfterNativeCallbackConsumption(GameTestHelper h)throws Exception{
        var held=new AtomicReference<Fixture>();var destination=new AtomicReference<BarrelBlockEntity>();
        single(()->{
            var f=fixture(h,Items.CARROT,2);held.set(f);h.setBlock(P.east(2),Blocks.BARREL);var barrel=h.getBlockEntity(P.east(2),BarrelBlockEntity.class);destination.set(barrel);
            var mutation=new AtomicReference<AutoCloseable>();
            try{
                try(var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity.level()==h.getLevel()&&entity.position().distanceToSqr(f.position)<1e-10&&entity instanceof ItemEntity item&&item.getItem().is(Items.DIAMOND),entity->{
                    mutation.set(CartAcceptanceTestFixtures.beforeNativeUuidCheck(candidate->candidate.level()==h.getLevel()&&candidate.position().distanceToSqr(f.position)<1e-10&&candidate instanceof ItemEntity item&&item.getItem().is(Items.EMERALD),candidate->{
                        var actual=(ItemEntity)candidate;barrel.setItem(0,actual.getItem().split(1));actual.discard();
                    }));
                    h.getLevel().destroyBlock(f.press.getBlockPos(),false);
                })){
                    h.assertTrue(f.press.tryProcessInWorld(f.input,false),"The first accepted native endpoint already paid this finite multi-output batch");
                }
            }finally{if(mutation.get()!=null)mutation.get().close();}
            var remaining=journalAt(h,f);h.assertTrue(remaining.size()==1,"Exactly one current rejected paid remainder remains owned");
            var loose=remaining.getFirst().read("Loose",ItemStack.OPTIONAL_CODEC,h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE)).orElseThrow();
            h.assertTrue(loose.is(Items.EMERALD)&&loose.getCount()==2&&barrel.getItem(0).getCount()==1&&physical(h,Items.EMERALD)==0&&physical(h,Items.DIAMOND)==2,"Actual callback split is preserved without restoring the original three-emerald copy");
        });
        h.succeedWhen(()->{
            var f=held.get();h.assertTrue(f.input.getItem().getCount()==1&&physical(h,Items.DIAMOND)==2&&physical(h,Items.EMERALD)==2&&destination.get().getItem(0).getCount()==1&&journalAt(h,f).isEmpty(),"Native retry transfers only the two actual paid leftover emeralds; the real barrel still owns its consumed third");
        });
    }
}
