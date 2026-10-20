package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class ToolboxMountedStorageType extends MountedItemStorageType<ToolboxMountedStorage>{
    public ToolboxMountedStorageType(){super(ToolboxMountedStorage.CODEC);}
    @Override public ToolboxMountedStorage mount(Level level,BlockState state,BlockPos pos,BlockEntity owner){return owner instanceof ToolboxBlockEntity toolbox?ToolboxMountedStorage.fromToolbox(toolbox):null;}
}
