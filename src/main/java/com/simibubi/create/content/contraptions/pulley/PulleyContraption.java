package com.simibubi.create.content.contraptions.pulley;

import com.simibubi.create.AllContraptionTypes;
import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The rope column is world/controller metadata; only the actual body is this native owner. */
public class PulleyContraption extends TranslatingContraption {
    protected int initialOffset;
    public PulleyContraption(){}
    public PulleyContraption(int offset){if(offset<0)throw new IllegalArgumentException("Negative initial rope length");initialOffset=offset;}
    public int getInitialOffset(){return initialOffset;}
    @Override public ContraptionType getType(){return AllContraptionTypes.PULLEY.value();}
    @Override public boolean assemble(Level level,BlockPos pos)throws AssemblyException{if(!searchMovedStructure(level,pos,null))return false;startMoving(level);invalidateColliders();return true;}
    @Override protected boolean isAnchoringBlockAt(BlockPos pos){return pos.getX()==anchor.getX()&&pos.getZ()==anchor.getZ()&&pos.getY()>anchor.getY()&&pos.getY()<=anchor.getY()+initialOffset+1;}
    @Override public Vec3 positionAtProgress(float progress){return Vec3.atLowerCornerOf(anchor).add(0,initialOffset-progress,0);}
    @Override public CompoundTag writeNBT(HolderLookup.Provider registries,boolean spawn){var tag=super.writeNBT(registries,spawn);tag.putInt("InitialOffset",initialOffset);return tag;}
    @Override public void readNBT(Level level,CompoundTag tag,boolean spawn){initialOffset=tag.getIntOr("InitialOffset",0);if(initialOffset<0||initialOffset>level.getHeight())throw new IllegalArgumentException("Invalid saved rope offset");super.readNBT(level,tag,spawn);invalidateColliders();}
}
