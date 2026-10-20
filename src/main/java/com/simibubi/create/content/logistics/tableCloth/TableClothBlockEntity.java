package com.simibubi.create.content.logistics.tableCloth;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.redstoneRequester.AutoRequestData;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;

public final class TableClothBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider,Clearable {
    public InventorySummary clientStock=new InventorySummary();
    public AutoRequestData requestData=new AutoRequestData();
    public final List<ItemStack> manuallyAddedItems=new ArrayList<>();
    public final TableClothFilteringBehaviour priceTag=new TableClothFilteringBehaviour(this);
    public UUID owner;
    public Direction facing=Direction.SOUTH;
    public boolean sideOccluded;
    private int lazyTicks;
    public TableClothBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.TABLE_CLOTH,pos,state);}
    public boolean isShop(){return !requestData.encodedRequest().isEmpty();}
    public ItemStack getPaymentItem(){return priceTag.getFilter();}
    public int getPaymentAmount(){return getPaymentItem().isEmpty()?1:priceTag.count;}
    public List<ItemStack> getItemsForRender(){return isShop()?requestData.encodedRequest().stacks().stream().limit(4).map(entry->entry.stack).toList():List.copyOf(manuallyAddedItems);}
    public StockTickerBlockEntity getTicker(){
        if(level==null||!requestData.isValid()||!requestData.targetOffset().closerThan(BlockPos.ZERO,128)||!requestData.targetDim().equals(level.dimension().identifier().toString()))return null;
        return LoadedLevelAccess.blockEntity(level,worldPosition.offset(requestData.targetOffset())) instanceof StockTickerBlockEntity ticker?ticker:null;
    }
    public void tick(){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;

        if(!getBlockState().getValue(TableClothBlock.HAS_BE)){level.removeBlockEntity(worldPosition);return;}
        if(lazyTicks--<=0){lazyTicks=10;var neighbor=worldPosition.relative(facing);var state=LoadedLevelAccess.blockState(level,neighbor);var support=LoadedLevelAccess.blockState(level,neighbor.below());sideOccluded=state.getBlock() instanceof TableClothBlock||Block.isFaceFull(support.getOcclusionShape(),facing.getOpposite());}
    
        }
    }
    public Vec3 pricePosition(){
        var local=sideOccluded?new Vec3(8/16.,.75/16,15.25/16):new Vec3(12/16.,-2.75/16,16.75/16);
        return local.subtract(.5,.5,.5).yRot((float)Math.toRadians(-facing.toYRot())).add(.5,.5,.5);
    }
    public boolean targetsPriceTag(BlockHitResult hit){return isShop()&&hit.getLocation().subtract(Vec3.atLowerCornerOf(worldPosition)).distanceTo(pricePosition())<.25;}
    public boolean use(Player player){
        if(isShop())return useShop(player);var held=player.getMainHandItem();
        if(held.isEmpty()){
            if(manuallyAddedItems.isEmpty())return true;player.setItemInHand(InteractionHand.MAIN_HAND,manuallyAddedItems.removeLast());
            level.playSound(null,worldPosition,SoundEvents.ITEM_FRAME_REMOVE_ITEM,SoundSource.BLOCKS,.5f,1);
            if(manuallyAddedItems.isEmpty()){level.setBlockAndUpdate(worldPosition,getBlockState().setValue(TableClothBlock.HAS_BE,false));level.removeBlockEntity(worldPosition);}else notifyUpdate();return true;
        }
        if(manuallyAddedItems.size()>=4)return true;manuallyAddedItems.add(held.copyWithCount(1));held.shrink(1);facing=player.getDirection().getOpposite();
        level.playSound(null,worldPosition,SoundEvents.ITEM_FRAME_ADD_ITEM,SoundSource.BLOCKS,.5f,1);notifyUpdate();return true;
    }
    private boolean deny(Player player,String key){player.sendOverlayMessage(Component.translatable("create.stock_keeper."+key).withStyle(net.minecraft.ChatFormatting.RED));level.playSound(null,worldPosition,AllSoundEvents.DENY,SoundSource.BLOCKS,.5f,1);return true;}
    public boolean useShop(Player player){
        var held=player.getMainHandItem();int slot=-1;ItemStack previous=held.is(AllItems.SHOPPING_LIST)?held:ItemStack.EMPTY;
        if(previous.isEmpty())for(int i=0;i<9;i++)if(player.getInventory().getItem(i).is(AllItems.SHOPPING_LIST)){slot=i;previous=player.getInventory().getItem(i);}
        if(!held.isEmpty()&&previous.isEmpty())return deny(player,"shopping_list_empty_hand");
        if(getPaymentItem().isEmpty())return deny(player,"no_price_set");var ticker=getTicker();if(ticker==null||!ticker.isKeeperPresent()||owner==null)return deny(player,"keeper_missing");
        var list=ShoppingListItem.getList(previous);boolean compatible=list!=null&&owner.equals(list.shopOwner())&&ticker.behaviour.freqId.equals(list.shopNetwork());
        if(!compatible)list=new ShoppingListItem.ShoppingList(List.of(),owner,ticker.behaviour.freqId);
        int stock=getStockLevelForTrade(list);if(stock<=0)return deny(player,"out_of_stock");int before=list.getPurchases(worldPosition);if(before==0&&list.purchases().size()>=1024)return deny(player,"limited_stock");int amount=before>=stock?stock:before+1;
        if(before>=stock)deny(player,"limited_stock");else level.playSound(null,worldPosition,AllSoundEvents.CONFIRM_2,SoundSource.BLOCKS,.5f,1);
        if(!compatible){level.playSound(null,worldPosition,SoundEvents.BOOK_PAGE_TURN,SoundSource.BLOCKS,1,1.5f);player.sendOverlayMessage(Component.translatable("create.stock_keeper.use_list_to_add_purchases"));}
        var result=ShoppingListItem.saveList(new ItemStack(AllItems.SHOPPING_LIST),list.withPurchases(worldPosition,amount),requestData.encodedTargetAddress());
        if(slot>=0){if(compatible)player.getInventory().setItem(slot,ItemStack.EMPTY);}
        if(held.isEmpty()||held.is(AllItems.SHOPPING_LIST)){if(!compatible&&!held.isEmpty())player.getInventory().placeItemBackInInventory(held.copy(),net.minecraft.util.Prediction.SERVER_ONLY);player.setItemInHand(InteractionHand.MAIN_HAND,result);}else player.getInventory().placeItemBackInInventory(result,net.minecraft.util.Prediction.SERVER_ONLY);
        return true;
    }
    public int getStockLevelForTrade(ShoppingListItem.ShoppingList other){
        var ticker=getTicker();if(ticker==null)return 0;var summary=level.isClientSide()?clientStock:ticker.getRecentSummary();var used=new InventorySummary();
        if(other!=null&&owner!=null&&owner.equals(other.shopOwner())&&ticker.behaviour.freqId.equals(other.shopNetwork())){var baked=other.bakeEntries(level,worldPosition);if(!baked.valid())return 0;used=baked.goods();}
        var perOrder=new InventorySummary();for(var entry:requestData.encodedRequest().stacks()){if(entry.count<=0)return 0;perOrder.add(entry);}
        int available=BigItemStack.INF;for(var entry:perOrder.getStacksByCount())available=Math.min(available,Math.max(0,summary.getCountOf(entry.stack)-used.getCountOf(entry.stack))/entry.count);
        return perOrder.isEmpty()?0:available;
    }
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override public void clearContent(){manuallyAddedItems.clear();}
    @Override public void preRemoveSideEffects(BlockPos pos,BlockState state){for(var item:manuallyAddedItems)Containers.dropItemStack(level,pos.getX(),pos.getY(),pos.getZ(),item);clearContent();super.preRemoveSideEffects(pos,state);}
    @Override protected void write(ValueOutput output, boolean clientPacket){super.write(output, clientPacket);output.store("Items",ItemStack.OPTIONAL_CODEC.listOf(),manuallyAddedItems);output.store("RequestData",AutoRequestData.CODEC,requestData);output.putInt("Facing",facing.get2DDataValue());if(owner!=null)output.store("OwnerUUID",UUIDUtil.CODEC,owner);}
    @Override protected void read(ValueInput input, boolean clientPacket){super.read(input, clientPacket);manuallyAddedItems.clear();manuallyAddedItems.addAll(input.read("Items",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()).stream().filter(item->!item.isEmpty()).limit(4).map(item->item.copyWithCount(1)).toList());requestData=input.read("RequestData",AutoRequestData.CODEC).orElse(new AutoRequestData());owner=input.read("OwnerUUID",UUIDUtil.CODEC).orElse(null);facing=Direction.from2DDataValue(Math.floorMod(input.getIntOr("Facing",0),4));}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var output=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,registries);write(output, true);return output.buildResult();}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (priceTag != null) behaviours.add(priceTag);
    }
}
