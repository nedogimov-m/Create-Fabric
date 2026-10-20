package com.simibubi.create.content.logistics.itemHatch;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.api.schematic.requirement.SpecialBlockEntityItemRequirement;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
/** Actual paid filter ownership remains in shared source FilteringBehaviour, with native save/sync/drop. */
public final class ItemHatchBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BlockEntityBehaviour.Provider,Clearable,SpecialBlockEntityItemRequirement {
 public final FilteringBehaviour filtering=new FilteringBehaviour(this);
 public ItemHatchBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.ITEM_HATCH,pos,state);}
 @Override public void clearContent(){filtering.setFilter(ItemStack.EMPTY);}
 @Override public ItemRequirement getRequiredItems(BlockState state){return filtering.getRequiredItems();}
 @Override public net.minecraft.nbt.CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
 @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (filtering != null) behaviours.add(filtering);
    }
}
