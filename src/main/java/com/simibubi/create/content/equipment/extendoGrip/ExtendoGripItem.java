package com.simibubi.create.content.equipment.extendoGrip;

import com.simibubi.create.Create;
import com.simibubi.create.content.equipment.EquipmentAdvancements;
import com.simibubi.create.content.equipment.EquipmentConfig;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;

/** The original held-grip modifiers and actual action costs, without persistent surrogate flags. */
public class ExtendoGripItem extends Item {
    public static final int MAX_DAMAGE=200;
    public static final String EXTENDO_MARKER="createExtendo",DUAL_EXTENDO_MARKER="createDualExtendo";
    public static final AttributeModifier singleRangeAttributeModifier=new AttributeModifier(Create.asResource("single_range_attribute_modifier"),3,AttributeModifier.Operation.ADD_VALUE);
    public static final AttributeModifier doubleRangeAttributeModifier=new AttributeModifier(Create.asResource("double_range_attribute_modifier"),5,AttributeModifier.Operation.ADD_VALUE);
    public ExtendoGripItem(Properties properties){super(properties.durability(MAX_DAMAGE).rarity(Rarity.UNCOMMON));}
    public static boolean isHoldingExtendoGrip(Player player){return grip(player.getMainHandItem())||grip(player.getOffhandItem());}
    private static boolean grip(ItemStack stack){return stack.is(com.simibubi.create.AllItems.EXTENDO_GRIP);}
    public static void holdingExtendoGripIncreasesRange(Player player){
        var attribute=player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);if(attribute==null)return;
        boolean main=grip(player.getMainHandItem()),off=grip(player.getOffhandItem()),dual=main&&off,single=main^off;
        boolean hadSingle=attribute.hasModifier(singleRangeAttributeModifier.id()),hadDual=attribute.hasModifier(doubleRangeAttributeModifier.id());
        if(!single&&hadSingle)attribute.removeModifier(singleRangeAttributeModifier.id());
        if(!dual&&hadDual)attribute.removeModifier(doubleRangeAttributeModifier.id());
        if(single&&!hadSingle){attribute.addTransientModifier(singleRangeAttributeModifier);EquipmentAdvancements.award(player,"extendo_grip");}
        if(dual&&!hadDual){attribute.addTransientModifier(doubleRangeAttributeModifier);EquipmentAdvancements.award(player,"extendo_grip_dual");}
    }
    public static void findAndDamageExtendoGrip(Player player){
        if(player==null||player.level().isClientSide())return;
        var slot=EquipmentSlot.MAINHAND;var stack=player.getMainHandItem();if(!grip(stack)){stack=player.getOffhandItem();slot=EquipmentSlot.OFFHAND;}if(!grip(stack))return;
        if(!BacktankUtil.canAbsorbDamage(player,EquipmentConfig.get(player.level()).maxExtendoGripActions()))stack.hurtAndBreak(1,player,slot);
    }
    private static boolean registered;
    public static void register(){if(registered)return;registered=true;
        PlayerBlockBreakEvents.AFTER.register((level,player,pos,state,be)->{if(player instanceof net.minecraft.server.level.ServerPlayer server&&com.simibubi.create.content.equipment.symmetryWand.SymmetryPlacement.internalBreak(server,pos))return;findAndDamageExtendoGrip(player);});
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((target,source,damage)->{if(source.getEntity() instanceof Player player)findAndDamageExtendoGrip(player);return true;});
        com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
            if(player.isSpectator()||!player.isSecondaryUseActive()||!isHoldingExtendoGrip(player))return InteractionResult.PASS;
            var main=player.getMainHandItem();var off=player.getOffhandItem();
            if(!(main.isEmpty()||grip(main))||!(off.isEmpty()||grip(off)))return InteractionResult.PASS;
            if(!player.isWithinBlockInteractionRange(hit.getBlockPos(),0)||!level.mayInteract(player,hit.getBlockPos())||!level.hasChunkAt(hit.getBlockPos()))return InteractionResult.PASS;
            return level.getBlockState(hit.getBlockPos()).useWithoutItem(level,player,hit);
        });
    }
    @Override public boolean isBarVisible(ItemStack stack){return BacktankUtil.isBarVisible(stack,EquipmentConfig.client().maxExtendoGripActions());}
    @Override public int getBarWidth(ItemStack stack){return BacktankUtil.getBarWidth(stack,EquipmentConfig.client().maxExtendoGripActions());}
    @Override public int getBarColor(ItemStack stack){return BacktankUtil.getBarColor(stack,EquipmentConfig.client().maxExtendoGripActions());}
}
