package com.simibubi.create.content.trains.track;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Source three-random-tick lease, renewed by genuine loaded primary curves every100 ticks. */
public final class FakeTrackBlockEntity extends com.simibubi.create.foundation.blockEntity.SyncedBlockEntity {
    private int keepAlive;
    public FakeTrackBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.FAKE_TRACK,pos,state);keepAlive();}
    public void randomTick(){if(level!=null&&!level.isClientSide()&&--keepAlive<=0&&LoadedLevelAccess.blockEntity(level,worldPosition)==this)LoadedLevelAccess.setBlockQuiet(level,worldPosition,getBlockState().getFluidState().createLegacyBlock());}
    public void keepAlive(){keepAlive=3;}
    public int remainingRandomTicks(){return keepAlive;}
}
