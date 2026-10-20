package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.AllDataComponents;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.equipment.*;
import net.minecraft.world.level.block.Block;

public class BacktankItem extends BaseArmorItem {
    public static final EquipmentSlot SLOT=EquipmentSlot.CHEST;
    public static final ArmorType TYPE=ArmorType.CHESTPLATE;
    public static final int BAR_COLOR=0xEFEFEF;
    private final Supplier<BacktankBlockItem> blockItem;
    public BacktankItem(ArmorMaterial material,Properties properties,Identifier textureLoc,Supplier<BacktankBlockItem> placeable){super(material,TYPE,properties,textureLoc);blockItem=placeable;}
    public static BacktankItem getWornBy(Entity entity){return entity instanceof LivingEntity living&&living.getItemBySlot(SLOT).getItem() instanceof BacktankItem item?item:null;}
    @Override public InteractionResult useOn(UseOnContext context){return blockItem.get().useOn(context);}
    public boolean supportsEnchantment(ItemStack stack,Holder<Enchantment> enchantment){return !enchantment.is(Enchantments.MENDING)&&!enchantment.is(Enchantments.UNBREAKING);}
    @Override public boolean isBarVisible(ItemStack stack){return true;}
    @Override public int getBarWidth(ItemStack stack){return Math.round(13f*Math.clamp(getRemainingAir(stack)/(float)BacktankUtil.maxAir(stack),0,1));}
    @Override public int getBarColor(ItemStack stack){return BAR_COLOR;}
    public Block getBlock(){return blockItem.get().getBlock();}
    public static int getRemainingAir(ItemStack stack){return stack.getOrDefault(AllDataComponents.BACKTANK_AIR,0);}
    public static class BacktankBlockItem extends BlockItem {
        private final Supplier<Item> actualItem;
        public BacktankBlockItem(Block block,Supplier<Item> actualItem,Properties properties){super(block,properties);this.actualItem=actualItem;}
        public Item getActualItem(){return actualItem.get();}
    }
    public static class Layered extends BacktankItem {
        public Layered(ArmorMaterial material,Properties properties,Identifier textureLoc,Supplier<BacktankBlockItem> placeable){super(material,properties,textureLoc,placeable);}
        public String getArmorTextureLocation(LivingEntity entity,EquipmentSlot slot,ItemStack stack,int layer){return String.format(Locale.ROOT,"%s:textures/models/armor/%s_layer_%d.png",textureLoc.getNamespace(),textureLoc.getPath(),layer);}
    }
}
