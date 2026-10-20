package com.simibubi.create.content.fluids.hosePulley;

import com.simibubi.create.content.fluids.transfer.*;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import java.util.*;

/** A 1500 mB buffer plus at most one world bucket per transfer, sharing its transaction. */
public final class HosePulleyFluidHandler implements Storage<FluidVariant> {
    private static final long B=FluidConstants.BUCKET;
    private final HosePulleyBlockEntity owner;
    private final SmartFluidTank tank;
    private final FluidDrainingBehaviour drainer;
    private final FluidFillingBehaviour filler;
    HosePulleyFluidHandler(HosePulleyBlockEntity owner,SmartFluidTank tank,FluidDrainingBehaviour drainer,FluidFillingBehaviour filler){this.owner=owner;this.tank=tank;this.drainer=drainer;this.filler=filler;}
    @Override public long insert(FluidVariant fluid,long max,TransactionContext tx){
        StoragePreconditions.notBlankNotNegative(fluid,max);
        if(max==0||fluid.getFluid().defaultFluidState().createLegacyBlock().isAir()||!tank.isEmpty()&&!tank.getResource().equals(fluid))return 0;
        if(owner.canTransfer()&&max>=B-tank.getAmount()){
            try(var nested=tx.openNested()){
                if(filler.tryDeposit(fluid.getFluid(),owner.rootPos(),nested)){
                    drainer.enlist(nested);drainer.counterpartActed();long incoming=Math.min(max,B),fromTank=B-incoming;
                    if(fromTank>0&&tank.extract(fluid,fromTank,nested)!=fromTank)return 0;
                    long inserted=tank.insert(fluid,max-incoming,nested);nested.commit();return incoming+inserted;
                }
            }
        }return tank.insert(fluid,max,tx);
    }
    @Override public long extract(FluidVariant fluid,long max,TransactionContext tx){
        StoragePreconditions.notBlankNotNegative(fluid,max);if(max==0||!tank.isEmpty()&&!tank.getResource().equals(fluid))return 0;
        // NeoForge's direct setFluid could overfill the 1500 mB buffer. Fabric enforces capacity:
        // use an existing remainder when it already satisfies this request before reserving another source.
        if(tank.getAmount()>=max||tank.getAmount()>=B||!owner.canTransfer())return tank.extract(fluid,max,tx);
        var source=drainer.getDrainableFluid(owner.rootPos());
        if(source.isEmpty()||!source.variant().equals(fluid))return tank.extract(fluid,max,tx);
        try(var nested=tx.openNested()){
            if(drainer.pullNext(owner.rootPos(),nested)){
                filler.enlist(nested);filler.counterpartActed();long available=B+tank.getAmount(),moved=Math.min(max,available);
                if(tank.getAmount()>0)tank.extract(fluid,tank.getAmount(),nested);
                if(available>moved&&tank.insert(fluid,available-moved,nested)!=available-moved)return 0;
                nested.commit();return moved;
            }
        }return tank.extract(fluid,max,tx);
    }
    @Override public Iterator<StorageView<FluidVariant>> iterator(){return Collections.<StorageView<FluidVariant>>singleton(new StorageView<>(){
        @Override public boolean isResourceBlank(){return getResource().isBlank();}
        @Override public FluidVariant getResource(){return tank.isEmpty()&&owner.canTransfer()?drainer.getDrainableFluid(owner.rootPos()).variant():tank.getResource();}
        @Override public long getAmount(){
            long amount=tank.getAmount();
            if(amount<B&&owner.canTransfer()){
                var source=drainer.getDrainableFluid(owner.rootPos());
                if(!source.isEmpty()&&(tank.isEmpty()||tank.getResource().equals(source.variant())))amount+=B;
            }return amount;
        }
        @Override public long getCapacity(){return Math.max(1500*81L,getAmount());}
        @Override public long extract(FluidVariant fluid,long max,TransactionContext tx){return HosePulleyFluidHandler.this.extract(fluid,max,tx);}
    }).iterator();}
}
