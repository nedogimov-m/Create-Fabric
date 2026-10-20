package com.simibubi.create.content.redstone.displayLink.target;

import com.mojang.serialization.JsonOps;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.nixieTube.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.*;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;

/** Actual complete loaded Nixie row, source component serialization and real two-character cell distribution. */
public final class NixieTubeDisplayTarget extends SingleLineDisplayTarget {
    @Override protected void acceptLine(MutableComponent text,DisplayLinkContext context){var json=ComponentSerialization.CODEC.encodeStart(context.level().registryAccess().createSerializationContext(JsonOps.INSTANCE),text).getOrThrow().toString();NixieTubeBlock.walkNixies(context.level(),context.getTargetPos(),(pos,index)->{if(LoadedLevelAccess.blockEntity(context.level(),pos)instanceof NixieTubeBlockEntity nixie)nixie.displayCustomText(json,index);});}
    @Override protected int getWidth(DisplayLinkContext context){var count=new int[1];return NixieTubeBlock.walkNixies(context.level(),context.getTargetPos(),(pos,index)->count[0]+=2)?count[0]:0;}
    @Override public AABB getMultiblockBounds(LevelAccessor level,BlockPos pos){var bounds=new AABB[]{null};if(!NixieTubeBlock.walkNixies(level,pos,(cell,index)->{var shape=LoadedLevelAccess.blockState(level,cell).getShape(level,cell);var box=shape.isEmpty()?new AABB(cell):shape.bounds().move(cell);bounds[0]=bounds[0]==null?box:bounds[0].minmax(box);}))return new AABB(pos);return bounds[0]==null?new AABB(pos):bounds[0];}
}
