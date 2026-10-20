package com.simibubi.create.content.logistics.crate;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.blockEntity.behaviour.*;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Clearable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class CreativeCrateBlockEntity extends CrateBlockEntity implements BlockEntityBehaviour.Provider,Clearable {
    public final FilteringBehaviour filtering=new FilteringBehaviour(this);
    public final BottomlessItemHandler inv=new BottomlessItemHandler(filtering::getFilter);
    public CreativeCrateBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.CREATIVE_CRATE,pos,state);}
    @Override public void clearContent(){filtering.setFilter(ItemStack.EMPTY);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (filtering != null) behaviours.add(filtering);
    }
}
