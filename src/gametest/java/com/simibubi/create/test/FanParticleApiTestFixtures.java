package com.simibubi.create.test;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Independent addon callbacks: dormant outside the one focused native client case. */
public final class FanParticleApiTestFixtures implements ModInitializer {
    public static final Identifier ID=Identifier.fromNamespaceAndPath("create_test","fan_client_callbacks");
    public static final AtomicBoolean ACTIVE=new AtomicBoolean();
    public static final AtomicInteger MORPH_CALLS=new AtomicInteger(),PROCESSING_PARTICLE_CALLS=new AtomicInteger();
    public static final int COLOR=0x2A6FA4;
    public static final float ALPHA=.625f;
    public static final FanProcessingType TYPE=new FanProcessingType() {
        @Override public boolean isValidAt(Level level,BlockPos pos) { return ACTIVE.get()&&level.getBlockState(pos).is(Blocks.REDSTONE_TORCH); }
        @Override public int getPriority() { return 10000; }
        @Override public boolean canProcess(ItemStack stack,Level level) { return ACTIVE.get()&&stack.is(Items.STRUCTURE_VOID); }
        @Override public List<ItemStack> process(ItemStack stack,Level level) { return canProcess(stack,level)?List.of(stack.copy()):null; }
        @Override public void spawnProcessingParticles(Level level,Vec3 pos) {
            if(!level.isClientSide())throw new AssertionError("Source processing particle callback must run in actual client world");
            PROCESSING_PARTICLE_CALLS.incrementAndGet();
            level.addParticle(ParticleTypes.HEART,pos.x,pos.y+.25,pos.z,0,.025,0);
        }
        @Override public void morphAirFlow(AirFlowParticleAccess particle,RandomSource random) {
            MORPH_CALLS.incrementAndGet();
            particle.setColor(COLOR);particle.setAlpha(ALPHA);
            particle.spawnExtraParticle(ParticleTypes.END_ROD,.25f);
        }
        @Override public void affectEntity(Entity entity,Level level) {
            // This fixture type changes appearance and preserves the original entity's owned cargo.
        }
    };
    @Override public void onInitialize() { Registry.register(CreateBuiltInRegistries.FAN_PROCESSING_TYPE,ID,TYPE); }
    public static void reset() { ACTIVE.set(false);MORPH_CALLS.set(0);PROCESSING_PARTICLE_CALLS.set(0); }
}
