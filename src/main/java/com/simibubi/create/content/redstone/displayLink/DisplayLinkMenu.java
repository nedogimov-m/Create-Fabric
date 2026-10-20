package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.display.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.menu.v1.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/** Real native menu lifetime and server-snapshotted available source schemas/capacity. No client remote-world assumptions. */
public final class DisplayLinkMenu extends AbstractContainerMenu {
    public record SourceEntry(Identifier id,List<DisplaySourceConfiguration> options,CompoundTag defaults){
        public SourceEntry{options=List.copyOf(options);defaults=defaults.copy();}
        public SourceEntry(Identifier id,List<DisplaySourceConfiguration> options){this(id,options,new CompoundTag());}
    }
    public record Opening(BlockPos pos,List<SourceEntry> sources,Identifier target,CompoundTag config,int line,int rows){
        public Opening{pos=pos.immutable();sources=List.copyOf(sources);config=config.copy();rows=Math.clamp(rows,0,256);}
        public static final StreamCodec<RegistryFriendlyByteBuf,Opening> CODEC=StreamCodec.of((buffer,value)->{
            buffer.writeBlockPos(value.pos);buffer.writeVarInt(value.sources.size());for(var source:value.sources){buffer.writeIdentifier(source.id);buffer.writeVarInt(source.options.size());for(var option:source.options){buffer.writeUtf(option.key(),64);buffer.writeVarInt(option.maxLength());buffer.writeVarInt(option.choices().size());for(var choice:option.choices())buffer.writeUtf(choice,128);
                buffer.writeVarInt(option.minimum());buffer.writeVarInt(option.maximum());buffer.writeVarInt(option.shiftStep());buffer.writeVarInt(option.defaultValue());
                buffer.writeVarInt(option.widgetLine());buffer.writeVarInt(option.widgetOffset());buffer.writeVarInt(option.widgetWidth());buffer.writeUtf(option.title(),128);buffer.writeUtf(option.suffix(),32);
            }buffer.writeNbt(source.defaults);}buffer.writeBoolean(value.target!=null);if(value.target!=null)buffer.writeIdentifier(value.target);buffer.writeNbt(value.config);buffer.writeVarInt(value.line);buffer.writeVarInt(value.rows);
        },buffer->{var pos=buffer.readBlockPos();int count=count(buffer,64);var sources=new ArrayList<SourceEntry>();for(int i=0;i<count;i++){var id=buffer.readIdentifier();int options=count(buffer,16);var list=new ArrayList<DisplaySourceConfiguration>();for(int j=0;j<options;j++){var key=buffer.readUtf(64);int length=count(buffer,256),choices=count(buffer,32);var values=new ArrayList<String>();for(int k=0;k<choices;k++)values.add(buffer.readUtf(128));int minimum=buffer.readVarInt(),maximum=buffer.readVarInt(),shift=buffer.readVarInt(),initial=buffer.readVarInt();
                int line=buffer.readVarInt(),offset=buffer.readVarInt(),width=buffer.readVarInt();String title=buffer.readUtf(128),suffix=buffer.readUtf(32);
                list.add(new DisplaySourceConfiguration(key,values,length,minimum,maximum,shift,initial,line,offset,width,title,suffix));
            }var defaultTag=buffer.readNbt(NbtAccounter.create(8192));if(!(defaultTag instanceof CompoundTag defaults))throw new io.netty.handler.codec.DecoderException("Actual display source defaults must be a compound");sources.add(new SourceEntry(id,list,defaults));}var target=buffer.readBoolean()?buffer.readIdentifier():null;var raw=buffer.readNbt(NbtAccounter.create(16384));var config=raw instanceof CompoundTag tag?tag:new CompoundTag();return new Opening(pos,sources,target,config,buffer.readVarInt(),count(buffer,256));});
        private static int count(RegistryFriendlyByteBuf buffer,int max){int count=buffer.readVarInt();if(count<0||count>max)throw new io.netty.handler.codec.DecoderException("Invalid display configuration count "+count);return count;}
    }
    public static final ExtendedMenuType<DisplayLinkMenu,Opening> TYPE=Registry.register(BuiltInRegistries.MENU,Create.asResource("display_link"),new ExtendedMenuType<>(DisplayLinkMenu::new,Opening.CODEC));
    private final Inventory inventory;
    private final DisplayLinkBlockEntity owner;
    private Opening opening;
    private int revision,refresh;
    public DisplayLinkMenu(int id,Inventory inventory,Opening opening){super(TYPE,id);this.inventory=inventory;this.opening=opening;owner=inventory.player.level().isClientSide()?null:LoadedLevelAccess.blockEntity(inventory.player.level(),opening.pos)instanceof DisplayLinkBlockEntity link?link:null;}
    public Opening opening(){return opening;}
    public int revision(){return revision;}
    public void updateSnapshot(Opening next){if(!next.pos().equals(opening.pos()))return;opening=next;revision++;}
    @Override public void broadcastChanges(){super.broadcastChanges();if(owner==null||!(inventory.player instanceof ServerPlayer player)||!stillValid(player)||++refresh<10)return;refresh=0;var next=snapshot(owner);if(!next.sources().equals(opening.sources())||!Objects.equals(next.target(),opening.target())||next.rows()!=opening.rows()){opening=next;net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,new DisplayLinkSnapshotPacket(containerId,next));}}
    public DisplayLinkBlockEntity owner(){return owner;}
    public static Opening snapshot(DisplayLinkBlockEntity link){
        var context=new DisplayLinkContext(link.getLevel(),link);var sources=DisplaySource.getAll(link.getLevel(),link.getSourcePosition()).stream().limit(64).map(source->{var defaults=source==link.activeSource?link.getSourceConfig().copy():new CompoundTag();var draft=new DisplayLinkContext(link.getLevel(),link,defaults);source.populateData(draft);defaults.putString("Id",source.getId().toString());return new SourceEntry(source.getId(),source.configuration(draft),defaults);}).toList();var target=DisplayTarget.get(link.getLevel(),link.getTargetPosition());var stats=target==null?null:target.provideStats(context);var config=new CompoundTag();if(link.activeSource!=null){config.putString("Id",link.activeSource.getId().toString());for(var option:link.activeSource.configuration(context))option.copyValidated(link.getSourceConfig(),config);}return new Opening(link.getBlockPos(),sources,target==null?null:com.simibubi.create.api.registry.CreateBuiltInRegistries.DISPLAY_TARGET.getKey(target),config,link.targetLine,stats==null?0:stats.maxRows());
    }
    public static void open(Player player,DisplayLinkBlockEntity link){if(player instanceof ServerPlayer server){link.updateGatheredData();var data=snapshot(link);server.openMenu(new ExtendedMenuProvider<Opening>(){
        @Override public Component getDisplayName(){return Component.translatable("create.display_link.title");}
        @Override public Opening getScreenOpeningData(ServerPlayer ignored){return data;}
        @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player ignored){return new DisplayLinkMenu(id,inventory,data);}
    });}}
    @Override public boolean stillValid(Player player){if(player.getInventory()!=inventory||player.isSpectator()||!player.mayBuild()||!player.isWithinBlockInteractionRange(opening.pos,1)||!player.level().mayInteract(player,opening.pos))return false;return player.level().isClientSide()?LoadedLevelAccess.blockState(player.level(),opening.pos).getBlock()instanceof DisplayLinkBlock:owner!=null&&!owner.isRemoved()&&LoadedLevelAccess.blockEntity(player.level(),opening.pos)==owner;}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    public static void register(){DisplayLinkConfigurationPacket.register();DisplayLinkSnapshotPacket.register();ClickToLinkBlockItem.register();}
}
