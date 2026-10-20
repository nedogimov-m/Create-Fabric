package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllEnchantments;
import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.EquipmentConfig;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.*;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

/** Source air/debit/supplier API over actual equipped stacks. Backtank item gameplay is a separate port. */
public final class BacktankUtil {
    public static final TagKey<Item> PRESSURIZED_AIR_SOURCES=TagKey.create(Registries.ITEM,Create.asResource("pressurized_air_sources"));
    private static final List<Function<LivingEntity,List<ItemStack>>> BACKTANK_SUPPLIERS=new ArrayList<>();
    public static Supplier<Player> CLIENT_PLAYER=()->null;
    static {addBacktankSupplier(entity->{var result=new ArrayList<ItemStack>();for(var slot:EquipmentSlot.values())if(slot.isArmor()){var stack=entity.getItemBySlot(slot);if(stack.is(PRESSURIZED_AIR_SOURCES))result.add(stack);}return result;});}
    public static void addBacktankSupplier(Function<LivingEntity,List<ItemStack>> supplier){BACKTANK_SUPPLIERS.add(Objects.requireNonNull(supplier));}
    public static List<ItemStack> getAllWithAir(LivingEntity entity){
        var result=new ArrayList<ItemStack>();var seen=Collections.newSetFromMap(new IdentityHashMap<ItemStack,Boolean>());
        var config=EquipmentConfig.get(entity.level());
        for(var supplier:BACKTANK_SUPPLIERS)for(var stack:supplier.apply(entity))if(!stack.isEmpty()&&seen.add(stack)&&air(stack,config)>0)result.add(stack);
        result.sort(Comparator.comparingInt(stack->air(stack,config)));return result;
    }
    public static boolean hasAirRemaining(ItemStack stack){return getAir(stack)>0;}
    public static int getAir(ItemStack stack){return air(stack,EquipmentConfig.current());}
    private static int air(ItemStack stack,EquipmentConfig.Values config){return Math.clamp(stack.getOrDefault(AllDataComponents.BACKTANK_AIR,0),0,capacity(stack,config));}
    public static int maxAir(ItemStack stack){return capacity(stack,EquipmentConfig.current());}
    public static int maxAir(net.minecraft.world.level.Level level,ItemStack stack){return capacity(stack,EquipmentConfig.get(level));}
    private static int capacity(ItemStack stack,EquipmentConfig.Values config){
        int capacity=0;
        for(var entry:stack.getEnchantments().entrySet())if(entry.getKey().is(AllEnchantments.CAPACITY)){capacity=entry.getIntValue();break;}
        return (int)Math.min(Integer.MAX_VALUE,(long)config.airInBacktank()+(long)config.enchantedBacktankCapacity()*Math.max(0,capacity));
    }
    public static int maxAir(int enchantLevel){var config=EquipmentConfig.current();return (int)Math.min(Integer.MAX_VALUE,(long)config.airInBacktank()+(long)config.enchantedBacktankCapacity()*Math.max(0,enchantLevel));}
    public static int maxAirWithoutEnchants(){return EquipmentConfig.current().airInBacktank();}
    public static void consumeAir(LivingEntity entity,ItemStack backtank,int amount){
        if(amount<0)throw new IllegalArgumentException("Negative backtank debit");
        var config=EquipmentConfig.get(entity.level());int previous=air(backtank,config),maximum=capacity(backtank,config),next=Math.max(previous-amount,0);
        backtank.set(AllDataComponents.BACKTANK_AIR,Math.min(next,maximum));
        if(entity instanceof ServerPlayer player){warn(player,previous,next,maximum/10f);warn(player,previous,next,1);}
    }
    private static void warn(ServerPlayer player,float previous,float next,float threshold){
        if(next>threshold||previous<=threshold)return;boolean empty=threshold==1;
        var text=Component.translatable("create.backtank."+(empty?"depleted":"low"));
        player.level().playSound(null,player.blockPosition(),com.simibubi.create.AllSoundEvents.DENY,SoundSource.BLOCKS,1,1.25f);
        player.level().playSound(null,player.blockPosition(),com.simibubi.create.AllSoundEvents.STEAM,SoundSource.BLOCKS,.5f,.5f);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10,40,10));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("\u26A0 ").withStyle(empty?ChatFormatting.RED:ChatFormatting.GOLD).append(text.withStyle(ChatFormatting.GRAY))));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
    }
    public static boolean canAbsorbDamage(LivingEntity entity,int usesPerTank){
        if(usesPerTank==0||entity instanceof Player player&&player.hasInfiniteMaterials())return true;
        if(usesPerTank<0)throw new IllegalArgumentException("Negative tool uses");
        var sources=getAllWithAir(entity);if(sources.isEmpty())return false;
        consumeAir(entity,sources.getFirst(),Math.max(EquipmentConfig.get(entity.level()).airInBacktank()/usesPerTank,1));return true;
    }
    public static boolean isBarVisible(ItemStack stack,int uses){if(uses==0)return false;var player=CLIENT_PLAYER.get();if(player==null)return false;return !getAllWithAir(player).isEmpty()||stack.isDamaged();}
    public static int getBarWidth(ItemStack stack,int uses){
        if(uses==0)return 13;var player=CLIENT_PLAYER.get();if(player==null)return 13;var sources=getAllWithAir(player);
        if(sources.isEmpty())return Math.round(13f-stack.getDamageValue()/(float)stack.getMaxDamage()*13);
        return Math.round((float)sources.stream().mapToInt(tank->tank.getItem().getBarWidth(tank)).sum()/sources.size());
    }
    public static int getBarColor(ItemStack stack,int uses){
        if(uses==0)return 0;var player=CLIENT_PLAYER.get();if(player==null)return 0;var sources=getAllWithAir(player);
        return sources.isEmpty()?Mth.hsvToRgb(Math.max(0,1-stack.getDamageValue()/(float)stack.getMaxDamage())/3,1,1):sources.getFirst().getItem().getBarColor(sources.getFirst());
    }
    private BacktankUtil(){}
}
