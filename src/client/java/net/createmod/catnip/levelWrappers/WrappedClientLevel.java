// Ponder/Catnip 1.0.82 source, MIT; native26.3 client particle-world adaptation.
package net.createmod.catnip.levelWrappers;

import java.util.Objects;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.mixin.client.PonderClientPacketAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;

/** Actual ClientLevel view for native particle providers, confined to one Ponder world. */
public final class WrappedClientLevel extends ClientLevel {
  private final PonderLevel level;

  private WrappedClientLevel(PonderLevel level) {
    this(level, Minecraft.getInstance());
  }

  private WrappedClientLevel(PonderLevel level, Minecraft client) {
    super(
        Objects.requireNonNull(client.getConnection(), "Ponder particle connection"),
        new ClientLevelData(level.getDifficulty(), level.getLevelData().isHardcore(), false),
        level.dimension(), level.dimensionTypeRegistration(),
        ((PonderClientPacketAccessor) client.getConnection()).ponder$chunkRadius(),
        Objects.requireNonNull(client.level, "Ponder particle client level").getServerSimulationDistance(),
        client.levelExtractor, level.isDebug(), 0, level.getSeaLevel());
    this.level = level;
    refreshClock();
  }

  public static WrappedClientLevel of(Level level) {
    if (!(level instanceof PonderLevel ponder))
      throw new IllegalArgumentException("Particle view requires its actual PonderLevel owner");
    return new WrappedClientLevel(ponder);
  }

  public void refreshClock() { getLevelData().setGameTime(level.getGameTime()); }
  public Level getWrappedLevel() { return level; }
  @Override public boolean hasChunkAt(BlockPos pos) { return level.hasChunkAt(pos); }
  @Override public boolean isLoaded(BlockPos pos) { return level.isLoaded(pos); }
  @Override public BlockState getBlockState(BlockPos pos) { return level.getBlockState(pos); }
  @Override public BlockGetter getChunkForCollisions(int x,int z) { return level; }
  @Override public int getBrightness(LightLayer layer,BlockPos pos) { return level.getBrightness(layer,pos); }
  @Override public int getLightEmission(BlockPos pos) { return level.getLightEmission(pos); }
  @Override public FluidState getFluidState(BlockPos pos) { return level.getFluidState(pos); }
  @Override public int getBlockTint(BlockPos pos,ColorResolver resolver) { return level.getBlockTint(pos,resolver); }
  @Override public BlockEntity getBlockEntity(BlockPos pos) { return level.getBlockEntity(pos); }
  @Override public Holder<Biome> getBiome(BlockPos pos) { return level.getBiome(pos); }
  @Override public ChunkAccess getChunk(int x,int z,ChunkStatus status,boolean create) {
    return level.getChunk(x,z,status,create);
  }
  @Override public Entity getEntity(int id) { return level.getEntity(id); }
  @Override public void addEntity(Entity entity) {
    throw new UnsupportedOperationException("Particle views cannot acquire entity ownership");
  }
  @Override public boolean setBlock(BlockPos pos,BlockState state,int flags,int recursion) {
    return level.setBlock(pos,state,flags,recursion);
  }
  @Override public void setBlockEntity(BlockEntity entity) { level.setBlockEntity(entity); }
  @Override public void removeBlockEntity(BlockPos pos) { level.removeBlockEntity(pos); }
  @Override public void addParticle(ParticleOptions data,double x,double y,double z,double dx,double dy,double dz) {
    level.addParticle(data,x,y,z,dx,dy,dz);
  }
  @Override public void addParticle(ParticleOptions data,boolean forced,boolean limited,
      double x,double y,double z,double dx,double dy,double dz) {
    level.addParticle(data,x,y,z,dx,dy,dz);
  }
  @Override public void addAlwaysVisibleParticle(ParticleOptions data,
      double x,double y,double z,double dx,double dy,double dz) {
    level.addParticle(data,x,y,z,dx,dy,dz);
  }
  @Override public void addAlwaysVisibleParticle(ParticleOptions data,boolean limited,
      double x,double y,double z,double dx,double dy,double dz) {
    level.addParticle(data,x,y,z,dx,dy,dz);
  }
  @Override public void sendBlockUpdated(BlockPos pos,BlockState old,BlockState state,int flags) {
    level.sendBlockUpdated(pos,old,state,flags);
  }
  @Override public void levelEvent(Entity source,int event,BlockPos pos,int data) {
    level.levelEvent(source,event,pos,data);
  }
  @Override public void playLocalSound(double x,double y,double z,SoundEvent sound,
      SoundSource category,float volume,float pitch,boolean distanceDelay) {
    level.playLocalSound(x,y,z,sound,category,volume,pitch,distanceDelay);
  }
  @Override public void playLocalSound(Entity source,SoundEvent sound,
      SoundSource category,float volume,float pitch) {
    level.playLocalSound(source,sound,category,volume,pitch);
  }
  @Override public void playSeededSound(Entity source,double x,double y,double z,Holder<SoundEvent> sound,
      SoundSource category,float volume,float pitch,long seed) {
    level.playSeededSound(source,x,y,z,sound,category,volume,pitch,seed);
  }
  @Override public void playSeededSound(Entity source,Entity entity,Holder<SoundEvent> sound,
      SoundSource category,float volume,float pitch,long seed) {
    level.playSeededSound(source,entity,sound,category,volume,pitch,seed);
  }
}
