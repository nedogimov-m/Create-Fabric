package com.simibubi.create.content.logistics.stockTicker;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Server owns all category items. Network packets address the player's current, validated menu. */
public final class StockTickerMenu extends AbstractContainerMenu {
    public record Opening(BlockPos pos, CompoundTag state, boolean categories, boolean admin, boolean locked) {
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC = StreamCodec.of((buf,p) -> { buf.writeBlockPos(p.pos); buf.writeNbt(p.state); buf.writeBoolean(p.categories); buf.writeBoolean(p.admin); buf.writeBoolean(p.locked); }, buf -> new Opening(buf.readBlockPos(), buf.readNbt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
    }
    public record Action(int menu, int action, int index, String name, List<Integer> hidden) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Create.asResource("stock_keeper_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,Action::menu,ByteBufCodecs.VAR_INT,Action::action,ByteBufCodecs.VAR_INT,Action::index,ByteBufCodecs.stringUtf8(28),Action::name,ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(1024)),Action::hidden,Action::new);
        @Override public Type<Action> type() { return TYPE; }
    }
    public record Request(int menu, PackageOrderWithCrafts order, String address, boolean encode) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Create.asResource("stock_keeper_order"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,Request::menu,PackageOrderWithCrafts.STREAM_CODEC,Request::order,ByteBufCodecs.stringUtf8(25),Request::address,ByteBufCodecs.BOOL,Request::encode,Request::new);
        @Override public Type<Request> type() { return TYPE; }
    }
    public record State(int menu, CompoundTag state, boolean admin, boolean locked, int editing) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Create.asResource("stock_keeper_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC = StreamCodec.of((buf,p) -> { buf.writeVarInt(p.menu); buf.writeNbt(p.state); buf.writeBoolean(p.admin); buf.writeBoolean(p.locked); buf.writeVarInt(p.editing); }, buf -> new State(buf.readVarInt(), buf.readNbt(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt()));
        @Override public Type<State> type() { return TYPE; }
    }
    public record Snapshot(int menu, int sequence, int fragment, boolean last, List<BigItemStack> items) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Create.asResource("stock_keeper_stock"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,Snapshot::menu,ByteBufCodecs.VAR_INT,Snapshot::sequence,ByteBufCodecs.VAR_INT,Snapshot::fragment,ByteBufCodecs.BOOL,Snapshot::last,BigItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(100)),Snapshot::items,Snapshot::new);
        @Override public Type<Snapshot> type() { return TYPE; }
    }
    public static final int REFRESH=0, BEGIN=1, FINISH=2, DELETE=3, UP=4, DOWN=5, LOCK=6, HIDE=7, IDLE=Integer.MIN_VALUE;
    public static final ExtendedMenuType<StockTickerMenu,Opening> REQUEST_TYPE = Registry.register(BuiltInRegistries.MENU, Create.asResource("stock_keeper_request"), new ExtendedMenuType<>(StockTickerMenu::new, Opening.CODEC));
    public static final ExtendedMenuType<StockTickerMenu,Opening> CATEGORY_TYPE = Registry.register(BuiltInRegistries.MENU, Create.asResource("stock_keeper_category"), new ExtendedMenuType<>(StockTickerMenu::new, Opening.CODEC));
    private final Inventory inventory;
    public final StockTickerBlockEntity owner;
    public final boolean categoryMode;
    public boolean admin, locked;
    public int editing=IDLE;
    public final SimpleContainer proxy = new SimpleContainer(1);
    public List<BigItemStack> stock = List.of();
    public int snapshotVersion;
    public List<StockTickerCrafting.Choice> craftingChoices=List.of();
    public StockTickerCrafting.Cart craftingCart=new StockTickerCrafting.Cart(PackageOrderWithCrafts.empty(),List.of(),List.of());
    public int craftingCatalogVersion=-1,craftingRevision,craftingReply=-1;
    private int catalogSequence=-1,catalogFragment;
    private final List<StockTickerCrafting.Choice> pendingChoices=new ArrayList<>();
    private int sequence, pendingSequence=-1, nextFragment;
    private final List<BigItemStack> pending = new ArrayList<>();

    public StockTickerMenu(int id, Inventory inventory, Opening opening) {
        super(opening.categories ? CATEGORY_TYPE : REQUEST_TYPE, id); this.inventory=inventory; categoryMode=opening.categories; admin=opening.admin; locked=opening.locked;
        if (inventory.player.level().isClientSide()) {
            owner=new StockTickerBlockEntity(opening.pos, AllBlocks.STOCK_TICKER.defaultBlockState()); owner.setLevel(inventory.player.level());
            owner.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, inventory.player.registryAccess(), opening.state));
        } else owner=(StockTickerBlockEntity)LoadedLevelAccess.blockEntity(inventory.player.level(), opening.pos);
        if (categoryMode) {
            addSlot(new Slot(proxy,0,16,42) {
                @Override public boolean mayPlace(ItemStack stack) { return StockTickerBlockEntity.isCategory(stack); }
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean isActive() { return editing != IDLE; }
            });
            for(int row=0;row<3;row++)for(int column=0;column<9;column++)addSlot(playerSlot(inventory,column+row*9+9,16+column*18,142+row*18));
            for(int column=0;column<9;column++)addSlot(playerSlot(inventory,column,16+column*18,200));
        }
    }
    private Slot playerSlot(Inventory inventory,int index,int x,int y){return new Slot(inventory,index,x,y){@Override public boolean isActive(){return editing!=IDLE;}};}
    private static Opening opening(Player player, StockTickerBlockEntity owner, boolean categories) {
        var manager=GlobalLogisticsManager.get(player.level());
        return new Opening(owner.getBlockPos(), owner.getUpdateTag(player.registryAccess()), categories, owner.behaviour.mayAdministrate(player) && manager.isLockable(owner.behaviour.freqId), manager.isLocked(owner.behaviour.freqId));
    }
    public static boolean open(Player player, StockTickerBlockEntity owner, boolean categories) {
        if (!(player instanceof ServerPlayer server) || !owner.behaviour.mayInteract(player)) return false;
        if (categories && owner.categoryEditor != null) {
            if (owner.categoryEditor.stillValid(owner.categoryEditor.inventory.player)) return false;
            owner.categoryEditor.finishEditing(null); owner.categoryEditor=null;
        }
        server.openMenu(new ExtendedMenuProvider<Opening>() {
            @Override public Component getDisplayName() { return AllBlocks.STOCK_TICKER.getName(); }
            @Override public Opening getScreenOpeningData(ServerPlayer ignored) { return opening(player, owner, categories); }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player ignored) { return new StockTickerMenu(id, inventory, getScreenOpeningData(server)); }
        });
        if (server.containerMenu instanceof StockTickerMenu menu) { if (categories) owner.categoryEditor=menu; menu.refresh(); return true; }
        return false;
    }
    @Override public boolean stillValid(Player player) {
        return inventory.player==player && owner!=null && !owner.isRemoved() && !player.isSpectator() && player.mayBuild() && player.isWithinBlockInteractionRange(owner.getBlockPos(), 1) && player.level().mayInteract(player, owner.getBlockPos()) && (player.level().isClientSide() || LoadedLevelAccess.blockEntity(player.level(), owner.getBlockPos())==owner && owner.behaviour.mayInteract(player));
    }
    @Override public void clicked(int slot, int button, ContainerInput input, Player player) { if (stillValid(player) && (slot!=0 || editing!=IDLE)) super.clicked(slot,button,input,player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!categoryMode || !stillValid(player) || index<0 || index>=slots.size()) return ItemStack.EMPTY;
        var slot=slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY; var stack=slot.getItem(); var original=stack.copy();
        if (index==0) { if (!moveItemStackTo(stack, 1, slots.size(), true)) return ItemStack.EMPTY; }
        else if (editing==IDLE || !StockTickerBlockEntity.isCategory(stack) || !moveItemStackTo(stack,0,1,false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged(); slot.onTake(player, stack); return original;
    }
    public boolean beginEditing(int index) {
        if (!categoryMode || editing!=IDLE || index < -1 || index>=owner.categories.size() || owner.categoryEditor!=null && owner.categoryEditor!=this) return false;
        owner.categoryEditor=this; editing=index;
        if (index>=0) { proxy.setItem(0, owner.categories.get(index)); owner.categories.set(index,ItemStack.EMPTY); }
        owner.notifyUpdate(); broadcastChanges(); sendState(); return true;
    }
    public void finishEditing(String name) {
        if (editing==IDLE) return; var filter=proxy.removeItemNoUpdate(0);
        if (!filter.isEmpty() && name!=null) filter.set(DataComponents.CUSTOM_NAME, name.isBlank() ? null : Component.literal(name.substring(0,Math.min(28,name.length()))));
        if (editing>=0 && editing<owner.categories.size()) {
            if (filter.isEmpty()) owner.removeCategory(editing); else owner.categories.set(editing,filter);
        } else if (!filter.isEmpty()) owner.categories.add(filter);
        editing=IDLE; owner.notifyUpdate(); broadcastChanges(); sendState();
    }
    public boolean applyAction(Player player, Action action) {
        if (!stillValid(player) || player.level().isClientSide() || action.menu!=containerId) return false;
        if (action.action==REFRESH) { refresh(); return true; }
        if (action.action==LOCK) { boolean result=GlobalLogisticsManager.get(player.level()).setLocked(owner.behaviour.freqId,player,action.index!=0); sendState(); return result; }
        if (action.action==HIDE && !categoryMode) { if (action.hidden.stream().anyMatch(index -> index < -1 || index>=owner.categories.size())) return false; owner.hiddenCategoriesByPlayer.put(player.getUUID(),action.hidden.stream().distinct().toList()); owner.notifyUpdate(); return true; }
        if (!categoryMode || owner.categoryEditor!=null && owner.categoryEditor!=this) return false;
        if (action.action==BEGIN) return beginEditing(action.index);
        if (action.action==FINISH) { if (action.name.length()>28) return false; finishEditing(action.name); return true; }
        if (editing!=IDLE || action.index<0 || action.index>=owner.categories.size()) return false;
        if (action.action==DELETE) { var filter=owner.categories.get(action.index); owner.removeCategory(action.index); if (!filter.isEmpty()) player.getInventory().placeItemBackInInventory(filter,net.minecraft.util.Prediction.SERVER_ONLY); }
        else if (action.action==UP && action.index>0) owner.moveCategory(action.index,action.index-1);
        else if (action.action==DOWN && action.index+1<owner.categories.size()) owner.moveCategory(action.index,action.index+1);
        else return false;
        sendState(); return true;
    }
    public static boolean validOrder(PackageOrderWithCrafts order) {
        if (order.stacks().size()>1024 || order.orderedCrafts().size()>1024) return false;
        var totals=new java.util.HashMap<com.simibubi.create.foundation.item.ItemSampleEquality.Key,Long>();
        for (var entry:order.stacks()) {
            if (entry.stack.isEmpty() || entry.count<1 || entry.count>BigItemStack.INF) return false;
            if(totals.merge(com.simibubi.create.foundation.item.ItemSampleEquality.key(entry.stack),(long)entry.count,Long::sum)>BigItemStack.INF)return false;
        }
        totals.clear();
        for (var craft:order.orderedCrafts()) {
            if (craft.count()<1 || craft.count()>BigItemStack.INF || craft.pattern().stacks().size()!=9) return false;
            for (var entry:craft.pattern().stacks()) {
                long amount=(long)entry.count*craft.count();
                if (entry.count<0 || entry.count>64 || amount>BigItemStack.INF) return false;
                if(!entry.stack.isEmpty()&&totals.merge(com.simibubi.create.foundation.item.ItemSampleEquality.key(entry.stack),amount,Long::sum)>BigItemStack.INF)return false;
            }
        }
        return order.orderedCrafts().isEmpty() || order.orderedStacksMatchOrderedRecipes();
    }
    public boolean request(Player player, Request request) {
        if (categoryMode || !stillValid(player) || player.level().isClientSide() || request.menu!=containerId || request.address.length()>25 || !validOrder(request.order)) return false;
        if (request.encode) {
            if (!player.getMainHandItem().is(AllBlocks.REDSTONE_REQUESTER.asItem())&&!(player.getMainHandItem().getItem() instanceof com.simibubi.create.content.logistics.tableCloth.TableClothBlockItem)) return false;
            owner.previouslyUsedAddress=request.address;owner.notifyUpdate();
            if (!request.order.isEmpty()) player.level().playSound(null, owner.getBlockPos(), AllSoundEvents.CONFIRM, SoundSource.BLOCKS,1,1);
            RedstoneRequesterBlock.programRequester(player,owner,request.order,request.address); if(player instanceof ServerPlayer server)server.closeContainer(); return true;
        }
        if (!request.order.isEmpty()) {
            com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.award(player,"stock_ticker");
            player.level().playSound(null,owner.getBlockPos(),AllSoundEvents.STOCK_TICKER_REQUEST,SoundSource.BLOCKS,1,1);
            player.level().playSound(null,owner.getBlockPos(),AllSoundEvents.STOCK_LINK,SoundSource.BLOCKS,1,1);
            if(player.level() instanceof net.minecraft.server.level.ServerLevel level){var point=net.minecraft.world.phys.Vec3.atCenterOf(owner.getBlockPos());level.sendParticles(AllParticleTypes.WIFI,point.x,point.y,point.z,1,0,0,0,0);}
        }
        boolean result=owner.broadcastPackageRequest(LogisticallyLinkedBehaviour.RequestType.PLAYER,request.order,null,request.address);
        refresh(); return result;
    }
    public void refresh() {
        var summary=owner.getRecentSummary(); sendState(); if (categoryMode || !(inventory.player instanceof ServerPlayer server)) return;
        for (var packet:fragment(containerId,++sequence,summary.getStacksByCount())) ServerPlayNetworking.send(server,packet);
    }
    public static List<Snapshot> fragment(int menu, int sequence, List<BigItemStack> stock) {
        var packets=new ArrayList<Snapshot>();
        if (stock.isEmpty()) return List.of(new Snapshot(menu,sequence,0,true,List.of()));
        for (int start=0;start<stock.size();start+=100) packets.add(new Snapshot(menu,sequence,start/100,start+100>=stock.size(),stock.subList(start,Math.min(stock.size(),start+100)).stream().map(entry->new BigItemStack(entry.stack.copy(),entry.count)).toList()));
        return packets;
    }
    public void receive(Snapshot packet) {
        if (packet.menu!=containerId) return;
        if (packet.fragment==0) { if (packet.sequence<pendingSequence) return; pendingSequence=packet.sequence; nextFragment=0; pending.clear(); }
        if (packet.sequence!=pendingSequence || packet.fragment!=nextFragment) return;
        pending.addAll(packet.items); nextFragment++;
        if (packet.last) { stock=List.copyOf(pending); snapshotVersion++; pending.clear(); }
    }
    public void receive(State state) {
        if (state.menu!=containerId) return;
        owner.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,inventory.player.registryAccess(),state.state));
        admin=state.admin; locked=state.locked; editing=state.editing; snapshotVersion++;
    }
    public void receive(StockTickerCraftingPackets.Catalog packet){
        if(packet.menu()!=containerId||packet.version()<catalogSequence)return;
        if(packet.fragment()==0){catalogSequence=packet.version();catalogFragment=0;pendingChoices.clear();}
        if(packet.version()!=catalogSequence||packet.fragment()!=catalogFragment)return;
        pendingChoices.addAll(packet.choices());catalogFragment++;
        if(packet.last()){craftingChoices=List.copyOf(pendingChoices);craftingCatalogVersion=packet.version();pendingChoices.clear();snapshotVersion++;}
    }
    public void receive(StockTickerCraftingPackets.Preview packet){
        if(packet.menu()!=containerId||packet.revision()!=craftingRevision)return;
        craftingCart=new StockTickerCrafting.Cart(packet.order(),packet.recipes(),packet.results(),packet.manual());craftingReply=packet.revision();snapshotVersion++;
    }
    private void sendState() {
        if (inventory.player instanceof ServerPlayer server) { var opening=opening(server,owner,categoryMode); admin=opening.admin; locked=opening.locked; ServerPlayNetworking.send(server,new State(containerId,opening.state,admin,locked,editing)); }
    }
    @Override public void removed(Player player) {
        if (!player.level().isClientSide() && categoryMode) { finishEditing(null); if (owner.categoryEditor==this) owner.categoryEditor=null; }
        super.removed(player);
    }
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE,Action.CODEC); PayloadTypeRegistry.serverboundPlay().register(Request.TYPE,Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE,State.CODEC); PayloadTypeRegistry.clientboundPlay().register(Snapshot.TYPE,Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE,(packet,context) -> { if (context.player().containerMenu instanceof StockTickerMenu menu && menu.containerId==packet.menu) menu.applyAction(context.player(),packet); });
        ServerPlayNetworking.registerGlobalReceiver(Request.TYPE,(packet,context) -> { if (context.player().containerMenu instanceof StockTickerMenu menu && menu.containerId==packet.menu) menu.request(context.player(),packet); });
    }
}
