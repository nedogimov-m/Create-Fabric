package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.content.kinetics.deployer.DeployerCartItemSource;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Prepared debit of one actual canonical item; a FakePlayer avatar alone is never payment. */
public final class CartItemPayment {
    /** Implementations are narrowly bound to actual native inventory or an active canonical deployer action. */
    public interface Source {
        ItemStack actual();
        boolean valid();
        /** Direct inspected fields only; preparedHeld is the already encoded remaining hand. */
        void replacePure(ItemStack remaining,Tag preparedHeld);
        void notifyNative();
    }
    private final Source source;
    private final ItemStack original,expected,receipt,remaining;
    private final Tag preparedHeld;
    private boolean debited;
    private final boolean creativeMaterialization;
    private CartItemPayment(Source source,net.minecraft.core.HolderLookup.Provider registries,boolean creativeMaterialization){
        this.creativeMaterialization=creativeMaterialization;
        this.source=source;original=source.actual();expected=PackedCartData.detachedItemCopy(original);
        // A whole one-item stack moves by identity. For a larger stack this is a prospective split
        // token; it becomes owned only when that exact source count is debited in the native commit.
        receipt=!creativeMaterialization&&original.getCount()==1?original:PackedCartData.detachedOne(original);
        remaining=creativeMaterialization?original:original.getCount()==1?ItemStack.EMPTY:original;
        var encodedRemaining=original.getCount()==1?ItemStack.EMPTY:original.copyWithCount(original.getCount()-1);
        preparedHeld=ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE),encodedRemaining).getOrThrow();
    }
    public static Optional<CartItemPayment> prepare(Player player,InteractionHand hand){
        if(Transaction.getLifecycle()!=Transaction.Lifecycle.NONE)return Optional.empty();
        Source source;boolean creative=false;
        if(player instanceof FakePlayer){source=DeployerCartItemSource.active(player,hand);if(source==null)return Optional.empty();}
        else if(player instanceof ServerPlayer actual){
            var nativeMode=actual.gameMode.getGameModeForPlayer();boolean infinite=actual.hasInfiniteMaterials();creative=nativeMode==net.minecraft.world.level.GameType.CREATIVE&&infinite;
            var inventory=actual.getInventory();int slot=hand==InteractionHand.MAIN_HAND?inventory.getSelectedSlot():net.minecraft.world.entity.player.Inventory.SLOT_OFFHAND;
            source=new Source(){
                @Override public ItemStack actual(){return inventory.getItem(slot);}
                @Override public boolean valid(){return actual.isAlive()&&!actual.isRemoved()&&actual.level().getServer().getPlayerList().getPlayer(actual.getUUID())==actual&&actual.getInventory()==inventory&&actual.gameMode.getGameModeForPlayer()==nativeMode&&actual.hasInfiniteMaterials()==infinite&&(hand!=InteractionHand.MAIN_HAND||inventory.getSelectedSlot()==slot)&&actual.getItemInHand(hand)==actual();}
                @Override public void replacePure(ItemStack remaining,Tag encoded){inventory.setItem(slot,remaining);}
                @Override public void notifyNative(){inventory.setChanged();actual.connection.send(inventory.createInventoryUpdatePacket(slot));}
            };
        }else return Optional.empty();
        if(!source.valid()||source.actual().isEmpty())return Optional.empty();
        return Optional.of(new CartItemPayment(source,player.registryAccess(),creative));
    }
    /** Native dispenser passes the actual slot reference and later writes back the behavior's return value. */
    public static Optional<CartItemPayment> prepareDispenser(net.minecraft.core.dispenser.BlockSource actualSource,ItemStack supplied){
        if(Transaction.getLifecycle()!=Transaction.Lifecycle.NONE||supplied.isEmpty())return Optional.empty();
        var level=actualSource.level();var owner=actualSource.blockEntity();
        if(owner.isRemoved()||com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,actualSource.pos())!=owner||!owner.getBlockState().is(net.minecraft.world.level.block.Blocks.DISPENSER))return Optional.empty();
        var slots=((com.simibubi.create.foundation.mixin.accessor.DispenserInventoryAccessor)owner).create$getItems();int found=-1;
        for(int index=0;index<slots.size();index++)if(slots.get(index)==supplied){if(found!=-1)return Optional.empty();found=index;}
        if(found<0)return Optional.empty();final int slot=found;
        Source source=new Source(){
            @Override public ItemStack actual(){return slots.get(slot);}
            @Override public boolean valid(){return !owner.isRemoved()&&owner.getLevel()==level&&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,actualSource.pos())==owner&&owner.getBlockState().is(net.minecraft.world.level.block.Blocks.DISPENSER)&&((com.simibubi.create.foundation.mixin.accessor.DispenserInventoryAccessor)owner).create$getItems()==slots;}
            @Override public void replacePure(ItemStack remaining,Tag encoded){slots.set(slot,remaining);}
            @Override public void notifyNative(){owner.setChanged();}
        };
        return source.valid()?Optional.of(new CartItemPayment(source,level.registryAccess(),false)):Optional.empty();
    }
    /** Actual native current remainder, so late callbacks are not overwritten by a stale behavior argument. */
    public ItemStack currentRemainder(){return source.actual();}
    public boolean isCreativeMaterialization(){return creativeMaterialization;}
    /** Inactive prospective receipt, exclusively for off-world endpoint construction and preflight. */
    public ItemStack proposedReceipt(){return receipt;}
    public boolean validate(){return !debited&&Transaction.getLifecycle()==Transaction.Lifecycle.NONE&&source.valid()&&source.actual()==original&&ItemStack.matches(original,expected);}
    /** Pure commit only, after complete endpoint/cargo preflight. No callbacks follow from this method. */
    public ItemStack debit(){
        if(!validate())throw new IllegalStateException("Actual canonical cart-item source changed before debit");
        if(!creativeMaterialization){if(original.getCount()>1)original.shrink(1);source.replacePure(remaining,preparedHeld);}
        debited=true;return receipt;
    }
    public void notifyNative(){if(!debited)throw new IllegalStateException("Cannot notify an unpaid cart handoff");source.notifyNative();}
}
