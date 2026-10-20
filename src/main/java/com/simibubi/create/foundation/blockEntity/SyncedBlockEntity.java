package com.simibubi.create.foundation.blockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Source synchronization API over the actual native 26.3 serialization path. */
public abstract class SyncedBlockEntity extends BlockEntity {
    private int clientReadDepth;

    public SyncedBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return writeClient(new CompoundTag(), registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        readClient(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }

    public void readClient(ValueInput input) {
        withClientRead(() -> loadAdditional(input));
    }

    /** Also wraps native loadWithComponents: vanilla remains the sole component reader. */
    public final void withClientRead(Runnable nativeRead) {
        clientReadDepth++;
        try {
            nativeRead.run();
        } finally {
            clientReadDepth--;
        }
    }

    protected final boolean isReadingClientPacket() {
        return clientReadDepth != 0;
    }

    public CompoundTag writeClient(CompoundTag tag, HolderLookup.Provider registries) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        writeClient(output);
        tag.merge(output.buildResult());
        return tag;
    }

    protected void writeClient(ValueOutput output) {
        saveAdditional(output);
    }

    public void sendData() {
        if (level instanceof ServerLevel server)
            server.getChunkSource().blockChanged(getBlockPos());
    }

    public void notifyUpdate() {
        setChanged();
        sendData();
    }

    public HolderGetter<Block> blockHolderGetter() {
        return level != null ? level.holderLookup(Registries.BLOCK) : BuiltInRegistries.BLOCK;
    }
}
