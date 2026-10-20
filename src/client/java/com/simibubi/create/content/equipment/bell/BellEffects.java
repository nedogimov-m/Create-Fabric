package com.simibubi.create.content.equipment.bell;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.*;

public final class BellEffects {
    public static final SoulPulseEffectHandler PULSES=new SoulPulseEffectHandler();
    private static ClientLevel level;
    private static void world(ClientLevel current) { if(current!=level){PULSES.refresh();level=current;} }
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SoulPulseEffectPacket.TYPE,(packet,context)->{world(context.client().level);if(level!=null)PULSES.addPulse(new SoulPulseEffect(packet.pos(),packet.distance(),packet.canOverlap()));});
        ClientTickEvents.END_CLIENT_TICK.register(client->{world(client.level);if(level!=null&&!client.isPaused())PULSES.tick(level);});
        HauntedBellBlockEntity.CLIENT_EFFECT=bell->{var world=bell.getLevel();var random=world.getRandom();if(random.nextFloat()>.25f)return;var pos=bell.getBlockPos();world.addParticle(ParticleTypes.SOUL,pos.getX()+random.nextDouble(),pos.getY()+.5,pos.getZ()+random.nextDouble(),random.nextDouble()*.04-.02,.1,random.nextDouble()*.04-.02);float volume=random.nextFloat()*.4f+random.nextFloat()>.9f?.6f:0;world.playSound(null,pos,SoundEvents.SOUL_ESCAPE.value(),SoundSource.BLOCKS,volume,.6f+random.nextFloat()*.4f);};
    }
    private BellEffects() {}
}
