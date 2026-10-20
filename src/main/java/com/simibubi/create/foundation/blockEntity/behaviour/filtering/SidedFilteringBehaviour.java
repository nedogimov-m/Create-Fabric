package com.simibubi.create.foundation.blockEntity.behaviour.filtering;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;

/** Independent installed filters retain their original per-face persistence keys. */
public final class SidedFilteringBehaviour extends FilteringBehaviour {
    @Override public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(){var requirement=com.simibubi.create.content.schematics.requirement.ItemRequirement.NONE;for(var filter:filters.values())requirement=requirement.union(filter.getRequiredItems());return requirement;}
    private final Map<Direction,FilteringBehaviour> filters=new EnumMap<>(Direction.class);
    private final Predicate<Direction> valid;
    public SidedFilteringBehaviour(BlockEntity entity,Predicate<Direction> valid){super(entity);this.valid=valid;}
    public FilteringBehaviour get(Direction side){return filters.get(side);}
    public void updateFilterPresence(){for(var side:Direction.values())if(valid.test(side))filters.computeIfAbsent(side,key->new FilteringBehaviour(blockEntity));else removeFilter(side);}
    public void removeFilter(Direction side){var removed=filters.remove(side);if(removed!=null)removed.destroy();}
    @Override public boolean setFilter(Direction side,ItemStack stack){var filter=get(side);return filter==null||filter.setFilter(stack);}
    @Override public ItemStack getFilter(Direction side){var filter=get(side);return filter==null?ItemStack.EMPTY:filter.getFilter();}
    public boolean test(Direction side,ItemStack stack){var filter=get(side);return filter==null||filter.test(stack);}
    @Override public void destroy(){filters.values().forEach(FilteringBehaviour::destroy);filters.clear();super.destroy();}
    @Override public void write(ValueOutput out){var list=out.childrenList("Filters");for(var entry:filters.entrySet()){var child=list.addChild();child.putInt("Side",entry.getKey().ordinal());entry.getValue().write(child);}super.write(out);}
    @Override public void read(ValueInput in){super.read(in);filters.clear();for(var child:in.childrenListOrEmpty("Filters")){int side=child.getIntOr("Side",-1);if(side<0||side>=6)continue;var filter=new FilteringBehaviour(blockEntity);filter.read(child);filters.put(Direction.values()[side],filter);}}
}
