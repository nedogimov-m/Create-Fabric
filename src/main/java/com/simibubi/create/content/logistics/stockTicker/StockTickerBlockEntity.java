package com.simibubi.create.content.logistics.stockTicker;

import com.mojang.serialization.Codec;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.filter.*;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour.RequestType;
import com.simibubi.create.content.processing.burner.*;
import com.simibubi.create.foundation.item.ProcessingInventory;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.AABB;

public final class StockTickerBlockEntity extends StockCheckingBlockEntity implements Clearable,com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation {
    private UUID paymentViewer,paymentFrequency;
    private boolean paymentPermission;
    private long paymentPermissionTick=Long.MIN_VALUE;
    public void receivePaymentPermission(UUID viewer,UUID frequency,boolean allowed){paymentViewer=viewer;paymentFrequency=frequency;paymentPermission=allowed;paymentPermissionTick=level.getGameTime();}
    public boolean mayInspectPayments(Player player){return level!=null&&player!=null&&(level.isClientSide()?paymentPermission&&player.getUUID().equals(paymentViewer)&&behaviour.freqId.equals(paymentFrequency)&&level.getGameTime()-paymentPermissionTick<40:behaviour.mayAdministrate(player));}
    public InventorySummary paymentSummary(){var result=new InventorySummary();for(var slot:receivedPayments.inputs)if(!slot.isResourceBlank())result.add(slot.stack());return result;}
    @Override public boolean addToTooltip(List<net.minecraft.network.chat.Component> lines,boolean sneaking,Player viewer){
        if(!hasPayments()||!mayInspectPayments(viewer))return false;
        lines.add(net.minecraft.network.chat.Component.translatable("create.stock_ticker.contains_payments").withStyle(net.minecraft.ChatFormatting.WHITE));
        for(var entry:paymentSummary().getStacksByCount())lines.add(entry.stack.getHoverName().copy().append(" x"+entry.count).withStyle(net.minecraft.ChatFormatting.GREEN));
        lines.add(net.minecraft.network.chat.Component.translatable("create.stock_ticker.click_to_retrieve").withStyle(net.minecraft.ChatFormatting.GRAY));return true;
    }
    public String previouslyUsedAddress = "";
    public int activeLinks;
    public final List<ItemStack> categories = new ArrayList<>();
    public final Map<UUID,List<Integer>> hiddenCategoriesByPlayer = new HashMap<>();
    public final ProcessingInventory receivedPayments = new ProcessingInventory(27, 0, item -> true, this::notifyUpdate, true);
    StockTickerMenu categoryEditor;
    public StockTickerBlockEntity(BlockPos pos, BlockState state) { super(AllBlockEntityTypes.STOCK_TICKER, pos, state); }
    public static boolean isCategory(ItemStack stack) { return FilterItemStack.of(stack).isFilterItem(); }
    @Override public InventorySummary getRecentSummary() {
        var summary = super.getRecentSummary();
        if (activeLinks != summary.contributingLinks) { activeLinks = summary.contributingLinks; if (!isRemoved()) notifyUpdate(); }
        return summary;
    }
    @Override public boolean broadcastPackageRequest(RequestType type, PackageOrderWithCrafts order, IdentifiedInventory ignored, String address) {
        boolean result = super.broadcastPackageRequest(type, order, ignored, address);
        previouslyUsedAddress = address; notifyUpdate(); return result;
    }
    public boolean isKeeperPresent() {
        for (int y = 0; y < 2; y++) for (var direction : Direction.Plane.HORIZONTAL) {
            var pos = worldPosition.below(y).relative(direction);
            if (level.getEntitiesOfClass(SeatEntity.class, new AABB(pos)).stream().anyMatch(SeatEntity::isVehicle)) return true;
            if (y == 0 && LoadedLevelAccess.blockEntity(level, pos) instanceof BlazeBurnerBlockEntity && LoadedLevelAccess.blockState(level, pos).getValue(BlazeBurnerBlock.HEAT_LEVEL) != BlazeBurnerBlock.HeatLevel.NONE) return true;
        }
        return false;
    }
    public List<List<BigItemStack>> categorize(List<BigItemStack> stock) {
        var remaining = new ArrayList<>(stock); var result = new ArrayList<List<BigItemStack>>();
        for (var category : categories) {
            var entries = new ArrayList<BigItemStack>(); var predicate = FilterItemStack.of(category);
            if (!category.isEmpty()) for (var it = remaining.iterator(); it.hasNext();) { var item = it.next(); if (predicate.test(level, item.stack)) { entries.add(item); it.remove(); } }
            result.add(entries);
        }
        result.add(remaining); return result;
    }
    public boolean hasPayments() { return receivedPayments.inputs.stream().anyMatch(slot -> !slot.isResourceBlank()); }
    public void retrievePayments(Player player) {
        var stacks = new ArrayList<ItemStack>();
        try (var transaction = Transaction.openOuter()) {
            for (var slot : receivedPayments.inputs) if (!slot.isResourceBlank()) { stacks.add(slot.stack()); slot.extract(slot.getResource(), slot.getAmount(), transaction); }
            transaction.commit();
        }
        for (var stack : stacks) player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
    }
    void removeCategory(int index) {
        categories.remove(index);
        hiddenCategoriesByPlayer.replaceAll((id, indices) -> indices.stream().filter(value -> value != index).map(value -> value > index ? value - 1 : value).toList());
        notifyUpdate();
    }
    void moveCategory(int index, int other) {
        Collections.swap(categories, index, other);
        hiddenCategoriesByPlayer.replaceAll((id, indices) -> indices.stream().map(value -> value == index ? other : value == other ? index : value).toList());
        notifyUpdate();
    }
    @Override public void clearContent() {
        if (categoryEditor != null) { categoryEditor.proxy.clearContent(); categoryEditor.editing=StockTickerMenu.IDLE; }
        categories.clear();
        try (var transaction = Transaction.openOuter()) { for (var slot : receivedPayments.inputs) if (!slot.isResourceBlank()) slot.extract(slot.getResource(), slot.getAmount(), transaction); transaction.commit(); }
        notifyUpdate();
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (categoryEditor != null) categoryEditor.finishEditing(null);
        for (var category : categories) if (!category.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), category);
        for (var slot : receivedPayments.inputs) if (!slot.isResourceBlank()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), slot.stack());
        clearContent(); super.preRemoveSideEffects(pos, state);
    }
    @Override protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket); output.putString("PreviousAddress", previouslyUsedAddress); output.putInt("ActiveLinks", activeLinks);
        // An open menu owns a real filter; include its escrow in saves and update snapshots.
        var savedCategories = new ArrayList<>(categories);
        if (categoryEditor != null && categoryEditor.editing != StockTickerMenu.IDLE) {
            var filter = categoryEditor.proxy.getItem(0).copy();
            if (categoryEditor.editing >= 0 && categoryEditor.editing < savedCategories.size()) savedCategories.set(categoryEditor.editing, filter);
            else if (!filter.isEmpty()) savedCategories.add(filter);
        }
        output.store("Categories", ItemStack.OPTIONAL_CODEC.listOf(), savedCategories); receivedPayments.save(output.child("ReceivedPayments"));
        var hidden = output.childrenList("HiddenCategories");
        hiddenCategoriesByPlayer.forEach((id, indices) -> { var entry = hidden.addChild(); entry.store("Id", UUIDUtil.CODEC, id); entry.putIntArray("Indices", indices.stream().mapToInt(Integer::intValue).toArray()); });
    }
    @Override protected void read(ValueInput input, boolean clientPacket) {
        super.read(input, clientPacket); previouslyUsedAddress = input.getStringOr("PreviousAddress", ""); activeLinks = input.getIntOr("ActiveLinks", 0);
        categories.clear(); categories.addAll(input.read("Categories", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()).stream().filter(stack -> stack.isEmpty() || isCategory(stack)).map(stack -> stack.copyWithCount(1)).toList());
        receivedPayments.load(input.childOrEmpty("ReceivedPayments")); hiddenCategoriesByPlayer.clear();
        for (var entry : input.childrenListOrEmpty("HiddenCategories")) entry.read("Id", UUIDUtil.CODEC).ifPresent(id -> hiddenCategoriesByPlayer.put(id, Arrays.stream(entry.getIntArray("Indices").orElse(new int[0])).filter(value -> value >= -1 && value < categories.size()).distinct().boxed().toList()));
    }
}
