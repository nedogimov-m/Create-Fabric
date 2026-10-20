package com.simibubi.create.content.equipment.armor;

import com.simibubi.create.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class RemainingAirOverlay {
    public static final RemainingAirOverlay INSTANCE=new RemainingAirOverlay();
    public void extractOverlay(GuiGraphicsExtractor gui){var client=Minecraft.getInstance();var player=client.player;if(player==null||player.isCreative()||player.isSpectator()||client.gui.hud.isHidden())return;boolean lava=player.isInLava();boolean drowning=player.isEyeInFluid(FluidTags.WATER)||com.simibubi.create.content.fluids.FluidPhysics.eyesImmersed(player);boolean bubble=player.level().getBlockState(net.minecraft.core.BlockPos.containing(player.getX(),player.getEyeY(),player.getZ())).is(Blocks.BUBBLE_COLUMN);boolean natural=player.canBreatheUnderwater()||MobEffectUtil.hasWaterBreathing(player)||player.getAbilities().invulnerable;if((!drowning||bubble||natural)&&!lava||!DivingHelmetItem.canProvideAir(player,lava))return;long seconds=BacktankUtil.getAllWithAir(player).stream().mapToLong(BacktankUtil::getAir).sum();var tank=getDisplayedBacktank(player);int x=gui.guiWidth()/2+90,y=gui.guiHeight()-53+(NetheriteDivingHandler.isFireResistant(tank)?9:0);gui.item(tank,x,y);int color=0xFFFFFFFF;if(seconds<60&&seconds%2==0){float mix=Math.max(seconds/60f,.25f);color=0xFFFF0000|((int)(255*mix)<<8)|(int)(255*mix);}gui.text(client.font,Component.literal(StringUtil.formatTickDuration((int)Math.min(Integer.MAX_VALUE,Math.max(0L,seconds-1)*20L),client.level.tickRateManager().tickrate())),x+16,y+5,color,false);}
    public static ItemStack getDisplayedBacktank(LocalPlayer player){var tanks=BacktankUtil.getAllWithAir(player);return tanks.isEmpty()?new ItemStack(AllItems.COPPER_BACKTANK):tanks.getFirst();}
    private RemainingAirOverlay(){}
}
