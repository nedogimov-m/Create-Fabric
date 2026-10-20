package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class HauntedBellBlock extends AbstractBellBlock {
    public HauntedBellBlock(Properties properties) { super(properties); }
    @Override protected boolean ring(Level level,BlockPos pos,net.minecraft.core.Direction direction,net.minecraft.world.entity.player.Player player) {
        boolean rung=super.ring(level,pos,direction,player);
        if(rung && player instanceof net.minecraft.server.level.ServerPlayer server && server.level()==level)
            com.simibubi.create.foundation.advancement.NativeDeviceAdvancements.hauntedBellRung(server);
        return rung;
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) { return new HauntedBellBlockEntity(pos,state); }
    @Override public void playSound(Level level,BlockPos pos) { if(!level.isClientSide())level.playSound(null,pos,AllSoundEvents.HAUNTED_BELL_USE,SoundSource.BLOCKS,4,1); }
    @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState previous,boolean moved) { if(!level.isClientSide()&&!previous.is(this)&&LoadedLevelAccess.blockEntity(level,pos) instanceof HauntedBellBlockEntity bell)bell.awaken(); }
}
