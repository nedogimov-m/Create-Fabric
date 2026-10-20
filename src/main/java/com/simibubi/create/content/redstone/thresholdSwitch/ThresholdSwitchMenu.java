package com.simibubi.create.content.redstone.thresholdSwitch;

import com.simibubi.create.Create;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.Objects;
import net.fabricmc.fabric.api.menu.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Server-owned target snapshot: native screen never guesses a remote inventory or custom observable. */
public final class ThresholdSwitchMenu extends AbstractContainerMenu {
    public record Opening(BlockPos pos,int revision,ThresholdSwitchBlockEntity.ThresholdType kind,boolean ready,int min,int current,int max,int upper,int lower,boolean stacks,boolean inverted,boolean raw,boolean powered,ItemStack target,Component currentText,Component minText,Component maxText){
        public Opening{pos=pos.immutable();target=target.copy();}
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.of((buffer,value)->{buffer.writeBlockPos(value.pos);buffer.writeVarInt(value.revision);buffer.writeEnum(value.kind);buffer.writeBoolean(value.ready);buffer.writeInt(value.min);buffer.writeInt(value.current);buffer.writeInt(value.max);buffer.writeInt(value.upper);buffer.writeInt(value.lower);buffer.writeBoolean(value.stacks);buffer.writeBoolean(value.inverted);buffer.writeBoolean(value.raw);buffer.writeBoolean(value.powered);ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer,value.target);net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer,value.currentText);net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer,value.minText);net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer,value.maxText);},buffer->new Opening(buffer.readBlockPos(),buffer.readVarInt(),buffer.readEnum(ThresholdSwitchBlockEntity.ThresholdType.class),buffer.readBoolean(),buffer.readInt(),buffer.readInt(),buffer.readInt(),buffer.readInt(),buffer.readInt(),buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean(),ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buffer),net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buffer),net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(buffer)));
    }
    public static final ExtendedMenuType<ThresholdSwitchMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("threshold_switch"),new ExtendedMenuType<>(ThresholdSwitchMenu::new,Opening.CODEC));
    private final Inventory inventory;
    private final ThresholdSwitchBlockEntity owner;
    private Opening opening;
    private int refresh;
    public ThresholdSwitchMenu(int id,Inventory inventory,Opening opening){super(TYPE,id);this.inventory=inventory;this.opening=opening;owner=inventory.player.level().isClientSide()?null:LoadedLevelAccess.blockEntity(inventory.player.level(),opening.pos)instanceof ThresholdSwitchBlockEntity sensor?sensor:null;}
    public Opening opening(){return opening;}
    public ThresholdSwitchBlockEntity owner(){return owner;}
    public void updateSnapshot(Opening next){if(next.pos.equals(opening.pos)&&next.revision>=opening.revision)opening=next;}
    public static Opening snapshot(ThresholdSwitchBlockEntity sensor,int revision){boolean stacks=sensor.inStacks&&sensor.getTypeOfCurrentTarget()==ThresholdSwitchBlockEntity.ThresholdType.ITEM;int step=sensor.getTypeOfCurrentTarget()==ThresholdSwitchBlockEntity.ThresholdType.FLUID?1000:stacks?64:1;return new Opening(sensor.getBlockPos(),revision,sensor.getTypeOfCurrentTarget(),sensor.hasValidSample(),sensor.currentMinLevel,sensor.currentLevel,sensor.currentMaxLevel,sensor.onWhenAbove,sensor.offWhenBelow,stacks,sensor.isInverted(),sensor.getState(),sensor.isPowered(),sensor.getDisplayItemForScreen(),sensor.format(sensor.getLevelForDisplay()/step,stacks),sensor.format(sensor.currentMinLevel/step,stacks),sensor.format(sensor.currentMaxLevel/step,stacks));}
    public void sendSnapshot(ServerPlayer player){opening=snapshot(owner,opening.revision+1);net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new ThresholdSwitchSnapshotPacket(containerId,opening));}
    public boolean configure(ServerPlayer player,ConfigureThresholdSwitchPacket packet){if(!stillValid(player))return false;owner.updateCurrentLevel();var current=snapshot(owner,opening.revision);if(packet.revision()!=opening.revision||opening.kind!=current.kind||opening.ready!=current.ready||opening.min!=current.min||opening.max!=current.max||!ItemStack.isSameItemSameComponents(opening.target,current.target)){sendSnapshot(player);return false;}boolean accepted=owner.configure(packet.upper(),packet.lower(),packet.stacks(),packet.inverted());sendSnapshot(player);return accepted;}
    @Override public void broadcastChanges(){super.broadcastChanges();if(owner!=null&&inventory.player instanceof ServerPlayer player&&stillValid(player)&&++refresh>=10){refresh=0;var next=snapshot(owner,opening.revision);if(!equivalent(opening,next)){if(opening.kind!=next.kind||opening.ready!=next.ready||opening.min!=next.min||opening.max!=next.max||opening.upper!=next.upper||opening.lower!=next.lower||opening.stacks!=next.stacks||opening.inverted!=next.inverted)sendSnapshot(player);else{opening=next;net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new ThresholdSwitchSnapshotPacket(containerId,next));}}}}
    private static boolean equivalent(Opening a,Opening b){return a.kind==b.kind&&a.ready==b.ready&&a.min==b.min&&a.current==b.current&&a.max==b.max&&a.upper==b.upper&&a.lower==b.lower&&a.stacks==b.stacks&&a.inverted==b.inverted&&a.raw==b.raw&&a.powered==b.powered&&ItemStack.isSameItemSameComponents(a.target,b.target)&&Objects.equals(a.currentText,b.currentText)&&Objects.equals(a.minText,b.minText)&&Objects.equals(a.maxText,b.maxText);}
    public static void open(Player player,ThresholdSwitchBlockEntity sensor){if(player instanceof ServerPlayer server){sensor.updateCurrentLevel();var data=snapshot(sensor,0);server.openMenu(new ExtendedMenuProvider<Opening>(){
        @Override public Component getDisplayName(){return Component.translatable("block.create.stockpile_switch");}
        @Override public Opening getScreenOpeningData(ServerPlayer ignored){return data;}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player ignored){return new ThresholdSwitchMenu(id,inventory,data);}
    });}}
    @Override public boolean stillValid(Player player){if(player.getInventory()!=inventory||player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(opening.pos,1)||!player.level().mayInteract(player,opening.pos))return false;return player.level().isClientSide()?LoadedLevelAccess.blockState(player.level(),opening.pos).getBlock()instanceof ThresholdSwitchBlock:owner!=null&&!owner.isRemoved()&&LoadedLevelAccess.blockEntity(player.level(),opening.pos)==owner;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    public static void register(){ConfigureThresholdSwitchPacket.register();ThresholdSwitchSnapshotPacket.register();}
}
