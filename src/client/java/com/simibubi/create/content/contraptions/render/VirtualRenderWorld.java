package com.simibubi.create.content.contraptions.render;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
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

/** Isolated read/render world in local coordinates; no ticking, world tickets or host mutations. */
public final class VirtualRenderWorld extends Level implements BlockAndTintGetter {
  public void setCapturedBlockState(BlockPos pos, BlockState state) {
    blocks.put(pos.immutable(), state);
    var be = entities.get(pos);
    if (be != null) be.setBlockState(state);
    models.clear();
  }

  public final ContraptionBlockModels models = new ContraptionBlockModels();
  private final AbstractContraptionEntity owner;
  private final Level host;
  private final Map<BlockPos, BlockState> blocks = new HashMap<>();
  private final Map<BlockPos, BlockEntity> entities = new HashMap<>();
  private final Map<BlockEntity, com.simibubi.create.foundation.render.BlockEntityRenderHelper.FailureState>
      renderFailures = new IdentityHashMap<>();
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

  public VirtualRenderWorld(AbstractContraptionEntity owner) {
    super(
        (WritableLevelData) owner.level().getLevelData(),
        owner.level().dimension(),
        owner.registryAccess(),
        owner.level().dimensionTypeRegistration(),
        true,
        false,
        0,
        0);
    this.owner = owner;
    host = owner.level();
    var contraption = owner.getContraption();
    minY = Math.floorDiv((int) Math.floor(contraption.bounds.minY) - 16, 16) * 16;
    height = Math.max(16, ((int) Math.ceil(contraption.bounds.maxY) + 31 - minY) / 16 * 16);
    source =
        new ChunkSource() {
          @Override
          public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) {
            return chunks.get(ChunkPos.pack(x, z));
          }

          @Override
          public BlockGetter getLevel() {
            return VirtualRenderWorld.this;
          }

          @Override
          public void tick(BooleanSupplier budget, boolean tickChunks) {}

          @Override
          public String gatherStats() {
            return "Create local render chunks: " + chunks.size();
          }

          @Override
          public int getLoadedChunksCount() {
            return chunks.size();
          }

          @Override
          public LevelLightEngine getLightEngine() {
            return host.getLightEngine();
          }
        };
    for (var info : contraption.getBlocks().values()) {
      blocks.put(info.pos(), info.state());
      int x = info.pos().getX() >> 4, z = info.pos().getZ() >> 4;
      chunks.computeIfAbsent(
          ChunkPos.pack(x, z),
          ignored ->
              new LevelChunk(this, new ChunkPos(x, z)) {
                @Override
                public BlockState getBlockState(BlockPos pos) {
                  return VirtualRenderWorld.this.getBlockState(pos);
                }

                @Override
                public FluidState getFluidState(BlockPos pos) {
                  return VirtualRenderWorld.this.getFluidState(pos);
                }

                @Override
                public BlockEntity getBlockEntity(BlockPos pos, EntityCreationType type) {
                  return owner.getContraption().isHiddenInPortal(pos) ? null : entities.get(pos);
                }

                @Override
                public BlockEntity getBlockEntity(BlockPos pos) {
                  return owner.getContraption().isHiddenInPortal(pos) ? null : entities.get(pos);
                }
              });
    }
    for (var info : contraption.getBlocks().values())
      if (info.state().getBlock() instanceof EntityBlock factory) {
        var be = factory.newBlockEntity(info.pos(), info.state());
        if (be == null) continue;
        if (be instanceof net.createmod.ponder.api.VirtualBlockEntity virtual) virtual.markVirtual();
        be.setLevel(this);
        if (info.nbt() != null)
          be.loadWithComponents(
              TagValueInput.create(
                  ProblemReporter.DISCARDING, registryAccess(), info.nbt().copy()));
        entities.put(info.pos(), be);
      }
    contraption.setClientBlockEntityLookup(entities::get);
    contraption.getStorage().afterSync(contraption);
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

  public Collection<BlockEntity> blockEntities() {
    return entities.values().stream()
        .filter(entity -> !owner.getContraption().isHiddenInPortal(entity.getBlockPos()))
        .toList();
  }

  public com.simibubi.create.foundation.render.BlockEntityRenderHelper.FailureState renderFailure(BlockEntity owner) {
    return renderFailures.computeIfAbsent(owner,
        com.simibubi.create.foundation.render.BlockEntityRenderHelper.FailureState::new);
  }

  private BlockPos global(BlockPos local) {
    return BlockPos.containing(owner.toGlobalVector(Vec3.atCenterOf(local), 1));
  }

  @Override
  public BlockState getBlockState(BlockPos pos) {
    return owner.getContraption().isHiddenInPortal(pos)
        ? Blocks.AIR.defaultBlockState()
        : blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
  }

  @Override
  public FluidState getFluidState(BlockPos pos) {
    return getBlockState(pos).getFluidState();
  }

  @Override
  public BlockEntity getBlockEntity(BlockPos pos) {
    return owner.getContraption().isHiddenInPortal(pos) ? null : entities.get(pos);
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
