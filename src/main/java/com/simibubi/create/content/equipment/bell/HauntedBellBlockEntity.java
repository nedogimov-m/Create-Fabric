package com.simibubi.create.content.equipment.bell;

import com.simibubi.create.AllBlockEntityTypes;
import java.util.function.Consumer;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;

public final class HauntedBellBlockEntity extends AbstractBellBlockEntity {
    public static final int DISTANCE=10,RECHARGE_TICKS=65,EFFECT_TICKS=20;
    public static Consumer<HauntedBellBlockEntity> CLIENT_EFFECT = bell -> {};
    public int effectTicks;
    public HauntedBellBlockEntity(BlockPos pos,BlockState state) { super(AllBlockEntityTypes.HAUNTED_BELL,pos,state); }
    @Override public boolean ring(Level level,BlockPos pos,Direction direction) {
        if(isRinging&&ringingTicks<RECHARGE_TICKS)return false;
        if(level instanceof ServerLevel server)HauntedBellPulser.sendPulse(server,pos,DISTANCE,false);
        effectTicks=EFFECT_TICKS;return super.ring(level,pos,direction);
    }
    void tickEffect() { if(effectTicks<=0)return;effectTicks--;if(level!=null&&level.isClientSide())CLIENT_EFFECT.accept(this); }
    public void awaken() { effectTicks=EFFECT_TICKS;setChanged();sync(); }
    @Override protected void write(ValueOutput out, boolean clientPacket) { super.write(out, clientPacket);out.putInt("EffectTicks",effectTicks); }
    @Override protected void read(ValueInput in, boolean clientPacket) { super.read(in, clientPacket);effectTicks=Math.max(0,in.getIntOr("EffectTicks",0)); }
}
