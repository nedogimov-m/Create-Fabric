package com.simibubi.create.foundation.mixin;

import com.simibubi.create.AllDataComponents;
import net.minecraft.core.component.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextArg;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import java.util.function.Predicate;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The inert subset belongs to logical BEES even when native hive loot explicitly includes only BEES. */
@Mixin(CopyComponentsFunction.class)
public abstract class NeoForgeInertHiveLootMixin {
    @Shadow @Final private LootContextArg<DataComponentGetter> source;
    @Shadow @Final private Predicate<DataComponentType<?>> bakedPredicate;
    @Inject(method="run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",at=@At("RETURN"))
    private void create$copyPairedInert(ItemStack input,LootContext context,CallbackInfoReturnable<ItemStack> callback){
        var getter=source.get(context);if(getter==null)return;
        var inert=getter.get(AllDataComponents.INERT_HIVE_SLOTS);
        if(!bakedPredicate.test(DataComponents.BEES)){
            if(inert!=null)callback.getReturnValue().remove(AllDataComponents.INERT_HIVE_SLOTS);return;
        }
        if(getter.get(DataComponents.BEES)==null)return;
        if(inert!=null&&!inert.isEmpty())callback.getReturnValue().set(AllDataComponents.INERT_HIVE_SLOTS,inert);
        else callback.getReturnValue().remove(AllDataComponents.INERT_HIVE_SLOTS);
    }
}
