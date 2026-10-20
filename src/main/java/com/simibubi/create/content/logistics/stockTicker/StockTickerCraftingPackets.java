package com.simibubi.create.content.logistics.stockTicker;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.BigItemStack;
import java.util.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class StockTickerCraftingPackets {
    public static final StreamCodec<RegistryFriendlyByteBuf,StockTickerCrafting.Selection> SELECTION=StreamCodec.composite(Identifier.STREAM_CODEC,StockTickerCrafting.Selection::recipe,ByteBufCodecs.VAR_INT,StockTickerCrafting.Selection::crafts,StockTickerCrafting.Selection::new);
    private static final StreamCodec<RegistryFriendlyByteBuf,StockTickerCrafting.Adjustment> ADJUSTMENT=StreamCodec.composite(Identifier.STREAM_CODEC,StockTickerCrafting.Adjustment::recipe,ByteBufCodecs.VAR_INT,StockTickerCrafting.Adjustment::crafts,StockTickerCrafting.Adjustment::new);
    public static final StreamCodec<RegistryFriendlyByteBuf,StockTickerCrafting.Choice> CHOICE=StreamCodec.composite(Identifier.STREAM_CODEC,StockTickerCrafting.Choice::recipe,ItemStack.STREAM_CODEC,StockTickerCrafting.Choice::result,ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9)),StockTickerCrafting.Choice::grid,ByteBufCodecs.VAR_INT,StockTickerCrafting.Choice::available,StockTickerCrafting.Choice::new);
    public record Query(int menu,int version) implements CustomPacketPayload {
        public static final Type<Query> TYPE=new Type<>(Create.asResource("stock_keeper_recipes"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Query> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Query::menu,ByteBufCodecs.VAR_INT,Query::version,Query::new);
        @Override public Type<Query> type(){return TYPE;}
    }
    public record Catalog(int menu,int version,int fragment,boolean last,List<StockTickerCrafting.Choice> choices) implements CustomPacketPayload {
        public static final Type<Catalog> TYPE=new Type<>(Create.asResource("stock_keeper_recipe_catalog"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Catalog> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Catalog::menu,ByteBufCodecs.VAR_INT,Catalog::version,ByteBufCodecs.VAR_INT,Catalog::fragment,ByteBufCodecs.BOOL,Catalog::last,CHOICE.apply(ByteBufCodecs.list(32)),Catalog::choices,Catalog::new);
        @Override public Type<Catalog> type(){return TYPE;}
    }
    public record Edit(int menu,int revision,List<StockTickerCrafting.Selection> recipes,List<BigItemStack> manual,boolean submit,String address,boolean encode,boolean exactMaterials,StockTickerCrafting.Adjustment adjustment) implements CustomPacketPayload {
        public Edit(int menu,int revision,List<StockTickerCrafting.Selection> recipes,List<BigItemStack> manual,boolean submit,String address,boolean encode){this(menu,revision,recipes,manual,submit,address,encode,false,null);}
        public Edit(int menu,int revision,List<StockTickerCrafting.Selection> recipes,List<BigItemStack> manual,boolean submit,String address,boolean encode,boolean exactMaterials){this(menu,revision,recipes,manual,submit,address,encode,exactMaterials,null);}
        public static final Type<Edit> TYPE=new Type<>(Create.asResource("stock_keeper_crafting_order"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Edit> CODEC=StreamCodec.of((buf,p)->{buf.writeVarInt(p.menu);buf.writeVarInt(p.revision);SELECTION.apply(ByteBufCodecs.list(1024)).encode(buf,p.recipes);BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(9)).encode(buf,p.manual);buf.writeBoolean(p.submit);buf.writeUtf(p.address,25);buf.writeBoolean(p.encode);buf.writeBoolean(p.exactMaterials);buf.writeBoolean(p.adjustment!=null);if(p.adjustment!=null)ADJUSTMENT.encode(buf,p.adjustment);},buf->{int menu=buf.readVarInt(),revision=buf.readVarInt();var recipes=SELECTION.apply(ByteBufCodecs.list(1024)).decode(buf);var materials=BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(9)).decode(buf);boolean submit=buf.readBoolean();String address=buf.readUtf(25);boolean encode=buf.readBoolean(),exact=buf.readBoolean();return new Edit(menu,revision,recipes,materials,submit,address,encode,exact,buf.readBoolean()?ADJUSTMENT.decode(buf):null);});
        @Override public Type<Edit> type(){return TYPE;}
    }
    public record Preview(int menu,int revision,PackageOrderWithCrafts order,List<StockTickerCrafting.Selection> recipes,List<StockTickerCrafting.Choice> results,List<BigItemStack> manual) implements CustomPacketPayload {
        public Preview(int menu,int revision,PackageOrderWithCrafts order,List<StockTickerCrafting.Selection> recipes,List<StockTickerCrafting.Choice> results){this(menu,revision,order,recipes,results,List.of());}
        public static final Type<Preview> TYPE=new Type<>(Create.asResource("stock_keeper_crafting_preview"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Preview> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Preview::menu,ByteBufCodecs.VAR_INT,Preview::revision,PackageOrderWithCrafts.STREAM_CODEC,Preview::order,SELECTION.apply(ByteBufCodecs.list(1024)),Preview::recipes,CHOICE.apply(ByteBufCodecs.list(1024)),Preview::results,BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(9)),Preview::manual,Preview::new);
        @Override public Type<Preview> type(){return TYPE;}
    }
    private static final class CachedCatalog {
        Object manager;
        int version,recipesHash;
        List<BigItemStack> stock=List.of();
        List<StockTickerCrafting.Choice> choices=List.of();
    }
    private static final Map<StockTickerMenu,CachedCatalog> CATALOGS=new WeakHashMap<>();
    public static List<Catalog> fragment(int menu,int version,List<StockTickerCrafting.Choice> choices){
        if(choices.isEmpty())return List.of(new Catalog(menu,version,0,true,List.of()));var packets=new ArrayList<Catalog>();
        for(int start=0;start<choices.size();start+=32)packets.add(new Catalog(menu,version,start/32,start+32>=choices.size(),List.copyOf(choices.subList(start,Math.min(start+32,choices.size())))));return packets;
    }
    public static void query(StockTickerMenu menu,Player player,Query query){
        if(query.menu!=menu.containerId||menu.categoryMode||!menu.stillValid(player)||!(player instanceof ServerPlayer server))return;
        var summary=menu.owner.getRecentSummary();var manager=player.level().getServer().getRecipeManager();int hash=manager.getRecipes().hashCode();
        var cache=CATALOGS.computeIfAbsent(menu,ignored->new CachedCatalog());var stock=summary.getStacksByCount();
        if(cache.manager!=manager||cache.recipesHash!=hash||!cache.stock.equals(stock)){
            cache.manager=manager;cache.recipesHash=hash;cache.stock=stock.stream().map(entry->new BigItemStack(entry.stack.copy(),entry.count)).toList();cache.choices=StockTickerCrafting.catalog(player.level(),summary);cache.version++;
        }
        if(query.version==cache.version)return;for(var packet:fragment(menu.containerId,cache.version,cache.choices))ServerPlayNetworking.send(server,packet);
    }
    public static StockTickerCrafting.Cart edit(StockTickerMenu menu,Player player,Edit edit){
        if(edit.menu!=menu.containerId||menu.categoryMode||!menu.stillValid(player)||player.level().isClientSide()||edit.revision<0||edit.recipes.size()>1024||edit.manual.size()>9||edit.address.length()>25)return null;
        if(edit.recipes.stream().anyMatch(recipe->recipe.crafts()<1||recipe.crafts()>BigItemStack.INF)||!StockTickerMenu.validOrder(PackageOrderWithCrafts.simple(edit.manual)))return null;
        if(edit.adjustment!=null&&(!edit.exactMaterials||edit.submit||edit.adjustment.crafts()==0||Math.abs((long)edit.adjustment.crafts())>BigItemStack.INF))return null;
        if(edit.submit)com.simibubi.create.content.logistics.packagerLink.GlobalLogisticsManager.get(player.level()).invalidate(menu.owner.behaviour.freqId);
        var summary=menu.owner.getAccurateSummary();var cart=edit.adjustment!=null?StockTickerCrafting.adjust(player.level(),summary,edit.manual,edit.recipes,edit.adjustment):edit.exactMaterials?StockTickerCrafting.reconcile(player.level(),summary,edit.manual,edit.recipes):StockTickerCrafting.cart(player.level(),summary,edit.manual,edit.recipes);
        if(!StockTickerMenu.validOrder(cart.order()))return null;
        if(player instanceof ServerPlayer server)ServerPlayNetworking.send(server,new Preview(menu.containerId,edit.revision,cart.order(),cart.selections(),cart.results(),cart.manual()));
        if(edit.submit)menu.request(player,new StockTickerMenu.Request(menu.containerId,cart.order(),edit.address,edit.encode));
        return cart;
    }
    public static void register(){
        PayloadTypeRegistry.serverboundPlay().register(Query.TYPE,Query.CODEC);PayloadTypeRegistry.serverboundPlay().register(Edit.TYPE,Edit.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Catalog.TYPE,Catalog.CODEC);PayloadTypeRegistry.clientboundPlay().register(Preview.TYPE,Preview.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Query.TYPE,(packet,context)->{if(context.player().containerMenu instanceof StockTickerMenu menu)query(menu,context.player(),packet);});
        ServerPlayNetworking.registerGlobalReceiver(Edit.TYPE,(packet,context)->{if(context.player().containerMenu instanceof StockTickerMenu menu)edit(menu,context.player(),packet);});
    }
    private StockTickerCraftingPackets(){}
}
