package com.simibubi.create.content.logistics.packager.repackager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.box.PackageItem.PackageOrderData;
import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts.CraftingEntry;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.NonNullList;

public class PackageRepackageHelper {

	protected Map<Integer, List<ItemStack>> collectedPackages = new HashMap<>();

	public void clear() {
		collectedPackages.clear();
	}

	public boolean isFragmented(ItemStack box) {
		return box.has(AllDataComponents.PACKAGE_ORDER_DATA);
	}

	public int addPackageFragment(ItemStack box) {
		int collectedOrderId = PackageItem.getOrderId(box);
		if (collectedOrderId == -1)
			return -1;

		List<ItemStack> collectedOrder = collectedPackages.computeIfAbsent(collectedOrderId, $ -> Lists.newArrayList());
		collectedOrder.add(box);

		if (!isOrderComplete(collectedOrderId))
			return -1;

		return collectedOrderId;
	}

	public List<BigItemStack> repack(int orderId, RandomSource r) {
		List<BigItemStack> exportingPackages = new ArrayList<>();
		String address = "";
		PackageOrderWithCrafts orderContext = null;
		InventorySummary summary = InventorySummary.physical();

		for (ItemStack box : collectedPackages.get(orderId)) {
			address = PackageItem.getAddress(box);
			if (box.has(AllDataComponents.PACKAGE_ORDER_DATA)) {
				PackageOrderWithCrafts context = box.get(AllDataComponents.PACKAGE_ORDER_DATA).orderContext();
				if (context != null && !context.isEmpty())
					orderContext = context;
			}

			var contents = PackageItem.getContents(box);
			for (int slot = 0; slot < contents.size(); slot++)
				summary.add(contents.get(slot));
		}

		List<BigItemStack> orderedStacks = new ArrayList<>();
		if (orderContext != null) {
			List<BigItemStack> packagesSplitByRecipe = repackBasedOnRecipes(summary, orderContext, address, r);
			exportingPackages.addAll(packagesSplitByRecipe);
			
			if (packagesSplitByRecipe.isEmpty())
				for (BigItemStack stack : orderContext.stacks())
					orderedStacks.add(new BigItemStack(stack.stack, stack.count));
		}

		List<BigItemStack> allItems = summary.getStacks();
		List<ItemStack> outputSlots = new ArrayList<>();

		Repack:
		while (true) {
			allItems.removeIf(e -> e.count == 0);
			if (allItems.isEmpty())
				break;

			BigItemStack targetedEntry = null;
			if (!orderedStacks.isEmpty())
				targetedEntry = orderedStacks.remove(0);

			ItemSearch:
			for (BigItemStack entry : allItems) {
				int targetAmount = entry.count;
				if (targetAmount == 0)
					continue;
				if (targetedEntry != null) {
					targetAmount = targetedEntry.count;
					if (!com.simibubi.create.foundation.item.ItemSampleEquality.same(entry.stack, targetedEntry.stack))
						continue;
				}

				while (targetAmount > 0) {
					int removedAmount = Math.min(Math.min(targetAmount, entry.stack.getMaxStackSize()), entry.count);
					if (removedAmount == 0)
						continue ItemSearch;

					ItemStack output = entry.stack.copyWithCount(removedAmount);
					targetAmount -= removedAmount;
					if (targetedEntry != null)
						targetedEntry.count = targetAmount;
					entry.count -= removedAmount;
					outputSlots.add(output);
				}

				continue Repack;
			}
		}

		int currentSlot = 0;
		var target = NonNullList.withSize(PackageItem.SLOTS, ItemStack.EMPTY);

		for (ItemStack item : outputSlots) {
			target.set(currentSlot++, item);
			if (currentSlot < PackageItem.SLOTS)
				continue;
			exportingPackages.add(new BigItemStack(PackageItem.containingSlots(target), 1));
			target = NonNullList.withSize(PackageItem.SLOTS, ItemStack.EMPTY);
			currentSlot = 0;
		}

		for (int slot = 0; slot < target.size(); slot++)
			if (!target.get(slot)
				.isEmpty()) {
				exportingPackages.add(new BigItemStack(PackageItem.containingSlots(target), 1));
				break;
			}

		for (BigItemStack box : exportingPackages)
			PackageItem.addAddress(box.stack, address);

		for (int i = 0; i < exportingPackages.size(); i++) {
			BigItemStack box = exportingPackages.get(i);
			boolean isfinal = i == exportingPackages.size() - 1;
			PackageOrderWithCrafts outboundOrderContext = isfinal && orderContext != null ? orderContext : null;
			if (PackageItem.getOrderId(box.stack) == -1)
				PackageItem.setOrder(box.stack, orderId, 0, true, 0, true, outboundOrderContext);
		}

		return exportingPackages;
	}

    private boolean isOrderComplete(int orderId) {
        for (int link = 0; link < 1000; link++) {
            boolean ended = false;
            for (int fragment = 0; fragment < 1000; fragment++) {
                PackageOrderData found = null;
                for (var box : collectedPackages.get(orderId)) {
                    var data = box.get(AllDataComponents.PACKAGE_ORDER_DATA);
                    if (data.linkIndex() == link && data.fragmentIndex() == fragment) {found = data;break;}
                }
                if (found == null) return false;
                if (found.isFinal()) {if (found.isFinalLink()) return true;ended = true;break;}
            }
            if (!ended) return false;
        }
        return false;
    }

	protected List<BigItemStack> repackBasedOnRecipes(InventorySummary summary, PackageOrderWithCrafts order, String address, RandomSource r) {
		if (order.orderedCrafts().isEmpty())
			return List.of();
		
		List<BigItemStack> packages = new ArrayList<>();
		for(CraftingEntry craft:order.orderedCrafts()){
            var produced=new ArrayList<BigItemStack>();
            for(int attempt=0;attempt<craft.count();attempt++){
                var actual=NonNullList.withSize(PackageItem.SLOTS,ItemStack.EMPTY);var consumed=new ArrayList<ItemStack>();boolean complete=true;
                var pattern=craft.pattern().stacks();
                if(pattern.size()>actual.size())throw new IllegalArgumentException("Crafting pattern exceeds real package slots");
                for(int slot=0;slot<pattern.size();slot++){
                    var required=pattern.get(slot);if(required.stack.isEmpty())continue;
                    var taken=summary.takeMatchingOne(required.stack);
                    if(taken.isEmpty()){complete=false;break;}
                    actual.set(slot,taken);consumed.add(taken);
                }
                if(!complete){consumed.forEach(item->summary.add(item,1));break;}
                BigItemStack same=null;
                // Source counts identical crafts before choosing their one random box style.
                // Compare the real material slots so distinct components retain separate owners.
                for(var existing:produced)if(sameContents(PackageItem.getContents(existing.stack),actual)){same=existing;break;}
                if(same!=null)same.count++;else produced.add(new BigItemStack(PackageItem.containingSlots(actual),1));
            }
            for(var actual:produced){PackageItem.setOrder(actual.stack,r.nextInt(),0,true,0,true,PackageOrderWithCrafts.singleRecipe(craft.pattern().stacks()));packages.add(actual);}
        }
        
		return packages;
	}

    private static boolean sameContents(List<ItemStack> first,List<ItemStack> second){
        if(first.size()!=second.size())return false;
        for(int slot=0;slot<first.size();slot++){
            var left=first.get(slot);var right=second.get(slot);
            if(left.getCount()!=right.getCount()||!ItemStack.isSameItemSameComponents(left,right))return false;
        }
        return true;
    }

}
