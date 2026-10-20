package com.simibubi.create.content.processing.sequenced;

import com.simibubi.create.*;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.simibubi.create.foundation.item.ItemTooltipModifiers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.*;

public final class SequencedAssemblyTooltip {
    public static void register(){ItemTooltipModifiers.register();}
    public static void addToTooltip(TooltipModifier.Context context){
        var stack=context.getItemStack();var tooltip=context.getToolTip();
        var progress=stack.get(AllDataComponents.SEQUENCED_ASSEMBLY);var level=net.minecraft.client.Minecraft.getInstance().level;if(progress==null||level==null)return;
        var holder=level.recipeAccess().getSynchronizedRecipes().get(ResourceKey.create(Registries.RECIPE,progress.id()));if(holder==null||!(holder.value() instanceof SequencedAssemblyRecipe recipe))return;
        tooltip.add(Component.empty());tooltip.add(GoggleTooltip.translate("recipe.sequenced_assembly").withStyle(ChatFormatting.GRAY));tooltip.add(GoggleTooltip.translate("recipe.assembly.progress",progress.step(),recipe.totalSteps()).withStyle(ChatFormatting.DARK_GRAY));
        for(int n=0;n<recipe.sequence().size()&&n<recipe.totalSteps()-progress.step();n++){
            var step=recipe.sequence().get((n+progress.step())%recipe.sequence().size()).recipe();Component text;
            if(step instanceof ItemApplicationRecipe application){var item=application.getRequiredHeldItem().items().findFirst();text=GoggleTooltip.translate("recipe.assembly.deploying_item",item.isPresent()?item.get().value().getName(new net.minecraft.world.item.ItemStack(item.get().value())):Component.literal("Invalid"));}
            else if(step instanceof com.simibubi.create.content.fluids.transfer.FillingRecipe filling){var ingredient=filling.getRequiredFluid();var fluid=net.minecraft.core.registries.BuiltInRegistries.FLUID.stream().filter(candidate->ingredient.test(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(candidate))).findFirst();text=GoggleTooltip.translate("recipe.assembly.spout_filling_fluid",fluid.isPresent()?net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid.get())):Component.literal("Invalid"));}
            else text=GoggleTooltip.translate("recipe.assembly."+(step.getType()==AllRecipeTypes.PRESSING?"pressing":"cutting"));
            tooltip.add(n==0?GoggleTooltip.translate("recipe.assembly.next",text).withStyle(ChatFormatting.AQUA):Component.literal("-> ").append(text).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
    private SequencedAssemblyTooltip(){}
}
