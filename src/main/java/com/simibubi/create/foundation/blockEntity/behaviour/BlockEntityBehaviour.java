package com.simibubi.create.foundation.blockEntity.behaviour;

import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

/** Source behaviour API; the actual Smart owner controls lifecycle and native persistence. */
public abstract class BlockEntityBehaviour {
    public com.simibubi.create.content.schematics.requirement.ItemRequirement getRequiredItems(){return com.simibubi.create.content.schematics.requirement.ItemRequirement.NONE;}
    public interface Provider {<T extends BlockEntityBehaviour>T getBehaviour(BehaviourType<T> type);}
    public SmartBlockEntity blockEntity;
    private int lazyTickRate=10,lazyTickCounter=10;
    public BlockEntityBehaviour(SmartBlockEntity blockEntity){this.blockEntity=blockEntity;}
    protected BlockEntityBehaviour(BlockEntity blockEntity){
        if(!(blockEntity instanceof SmartBlockEntity smart))throw new IllegalArgumentException("A block entity behaviour requires its actual Smart owner");
        this.blockEntity=smart;
    }
    public abstract BehaviourType<?> getType();
    public void initialize(){}
    public void tick(){if(lazyTickCounter--<=0){lazyTickCounter=lazyTickRate;lazyTick();}}
    public void lazyTick(){}
    public void setLazyTickRate(int rate){lazyTickRate=rate;lazyTickCounter=rate;}
    public void read(ValueInput input){}
    public void write(ValueOutput output){}
    public void read(ValueInput input,boolean clientPacket){read(input);}
    public void write(ValueOutput output,boolean clientPacket){write(output);}
    public void writeSafe(ValueOutput output){write(output,false);}
    public void writeSafe(CompoundTag tag,HolderLookup.Provider registries){
        var output=TagValueOutput.createWithContext(ProblemReporter.DISCARDING,registries);
        writeSafe(output);tag.merge(output.buildResult());
    }
    public boolean isSafeNBT(){return false;}
    public void onBlockChanged(BlockState previous){}
    public void onNeighborChanged(BlockPos neighbor){}
    public void unload(){}
    public void destroy(){}
    public BlockPos getPos(){return blockEntity.getBlockPos();}
    public Level getWorld(){return blockEntity.getLevel();}
    public static <T extends BlockEntityBehaviour>T get(BlockGetter level,BlockPos pos,BehaviourType<T> type){return get(LoadedLevelAccess.blockEntity(level,pos),type);}
    public static <T extends BlockEntityBehaviour>T get(BlockEntity blockEntity,BehaviourType<T> type){if(blockEntity instanceof Provider provider)return provider.getBehaviour(type);return type.resolve(blockEntity);}
    protected void changed(){blockEntity.setChanged();var level=getWorld();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(getPos(),blockEntity.getBlockState(),blockEntity.getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);}
}
