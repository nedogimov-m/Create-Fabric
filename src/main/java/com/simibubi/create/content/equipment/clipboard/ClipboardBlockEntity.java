package com.simibubi.create.content.equipment.clipboard;

import com.simibubi.create.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.UUID;

/** Actual native component-bearing placed clipboard. Native BE saving retains all component data. */
public final class ClipboardBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    private UUID lastEdit;
    public ClipboardBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.CLIPBOARD,pos,state);}
    public UUID lastEdit(){return lastEdit;}
    public void onEditedBy(Player player){lastEdit=player.getUUID();updateWrittenState();notifyUpdate();}
    public void updateWrittenState(){if(level==null||level.isClientSide()||isRemoved()||com.simibubi.create.content.schematics.SchematicWorldAccess.blockEntity(level,worldPosition)!=this||!getBlockState().is(AllBlocks.CLIPBOARD))return;boolean written=components().has(AllDataComponents.CLIPBOARD_CONTENT);if(getBlockState().getValue(ClipboardBlock.WRITTEN)!=written)level.setBlockAndUpdate(worldPosition,getBlockState().setValue(ClipboardBlock.WRITTEN,written));}
    public static void tick(Level level,BlockPos pos,BlockState state,ClipboardBlockEntity entity){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, entity)) {
            if (!createTick.shouldTick()) return;
entity.updateWrittenState();
        }
    }
    public void notifyUpdate(){setChanged();if(level!=null&&!level.isClientSide()&&!isRemoved())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    @Override public void setComponents(DataComponentMap components){super.setComponents(components);setChanged();}
    @Override protected void write(ValueOutput output, boolean clientPacket){super.write(output, clientPacket);if(lastEdit!=null)output.store("LastEdit",UUIDUtil.CODEC,lastEdit);}
    @Override protected void read(ValueInput input, boolean clientPacket){super.read(input, clientPacket);lastEdit=input.read("LastEdit",UUIDUtil.CODEC).orElse(null);input.read("components",DataComponentMap.CODEC).ifPresent(this::setComponents);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);tag.store("components",DataComponentMap.CODEC,registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),components());return tag;}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
