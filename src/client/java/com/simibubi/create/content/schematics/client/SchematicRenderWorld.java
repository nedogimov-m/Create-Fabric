package com.simibubi.create.content.schematics.client;

import com.simibubi.create.content.schematics.SchematicStructure;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.*;
import net.minecraft.util.*;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.*;
import net.minecraft.world.level.saveddata.maps.*;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.*;

/** Complete native read/render level for schematic models and BEs; all mutation remains confined to this detached view. */
public final class SchematicRenderWorld extends Level implements BlockAndTintGetter {
  public void setCapturedBlockState(BlockPos pos, BlockState state) {
    blocks.put(pos.immutable(), state);
    var be = entities.get(pos);
    if (be != null) be.setBlockState(state);
    models.clear();
  }

  public final SchematicBlockModels models = new SchematicBlockModels();
  private final java.util.function.Function<BlockPos,BlockPos> worldPosition;
  private final Level host;
  private final Map<BlockPos, BlockState> blocks = new HashMap<>();
  private final Map<BlockPos, BlockEntity> entities = new HashMap<>();
  private final Map<Long, LevelChunk> chunks = new HashMap<>();
  private final ChunkSource source;
  private final int minY, height;
  private long animationTick = Long.MIN_VALUE;
  private final LevelEntityGetter<Entity> emptyEntities =
      new LevelEntityGetter<>() {
        @Override
        public Entity get(int id) {
          return null;
        }

        @Override
        public Entity get(UUID uuid) {
          return null;
        }

        @Override
        public Iterable<Entity> getAll() {
          return List.of();
        }

        @Override
        public <T extends Entity> void get(
            EntityTypeTest<Entity, T> type, AbortableIterationConsumer<T> consumer) {}

        @Override
        public void get(AABB bounds, Consumer<Entity> consumer) {}

        @Override
        public <T extends Entity> void get(
            EntityTypeTest<Entity, T> type, AABB bounds, AbortableIterationConsumer<T> consumer) {}
      };

