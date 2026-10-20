package com.simibubi.create.content.logistics.packagerLink;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.nbt.NbtOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RequestPromiseQueue {
    public static final Codec<RequestPromiseQueue> CODEC = RequestPromise.CODEC.listOf().xmap(values -> {
        var queue = new RequestPromiseQueue(() -> {});
        for (var value : values) queue.add(value);
        return queue;
    }, queue -> queue.flatten(false));
	private Map<Item, List<RequestPromise>> promisesByItem;
	private Runnable onChanged;

	public RequestPromiseQueue(Runnable onChanged) {
		promisesByItem = new IdentityHashMap<>();
		this.onChanged = onChanged;
	}

	public void add(RequestPromise promise) {
		promisesByItem.computeIfAbsent(promise.promisedStack.stack.getItem(), $ -> new LinkedList<>())
			.add(promise);
		onChanged.run();
	}

	public void setOnChanged(Runnable onChanged) {
		this.onChanged = onChanged;
	}

	public int getTotalPromisedAndRemoveExpired(ItemStack stack, int expiryTime) {
		int promised = 0;
		List<RequestPromise> list = promisesByItem.get(stack.getItem());
		if (list == null)
			return promised;

		for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
			RequestPromise promise = iterator.next();
			if (!com.simibubi.create.foundation.item.ItemSampleEquality.same(promise.promisedStack.stack, stack))
				continue;
			if (expiryTime != -1 && promise.ticksExisted >= expiryTime) {
				iterator.remove();
				onChanged.run();
				continue;
			}

			promised += promise.promisedStack.count;
		}
		if (list.isEmpty()) promisesByItem.remove(stack.getItem());
		return promised;
	}

	public void forceClear(ItemStack stack) {
		List<RequestPromise> list = promisesByItem.get(stack.getItem());
		if (list == null)
			return;

		for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
			RequestPromise promise = iterator.next();
			if (!com.simibubi.create.foundation.item.ItemSampleEquality.same(promise.promisedStack.stack, stack))
				continue;
			iterator.remove();
			onChanged.run();
		}

		if (list.isEmpty())
			promisesByItem.remove(stack.getItem());
	}

	public void itemEnteredSystem(ItemStack stack, int amount) {
		List<RequestPromise> list = promisesByItem.get(stack.getItem());
		if (list == null)
			return;

		for (Iterator<RequestPromise> iterator = list.iterator(); iterator.hasNext();) {
			RequestPromise requestPromise = iterator.next();
			if (!com.simibubi.create.foundation.item.ItemSampleEquality.same(requestPromise.promisedStack.stack, stack))
				continue;

			int toSubtract = Math.min(amount, requestPromise.promisedStack.count);
			amount -= toSubtract;
			requestPromise.promisedStack.count -= toSubtract;
            if (toSubtract > 0) onChanged.run();

			if (requestPromise.promisedStack.count <= 0) {
				iterator.remove();
				onChanged.run();
			}
			if (amount <= 0)
				break;
		}

		if (list.isEmpty())
			promisesByItem.remove(stack.getItem());
	}

	public List<RequestPromise> flatten(boolean sorted) {
		List<RequestPromise> all = new ArrayList<>();
		promisesByItem.forEach((key, list) -> all.addAll(list));
		if (sorted)
			all.sort(RequestPromise.ageComparator());
		return all;
	}

	public CompoundTag write(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.put("List", RequestPromise.CODEC.listOf().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), flatten(false)).getOrThrow());
		return tag;
	}

	public static RequestPromiseQueue read(CompoundTag tag, HolderLookup.Provider registries, Runnable onChanged) {
		RequestPromiseQueue queue = new RequestPromiseQueue(onChanged);
		List<RequestPromise> promises = RequestPromise.CODEC.listOf().parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("List")).result().orElse(List.of());
		for (RequestPromise promise : promises) {
			queue.add(promise);
		}
		return queue;
	}

	public void tick() {
		if (!promisesByItem.isEmpty()) {
            promisesByItem.forEach((key, list) -> list.forEach(RequestPromise::tick));
            onChanged.run();
        }
	}

	public boolean isEmpty() {
		return promisesByItem.values().stream().allMatch(List::isEmpty);
	}
}
