package com.simibubi.create.content.contraptions.behaviour.dispenser;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.contraption.dispenser.DefaultMountedDispenseBehavior;
import com.simibubi.create.api.contraption.dispenser.MountedDispenseBehavior;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.item.MountedItemInventory;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.ArrayList;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LevelEvent;

/** Source sample reservation and top-off, with atomic private/shared inventory transfer. */
public class DropperMovementBehaviour implements MovementBehaviour {
    @Override public void visitNewPosition(MovementContext context,BlockPos pos) {
        if(!(context.world instanceof ServerLevel)||!LoadedLevelAccess.isLoaded(context.world,pos))return;
        MountedItemInventory.requireNoTransaction();
        var storage=context.getItemStorage();if(storage==null||!storage.isAvailable())return;
        MountedDispenseExecution.retryPendingRemainders(context,pos);
        int slot=getSlot(storage,context.world.getRandom(),context.contraption.getStorage().getAllItems());
        if(slot<0){context.world.levelEvent(LevelEvent.SOUND_DISPENSER_FAIL,pos,0);return;}
        var stack=storage.getStackInSlot(slot);
        MountedDispenseExecution.dispense(storage,slot,getDispenseBehavior(context,pos,stack),context,pos);
    }
    protected MountedDispenseBehavior getDispenseBehavior(MovementContext context,BlockPos pos,ItemStack stack) { return DefaultMountedDispenseBehavior.INSTANCE; }

    @Override public void tick(MovementContext context) {
        if(!context.world.isClientSide()&&context.position!=null)
            MountedDispenseExecution.retryPendingRemainders(context,BlockPos.containing(context.position));
    }

    public static int getSlot(MountedItemStorage storage,RandomSource random,Storage<ItemVariant> shared) {
        var eligible=new ArrayList<Integer>();
        for(int slot=0;slot<storage.getSlotCount();slot++){
            var stack=storage.getStackInSlot(slot);if(stack.isEmpty())continue;
            if(stack.getCount()==1&&stack.getMaxStackSize()!=1){
                var variant=ItemVariant.of(stack);
                try(var transaction=Transaction.openOuter()){
                    long extracted=shared.extract(variant,stack.getMaxStackSize()-1,transaction);
                    if(extracted==0)continue;
                    if(storage.getSlot(slot).insert(variant,extracted,transaction)!=extracted)throw new IllegalStateException("Mounted top-off slot changed");
                    transaction.commit();
                }
            }
            eligible.add(slot);
        }
        return eligible.isEmpty()?-1:eligible.get(eligible.size()==1?0:random.nextInt(eligible.size()));
    }
}
