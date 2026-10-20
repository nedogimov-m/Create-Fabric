package com.simibubi.create.content.equipment.blueprint;

import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import com.simibubi.create.test.*;
import com.simibubi.create.test.mixin.GameTestHelperNativeInfoAccessor;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Actual PlayerList player, input inventory transactions, recipe callbacks and cached journal replacement. Runtime UNRUN. */
public final class BlueprintCanonicalDeliveryGameTests {
    private static final BlockPos SOURCE=new BlockPos(3,3,3);
    private static Runnable cleanup(GameTestHelper h,NativeSurvivalTestPlayer joined){var closed=new AtomicBoolean();Runnable cleanup=()->{if(closed.compareAndSet(false,true))joined.close();};((GameTestHelperNativeInfoAccessor)(Object)h).create$getActualNativeTestInfo().addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo info){}public void testPassed(GameTestInfo info,GameTestRunner runner){cleanup.run();}public void testFailed(GameTestInfo info,GameTestRunner runner){cleanup.run();}public void testAddedForRerun(GameTestInfo old,GameTestInfo next,GameTestRunner runner){cleanup.run();}});return cleanup;}
    private static int owned(GameTestHelper h,ServerPlayer player,Item item){int amount=0;var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);for(var tag:BlueprintDeliveryTestFixtures.capture(h.getLevel()).getListOrEmpty("Pending")){var entry=(CompoundTag)tag;if(!player.getUUID().equals(entry.read("Player",UUIDUtil.CODEC).orElse(null)))continue;var loose=entry.read("Loose",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);var pending=entry.getCompoundOrEmpty("Entity").read("Item",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);if(loose.is(item))amount+=loose.getCount();if(pending.is(item))amount+=pending.getCount();}return amount;}
    private static List<CompoundTag> crafts(GameTestHelper h,ServerPlayer player){var result=new ArrayList<CompoundTag>();for(var tag:BlueprintDeliveryTestFixtures.capture(h.getLevel()).getListOrEmpty("Crafts")){var entry=(CompoundTag)tag;if(player.getUUID().equals(entry.read("Player",UUIDUtil.CODEC).orElse(null)))result.add(entry);}return result;}
    private static int drops(GameTestHelper h,Item item){return h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(h.absolutePos(SOURCE)).inflate(7),entity->entity.getItem().is(item)).stream().mapToInt(entity->entity.getItem().getCount()).sum();}
    private static List<ItemStack> grid(Item item){var grid=new ArrayList<ItemStack>(Collections.nCopies(9,ItemStack.EMPTY));grid.set(0,new ItemStack(item));return grid;}
    private static void fill(ServerPlayer player){for(int slot=0;slot<36;slot++)player.getInventory().setItem(slot,new ItemStack(Items.STONE,64));player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.STONE,64));player.getInventory().setItem(0,new ItemStack(Items.OAK_LOG,2));}
    @GameTest public void actualNativeHeadReplacementRefusesOldBlueprintDropAndOnlyCurrentJournalDelivers(GameTestHelper h)throws Exception{
        var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)));var cleanup=cleanup(h,joined);var player=joined.player();fill(player);var old=BlueprintDelivery.get(h.getLevel());var grid=grid(Items.OAK_LOG);var input=CraftingInput.of(3,3,grid);var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();BlueprintDelivery.Receipt receipt;
        try(var tx=Transaction.openOuter()){h.assertTrue(PlayerInventoryStorage.of(player).getSlot(0).extract(ItemVariant.of(Items.OAK_LOG),1,tx)==1,"Literal native source inventory pays one original log");receipt=old.ownCraft(player,recipe.value().assemble(input),recipe,grid,tx);tx.commit();}
        var rejected=new AtomicReference<ItemEntity>();
        try(var observer=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity instanceof ItemEntity item&&item.getItem().is(Items.OAK_PLANKS),entity->{h.assertTrue(NativeEntityAcceptance.isPreparedEndpointInCurrentAdd(entity),"Actual paid result passes native HEAD before canonical replacement");rejected.set((ItemEntity)entity);BlueprintDeliveryTestFixtures.installDecoded(h.getLevel(),BlueprintDeliveryTestFixtures.capture(h.getLevel()));})){
            old.finishCraft(player,receipt,recipe);
        }
        old.deliver(player,receipt);
        h.assertTrue(rejected.get()!=null&&!old.owns(receipt)&&h.getLevel().getEntity(rejected.get().getUUID())==null&&player.getInventory().countItem(Items.OAK_LOG)==1&&player.getInventory().countItem(Items.OAK_PLANKS)==0&&drops(h,Items.OAK_PLANKS)==0&&owned(h,player,Items.OAK_PLANKS)==4,"Retired native add and explicit old-publisher retry leave exactly one paid current result and preserve actual original log remainder");
        h.startSequence().thenWaitUntil(()->h.assertTrue(owned(h,player,Items.OAK_PLANKS)==0&&drops(h,Items.OAK_PLANKS)==4&&player.getInventory().countItem(Items.OAK_LOG)==1,"Actual registered current-journal tick publishes four paid native planks once with no second source debit")).thenExecute(cleanup).thenSucceed();
    }
    @GameTest public void actualRetiredLooseBlueprintReceiptCannotExtractOrPublishAnotherPaidCraft(GameTestHelper h){
        var joined=NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(SOURCE)));var cleanup=cleanup(h,joined);
        {
            var player=joined.player();player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(Items.OAK_LOG,3));var old=BlueprintDelivery.get(h.getLevel());var grid=grid(Items.OAK_LOG);var input=CraftingInput.of(3,3,grid);var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();BlueprintDelivery.Receipt receipt;
            try(var tx=Transaction.openOuter()){h.assertTrue(PlayerInventoryStorage.of(player).getSlot(0).extract(ItemVariant.of(Items.OAK_LOG),1,tx)==1,"Genuine first original input payment");receipt=old.ownCraft(player,recipe.value().assemble(input),recipe,grid,tx);tx.commit();}
            h.assertTrue(receipt.getAmount()==4&&receipt.pending==null,"Actual old receipt still contains four loose source items before replacement");BlueprintDeliveryTestFixtures.installDecoded(h.getLevel(),BlueprintDeliveryTestFixtures.capture(h.getLevel()));
            try(var tx=Transaction.openOuter()){h.assertTrue(receipt.extract(ItemVariant.of(Items.OAK_PLANKS),4,tx)==0,"Genuine SingleStackStorage extraction refuses retired owner even though its physical old stack is nonempty");tx.commit();}
            boolean refused=false;
            try(var tx=Transaction.openOuter()){h.assertTrue(PlayerInventoryStorage.of(player).getSlot(0).extract(ItemVariant.of(Items.OAK_LOG),1,tx)==1,"Second attempt touches the actual remaining source inventory transaction");old.ownCraft(player,recipe.value().assemble(input),recipe,grid,tx);tx.commit();}catch(IllegalStateException failure){refused=failure.getMessage().contains("Retired blueprint journal");}
            old.finishCraft(player,receipt,recipe);old.deliver(player,receipt);
            h.assertTrue(refused&&player.getInventory().countItem(Items.OAK_LOG)==2&&player.getInventory().countItem(Items.OAK_PLANKS)==0&&receipt.getAmount()==4&&owned(h,player,Items.OAK_PLANKS)==4&&crafts(h,player).size()==1,"Actual aborted native inventory payment restores the exact remainder; retired old receipt/debt cannot extract, craft or deliver current paid stock");
            h.startSequence().thenWaitUntil(()->h.assertTrue(player.getInventory().countItem(Items.OAK_LOG)==2&&player.getInventory().countItem(Items.OAK_PLANKS)==4&&owned(h,player,Items.OAK_PLANKS)==0&&crafts(h,player).isEmpty(),"The current actual journal alone completes and delivers its one previously paid recipe without leaking pending test custody")).thenExecute(cleanup).thenSucceed();
        }
    }
    private static BlueprintEntity blueprint(GameTestHelper h){var pos=h.absolutePos(SOURCE);h.getLevel().setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState());var entity=new BlueprintEntity(h.getLevel(),pos,Direction.SOUTH,Direction.DOWN);h.assertTrue(entity.size()==1&&h.getLevel().addFreshEntity(entity),"Actual source blueprint admitted");var ghosts=new SimpleContainer(11);ghosts.setItem(0,new ItemStack(BlueprintTestFixtures.NATIVE_MILK));ghosts.setItem(9,new ItemStack(BlueprintTestFixtures.OUTPUT));entity.getSection(0).save(ghosts,false);return entity;}
    private static void callbackReplacement(GameTestHelper h,BlueprintTestFixtures.Mode mode){
        var blueprint=blueprint(h);var joined=NativeSurvivalTestPlayer.join(h,blueprint.position().add(0,0,2));var cleanup=cleanup(h,joined);var player=joined.player();player.getInventory().clearContent();player.getInventory().setItem(0,new ItemStack(BlueprintTestFixtures.NATIVE_MILK));var old=BlueprintDelivery.get(h.getLevel());var observation=new BlueprintTestFixtures.Observation();var error=new BlueprintTestFixtures.NativeRefusal();boolean exactError=false;
        try{BlueprintTestFixtures.with(new BlueprintTestFixtures.Scope(player,blueprint.getSection(0),mode,error,observation),()->player.interactOn(blueprint,InteractionHand.MAIN_HAND,Vec3.ZERO));}catch(BlueprintTestFixtures.NativeRefusal failure){exactError=failure==error;}
        boolean replacedDuringHook=mode!=BlueprintTestFixtures.Mode.REPLACE_ON_REMAINDER;
        h.assertTrue(BlueprintDelivery.get(h.getLevel())!=old&&observation.calls==1&&observation.remainders==(replacedDuringHook?0:1)&&exactError==(mode==BlueprintTestFixtures.Mode.REPLACE_ON_CRAFTED_ERROR)&&player.getInventory().countItem(BlueprintTestFixtures.NATIVE_MILK)==0&&player.getInventory().countItem(BlueprintTestFixtures.OUTPUT)==0&&player.getInventory().countItem(Items.BUCKET)==0&&owned(h,player,BlueprintTestFixtures.OUTPUT)==4&&owned(h,player,Items.BUCKET)==0,"Retired actual callback frame preserves source-order hook attempt and exact error; it cannot continue callbacks, publish remainders, retire debt or deliver copied output after native cache replacement");
        var crafts=crafts(h,player);h.assertTrue(crafts.size()==1&&crafts.getFirst().getBooleanOr("HookAttempted",false),"Current journal alone retains the exact already-attempted paid native craft continuation");
        h.startSequence().thenWaitUntil(()->h.assertTrue(player.getInventory().countItem(BlueprintTestFixtures.OUTPUT)==4&&player.getInventory().countItem(Items.BUCKET)==1&&owned(h,player,BlueprintTestFixtures.OUTPUT)==0&&crafts(h,player).isEmpty(),"Actual replacement world tick finishes genuine native recipe remainders and delivers exactly one original paid result plus one bucket")).thenExecute(()->{h.assertTrue(observation.calls==1,"Current loaded debt never repeats the original actual crafted hook");cleanup.run();}).thenSucceed();
    }
    @GameTest public void actualNativeCraftedCacheReplacementStopsOldDebtBeforeRemainingCallback(GameTestHelper h){callbackReplacement(h,BlueprintTestFixtures.Mode.REPLACE_ON_CRAFTED);}
    @GameTest public void actualNativeCraftedErrorStillPropagatesWhenCallbackReplacesCanonicalDebtOwner(GameTestHelper h){callbackReplacement(h,BlueprintTestFixtures.Mode.REPLACE_ON_CRAFTED_ERROR);}
    @GameTest public void actualNativeRemainingCacheReplacementStopsOldPublicationAndDebtCommit(GameTestHelper h){callbackReplacement(h,BlueprintTestFixtures.Mode.REPLACE_ON_REMAINDER);}
}
