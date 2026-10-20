package com.simibubi.create.content.logistics.redstoneRequester;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.*;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class RedstoneRequesterMenu extends AbstractContainerMenu {
    public record Opening(net.minecraft.core.BlockPos pos,CompoundTag state) {
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.of((buf,packet)->{buf.writeBlockPos(packet.pos);buf.writeNbt(packet.state);},buf->new Opening(buf.readBlockPos(),buf.readNbt()));
    }
    public record Update(int menu,String address,boolean allowPartial,List<Integer> amounts) implements CustomPacketPayload {
        public static final Type<Update> TYPE=new Type<>(Create.asResource("redstone_requester_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Update> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Update::menu,ByteBufCodecs.stringUtf8(25),Update::address,ByteBufCodecs.BOOL,Update::allowPartial,ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(9)),Update::amounts,Update::new);
        @Override public Type<Update> type() { return TYPE; }
    }
    public static final ExtendedMenuType<RedstoneRequesterMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("redstone_requester"),new ExtendedMenuType<>(RedstoneRequesterMenu::new,Opening.CODEC));
    private final Inventory inventory;
    private final RedstoneRequesterBlockEntity owner;
    private final SimpleContainer ghosts=new SimpleContainer(9);
    private final int[] amounts=new int[9];
    public RedstoneRequesterMenu(int id,Inventory inventory,Opening opening) {
        super(TYPE,id);this.inventory=inventory;
        if(inventory.player.level().isClientSide()){
            owner=new RedstoneRequesterBlockEntity(opening.pos,AllBlocks.REDSTONE_REQUESTER.defaultBlockState());owner.setLevel(inventory.player.level());owner.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,inventory.player.registryAccess(),opening.state));
        }else owner=(RedstoneRequesterBlockEntity)LoadedLevelAccess.blockEntity(inventory.player.level(),opening.pos);
        Arrays.fill(amounts,1);
        var stacks=owner.encodedRequest.stacks();for(int i=0;i<Math.min(9,stacks.size());i++){ghosts.setItem(i,stacks.get(i).stack.copyWithCount(1));amounts[i]=Math.max(stacks.get(i).count,1);}
        for(int i=0;i<9;i++)addSlot(new Slot(ghosts,i,27+20*i,28){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player player){return false;}});
        addStandardInventorySlots(inventory,5,142);
    }
    public RedstoneRequesterBlockEntity owner() { return owner; }
    public int amount(int index) { return amounts[index]; }
    public static void open(Player player,RedstoneRequesterBlockEntity requester) {
        if(player instanceof ServerPlayer server)server.openMenu(new ExtendedMenuProvider<Opening>(){
            @Override public Component getDisplayName(){return AllBlocks.REDSTONE_REQUESTER.getName();}
            @Override public Opening getScreenOpeningData(ServerPlayer ignored){return new Opening(requester.getBlockPos(),requester.getUpdateTag(server.registryAccess()));}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player ignored){return new RedstoneRequesterMenu(id,inventory,getScreenOpeningData(server));}
        });
    }
    @Override public boolean stillValid(Player player) { return player.getInventory()==inventory&&owner!=null&&!owner.isRemoved()&&!player.isSpectator()&&player.mayBuild()&&player.isWithinBlockInteractionRange(owner.getBlockPos(),1)&&player.level().mayInteract(player,owner.getBlockPos())&&(player.level().isClientSide()||LoadedLevelAccess.blockEntity(player.level(),owner.getBlockPos())==owner&&owner.behaviour.mayInteract(player)); }
    private void saveRequest() {
        var stacks=new ArrayList<BigItemStack>();for(int i=0;i<9;i++)if(!ghosts.getItem(i).isEmpty())stacks.add(new BigItemStack(ghosts.getItem(i).copyWithCount(1),amounts[i]));
        var request=new PackageOrderWithCrafts(new PackageOrder(stacks),owner.encodedRequest.orderedCrafts());
        owner.encodedRequest=request.orderedStacksMatchOrderedRecipes()?request:PackageOrderWithCrafts.simple(stacks);owner.notifyUpdate();broadcastChanges();
    }
    private void copySample(int index,ItemStack sample) { ghosts.setItem(index,sample.copyWithCount(1));if(sample.isEmpty())amounts[index]=1;saveRequest(); }
    @Override public void clicked(int index,int button,ContainerInput input,Player player) {
        if(!player.level().isClientSide()&&!stillValid(player))return;
        if(index>=0&&index<9){if(!player.level().isClientSide()&&(input==ContainerInput.PICKUP||input==ContainerInput.QUICK_MOVE||input==ContainerInput.CLONE))copySample(index,button==1||input==ContainerInput.QUICK_MOVE?ItemStack.EMPTY:getCarried());return;}
        if(index>=9&&index<slots.size()&&input==ContainerInput.QUICK_MOVE){if(!player.level().isClientSide())for(int i=0;i<9;i++)if(ghosts.getItem(i).isEmpty()){copySample(i,slots.get(index).getItem());break;}return;}
        super.clicked(index,button,input,player);
    }
    public boolean apply(Player player,Update update) {
        if(!stillValid(player)||player.level().isClientSide()||update.address.length()>25||update.amounts.size()!=9)return false;
        // Stock Ticker can encode larger orders; opening the scroll editor must preserve them.
        for(int i=0;i<9;i++)if(update.amounts.get(i)<1||update.amounts.get(i)>256&&update.amounts.get(i)!=amounts[i])return false;
        for(int i=0;i<9;i++)amounts[i]=update.amounts.get(i);owner.encodedTargetAdress=update.address;owner.allowPartialRequests=update.allowPartial;saveRequest();return true;
    }
    @Override public ItemStack quickMoveStack(Player player,int index) { return ItemStack.EMPTY; }
    @Override public boolean canDragTo(Slot slot) { return slot.container==inventory; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot) { return slot.container==inventory; }
    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Update.TYPE,Update.CODEC);PayloadTypeRegistry.clientboundPlay().register(RedstoneRequesterEffectPacket.TYPE,RedstoneRequesterEffectPacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Update.TYPE,(packet,context)->{if(context.player().containerMenu instanceof RedstoneRequesterMenu menu&&menu.containerId==packet.menu)menu.apply(context.player(),packet);});
    }
}
