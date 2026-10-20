package com.simibubi.create.content.equipment.bell;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public final class SoulPulseEffectHandler {
    private final List<SoulPulseEffect> pulses=new ArrayList<>();
    private final Set<BlockPos> occupied=new HashSet<>();
    public int activePulses() { return pulses.size(); }
    public int occupiedPositions() { return occupied.size(); }
    public void addPulse(SoulPulseEffect pulse) { pulses.add(pulse); }
    public void refresh() { pulses.clear();occupied.clear(); }
    public void tick(Level level) {
        for(var pulse:pulses){var positions=pulse.tick(level);if(positions==null)continue;for(var pos:positions){if(!pulse.canOverlap()&&!occupied.add(pos))continue;pulse.spawnParticles(level,pos);if(!pulse.canOverlap())pulse.added.add(pos);}}
        pulses.removeIf(pulse->{if(!pulse.finished())return false;if(!pulse.canOverlap())occupied.removeAll(pulse.added);return true;});
    }
}
