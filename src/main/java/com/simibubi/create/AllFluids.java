package com.simibubi.create;

import com.simibubi.create.content.fluids.CreateFlowingFluid;
import com.simibubi.create.content.fluids.potion.PotionFluidHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.*;
import net.minecraft.core.Registry;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/** Paired registry IDs follow Create; milk supplies the platform fluid that NeoForge normally provides. */
public final class AllFluids {
    public static final CreateFlowingFluid.Family POTION_FAMILY=family("potion",true,1000);
    public static final CreateFlowingFluid.Family TEA_FAMILY=family("tea",true,1000);
    public static final CreateFlowingFluid.Family MILK_FAMILY=family("milk",true,1000);
    public static final CreateFlowingFluid.Family HONEY_FAMILY=family("honey",false,2000);
    public static final CreateFlowingFluid.Family CHOCOLATE_FAMILY=family("chocolate",false,1500);
    public static final CreateFlowingFluid POTION=POTION_FAMILY.source,FLOWING_POTION=POTION_FAMILY.flowing,
        TEA=TEA_FAMILY.source,FLOWING_TEA=TEA_FAMILY.flowing,MILK=MILK_FAMILY.source,FLOWING_MILK=MILK_FAMILY.flowing,
        HONEY=HONEY_FAMILY.source,FLOWING_HONEY=HONEY_FAMILY.flowing,CHOCOLATE=CHOCOLATE_FAMILY.source,FLOWING_CHOCOLATE=CHOCOLATE_FAMILY.flowing;
    private AllFluids(){}
    private static CreateFlowingFluid.Family family(String name,boolean virtual,int viscosity){
        var family=new CreateFlowingFluid.Family(virtual,viscosity);
        Registry.register(BuiltInRegistries.FLUID,Create.asResource(name),family.source);
        Registry.register(BuiltInRegistries.FLUID,Create.asResource("flowing_"+name),family.flowing);
        return family;
    }
    public static void register(){}
    public static net.minecraft.world.level.block.state.BlockState getLavaInteraction(net.minecraft.world.level.material.FluidState fluid){return fluid.getType().isSame(HONEY)?AllBlocks.LIMESTONE.defaultBlockState():fluid.getType().isSame(CHOCOLATE)?AllBlocks.SCORIA.defaultBlockState():null;}
    public static void registerStorage(){
        for(var family:java.util.List.of(POTION_FAMILY,TEA_FAMILY,MILK_FAMILY,HONEY_FAMILY,CHOCOLATE_FAMILY)){
            var handler=new FluidVariantAttributeHandler(){
                @Override public int getViscosity(FluidVariant fluid,net.minecraft.world.level.Level level){return family.viscosity;}
                @Override public Component getName(FluidVariant fluid){return fluid.isOf(POTION)?PotionFluidHandler.name(fluid):Component.translatable("fluid.create."+BuiltInRegistries.FLUID.getKey(family.source).getPath());}
            };
            FluidVariantAttributes.register(family.source,handler);FluidVariantAttributes.register(family.flowing,handler);
        }
        // Expose the platform milk container without making a new survival milk bucket.
        FluidStorage.ITEM.registerForItems((stack,context)->new FullItemFluidStorage(context,Items.BUCKET,FluidVariant.of(MILK),FluidConstants.BUCKET),Items.MILK_BUCKET);
        FluidStorage.combinedItemApiProvider(Items.BUCKET).register(context->new EmptyItemFluidStorage(context,Items.MILK_BUCKET,MILK,FluidConstants.BUCKET));
        // Use the native container behaviour so failed placement dispenses the full bucket.
        var fluidDispenser=new net.minecraft.core.dispenser.DefaultDispenseItemBehavior(){
            @Override protected net.minecraft.world.item.ItemStack execute(net.minecraft.core.dispenser.BlockSource source,net.minecraft.world.item.ItemStack stack){
                var container=(net.minecraft.world.item.DispensibleContainerItem)stack.getItem();
                var pos=source.pos().relative(source.state().getValue(net.minecraft.world.level.block.DispenserBlock.FACING));
                if(container.emptyContents(null,source.level(),pos,null)){
                    container.checkExtraContent(null,source.level(),stack,pos);
                    return consumeWithRemainder(source,stack,new net.minecraft.world.item.ItemStack(Items.BUCKET));
                }
                return super.execute(source,stack);
            }
        };
        net.minecraft.world.level.block.DispenserBlock.registerBehavior(HONEY_FAMILY.bucket,fluidDispenser);
        net.minecraft.world.level.block.DispenserBlock.registerBehavior(CHOCOLATE_FAMILY.bucket,fluidDispenser);
    }
}
