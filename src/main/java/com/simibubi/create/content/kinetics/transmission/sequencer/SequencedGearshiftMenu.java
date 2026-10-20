package com.simibubi.create.content.kinetics.transmission.sequencer;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.List;
import net.fabricmc.fabric.api.menu.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** No physical slots: the source's five-step editor has a real native menu lifetime. */
public final class SequencedGearshiftMenu extends AbstractContainerMenu {
    public record Opening(BlockPos pos,List<Instruction> instructions){
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.of((buffer,data)->{
            buffer.writeBlockPos(data.pos);Instruction.LIST_STREAM_CODEC.encode(buffer,data.instructions);
        },buffer->new Opening(buffer.readBlockPos(),Instruction.LIST_STREAM_CODEC.decode(buffer)));
        public Opening{pos=pos.immutable();instructions=List.copyOf(instructions);}
    }
    public static final ExtendedMenuType<SequencedGearshiftMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("sequenced_gearshift"),new ExtendedMenuType<>(SequencedGearshiftMenu::new,Opening.CODEC));
    private final Inventory inventory;
    private final SequencedGearshiftBlockEntity owner;
    private final Opening opening;
    public SequencedGearshiftMenu(int id,Inventory inventory,Opening opening){
        super(TYPE,id);this.inventory=inventory;this.opening=opening;
        owner=inventory.player.level().isClientSide()?null:LoadedLevelAccess.blockEntity(inventory.player.level(),opening.pos)instanceof SequencedGearshiftBlockEntity sequencer?sequencer:null;
    }
    public Opening opening(){return opening;}
    public static void open(Player player,SequencedGearshiftBlockEntity entity){
        if(player instanceof ServerPlayer server)server.openMenu(new ExtendedMenuProvider<Opening>(){
            @Override public Component getDisplayName(){return AllBlocks.SEQUENCED_GEARSHIFT.getName();}
            @Override public Opening getScreenOpeningData(ServerPlayer ignored){return new Opening(entity.getBlockPos(),entity.getInstructions());}
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player ignored){return new SequencedGearshiftMenu(id,inventory,getScreenOpeningData(server));}
        });
    }
    @Override public boolean stillValid(Player player){
        if(player.getInventory()!=inventory||player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(opening.pos,1)||!player.level().mayInteract(player,opening.pos))return false;
        if(player.level().isClientSide())return LoadedLevelAccess.blockState(player.level(),opening.pos).is(AllBlocks.SEQUENCED_GEARSHIFT);
        return owner!=null&&!owner.isRemoved()&&LoadedLevelAccess.blockEntity(player.level(),opening.pos)==owner;
    }
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    public static void register(){ConfigureSequencedGearshiftPacket.register();}
}
