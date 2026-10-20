package com.simibubi.create.content.schematics.table;

import com.simibubi.create.*;
import com.simibubi.create.content.schematics.ServerSchematicLoader;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.menu.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;

/** Actual native two-slot menu; every upload authenticates this exact live server owner/lifetime. */
public final class SchematicTableMenu extends AbstractContainerMenu {
    public record Opening(BlockPos pos,CompoundTag state,int packetBytes,long maximumBytes){
        public Opening{pos=pos.immutable();state=state.copy();if(packetBytes<256||packetBytes>32767||maximumBytes<1||maximumBytes>Integer.MAX_VALUE*1000L)throw new IllegalArgumentException("Invalid native schematic upload limits");}
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.of((buffer,value)->{buffer.writeBlockPos(value.pos);buffer.writeNbt(value.state);buffer.writeVarInt(value.packetBytes);buffer.writeVarLong(value.maximumBytes);},buffer->{var pos=buffer.readBlockPos();var raw=buffer.readNbt(NbtAccounter.create(1_048_576));if(!(raw instanceof CompoundTag state))throw new io.netty.handler.codec.DecoderException("Native schematic table state must be a compound");return new Opening(pos,state,buffer.readVarInt(),buffer.readVarLong());});
    }
    public static final ExtendedMenuType<SchematicTableMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("schematic_table"),new ExtendedMenuType<>(SchematicTableMenu::new,Opening.CODEC));
    public final SchematicTableBlockEntity owner;
    public final Opening opening;
    private final Inventory playerInventory;
    public SchematicTableMenu(int id,Inventory inventory,Opening opening){
        super(TYPE,id);this.opening=opening;playerInventory=inventory;
        if(inventory.player.level().isClientSide()){
            owner=new SchematicTableBlockEntity(opening.pos,AllBlocks.SCHEMATIC_TABLE.defaultBlockState());owner.setLevel(inventory.player.level());owner.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,inventory.player.registryAccess(),opening.state));
        }else{var actual=com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(inventory.player.level(),opening.pos);if(!(actual instanceof SchematicTableBlockEntity table))throw new IllegalArgumentException("Actual schematic table owner changed");owner=table;}
        addSlot(new Slot(owner.inventory,0,21,59){@Override public boolean mayPlace(ItemStack item){return owner.inventory.canPlaceItem(0,item);}@Override public int getMaxStackSize(){return 1;}});
        addSlot(new Slot(owner.inventory,1,166,59){@Override public boolean mayPlace(ItemStack item){return false;}@Override public int getMaxStackSize(){return 1;}});
        addStandardInventorySlots(inventory,28,127);
        addDataSlot(new DataSlot(){@Override public int get(){return owner.isUploading?1:0;}@Override public void set(int value){owner.isUploading=value!=0;}});
        addDataSlot(new DataSlot(){@Override public int get(){return (int)(owner.uploadingProgress*10000);}@Override public void set(int value){owner.uploadingProgress=Math.clamp(value/10000f,0,1);}});
    }
    @Override public boolean stillValid(Player player){return player.getInventory()==playerInventory&&owner.canPlayerUse(player);}
    @Override public void clicked(int index,int button,ContainerInput input,Player player){if(!player.level().isClientSide()&&!stillValid(player))return;super.clicked(index,button,input,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;var item=slot.getItem().copy();var original=item.copy();
        if(index<2?!moveItemStackTo(item,2,slots.size(),true):!moveItemStackTo(item,0,1,false))return ItemStack.EMPTY;slot.setByPlayer(item);return original;
    }
    @Override public void removed(Player player){super.removed(player);if(player instanceof ServerPlayer server)ServerSchematicLoader.get(server.level().getServer()).cancelForMenu(server,containerId);}
    public static ExtendedMenuType<SchematicTableMenu,Opening> register(){return TYPE;}
}
