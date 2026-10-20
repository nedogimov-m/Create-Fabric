package com.simibubi.create.content.logistics.tableCloth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.*;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class ShoppingListItem extends Item {
    public record Purchase(int count,BlockPos pos){
        public static final Codec<Purchase> CODEC=RecordCodecBuilder.create(i->i.group(Codec.intRange(1,BigItemStack.INF).fieldOf("first").forGetter(Purchase::count),BlockPos.CODEC.fieldOf("second").forGetter(Purchase::pos)).apply(i,Purchase::new));
        public static final StreamCodec<RegistryFriendlyByteBuf,Purchase> STREAM_CODEC=StreamCodec.composite(ByteBufCodecs.INT,Purchase::count,BlockPos.STREAM_CODEC,Purchase::pos,Purchase::new);
    }
    public record Baked(InventorySummary goods,InventorySummary payments,boolean valid){static Baked invalid(){return new Baked(new InventorySummary(),new InventorySummary(),false);}}
    public record ShoppingList(List<Purchase> purchases,UUID shopOwner,UUID shopNetwork){
        public static final Codec<ShoppingList> CODEC=RecordCodecBuilder.create(i->i.group(Purchase.CODEC.listOf().fieldOf("purchases").forGetter(ShoppingList::purchases),UUIDUtil.CODEC.fieldOf("shop_owner").forGetter(ShoppingList::shopOwner),UUIDUtil.CODEC.fieldOf("shop_network").forGetter(ShoppingList::shopNetwork)).apply(i,ShoppingList::new));
        public static final StreamCodec<RegistryFriendlyByteBuf,ShoppingList> STREAM_CODEC=StreamCodec.composite(Purchase.STREAM_CODEC.apply(ByteBufCodecs.list(1024)),ShoppingList::purchases,UUIDUtil.STREAM_CODEC,ShoppingList::shopOwner,UUIDUtil.STREAM_CODEC,ShoppingList::shopNetwork,ShoppingList::new);
        public ShoppingList{purchases=List.copyOf(purchases);}
        public int getPurchases(BlockPos pos){long count=0;for(var entry:purchases)if(entry.pos.equals(pos))count+=entry.count;return (int)Math.clamp(count,0,BigItemStack.INF);}
        public ShoppingList withPurchases(BlockPos pos,int amount){var next=new ArrayList<Purchase>();for(var entry:purchases)if(!entry.pos.equals(pos))next.add(entry);if(amount>0)next.add(new Purchase(amount,pos.immutable()));return new ShoppingList(next,shopOwner,shopNetwork);}
        public Baked bakeEntries(Level level,BlockPos ignore){
            if(level==null||purchases.size()>1024)return Baked.invalid();var goods=new InventorySummary();var payments=new InventorySummary();
            for(var entry:purchases){
                if(entry.count<1||entry.count>BigItemStack.INF)return Baked.invalid();if(entry.pos.equals(ignore))continue;
                if(!(LoadedLevelAccess.blockEntity(level,entry.pos) instanceof TableClothBlockEntity cloth)||!cloth.isShop()||!shopOwner.equals(cloth.owner)||cloth.getPaymentItem().isEmpty())return Baked.invalid();
                var ticker=cloth.getTicker();if(ticker==null||!shopNetwork.equals(ticker.behaviour.freqId))return Baked.invalid();
                if(!add(payments,cloth.getPaymentItem(),(long)cloth.getPaymentAmount()*entry.count))return Baked.invalid();
                for(var item:cloth.requestData.encodedRequest().stacks())if(item.count<1||!add(goods,item.stack,(long)item.count*entry.count))return Baked.invalid();
            }
            return new Baked(goods,payments,true);
        }
        private static boolean add(InventorySummary summary,ItemStack item,long amount){if(item.isEmpty()||amount<=0||amount+summary.getCountOf(item)>BigItemStack.INF)return false;summary.add(item.copyWithCount(1),(int)amount);return true;}
    }
    public static java.util.function.Function<ShoppingList,Baked> TOOLTIP_BAKE=list->Baked.invalid();
    public ShoppingListItem(Properties properties){super(properties.stacksTo(1));}
    public static ShoppingList getList(ItemStack stack){return stack.get(AllDataComponents.SHOPPING_LIST);}
    public static String getAddress(ItemStack stack){return stack.getOrDefault(AllDataComponents.SHOPPING_LIST_ADDRESS,"");}
    public static ItemStack saveList(ItemStack stack,ShoppingList list,String address){stack.set(AllDataComponents.SHOPPING_LIST,list);stack.set(AllDataComponents.SHOPPING_LIST_ADDRESS,address);return stack;}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(hand!=InteractionHand.MAIN_HAND||!player.isShiftKeyDown())return InteractionResult.PASS;
        if(!level.isClientSide()){player.setItemInHand(hand,ItemStack.EMPTY);player.sendOverlayMessage(Component.translatable("create.table_cloth.shopping_list_discarded"));level.playSound(null,player.blockPosition(),net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN,net.minecraft.sounds.SoundSource.PLAYERS,1,1);}return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context){return context.getPlayer()==null?InteractionResult.PASS:use(context.getLevel(),context.getPlayer(),context.getHand());}
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,Consumer<Component> lines,TooltipFlag flag){
        var list=getList(stack);if(list!=null){var baked=TOOLTIP_BAKE.apply(list);if(baked.valid){for(var entry:baked.goods.getStacksByCount())lines.accept(entry.stack.getHoverName().copy().append(" x"+entry.count).withStyle(ChatFormatting.GRAY));lines.accept(Component.translatable("create.table_cloth.total_cost").withStyle(ChatFormatting.GOLD));for(var entry:baked.payments.getStacksByCount())lines.accept(entry.stack.getHoverName().copy().append(" x"+entry.count).withStyle(ChatFormatting.YELLOW));}}
        lines.accept(Component.translatable("create.table_cloth.hand_to_shop_keeper").withStyle(ChatFormatting.GRAY));lines.accept(Component.translatable("create.table_cloth.sneak_click_discard").withStyle(ChatFormatting.DARK_GRAY));
    }
}
