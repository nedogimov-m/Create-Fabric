package com.simibubi.create.content.logistics.packager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.commons.lang3.mutable.MutableInt;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.simibubi.create.content.logistics.BigItemStack;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class InventorySummary {
	public static Codec<InventorySummary> CODEC = Codec.list(BigItemStack.CODEC)
		.xmap(i -> {
				InventorySummary summary = new InventorySummary();
				summary.addAllBigItemStacks(i);
				return summary;
			},
			i -> {
				List<BigItemStack> all = new ArrayList<>();
				i.items.forEach((key, list) -> all.addAll(list));
				return all;
			});

	public static final InventorySummary EMPTY = new InventorySummary();

	private final boolean retainPhysicalVariants;
	public InventorySummary(){this(false);}
	private InventorySummary(boolean retainPhysicalVariants){this.retainPhysicalVariants=retainPhysicalVariants;}
	/** Actual material reconstruction must never discard distinct native item metadata. */
	public static InventorySummary physical(){return new InventorySummary(true);}
	private boolean same(ItemStack left,ItemStack right){return retainPhysicalVariants?ItemStack.isSameItemSameComponents(left,right):com.simibubi.create.foundation.item.ItemSampleEquality.same(left,right);}
	private Map<Item, List<BigItemStack>> items = new IdentityHashMap<>();
	private List<BigItemStack> stacksByCount;
	private int totalCount;

	public int contributingLinks;

	public void add(InventorySummary summary) {
		summary.items.forEach((i, list) -> list.forEach(this::add));
		contributingLinks += summary.contributingLinks;
	}

	public void add(ItemStack stack) {
		add(stack, stack.getCount());
	}

	public void add(BigItemStack entry) {
		add(entry.stack, entry.count);
	}

	public Map<Item, List<BigItemStack>> getItemMap() {
		return items;
	}

	public void addAllItemStacks(List<ItemStack> list) {
		for (ItemStack stack : list)
			add(stack, stack.getCount());
	}

	public void addAllBigItemStacks(List<BigItemStack> list) {
		for (BigItemStack entry : list)
			add(entry.stack, entry.count);
	}

	public InventorySummary copy() {
		InventorySummary inventorySummary = new InventorySummary(retainPhysicalVariants);
		items.forEach((i, list) -> list.forEach(entry -> inventorySummary.add(entry.stack, entry.count)));
		return inventorySummary;
	}

	public void add(ItemStack stack, int count) {
        stacksByCount = null;
		if (count == 0 || stack.isEmpty())
			return;

		if (totalCount < BigItemStack.INF)
			totalCount += count;

		List<BigItemStack> stacks = items.computeIfAbsent(stack.getItem(), $ -> Lists.newArrayList());
		for (BigItemStack existing : stacks) {
			ItemStack existingStack = existing.stack;
			if (same(existingStack, stack)) {
				if (existing.count < BigItemStack.INF)
					existing.count += count;
				return;
			}
		}

		if (stack.getCount() > stack.getMaxStackSize())
			stack = stack.copyWithCount(1);

		BigItemStack newEntry = new BigItemStack(stack, count);
		stacks.add(newEntry);
	}

	public boolean erase(ItemStack stack) {
        stacksByCount = null;
		List<BigItemStack> stacks = items.get(stack.getItem());
		if (stacks == null)
			return false;
		for (Iterator<BigItemStack> iterator = stacks.iterator(); iterator.hasNext();) {
			BigItemStack existing = iterator.next();
			ItemStack existingStack = existing.stack;
			if (!same(existingStack, stack))
				continue;
			totalCount -= existing.count;
			iterator.remove();
			return true;
		}
		return false;
	}

	public int getCountOf(ItemStack stack) {
		List<BigItemStack> list = items.get(stack.getItem());
		if (list == null)
			return 0;
        long count=0;
        for(BigItemStack entry:list)if(com.simibubi.create.foundation.item.ItemSampleEquality.same(entry.stack,stack))count+=entry.count;
        return (int)Math.clamp(count,Integer.MIN_VALUE,BigItemStack.INF);
	}


    public ItemStack takeMatchingOne(ItemStack sample){
        if(!retainPhysicalVariants)throw new IllegalStateException("Real material consumption requires a physical-variant summary");
        var list=items.get(sample.getItem());if(list==null)return ItemStack.EMPTY;
        for(var entry:list)if(entry.count>0&&com.simibubi.create.foundation.item.ItemSampleEquality.same(entry.stack,sample)){
            stacksByCount=null;entry.count--;totalCount--;return entry.stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

	public int getTotalOfMatching(Predicate<ItemStack> filter) {
		MutableInt sum = new MutableInt();
		items.forEach(($, list) -> {
			for (BigItemStack entry : list)
				if (filter.test(entry.stack))
					sum.add(entry.count);
		});
		return sum.getValue();
	}

	public List<BigItemStack> getStacks() {
		if (stacksByCount == null) {
			List<BigItemStack> stacks = new ArrayList<>();
			items.forEach((i, list) -> stacks.addAll(list));
			return stacks;
		}
		return stacksByCount;
	}

	public List<BigItemStack> getStacksByCount() {
		if (stacksByCount == null) {
			stacksByCount = new ArrayList<>();
			items.forEach((i, list) -> stacksByCount.addAll(list));
			stacksByCount.sort(BigItemStack.comparator());
		}
		return stacksByCount;
	}

	public int getTotalCount() {
		return totalCount;
	}


	public boolean isEmpty() {
		return items.isEmpty();
	}

}
