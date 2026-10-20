package com.simibubi.create.content.logistics.factoryBoard;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.packagerLink.GlobalLogisticsManager;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.*;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** A native server-backed panel editor; ghost samples never transfer player inventory. */
public final class FactoryPanelMenu extends AbstractContainerMenu {
    public record Opening(FactoryPanelPosition position,CompoundTag state,List<ItemStack> autoCraft,int output) {
        public Opening(FactoryPanelPosition position,CompoundTag state){this(position,state,List.of(),1);}
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.of((buf,packet)->{FactoryPanelPosition.STREAM_CODEC.encode(buf,packet.position);buf.writeNbt(packet.state);ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf,packet.autoCraft);buf.writeVarInt(packet.output);},buf->new Opening(FactoryPanelPosition.STREAM_CODEC.decode(buf),buf.readNbt(),ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),buf.readVarInt()));
    }
    public record Update(int menu,CompoundTag config) implements CustomPacketPayload {
        public static final Type<Update> TYPE=new Type<>(Create.asResource("factory_panel_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Update> CODEC=CustomPacketPayload.codec((packet,buf)->{buf.writeVarInt(packet.menu);buf.writeNbt(packet.config);},buf->new Update(buf.readVarInt(),buf.readNbt()));
        @Override public Type<Update> type() { return TYPE; }
    }
    public record Connect(FactoryPanelPosition from,FactoryPanelPosition to,boolean relocate) implements CustomPacketPayload {
        public static final Type<Connect> TYPE=new Type<>(Create.asResource("factory_panel_connection"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Connect> CODEC=CustomPacketPayload.codec((packet,buf)->{FactoryPanelPosition.STREAM_CODEC.encode(buf,packet.from);FactoryPanelPosition.STREAM_CODEC.encode(buf,packet.to);buf.writeBoolean(packet.relocate);},buf->new Connect(FactoryPanelPosition.STREAM_CODEC.decode(buf),FactoryPanelPosition.STREAM_CODEC.decode(buf),buf.readBoolean()));
        @Override public Type<Connect> type() { return TYPE; }
    }
    public static final ExtendedMenuType<FactoryPanelMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("factory_panel"),new ExtendedMenuType<>(FactoryPanelMenu::new,Opening.CODEC));
    public final FactoryPanelPosition position;
    private final FactoryPanelBlockEntity owner;
    private final Inventory inventory;
    private final net.minecraft.world.SimpleContainer sample=new net.minecraft.world.SimpleContainer(1);
    private final int[] values=new int[5];
    public final List<ItemStack> autoCraft;
    public final int autoOutput;
    public FactoryPanelMenu(int id,Inventory inventory,Opening opening) {
        super(TYPE,id);this.inventory=inventory;position=opening.position;autoCraft=opening.autoCraft;autoOutput=opening.output;
        if(inventory.player.level().isClientSide()){
            owner=new FactoryPanelBlockEntity(position.pos(),AllBlocks.FACTORY_GAUGE.defaultBlockState());owner.setLevel(inventory.player.level());owner.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,inventory.player.registryAccess(),opening.state));
        }else owner=(FactoryPanelBlockEntity)LoadedLevelAccess.blockEntity(inventory.player.level(),position.pos());
        sample.setItem(0,panel().getFilter().copy());addSlot(new Slot(sample,0,184,48){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player player){return false;}});addStandardInventorySlots(inventory,242,100);
        for(int index=0;index<values.length;index++)for(int half=0;half<2;half++){final int i=index,shift=half*16;addDataSlot(new DataSlot(){@Override public int get(){int value=inventory.player.level().isClientSide()?values[i]:switch(i){case 0->panel().getLevelInStorage();case 1->panel().getPromised();case 2->panel().getUnloadedLinks();case 3->panel().count;default->panel().upTo?1:0;};return value>>>shift&0xFFFF;}@Override public void set(int value){values[i]=values[i]&~(0xFFFF<<shift)|(value&0xFFFF)<<shift;}});}
    }
    public FactoryPanelBehaviour panel() { return owner.panels.get(position.slot()); }
    public int stock() { return inventory.player.level().isClientSide()?values[0]:panel().getLevelInStorage(); }
    public int promised() { return inventory.player.level().isClientSide()?values[1]:panel().getPromised(); }
    public static void open(Player player,FactoryPanelBehaviour panel) {
        if(!(player instanceof ServerPlayer server))return;server.openMenu(new ExtendedMenuProvider<Opening>(){
            @Override public Component getDisplayName(){return Component.translatable(panel.panelBE().restocker?"create.gui.factory_panel.title_as_restocker":"create.gui.factory_panel.title_as_recipe");}
            @Override public Opening getScreenOpeningData(ServerPlayer ignored){var arrangement=FactoryPanelCrafting.find(panel);return new Opening(panel.getPanelPosition(),panel.panelBE().getUpdateTag(server.registryAccess()),arrangement==null?List.of():arrangement.grid(),arrangement==null?1:arrangement.output());}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner){return new FactoryPanelMenu(id,inventory,getScreenOpeningData(server));}
        });
    }
    @Override public boolean stillValid(Player player) { return player.getInventory()==inventory&&owner!=null&&!owner.isRemoved()&&panel().active&&player.mayBuild()&&!player.isSpectator()&&player.isWithinBlockInteractionRange(position.pos(),1)&&player.level().mayInteract(player,position.pos())&&(player.level().isClientSide()||LoadedLevelAccess.blockEntity(player.level(),position.pos())==owner&&GlobalLogisticsManager.get(player.level()).mayInteract(panel().network,player)); }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return slot.container==inventory; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot) { return slot.container==inventory; }
    @Override public void clicked(int index,int button,ContainerInput input,Player player) {
        if(!player.level().isClientSide()&&!stillValid(player))return;
        if(index==0||index>0&&index<slots.size()&&input==ContainerInput.QUICK_MOVE){var next=index==0?(button==1?ItemStack.EMPTY:getCarried()):slots.get(index).getItem();if(!player.level().isClientSide()&&panel().setFilter(next)){sample.setItem(0,panel().getFilter());broadcastChanges();}return;}super.clicked(index,button,input,player);
    }
    public boolean apply(Player player,CompoundTag config) {
        if(!stillValid(player)||player.level().isClientSide())return false;
        String address=config.getStringOr("Address",panel().recipeAddress);int count=config.getIntOr("Amount",panel().count),output=config.getIntOr("Output",panel().recipeOutput),expiry=config.getIntOr("Expiry",panel().promiseClearingInterval);if(address.length()>25||count<0||count>100||output<1||output>64||expiry< -1||expiry>30)return false;
        var ops=player.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);var inputs=config.read("Inputs",FactoryPanelConnection.CODEC.listOf()).orElse(List.of());if(inputs.size()>9||inputs.stream().anyMatch(connection->connection.amount<0||connection.amount>64))return false;var craft=config.read("Craft",ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());if(craft.size()>9)return false;
        panel().recipeAddress=address;panel().recipeOutput=output;panel().promiseClearingInterval=expiry;panel().setAmount(count,config.getBooleanOr("Items",panel().upTo));panel().activeCraftingArrangement=craft.stream().map(stack->stack.copyWithCount(1)).toList();for(var input:inputs){var existing=panel().targetedBy.get(input.from);if(existing!=null)existing.amount=input.amount;}
        if(config.getBooleanOr("Clear",false))panel().forceClearPromises=true;
        config.read("Remove",FactoryPanelPosition.CODEC).ifPresent(panel()::removeConnection);
        if(config.getBooleanOr("ResetLinks",false))panel().disconnectAllLinks();
        if(config.getBooleanOr("Reset",false)){panel().disconnectAll();panel().setFilter(ItemStack.EMPTY);panel().count=0;panel().recipeAddress="";panel().recipeOutput=1;panel().activeCraftingArrangement=List.of();panel().promiseClearingInterval=-1;panel().forceClearPromises=true;sample.setItem(0,ItemStack.EMPTY);}
        owner.notifyUpdate();broadcastChanges();return true;
    }
    public static boolean connect(Player player,Connect packet) {
        var level=player.level();if(level.isClientSide()||player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(packet.to.pos(),16)||!level.mayInteract(player,packet.from.pos())||!level.mayInteract(player,packet.to.pos())||!level.isLoaded(packet.to.pos())||!level.isLoaded(packet.from.pos()))return false;
        var panel=FactoryPanelBehaviour.at(level,packet.to);if(panel==null||!GlobalLogisticsManager.get(level).mayInteract(panel.network,player))return false;var source=FactoryPanelBehaviour.at(level,packet.from);if(source!=null&&!GlobalLogisticsManager.get(level).mayInteract(source.network,player))return false;
        return packet.relocate?FactoryPanelRelocation.move(panel,packet.from,player):panel.addConnection(packet.from);
    }
    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(FactoryPanelEffectPacket.TYPE,FactoryPanelEffectPacket.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Update.TYPE,Update.CODEC);PayloadTypeRegistry.serverboundPlay().register(Connect.TYPE,Connect.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Update.TYPE,(packet,context)->{if(context.player().containerMenu instanceof FactoryPanelMenu menu&&menu.containerId==packet.menu)menu.apply(context.player(),packet.config);});
        ServerPlayNetworking.registerGlobalReceiver(Connect.TYPE,(packet,context)->connect(context.player(),packet));
    }
}
