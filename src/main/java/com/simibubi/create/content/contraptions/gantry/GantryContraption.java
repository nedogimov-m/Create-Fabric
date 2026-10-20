package com.simibubi.create.content.contraptions.gantry;

import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/** The real carriage is part of its payload; its supporting rack stays in the world. */
public class GantryContraption extends TranslatingContraption {
    private Direction facing=Direction.UP;
    public GantryContraption(){}
    public GantryContraption(Direction facing){this.facing=facing;}
    public Direction getFacing(){return facing;}
    @Override public ContraptionType getType(){return AllContraptionTypes.GANTRY.value();}
    @Override public boolean assemble(Level level,BlockPos pos)throws AssemblyException{if(!searchMovedStructure(level,pos,null))return false;startMoving(level);return true;}
    @Override protected boolean isAnchoringBlockAt(BlockPos pos){return super.isAnchoringBlockAt(pos.relative(facing));}
    @Override protected boolean shouldUpdateAfterMovement(StructureBlockInfo info){return super.shouldUpdateAfterMovement(info)&&!(info.state().getBlock() instanceof GantryCarriageBlock);}
    @Override public CompoundTag writeNBT(HolderLookup.Provider registries,boolean spawn){var tag=super.writeNBT(registries,spawn);tag.putInt("Facing",facing.get3DDataValue());return tag;}
    @Override public void readNBT(Level level,CompoundTag tag,boolean spawn){facing=Direction.from3DDataValue(tag.getIntOr("Facing",Direction.UP.get3DDataValue()));super.readNBT(level,tag,spawn);var carriage=blocks.get(BlockPos.ZERO);if(carriage==null||!(carriage.state().getBlock() instanceof GantryCarriageBlock)||carriage.state().getValue(GantryCarriageBlock.FACING)!=facing)throw new IllegalArgumentException("Saved gantry requires its actual oriented carriage at the anchor");invalidateColliders();}
}
