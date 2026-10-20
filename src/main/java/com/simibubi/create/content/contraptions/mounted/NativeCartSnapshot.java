package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.foundation.mixin.accessor.MinecartDisplayAccessor;
import com.simibubi.create.foundation.mixin.accessor.MinecartFurnaceAccessor;
import java.util.*;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.item.ItemStack;

/** Actual raw native custody identity plus detached codec validation; final matching does not run codecs/callbacks. */
public final class NativeCartSnapshot {
    private final AbstractMinecart actual;
    private final net.minecraft.world.phys.Vec3 position;
    private final float yaw,pitch;
    private final NativeCartState state;
    private final NativeCartState.Prepared expected;
    private final NonNullList<ItemStack> rawSlots;
    private final List<ItemStack> slotIdentities;
    private NativeCartSnapshot(AbstractMinecart actual,NativeCartState state,NativeCartState.Prepared expected,NonNullList<ItemStack> rawSlots,List<ItemStack> slotIdentities){this.actual=actual;position=actual.position();yaw=actual.getYRot();pitch=actual.getXRot();this.state=state;this.expected=expected;this.rawSlots=rawSlots;this.slotIdentities=slotIdentities;}
    public static NativeCartSnapshot capture(AbstractMinecart actual){
        var slots=actual instanceof MinecartChest chest?chest.getItemStacks():null;
        var identities=slots==null?List.<ItemStack>of():List.copyOf(slots);
        var data=NativeCartState.capture(actual);var expected=data.prepare(actual);
        var result=new NativeCartSnapshot(actual,data,expected,slots,identities);
        if(!result.matchesRaw())throw new IllegalStateException("Actual native cart changed during off-world snapshot codecs");return result;
    }
    public NativeCartState state(){return state;}
    public boolean matchesRaw(){
        if(!actual.position().equals(position)||Float.compare(actual.getYRot(),yaw)!=0||Float.compare(actual.getXRot(),pitch)!=0)return false;
        if(actual instanceof MinecartChest chest){
            var slots=chest.getItemStacks();if(slots!=rawSlots||slots.size()!=expected.items().size()||!Objects.equals(chest.getContainerLootTable(),expected.loot())||chest.getContainerLootTableSeed()!=expected.lootSeed())return false;
            for(int index=0;index<slots.size();index++)if(slots.get(index)!=slotIdentities.get(index)||!ItemStack.matches(slots.get(index),expected.items().get(index)))return false;
        }
        if(actual instanceof MinecartFurnace furnace&&(((MinecartFurnaceAccessor)furnace).create$getFuel()!=expected.fuel()||!furnace.push.equals(expected.push())))return false;
        return Objects.equals(actual.getCustomName(),expected.name())&&actual.getDisplayOffset()==expected.displayOffset()&&((MinecartDisplayAccessor)actual).create$getCustomDisplayBlockState().equals(expected.display());
    }
}
