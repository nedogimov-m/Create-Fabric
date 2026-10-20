package com.simibubi.create.test;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.Registry;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;

/** Synthetic addon container with component-sensitive fluid and a mutable item context. */
public final class FluidTransferFixtures implements ModInitializer {
    public static Item FLASK;
    @Override public void onInitialize() {
        var key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("create_test", "flask"));
        FLASK = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
        FluidStorage.ITEM.registerForItems((stack, context) -> new FlaskStorage(context), FLASK);
    }
    private static final class FlaskStorage extends SingleVariantStorage<FluidVariant> {
        private final ContainerItemContext context;
        FlaskStorage(ContainerItemContext context) {
            this.context=context;
            var stack=context.getItemVariant().toStack();amount=stack.getOrDefault(DataComponents.DAMAGE,0);
            var components=DataComponentPatch.builder();
            if(stack.has(DataComponents.CUSTOM_NAME))components.set(DataComponents.CUSTOM_NAME,stack.get(DataComponents.CUSTOM_NAME));
            variant=amount==0?FluidVariant.blank():FluidVariant.of(Fluids.WATER,components.build());
        }
        @Override protected FluidVariant getBlankVariant(){return FluidVariant.blank();}
        @Override protected long getCapacity(FluidVariant fluid){return 2*FluidConstants.BUCKET;}
        @Override protected boolean canInsert(FluidVariant fluid){return fluid.getFluid()==Fluids.WATER;}
        @Override public long insert(FluidVariant fluid,long max,TransactionContext tx){return transfer(fluid,max,tx,true);}
        @Override public long extract(FluidVariant fluid,long max,TransactionContext tx){return transfer(fluid,max,tx,false);}
        private long transfer(FluidVariant fluid,long max,TransactionContext parent,boolean filling){
            try(var tx=parent.openNested()){
                long moved=filling?super.insert(fluid,max,tx):super.extract(fluid,max,tx);
                if(moved==0)return 0;
                var result=context.getItemVariant().toStack();result.set(DataComponents.DAMAGE,(int)amount);
                if(!variant.isBlank()&&variant.getComponents().has(DataComponents.CUSTOM_NAME))result.set(DataComponents.CUSTOM_NAME,variant.getComponents().get(DataComponents.CUSTOM_NAME));
                if(context.exchange(ItemVariant.of(result),1,tx)!=1)return 0;
                tx.commit();return moved;
            }
        }
    }
}
