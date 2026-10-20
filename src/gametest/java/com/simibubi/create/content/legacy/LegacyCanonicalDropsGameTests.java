package com.simibubi.create.content.legacy;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import com.simibubi.create.test.CartAcceptanceTestFixtures;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Actual original ItemEntity source payment, transaction rollback and native cached ownership. Runtime UNRUN. */
public final class LegacyCanonicalDropsGameTests {
    private static LegacyMaterialDrops.Receipt paid(GameTestHelper h,LegacyMaterialDrops data,ItemEntity source){try(var tx=Transaction.openOuter()){var part=new LegacyCompoundSource(source,source.getItem()).takeOne(tx);part.set(AllDataComponents.CHROMATIC_COMPOUND_COLLECTING_LIGHT,1);var receipt=data.own(part,source.position(),Vec3.ZERO,40,true,tx);tx.commit();return receipt;}}
    private static int owned(GameTestHelper h){int amount=0;var ops=h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);for(var tag:LegacyMaterialTestFixtures.capture(h.getLevel()).getListOrEmpty("Pending")){var entry=(CompoundTag)tag;if(!Vec3.atCenterOf(h.absolutePos(LegacyMaterialGameTests.SOURCE)).equals(entry.read("Pos",Vec3.CODEC).orElse(null)))continue;var loose=entry.read("Loose",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);var pending=entry.getCompoundOrEmpty("Entity").read("Item",ItemStack.OPTIONAL_CODEC,ops).orElse(ItemStack.EMPTY);if(loose.is(AllItems.CHROMATIC_COMPOUND))amount+=loose.getCount();if(pending.is(AllItems.CHROMATIC_COMPOUND))amount+=pending.getCount();}return amount;}
    private static long charged(GameTestHelper h){return LegacyMaterialGameTests.nearby(h,AllItems.CHROMATIC_COMPOUND).stream().filter(entity->AllItems.CHROMATIC_COMPOUND.getLight(entity.getItem())==1).mapToLong(entity->entity.getItem().getCount()).sum();}
    @GameTest public void actualNativeHeadReplacementRejectsRetiredLegacyReceiptWithoutRefundingPayment(GameTestHelper h)throws Exception{
        var source=LegacyMaterialGameTests.source(h,5,0);var original=source.getItem();var old=LegacyMaterialDrops.get(h.getLevel());var receipt=paid(h,old,source);var rejected=new AtomicReference<ItemEntity>();
        try(var observer=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity instanceof ItemEntity item&&AllItems.CHROMATIC_COMPOUND.getLight(item.getItem())==1,entity->{h.assertTrue(NativeEntityAcceptance.isPreparedEndpointInCurrentAdd(entity),"The real paid native endpoint passed HEAD source validation before cache replacement");rejected.set((ItemEntity)entity);LegacyMaterialTestFixtures.decode(h.getLevel(),LegacyMaterialTestFixtures.capture(h.getLevel()));})){
            old.emit(h.getLevel(),receipt);
        }
        h.assertTrue(rejected.get()!=null&&source.getItem()==original&&original.getCount()==4&&charged(h)==0&&owned(h)==1,"Actual original compound pays once; replacement retains its sole paid output and retired native admission refuses");
        old.emit(h.getLevel(),receipt);h.assertTrue(charged(h)==0&&original.getCount()==4,"Explicit retry on the retired real publisher cannot publish another native endpoint or refund original source stock");
        h.startSequence().thenWaitUntil(()->h.assertTrue(charged(h)==1&&owned(h)==0&&source.getItem()==original&&original.getCount()==4,"Actual current journal tick admits only its one retained paid charged part with original physical source remainder unchanged")).thenSucceed();
    }
    @GameTest public void retiredLegacyJournalRefusesNewPaymentAndActualSourceTransactionRollsBack(GameTestHelper h){
        var source=LegacyMaterialGameTests.source(h,5,0);var original=source.getItem();var old=LegacyMaterialDrops.get(h.getLevel());var receipt=paid(h,old,source);
        LegacyMaterialTestFixtures.decode(h.getLevel(),LegacyMaterialTestFixtures.capture(h.getLevel()));boolean refused=false;
        try(var tx=Transaction.openOuter()){var unpaid=new LegacyCompoundSource(source,original).takeOne(tx);old.own(unpaid,source.position(),Vec3.ZERO,40,tx);tx.commit();}catch(IllegalStateException failure){refused=failure.getMessage().contains("Retired legacy material journal");}
        old.emit(h.getLevel(),receipt);
        h.assertTrue(refused&&source.getItem()==original&&original.getCount()==4&&charged(h)==0&&owned(h)==1,"Retired owner refuses before output publication; genuine original source count rolls back in its Fabric transaction, while replacement owns exactly the earlier paid part");
        h.startSequence().thenWaitUntil(()->h.assertTrue(charged(h)==1&&owned(h)==0&&source.getItem()==original&&original.getCount()==4,"Current registered journal tick drains exactly the earlier paid part and leaves no extra pending test custody")).thenSucceed();
    }
}
