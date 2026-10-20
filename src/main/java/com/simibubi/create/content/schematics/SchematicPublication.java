package com.simibubi.create.content.schematics;

import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Item custody for source BE material which a partially failing native reader actually installed. */
public final class SchematicPublication {
    private record Part(ItemRequirement.StackRequirement requirement,int amount){}
    private static List<Part> additional(BlockState state,BlockEntity entity){
        var full=ItemRequirement.of(state,entity);if(full.isInvalid())return List.of();
        var amounts=new ArrayList<Part>();for(var requirement:full.getRequiredItems())if(requirement.usage==ItemRequirement.ItemUseType.CONSUME&&!requirement.stack.isEmpty())amounts.add(new Part(requirement,requirement.stack.getCount()));
        for(var base:ItemRequirement.of(state,null).getRequiredItems()){
            if(base.usage!=ItemRequirement.ItemUseType.CONSUME)continue;int remaining=base.stack.getCount();
            for(int i=0;i<amounts.size()&&remaining>0;i++){var part=amounts.get(i);if(!base.matches(part.requirement.stack))continue;int used=Math.min(remaining,part.amount);amounts.set(i,new Part(part.requirement,part.amount-used));remaining-=used;}
        }
        return amounts.stream().filter(part->part.amount>0).toList();
    }
    public static ItemRequirement installedAdditional(BlockState state,BlockEntity source,BlockEntity actual){
        // Curve ownership is published by the bounded reciprocal merge, never inferred from item totals.
        if(source==null||actual==null||source.getType()!=actual.getType()||source instanceof com.simibubi.create.content.trains.track.TrackBlockEntity)return ItemRequirement.NONE;
        var present=new ArrayList<>(additional(state,actual));var published=new ArrayList<ItemRequirement.StackRequirement>();
        for(var planned:additional(state,source)){
            int remaining=planned.amount,accepted=0;
            for(int i=0;i<present.size()&&remaining>0;i++){var part=present.get(i);if(!ItemStack.isSameItemSameComponents(planned.requirement.stack,part.requirement.stack))continue;int used=Math.min(remaining,part.amount);accepted+=used;remaining-=used;present.set(i,new Part(part.requirement,part.amount-used));}
            if(accepted>0){var item=planned.requirement.stack.copyWithCount(accepted);published.add(planned.requirement instanceof ItemRequirement.StrictNbtStackRequirement?new ItemRequirement.StrictNbtStackRequirement(item,ItemRequirement.ItemUseType.CONSUME):new ItemRequirement.StackRequirement(item,ItemRequirement.ItemUseType.CONSUME));}
        }
        return published.isEmpty()?ItemRequirement.NONE:new ItemRequirement(published);
    }
    private SchematicPublication(){}
}
