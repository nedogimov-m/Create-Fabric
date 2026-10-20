package com.simibubi.create.content.logistics.packager.repackager;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.*;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class RepackagerBlockEntity extends PackagerBlockEntity {
    public final PackageRepackageHelper repackageHelper=new PackageRepackageHelper();
    public RepackagerBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.REPACKAGER,pos,state);}
    @Override public boolean redstoneModeActive(){return true;}
    @Override public boolean unwrapBox(ItemStack box,TransactionContext transaction){
        if(level==null||level.isClientSide()||animationTicks>0||!PackageItem.isPackage(box))return false;var storage=targetInventory();if(storage==null||storage instanceof PackagerItemHandler)return false;
        try(var tx=transaction.openNested()){if(storage.insert(ItemVariant.of(box),1,tx)!=1)return false;received(box,tx);tx.commit();return true;}
    }
    @Override public void attemptToSend(List<PackagingRequest> requests){
        if(level==null||level.isClientSide()||!heldBox.isEmpty()||animationTicks!=0||buttonCooldown>0||!queuedExitingPackages.isEmpty())return;
        var storage=targetInventory();if(storage==null||storage instanceof PackagerItemHandler)return;
        try(var tx=Transaction.openOuter()){if(repackage(storage,tx))tx.commit();}
    }
    private boolean repackage(Storage<ItemVariant> storage,TransactionContext transaction){
        repackageHelper.clear();int completed=-1;ItemStack standalone=ItemStack.EMPTY;
        try(var preview=transaction.openNested()){
            var candidates=new ArrayList<StorageView<ItemVariant>>();storage.forEach(candidates::add);
            for(var view:candidates){
                if(view.isResourceBlank()||!PackageItem.isPackage(view.getResource().toStack()))continue;var variant=view.getResource();
                if(storage.extract(variant,1,preview)!=1)continue;var box=variant.toStack();
                if(!repackageHelper.isFragmented(box)){standalone=box;break;}
                completed=repackageHelper.addPackageFragment(box);if(completed!=-1)break;
            }
        }
        if(!standalone.isEmpty()){
            if(storage.extract(ItemVariant.of(standalone),1,transaction)!=1)return false;
            updateSignAddress();if(!signBasedAddress.isBlank())PackageItem.addAddress(standalone,signBasedAddress);output(standalone,transaction);return true;
        }
        if(completed==-1)return false;
        repackageHelper.clear();boolean complete=false;var candidates=new ArrayList<StorageView<ItemVariant>>();storage.forEach(candidates::add);
        for(var view:candidates){
            if(view.isResourceBlank())continue;var variant=view.getResource();var box=variant.toStack();
            if(!PackageItem.isPackage(box)||PackageItem.getOrderId(box)!=completed||storage.extract(variant,1,transaction)!=1)continue;
            if(repackageHelper.addPackageFragment(box)==completed)complete=true;
        }
        if(!complete)return false;var boxes=repackageHelper.repack(completed,level.getRandom());if(boxes.isEmpty())return false;
        snapshot(transaction);for(var box:boxes)if(box.count>0)queuedExitingPackages.add(box);return true;
    }
}
