package com.simibubi.create.content.logistics.packagePort;
import com.simibubi.create.*;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.menu.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.*;
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
public final class PackagePortMenu extends AbstractContainerMenu {
    public record Opening(BlockPos pos,net.minecraft.world.level.block.state.BlockState block,CompoundTag state){
        public Opening(BlockPos pos,CompoundTag state){this(pos,AllBlocks.PACKAGE_FROGPORT.defaultBlockState(),state);}
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.of((buf,value)->{buf.writeBlockPos(value.pos);buf.writeVarInt(net.minecraft.world.level.block.Block.getId(value.block));buf.writeNbt(value.state);},buf->new Opening(buf.readBlockPos(),net.minecraft.world.level.block.Block.stateById(buf.readVarInt()),buf.readNbt()));
    }
    public record Settings(int menu,String address,boolean accepts) implements CustomPacketPayload{
        public static final Type<Settings> TYPE=new Type<>(Create.asResource("package_port_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Settings> CODEC=StreamCodec.composite(ByteBufCodecs.VAR_INT,Settings::menu,ByteBufCodecs.stringUtf8(25),Settings::address,ByteBufCodecs.BOOL,Settings::accepts,Settings::new);
        @Override public Type<Settings> type(){return TYPE;}
    }
    public static final ExtendedMenuType<PackagePortMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("package_port"),new ExtendedMenuType<>(PackagePortMenu::new,Opening.CODEC));
    public final PackagePortBlockEntity owner;
    private final Inventory playerInventory;
    public PackagePortMenu(int id,Inventory inventory,Opening opening){
        super(TYPE,id);playerInventory=inventory;
        if(inventory.player.level().isClientSide()){
            var actual=opening.block.getBlock() instanceof net.minecraft.world.level.block.EntityBlock block?block.newBlockEntity(opening.pos,opening.block):null;
            if(!(actual instanceof PackagePortBlockEntity port))throw new IllegalArgumentException("Native package-port menu has no actual matching block entity factory");
            owner=port;owner.setLevel(inventory.player.level());owner.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,inventory.player.registryAccess(),opening.state));
        }else{
            var actual=LoadedLevelAccess.blockEntity(inventory.player.level(),opening.pos);
            if(!(actual instanceof PackagePortBlockEntity port)||port.getBlockState()!=opening.block)throw new IllegalArgumentException("Actual package-port menu owner changed");owner=port;
        }
        for(int i=0;i<18;i++)addSlot(new Slot(owner.inventory,i,27+i%9*18,9+i/9*18){@Override public boolean mayPlace(ItemStack item){return PackageItem.isPackage(item);}@Override public int getMaxStackSize(){return 1;}});
        addStandardInventorySlots(inventory,38,108);if(!inventory.player.level().isClientSide())owner.startOpen();
    }
    public static void open(Player player,PackagePortBlockEntity owner){if(player instanceof ServerPlayer server)server.openMenu(new ExtendedMenuProvider<Opening>(){
        @Override public Component getDisplayName(){return owner.getBlockState().getBlock().getName();}
        @Override public Opening getScreenOpeningData(ServerPlayer ignored){return new Opening(owner.getBlockPos(),owner.getBlockState(),owner.getUpdateTag(server.registryAccess()));}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player ignored){return new PackagePortMenu(id,inventory,getScreenOpeningData(server));}
    });}
    @Override public boolean stillValid(Player player){return player.getInventory()==playerInventory&&!owner.isRemoved()&&!player.isSpectator()&&player.mayBuild()&&player.isWithinBlockInteractionRange(owner.getBlockPos(),1)&&player.level().mayInteract(player,owner.getBlockPos())&&(player.level().isClientSide()||LoadedLevelAccess.blockEntity(player.level(),owner.getBlockPos())==owner);}
    public boolean apply(Player player,Settings settings){if(player.level().isClientSide()||!stillValid(player)||settings.address.length()>25)return false;if(owner.target!=null)owner.target.deregister(owner);owner.addressFilter=settings.address;owner.acceptsPackages=settings.accepts;owner.filterChanged();return true;}
    @Override public void clicked(int index,int button,ContainerInput input,Player player){if(!player.level().isClientSide()&&!stillValid(player))return;super.clicked(index,button,input,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){if(index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var item=slot.getItem().copy();var original=item.copy();if(index<18?!moveItemStackTo(item,18,slots.size(),true):!moveItemStackTo(item,0,18,false))return ItemStack.EMPTY;slot.setByPlayer(item);return original;}
    @Override public void removed(Player player){super.removed(player);if(!player.level().isClientSide())owner.stopOpen();}
    public static void register(){PayloadTypeRegistry.serverboundPlay().register(Settings.TYPE,Settings.CODEC);ServerPlayNetworking.registerGlobalReceiver(Settings.TYPE,(packet,context)->{if(context.player().containerMenu instanceof PackagePortMenu menu&&menu.containerId==packet.menu)menu.apply(context.player(),packet);});}
}
