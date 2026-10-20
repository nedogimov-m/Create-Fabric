// Ponder/Catnip 1.0.82 source, MIT; native26.3 source adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.catnip.levelWrappers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.createmod.catnip.components.ComponentProcessors;
import net.createmod.ponder.api.VirtualBlockEntity;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.TickRateManager;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.BlackholeTickAccess;
import net.minecraft.world.ticks.LevelTickAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Source-shaped mutable Ponder schematic world. Blocks, BEs, entities, chunk writes and respawn
 * state belong to this client simulation; the host supplies registry and environment reads only.
 */
public class SchematicLevel extends Level
    implements ServerLevelAccessor, SchematicLevelAccessor, BlockAndTintGetter {
  private static final Logger LOGGER = LoggerFactory.getLogger(SchematicLevel.class);
  protected final Level level;
  protected Map<BlockPos, BlockState> blocks = new HashMap<>();
  protected Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
  protected List<BlockEntity> renderedBlockEntities = new ArrayList<>();
  protected List<Entity> entities = new ArrayList<>();
  private int nextEntityId;
  protected BoundingBox bounds = new BoundingBox(BlockPos.ZERO);
  public BlockPos anchor;
  public boolean renderMode;

  private final SchematicChunkSource chunkSource;
  private static final CardinalLighting FULL_LIGHT = new CardinalLighting(1, 1, 1, 1, 1, 1);
  private final WorldBorder border = new WorldBorder();
  private final Scoreboard scoreboard = new Scoreboard();
  private final TickRateManager tickRate = new TickRateManager();
  private final LevelEntityGetter<Entity> entityGetter = new LocalEntities();

  public SchematicLevel(Level original) {
    this(BlockPos.ZERO, original);
  }

  public SchematicLevel(BlockPos anchor, Level original) {
    super(
        new LocalLevelData(original.getLevelData()),
        original.dimension(),
        original.registryAccess(),
        original.dimensionTypeRegistration(),
        true,
        original.isDebug(),
        0,
        0);
    this.level = Objects.requireNonNull(original);
    this.anchor = anchor.immutable();
    this.chunkSource = new SchematicChunkSource(this);
  }

  @Override
  public Set<BlockPos> getAllPositions() {
    return blocks.keySet();
  }

  @Override
  public Map<BlockPos, BlockState> getBlockMap() {
    return blocks;
  }

  @Override
  public List<Entity> getEntityList() {
    return entities;
  }

  // Native Entity construction requests its ID before tick, render or local publication.
  // Keep allocation in this world, including across restore; never consume the host allocator.
  @Override
  public int getNextEntityId() {
    int candidate;
    do {
      candidate = ++nextEntityId;
    } while (candidate == 0 || entityGetter.get(candidate) != null);
    return candidate;
  }

  @Override
  public boolean addFreshEntity(Entity entity) {
    if (entity.level() != this)
      throw new IllegalArgumentException("A Ponder entity must be created in its owning schematic world");
    if (entities.contains(entity)) return false;
    if (entity instanceof ItemFrame frame)
      frame.setItem(ComponentProcessors.withUnsafeComponentsDiscarded(frame.getItem()));
    if (entity instanceof ArmorStand stand)
      for (var slot : EquipmentSlot.values())
        stand.setItemSlot(slot, ComponentProcessors.withUnsafeComponentsDiscarded(stand.getItemBySlot(slot)));
    return entities.add(entity);
  }

  @Override
  public BlockEntity getBlockEntity(BlockPos globalPos) {
    if (isOutsideBuildHeight(globalPos)) return null;
    var present = blockEntities.get(globalPos);
    if (present != null) return present;
    if (!blocks.containsKey(globalPos.subtract(anchor))) return null;
    var state = getBlockState(globalPos);
    if (!(state.getBlock() instanceof EntityBlock factory)) return null;
    try {
      var created = factory.newBlockEntity(globalPos.immutable(), state);
      if (created != null) setBlockEntity(created);
      return created;
    } catch (Exception invalid) {
      LOGGER.debug("Could not create BlockEntity of block {}", state, invalid);
      return null;
    }
  }

  protected void onBEadded(BlockEntity blockEntity, BlockPos pos) {
    if (blockEntity instanceof VirtualBlockEntity virtual) virtual.markVirtual();
    blockEntity.setLevel(this);
    blockEntity.clearRemoved();
  }

  @Override
  public void setBlockEntity(BlockEntity blockEntity) {
    if (blockEntity.getLevel() != null && blockEntity.getLevel() != this)
      throw new IllegalArgumentException("A Ponder world cannot adopt a live world's BlockEntity");
    var pos = blockEntity.getBlockPos().immutable();
    var state = getBlockState(pos);
    if (!blockEntity.getType().isValid(state))
      throw new IllegalArgumentException("Schematic BlockEntity type does not match its local block");
    var replaced = blockEntities.put(pos, blockEntity);
    if (replaced == blockEntity) return;
    if (replaced != null) {
      replaced.setRemoved();
      renderedBlockEntities.remove(replaced);
    }
    onBEadded(blockEntity, pos);
    renderedBlockEntities.add(blockEntity);
  }

  @Override
  public void removeBlockEntity(BlockPos globalPos) {
    var removed = blockEntities.remove(globalPos);
    if (removed == null) return;
    removed.setRemoved();
    renderedBlockEntities.remove(removed);
  }

  @Override
  public BlockState getBlockState(BlockPos globalPos) {
    var local = globalPos.subtract(anchor);
    if (local.getY() - bounds.minY() == -1 && !renderMode)
      return Blocks.DIRT.defaultBlockState();
    return bounds.isInside(local)
        ? processBlockStateForPrinting(blocks.getOrDefault(local, Blocks.AIR.defaultBlockState()))
        : Blocks.AIR.defaultBlockState();
  }

  @Override
  public FluidState getFluidState(BlockPos pos) {
    return getBlockState(pos).getFluidState();
  }

  @Override
  public boolean setBlock(BlockPos globalPos, BlockState state, int flags) {
    Objects.requireNonNull(state);
    if (isOutsideBuildHeight(globalPos)) return false;
    var local = globalPos.subtract(anchor).immutable();
    bounds.encapsulate(local);
    blocks.put(local, state);
    var previous = blockEntities.get(globalPos);
    if (previous != null) {
      if (previous.getType().isValid(state)) previous.setBlockState(state);
      else removeBlockEntity(globalPos);
    }
    chunkSource.ensureChunk(globalPos.getX() >> 4, globalPos.getZ() >> 4);
    getBlockEntity(globalPos);
    return true;
  }

  @Override
  public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursion) {
    return setBlock(pos, state, flags);
  }

  @Override
  public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> predicate) {
    return predicate.test(getBlockState(pos));
  }

  @Override
  public boolean destroyBlock(BlockPos pos, boolean dropResources) {
    return setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
  }

  @Override
  public boolean removeBlock(BlockPos pos, boolean moving) {
    return setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
  }

  /** Actual native StructureTemplate placement, including BEs and client-owned entities. */
  public boolean place(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings) {
    if (settings.shouldFinalizeEntities())
      throw new IllegalArgumentException("Client Ponder templates cannot finalize server mob spawning");
    return template.placeInWorld(this, origin, origin, settings, getRandom(), Block.UPDATE_CLIENTS);
  }

  @Override
  public BoundingBox getBounds() {
    return bounds;
  }

  @Override
  public void setBounds(BoundingBox bounds) {
    this.bounds = Objects.requireNonNull(bounds);
  }

  @Override
  public Iterable<BlockEntity> getBlockEntities() {
    return blockEntities.values();
  }

  @Override
  public Iterable<BlockEntity> getRenderedBlockEntities() {
    return renderedBlockEntities;
  }

  protected BlockState processBlockStateForPrinting(BlockState state) {
    return state.getBlock() instanceof AbstractFurnaceBlock && state.hasProperty(BlockStateProperties.LIT)
        ? state.setValue(BlockStateProperties.LIT, false)
        : state;
  }

  @Override
  public SchematicChunkSource getChunkSource() {
    return chunkSource;
  }

  @Override
  public BlockGetter getChunkForCollisions(int x, int z) {
    return this;
  }

  @Override
  public boolean isLoaded(BlockPos pos) {
    return !isOutsideBuildHeight(pos);
  }

  @Override
  public boolean hasChunk(int x, int z) {
    return true;
  }

  @Override
  public int getMinY() {
    return dimensionType().minY();
  }

  @Override
  public int getHeight() {
    return dimensionType().height();
  }

  @Override
  public Holder<Biome> getBiome(BlockPos pos) {
    return registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
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
  public int getBrightness(LightLayer layer, BlockPos pos) {
    return 15;
  }

  @Override
  public int getRawBrightness(BlockPos pos, int darken) {
    return 15;
  }

  @Override
  public int getSkyDarken() {
    return 0;
  }

  @Override
  public CardinalLighting cardinalLighting() {
    return FULL_LIGHT;
  }

  /** Old source API retained alongside native26.3 CardinalLighting. */
  public float getShade(Direction face, boolean hasShade) {
    return 1;
  }

  @Override
  public int getBlockTint(BlockPos pos, ColorResolver resolver) {
    return resolver.getColor(getBiome(pos).value(), pos.getX(), pos.getZ());
  }

  @Override
  public WorldBorder getWorldBorder() {
    return border;
  }

  @Override
  public Scoreboard getScoreboard() {
    return scoreboard;
  }

  @Override
  public TickRateManager tickRateManager() {
    return tickRate;
  }

  @Override
  public RecipeAccess recipeAccess() {
    return level.recipeAccess();
  }

  @Override
  public FeatureFlagSet enabledFeatures() {
    return level.enabledFeatures();
  }

  @Override
  public ClockManager clockManager() {
    return level.clockManager();
  }

  @Override
  public EnvironmentAttributeSystem environmentAttributes() {
    return level.environmentAttributes();
  }

  @Override
  public int getSeaLevel() {
    return level.getSeaLevel();
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
    return entityGetter;
  }

  @Override
  public Entity getEntity(int id) {
    return entityGetter.get(id);
  }

  // Source schematic simulation deliberately excludes entity collision queries.
  @Override
  public List<Entity> getEntities(Entity except, AABB bounds, Predicate<? super Entity> predicate) {
    return List.of();
  }

  @Override
  public <T extends Entity> List<T> getEntitiesOfClass(
      Class<T> type, AABB bounds, Predicate<? super T> predicate) {
    return List.of();
  }

  @Override
  public List<? extends Player> players() {
    return List.of();
  }

  @Override
  public Collection<EnderDragonPart> dragonParts() {
    return entities.stream().filter(EnderDragonPart.class::isInstance).map(EnderDragonPart.class::cast).toList();
  }

  @Override
  public MapItemSavedData getMapData(MapId id) {
    return null;
  }

  @Override
  public ServerLevel getLevel() {
    throw new IllegalStateException("A client schematic has no live ServerLevel authority");
  }

  @Override
  public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
    return new DifficultyInstance(getDifficulty(), getGameTime(), 0, 0);
  }

  @Override
  public LevelData.RespawnData getRespawnData() {
    return getLevelData().getRespawnData();
  }

  @Override
  public void setRespawnData(LevelData.RespawnData data) {
    ((WritableLevelData) getLevelData()).setSpawn(data);
  }

  @Override
  public String gatherChunkSourceStats() {
    return chunkSource.gatherStats();
  }

  // Source schematic levels do not deliver gameplay events, sounds or scheduled simulation ticks.
  @Override
  public void sendBlockUpdated(BlockPos pos, BlockState old, BlockState state, int flags) {}

  @Override
  public void destroyBlockProgress(int id, BlockPos pos, int progress) {}

  @Override
  public void updateNeighbourForOutputSignal(BlockPos pos, Block block) {}

  @Override
  public void levelEvent(Entity source, int event, BlockPos pos, int data) {}

  @Override
  public void gameEvent(Holder<GameEvent> event, Vec3 pos, GameEvent.Context context) {}

  @Override
  public void playSeededSound(
      Entity source, double x, double y, double z, Holder<SoundEvent> sound,
      SoundSource category, float volume, float pitch, long seed) {}

  @Override
  public void playSeededSound(
      Entity source, Entity entity, Holder<SoundEvent> sound,
      SoundSource category, float volume, float pitch, long seed) {}

  @Override
  public void explode(
      Entity source, DamageSource damage, ExplosionDamageCalculator calculator,
      double x, double y, double z, float radius, boolean fire, ExplosionInteraction interaction,
      ParticleOptions small, ParticleOptions large, WeightedList<ExplosionParticleInfo> particles,
      Holder<SoundEvent> sound) {}

  private final class LocalEntities implements LevelEntityGetter<Entity> {
    @Override
    public Entity get(int id) {
      return entities.stream().filter(entity -> entity.getId() == id).findFirst().orElse(null);
    }

    @Override
    public Entity get(UUID id) {
      return entities.stream().filter(entity -> entity.getUUID().equals(id)).findFirst().orElse(null);
    }

    @Override
    public Iterable<Entity> getAll() {
      return List.copyOf(entities);
    }

    @Override
    public <T extends Entity> void get(EntityTypeTest<Entity, T> type, AbortableIterationConsumer<T> consumer) {
      for (var entity : List.copyOf(entities)) {
        var typed = type.tryCast(entity);
        if (typed != null && consumer.accept(typed).shouldAbort()) return;
      }
    }

    @Override
    public void get(AABB bounds, Consumer<Entity> consumer) {
      for (var entity : List.copyOf(entities))
        if (entity.getBoundingBox().intersects(bounds)) consumer.accept(entity);
    }

    @Override
    public <T extends Entity> void get(
        EntityTypeTest<Entity, T> type, AABB bounds, AbortableIterationConsumer<T> consumer) {
      for (var entity : List.copyOf(entities)) {
        var typed = type.tryCast(entity);
        if (typed != null && entity.getBoundingBox().intersects(bounds)
            && consumer.accept(typed).shouldAbort()) return;
      }
    }
  }

  private static final class LocalLevelData implements WritableLevelData {
    private LevelData.RespawnData respawn;
    private final LevelData source;
    private final boolean hardcore, difficultyLocked;
    private final Difficulty difficulty;

    LocalLevelData(LevelData source) {
      this.source = source;
      respawn = source.getRespawnData();
      hardcore = source.isHardcore();
      difficulty = source.getDifficulty();
      difficultyLocked = source.isDifficultyLocked();
    }

    @Override public LevelData.RespawnData getRespawnData() { return respawn; }
    @Override public void setSpawn(LevelData.RespawnData respawn) { this.respawn = respawn; }
    @Override public long getGameTime() { return source.getGameTime(); }
    @Override public boolean isHardcore() { return hardcore; }
    @Override public Difficulty getDifficulty() { return difficulty; }
    @Override public boolean isDifficultyLocked() { return difficultyLocked; }
  }
}
