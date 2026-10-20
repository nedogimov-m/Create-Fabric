package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Real source link context; remote access never creates a chunk ticket. */
public final class DisplayLinkContext {
    private final Level level;
    private final DisplayLinkBlockEntity blockEntity;
    private final CompoundTag configuration;
    public Object flapDisplayContext;
    public DisplayLinkContext(Level level,DisplayLinkBlockEntity blockEntity){this(level,blockEntity,blockEntity.getSourceConfig());}
    /** The genuine native menu draft for this same actual world/owner, without mutating active source data. */
    public DisplayLinkContext(Level level,DisplayLinkBlockEntity blockEntity,CompoundTag configuration){this.level=level;this.blockEntity=blockEntity;this.configuration=java.util.Objects.requireNonNull(configuration);}
    public Level level(){return level;}
    public DisplayLinkBlockEntity blockEntity(){return blockEntity;}
    public BlockPos getSourcePos(){return blockEntity.getSourcePosition();}
    public BlockPos getTargetPos(){return blockEntity.getTargetPosition();}
    public BlockEntity getSourceBlockEntity(){return LoadedLevelAccess.blockEntity(level,getSourcePos());}
    public BlockEntity getTargetBlockEntity(){return LoadedLevelAccess.blockEntity(level,getTargetPos());}
    public CompoundTag sourceConfig(){return configuration;}
    public boolean endpointsLoaded(){return LoadedLevelAccess.isLoaded(level,getSourcePos())&&LoadedLevelAccess.isLoaded(level,getTargetPos());}
}
