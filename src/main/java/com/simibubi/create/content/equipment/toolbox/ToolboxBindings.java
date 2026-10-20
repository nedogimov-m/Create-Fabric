package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.Create;
import java.util.*;
import net.fabricmc.fabric.api.attachment.v1.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;

/** Platform-persisted, death-copied, owner-only synchronized real player data. */
public final class ToolboxBindings {
    public static final AttachmentType<CompoundTag> DATA=AttachmentRegistry.<CompoundTag>builder().persistent(CompoundTag.CODEC).copyOnDeath().initializer(CompoundTag::new).syncWith(ByteBufCodecs.COMPOUND_TAG,AttachmentSyncPredicate.targetOnly()).buildAndRegister(Create.asResource("toolbox_bindings"));
    public record Binding(BlockPos pos,int compartment,String dimension,UUID owner){}
    public static CompoundTag get(Player player){return player.getAttachedOrElse(DATA,new CompoundTag()).copy();}
    public static Binding get(Player player,int hotbar){if(hotbar<0||hotbar>8)return null;CompoundTag data=get(player).getCompoundOrEmpty(Integer.toString(hotbar));if(data.isEmpty())return null;BlockPos pos=data.read("Pos",BlockPos.CODEC).orElseGet(()->{var legacy=data.getCompoundOrEmpty("Pos");return legacy.contains("X")&&legacy.contains("Y")&&legacy.contains("Z")?new BlockPos(legacy.getIntOr("X",0),legacy.getIntOr("Y",0),legacy.getIntOr("Z",0)):null;});int compartment=data.getIntOr("Slot",-1);if(pos==null||compartment<0||compartment>=8)return null;return new Binding(pos,compartment,data.getStringOr("Dimension",""),data.read("Owner",UUIDUtil.CODEC).orElse(null));}
    public static void set(Player player,int hotbar,ToolboxBlockEntity box,int compartment){if(hotbar<0||hotbar>8||compartment<0||compartment>=8||box.getUniqueId()==null)throw new IllegalArgumentException("Actual initialized toolbox binding required");CompoundTag all=get(player),entry=new CompoundTag();entry.store("Pos",BlockPos.CODEC,box.getBlockPos());entry.putInt("Slot",compartment);entry.putString("Dimension",box.getLevel().dimension().identifier().toString());entry.store("Owner",UUIDUtil.CODEC,box.getUniqueId());all.put(Integer.toString(hotbar),entry);player.setAttached(DATA,all);}
    public static void remove(Player player,int hotbar){CompoundTag data=get(player);if(data.remove(Integer.toString(hotbar))!=null)player.setAttached(DATA,data);}
    public static boolean matches(Player player,Binding binding,ToolboxBlockEntity box){return binding!=null&&box!=null&&box.getLevel()==player.level()&&binding.pos.equals(box.getBlockPos())&&(binding.dimension.isEmpty()||binding.dimension.equals(player.level().dimension().identifier().toString()))&&(binding.owner==null||binding.owner.equals(box.getUniqueId()));}
    /** A migration reads genuine old saved player data; no synthetic player state is generated. */
    public static void migrate(Player player,ValueInput input){if(player.hasAttached(DATA)&&!get(player).isEmpty())return;for(ValueInput holder:List.of(input,input.childOrEmpty("NeoForgeData"),input.childOrEmpty("ForgeData"))){var old=holder.read("CreateToolboxData",CompoundTag.CODEC);if(old.isPresent()&&!old.get().isEmpty()){player.setAttached(DATA,old.get().copy());return;}}}
    public static AttachmentType<CompoundTag> register(){return DATA;}
    private ToolboxBindings(){}
}
