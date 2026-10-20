package com.simibubi.create;

import net.fabricmc.fabric.api.transfer.v1.client.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.renderer.block.*;
import net.minecraft.client.resources.model.sprite.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.material.Fluid;
import java.util.*;

/** Native baked fluid models work for chunk fluids and extracted block-entity meshes on every backend. */
public final class AllFluidModels {
    private AllFluidModels(){}
    public static Map<Fluid,FluidModel> bake(MaterialBaker baker,Map<Fluid,FluidModel> vanilla){
        var result=new IdentityHashMap<Fluid,FluidModel>();result.putAll(vanilla);
        var entries=Map.of("potion",AllFluids.POTION_FAMILY,"tea",AllFluids.TEA_FAMILY,"milk",AllFluids.MILK_FAMILY,"honey",AllFluids.HONEY_FAMILY,"chocolate",AllFluids.CHOCOLATE_FAMILY);
        entries.forEach((name,family)->{
            var model=new FluidModel.Unbaked(new Material(Create.asResource("block/"+name+"_still"),family.virtual),new Material(Create.asResource("block/"+name+"_flow"),family.virtual),null,null).bake(baker,()->"create:"+name);
            result.put(family.source,model);result.put(family.flowing,model);
        });return Collections.unmodifiableMap(result);
    }
    public static void register(){
        var potion=new FluidVariantRenderHandler(){
            @Override public int getColor(FluidVariant fluid,BlockAndTintGetter level,net.minecraft.core.BlockPos pos){return fluid.getComponents().getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY).getColor()|0xFF000000;}
            @Override public void appendTooltip(FluidVariant fluid,List<net.minecraft.network.chat.Component> tooltip,net.minecraft.world.item.TooltipFlag flags){
                var contents=fluid.getComponents().getOrDefault(DataComponents.POTION_CONTENTS,PotionContents.EMPTY);
                PotionContents.addPotionTooltip(contents.getAllEffects(),tooltip::add,1,20);
            }
        };FluidVariantRendering.register(AllFluids.POTION,potion);FluidVariantRendering.register(AllFluids.FLOWING_POTION,potion);
    }
}
