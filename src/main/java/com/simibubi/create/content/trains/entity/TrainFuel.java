package com.simibubi.create.content.trains.entity;

import java.util.Objects;
import java.util.Optional;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.context.ContextKeySet;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Genuine native cooking-fuel providers with one committed source fuel item and its full remainder. */
public final class TrainFuel {
    // A moving carriage has an actual position and candidate fuel inventory, but no furnace block.
    private static final ContextKeySet CONTEXT=new ContextKeySet.Builder()
        .required(LootContextParams.ORIGIN).required(LootContextParams.CONTAINER)
        .optional(LootContextParams.THIS_ENTITY).build();

    public static int consume(Storage<ItemVariant> fuel,ServerLevel level,Vec3 origin,@Nullable Entity front){
        Objects.requireNonNull(fuel);Objects.requireNonNull(level);Objects.requireNonNull(origin);
        if(!Double.isFinite(origin.x)||!Double.isFinite(origin.y)||!Double.isFinite(origin.z))
            throw new IllegalArgumentException("Nonfinite actual native train fuel position");
        if(front!=null&&(front.level()!=level||front.isRemoved()))
            throw new IllegalArgumentException("Fuel context must use this actual live native carriage front");
        for(var slot:fuel){
            if(slot.isResourceBlank()||slot.getAmount()<1)continue;
            var resource=slot.getResource();
            var stack=resource.toStack(1);
            int duration=duration(stack,level,origin,front);
            if(duration<=0)continue;
            try(var transaction=Transaction.openOuter()){
                if(slot.extract(resource,1,transaction)!=1)continue;
                var remainderTemplate=stack.getItem().getCraftingRemainder();
                if(remainderTemplate!=null){
                    var remainder=remainderTemplate.create();
                    if(!remainder.isEmpty()&&fuel.insert(ItemVariant.of(remainder),remainder.getCount(),transaction)!=remainder.getCount())
                        continue;
                }
                transaction.commit();
                return duration;
            }
        }
        return 0;
    }

    public static int duration(ItemStack candidate,ServerLevel level,Vec3 origin,@Nullable Entity front){
        if(candidate.isEmpty())return 0;
        // The native Container is a read-only contextual snapshot of this candidate, never cargo custody.
        var parameters=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,origin)
            .withParameter(LootContextParams.CONTAINER,new SimpleContainer(candidate.copyWithCount(1)))
            .withOptionalParameter(LootContextParams.THIS_ENTITY,front).create(CONTEXT);
        var context=new LootContext.Builder(parameters).create(Optional.empty());
        return Math.max(0,ResolvableInt.getFromItem(candidate,DataComponents.COOKING_FUEL,CookingFuel::burnTime,context,0));
    }
    private TrainFuel(){}
}