  public SchematicRenderWorld(Level host,SchematicStructure snapshot,java.util.function.Function<BlockPos,BlockPos> worldPosition) {
    super((WritableLevelData)host.getLevelData(),host.dimension(),host.registryAccess(),host.dimensionTypeRegistration(),true,false,0,0);
    this.host=host;this.worldPosition=worldPosition;minY=host.getMinY();height=host.getHeight();
    source=new ChunkSource(){
      @Override public ChunkAccess getChunk(int x,int z,ChunkStatus status,boolean create){return chunks.get(ChunkPos.pack(x,z));}
      @Override public BlockGetter getLevel(){return SchematicRenderWorld.this;}
      @Override public void tick(BooleanSupplier budget,boolean tickChunks){}
      @Override public String gatherStats(){return "Create schematic render chunks: "+chunks.size();}
      @Override public int getLoadedChunksCount(){return chunks.size();}
      @Override public LevelLightEngine getLightEngine(){return host.getLightEngine();}
    };
    for(var pos:snapshot.positions()){
      blocks.put(pos,snapshot.getBlockState(pos));int x=pos.getX()>>4,z=pos.getZ()>>4;
      chunks.computeIfAbsent(ChunkPos.pack(x,z),ignored->new LevelChunk(this,new ChunkPos(x,z)){
        @Override public BlockState getBlockState(BlockPos at){return SchematicRenderWorld.this.getBlockState(at);}
        @Override public FluidState getFluidState(BlockPos at){return SchematicRenderWorld.this.getFluidState(at);}
        @Override public BlockEntity getBlockEntity(BlockPos at,EntityCreationType type){return entities.get(at);}
        @Override public BlockEntity getBlockEntity(BlockPos at){return entities.get(at);}
      });
    }
    for(var pos:snapshot.positions())if(snapshot.getBlockState(pos).getBlock() instanceof EntityBlock factory){
      var original=snapshot.getBlockEntity(pos);if(original==null)continue;var be=factory.newBlockEntity(pos,snapshot.getBlockState(pos));if(be==null)continue;
      be.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,registryAccess(),original.saveWithFullMetadata(registryAccess())));be.setLevel(this);entities.put(pos,be);
    }
    for(var be:entities.values())if(be instanceof com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer container&&!container.isController()){
      var last=container.getLastKnownPos();if(last!=null&&!last.equals(be.getBlockPos()))container.setController(container.getController().offset(be.getBlockPos().subtract(last)));
    }
    tickClientAnimations();
  }

  public void tickClientAnimations() {
    if (animationTick == host.getGameTime()) return;
    animationTick = host.getGameTime();
    for (var be : entities.values())
      if (be instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity tank)
        tank.tickFluidLevel();
      else if (be
          instanceof com.simibubi.create.content.equipment.bell.AbstractBellBlockEntity bell)
        bell.tickClientAnimation();
      else if (be instanceof com.simibubi.create.content.redstone.deskBell.DeskBellBlockEntity bell)
        bell.tickClientAnimation();
  }

  public Collection<BlockPos> positions() { return Collections.unmodifiableSet(blocks.keySet()); }

  public Collection<BlockEntity> blockEntities() {
    return List.copyOf(entities.values());
  }

  private BlockPos global(BlockPos local) {
    return worldPosition.apply(local);
  }

  @Override
  public BlockState getBlockState(BlockPos pos) {
    return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
  }

  @Override
  public FluidState getFluidState(BlockPos pos) {
    return getBlockState(pos).getFluidState();
  }

  @Override
  public BlockEntity getBlockEntity(BlockPos pos) {
    return entities.get(pos);
  }

  @Override
  public boolean isLoaded(BlockPos pos) {
    return chunks.containsKey(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
  }

  @Override
  public int getMinY() {
    return minY;
  }

  @Override
  public int getHeight() {
    return height;
  }

  @Override
  public ChunkSource getChunkSource() {
    return source;
  }

  @Override
  public int getBrightness(LightLayer layer, BlockPos pos) {
    var target = global(pos);
    return host.isLoaded(target)
        ? host.getBrightness(layer, target)
        : layer == LightLayer.SKY ? 15 : 0;
  }

  @Override
  public int getRawBrightness(BlockPos pos, int darken) {
    return Math.max(
        getBrightness(LightLayer.BLOCK, pos), getBrightness(LightLayer.SKY, pos) - darken);
  }

  @Override
  public CardinalLighting cardinalLighting() {
    return ((BlockAndTintGetter) host).cardinalLighting();
  }

  @Override
  public int getBlockTint(BlockPos pos, ColorResolver resolver) {
    var target = global(pos);
    return resolver.getColor(getBiome(pos).value(), target.getX(), target.getZ());
  }

  @Override
  public Holder<Biome> getBiome(BlockPos pos) {
    var target = global(pos);
    return host.isLoaded(target)
        ? host.getBiome(target)
        : registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
  }

  @Override
  public Holder<Biome> getNoiseBiome(int x, int y, int z) {
    return getBiome(new BlockPos(x << 2, y << 2, z << 2));
  }

  @Override
  public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
    return getNoiseBiome(x, y, z);
  }

  @Override
  public WorldBorder getWorldBorder() {
    return host.getWorldBorder();
  }

  @Override
  public int getSeaLevel() {
    return host.getSeaLevel();
  }

  @Override
  public ClockManager clockManager() {
    return host.clockManager();
  }

  @Override
  public EnvironmentAttributeSystem environmentAttributes() {
    return host.environmentAttributes();
  }

  @Override
  public TickRateManager tickRateManager() {
    return host.tickRateManager();
  }

  @Override
  public RecipeAccess recipeAccess() {
    return host.recipeAccess();
  }

  @Override
  public FeatureFlagSet enabledFeatures() {
    return host.enabledFeatures();
  }

  @Override
  public Scoreboard getScoreboard() {
    return host.getScoreboard();
  }

  @Override
  public LevelTickAccess<Block> getBlockTicks() {
    return BlackholeTickAccess.emptyLevelList();
  }

  @Override
  public LevelTickAccess<Fluid> getFluidTicks() {
    return BlackholeTickAccess.emptyLevelList();
  }

  @Override
  protected LevelEntityGetter<Entity> getEntities() {
    return emptyEntities;
  }

  @Override
  public Entity getEntity(int id) {
    return null;
  }

  @Override
  public Collection<EnderDragonPart> dragonParts() {
    return List.of();
  }

  @Override
  public List<? extends Player> players() {
    return List.of();
  }

  @Override
  public MapItemSavedData getMapData(MapId id) {
    return host.getMapData(id);
  }

  @Override
  public String gatherChunkSourceStats() {
    return source.gatherStats();
  }

  @Override
  public LevelData.RespawnData getRespawnData() {
    return host.getRespawnData();
  }

  @Override
  public void setRespawnData(LevelData.RespawnData data) {
    throw new UnsupportedOperationException("Render view has no respawn state");
  }

  @Override
  public void sendBlockUpdated(BlockPos pos, BlockState old, BlockState state, int flags) {}

  @Override
  public void destroyBlockProgress(int id, BlockPos pos, int progress) {}

  @Override
  public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) {
    return false;
  }

  @Override
  public void updateNeighbourForOutputSignal(BlockPos pos, Block block) {}

  @Override
  public void levelEvent(Entity source, int event, BlockPos pos, int data) {}

  @Override
  public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {}

  @Override
  public void playSeededSound(
      Entity source,
      double x,
      double y,
      double z,
      Holder<SoundEvent> sound,
      SoundSource category,
      float volume,
      float pitch,
      long seed) {}

  @Override
  public void playSeededSound(
      Entity source,
      Entity entity,
      Holder<SoundEvent> sound,
      SoundSource category,
      float volume,
      float pitch,
      long seed) {}

  @Override
  public void explode(
      Entity entity,
      DamageSource damage,
      ExplosionDamageCalculator calculator,
      double x,
      double y,
      double z,
      float radius,
      boolean fire,
      ExplosionInteraction interaction,
      ParticleOptions small,
      ParticleOptions large,
      WeightedList<ExplosionParticleInfo> particles,
      Holder<SoundEvent> sound) {}
}
