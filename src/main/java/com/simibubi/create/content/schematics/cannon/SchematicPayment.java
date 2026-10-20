package com.simibubi.create.content.schematics.cannon;

import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.*;
import net.minecraft.world.item.ItemStack;

/** One actual Fabric transaction pays every source requirement; a receipt retains complete consumed originals. */
public final class SchematicPayment {
    public record Receipt(List<ItemStack> consumed){
        public Receipt{consumed=consumed.stream().filter(stack->!stack.isEmpty()).map(ItemStack::copy).toList();}
        @Override public List<ItemStack> consumed(){return consumed.stream().map(ItemStack::copy).toList();}
    }
    public record Result(Receipt receipt,ItemStack missing){public boolean complete(){return receipt!=null;}}
    public static Result collect(ItemRequirement requirement,Collection<Storage<ItemVariant>> inventories,TransactionContext parent){
        if(requirement.isInvalid())return new Result(null,ItemStack.EMPTY);
        var distinct=Collections.newSetFromMap(new IdentityHashMap<Storage<ItemVariant>,Boolean>());var sources=inventories.stream().filter(Objects::nonNull).filter(distinct::add).toList();var originals=new ArrayList<ItemStack>();
        try(var payment=parent.openNested()){
            for(var required:requirement.getRequiredItems()){
                if(required.stack.isEmpty())continue;
                if(required.usage==ItemRequirement.ItemUseType.DAMAGE){if(!damage(required,sources,payment))return new Result(null,required.stack.copy());continue;}
                int remaining=required.stack.getCount();
                for(var source:sources){for(var view:source){var resource=view.getResource();if(resource.isBlank()||!required.matches(resource.toStack()))continue;long moved=view.extract(resource,remaining,payment);if(moved<0||moved>remaining)throw new IllegalStateException("Native schematic material storage violated its actual extraction contract");if(moved>0){originals.add(resource.toStack(Math.toIntExact(moved)));remaining-=Math.toIntExact(moved);}if(remaining==0)break;}if(remaining==0)break;}
                if(remaining!=0)return new Result(null,required.stack.copy());
            }
            var receipt=new Receipt(originals);payment.commit();return new Result(receipt,ItemStack.EMPTY);
        }
    }
    private static boolean damage(ItemRequirement.StackRequirement required,List<Storage<ItemVariant>> sources,TransactionContext payment){
        for(var source:sources)for(var view:source){var resource=view.getResource();if(resource.isBlank())continue;var stack=resource.toStack();if(!required.matches(stack)||!stack.isDamageableItem())continue;
            try(var attempt=payment.openNested()){
                if(view.extract(resource,1,attempt)!=1)continue;var damaged=stack.copyWithCount(1);damaged.setDamageValue(Math.addExact(damaged.getDamageValue(),1));
                // As in the pinned source, the last inclusive durability point remains in the inventory.
                if(damaged.getDamageValue()<=damaged.getMaxDamage()&&source.insert(ItemVariant.of(damaged),1,attempt)!=1)continue;
                attempt.commit();return true;
            }
        }return false;
    }
    private SchematicPayment(){}
}
