package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.*;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import org.apache.commons.lang3.tuple.Pair;

public class BearingContraption extends Contraption {
    public static final TagKey<Block> WINDMILL_SAILS=TagKey.create(Registries.BLOCK,Create.asResource("windmill_sails"));
    protected int sailBlocks;
    protected Direction facing=Direction.UP;
    private boolean windmill;
    public BearingContraption(){}
    public BearingContraption(boolean windmill,Direction facing){this.windmill=windmill;this.facing=facing;}
    @Override public boolean assemble(Level level,BlockPos bearing)throws AssemblyException{
        sailBlocks=0;if(!searchMovedStructure(level,bearing.relative(facing),null))return false;startMoving(level);expandBoundsAroundAxis(facing.getAxis());if(windmill&&sailBlocks<ContraptionSettings.minimumWindmillSails)throw AssemblyException.notEnoughSails(sailBlocks);return !blocks.isEmpty();
    }
    @Override protected boolean isAnchoringBlockAt(BlockPos pos){return pos.equals(anchor.relative(facing.getOpposite()));}
    @Override public ContraptionType getType(){return AllContraptionTypes.BEARING.value();}
    @Override public void addBlock(Level level,BlockPos pos,Pair<StructureBlockInfo,BlockEntity> capture){if(!blocks.containsKey(pos.subtract(anchor))&&capture.getLeft().state().is(WINDMILL_SAILS))sailBlocks++;super.addBlock(level,pos,capture);}
    public int getSailBlocks(){return sailBlocks;}
    public Direction getFacing(){return facing;}
    @Override public boolean canBeStabilized(Direction direction,BlockPos localPos){return !(direction.getOpposite()==facing&&localPos.equals(BlockPos.ZERO))&&direction.getAxis()==facing.getAxis();}
    @Override public CompoundTag writeNBT(HolderLookup.Provider registries,boolean spawnData){var data=super.writeNBT(registries,spawnData);data.putInt("Sails",sailBlocks);data.putInt("Facing",facing.get3DDataValue());return data;}
    @Override public void readNBT(Level level,CompoundTag data,boolean spawnData){sailBlocks=data.getIntOr("Sails",0);facing=Direction.from3DDataValue(data.getIntOr("Facing",Direction.UP.get3DDataValue()));super.readNBT(level,data,spawnData);}
}
