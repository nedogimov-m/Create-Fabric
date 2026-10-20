package com.simibubi.create.content.redstone.displayLink.source;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.smartObserver.*;
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;

/** Read-only adapters to the actual observer/filter/native storages. Simulated count extraction always rolls back. */
public final class ObserverDisplayAccess {
    public static SmartObserverBlockEntity observer(DisplayLinkContext context){return context.getSourceBlockEntity()instanceof SmartObserverBlockEntity observer?observer:null;}
    public static BlockPos target(SmartObserverBlockEntity observer){return observer.getBlockPos().relative(SmartObserverBlock.getTargetDirection(observer.getBlockState()));}
    public static boolean available(DisplayLinkContext context){var observer=observer(context);if(observer==null)return false;var pos=target(observer);if(!LoadedLevelAccess.isLoaded(context.level(),pos))return false;var state=LoadedLevelAccess.blockState(context.level(),pos);if(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock&&state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)!=net.minecraft.world.level.block.state.properties.ChestType.SINGLE&&!LoadedLevelAccess.isLoaded(context.level(),net.minecraft.world.level.block.ChestBlock.getConnectedBlockPos(pos,state)))return false;var block=LoadedLevelAccess.blockEntity(context.level(),pos);if(block instanceof com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity vault){var master=vault.getControllerBE();return master!=null&&master.structureLoaded();}if(block instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank)return tank.getControllerBE()!=null&&tank.structureLoaded();return true;}
    public static net.fabricmc.fabric.api.transfer.v1.storage.Storage<FluidVariant> fluidStorage(DisplayLinkContext context){var observer=observer(context);return observer==null||!available(context)?null:SmartObserverBlockEntity.findFluids(context.level(),target(observer));}
    public static Map<ItemVariant,Long> items(DisplayLinkContext context,boolean extractable){
        var observer=observer(context);if(observer==null)return Map.of();var target=target(observer);if(!LoadedLevelAccess.isLoaded(context.level(),target))return Map.of();var inventory=SmartObserverBlockEntity.findItems(context.level(),target);if(inventory==null)return Map.of();var values=new LinkedHashMap<ItemVariant,Long>();for(var view:inventory)if(!view.isResourceBlank()&&view.getAmount()>0&&observer.filtering.test(view.getResource().toStack()))values.merge(view.getResource(),view.getAmount(),ObserverDisplayAccess::sum);
        if(extractable)for(var variant:List.copyOf(values.keySet()))try(var tx=Transaction.openOuter()){long amount=inventory.extract(variant,Long.MAX_VALUE,tx);if(amount<=0)values.remove(variant);else values.put(variant,amount);}return values;
    }
    public static Map<FluidVariant,Long> fluids(DisplayLinkContext context){var observer=observer(context);if(observer==null)return Map.of();var target=target(observer);if(!LoadedLevelAccess.isLoaded(context.level(),target))return Map.of();var inventory=SmartObserverBlockEntity.findFluids(context.level(),target);if(inventory==null)return Map.of();var values=new LinkedHashMap<FluidVariant,Long>();for(var view:inventory)if(!view.isResourceBlank()&&view.getAmount()>0&&observer.filtering.test(new FluidStack(view.getResource(),view.getAmount())))values.merge(view.getResource(),view.getAmount(),ObserverDisplayAccess::sum);return values;}
    public static int count(long value){return (int)Math.clamp(value,0,Integer.MAX_VALUE);}
    public static long sum(long first,long second){return first>Long.MAX_VALUE-second?Long.MAX_VALUE:first+second;}
    private ObserverDisplayAccess(){}
}
