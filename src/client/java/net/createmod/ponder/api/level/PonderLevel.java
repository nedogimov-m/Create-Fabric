// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.api.level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.createmod.ponder.api.scene.Selection;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.PonderWorldParticles;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.ParticlesRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.createmod.ponder.api.element.WorldSectionElement;

/** Actual source Ponder simulation world, backup/restore, entity ticks and native local particles. */
public class PonderLevel extends SchematicLevel implements com.simibubi.create.foundation.utility.VirtualLevelClock {
  private static final java.util.Set<PonderLevel> ACTIVE_WORLDS =
      java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
  public PonderScene scene;
  protected Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
  protected Map<BlockPos, CompoundTag> originalBlockEntities = new HashMap<>();
  protected Map<BlockPos, Integer> blockBreakingProgressions = new HashMap<>();
  protected List<CompoundTag> originalEntities = new ArrayList<>();
  protected final PonderWorldParticles particles;
  private int overrideLight = -1;
  private Selection mask;
  private boolean currentlyTickingEntities;
  private int simulationTicks;
  private float scenePartialTicks;

  public int simulationTicks(){return simulationTicks;}
  @Override public long virtualGameTime(){return simulationTicks;}
  @Override public long getGameTime(){return simulationTicks;}
  public float scenePartialTicks(){return scenePartialTicks;}
  public void scenePartialTicks(float partial){scenePartialTicks=partial;}

  public PonderLevel(BlockPos anchor, Level original) {
    super(anchor, original);
    particles = new PonderWorldParticles(this);
    renderMode = true;
    ACTIVE_WORLDS.add(this);
  }

  /** Called on the native client apply thread after vanilla resources finish reloading. */
  public static void invalidateResources() {
    for(var world:List.copyOf(ACTIVE_WORLDS)) {
      world.particles.invalidateResources();
      world.redraw();
    }
  }

  public void createBackup() {
    originalBlocks.clear();
    originalBlockEntities.clear();
    originalEntities.clear();
    originalBlocks.putAll(blocks);
    blockEntities.forEach((pos, entity) ->
        originalBlockEntities.put(pos.immutable(), entity.saveWithFullMetadata(registryAccess()).copy()));
    for (var entity : entities) {
      if (entity.isPassenger()) continue;
      var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registryAccess());
      if (entity.save(output)) originalEntities.add(output.buildResult().copy());
    }
  }

  public void restore() {
    simulationTicks=0;
    for (var entity : List.copyOf(entities)) entity.discard();
    for (var entity : List.copyOf(blockEntities.values())) entity.setRemoved();
    entities.clear();
    blocks.clear();
    blockEntities.clear();
    blockBreakingProgressions.clear();
    renderedBlockEntities.clear();
    blocks.putAll(originalBlocks);
    originalBlockEntities.forEach(this::restoreBlockEntity);
    for (var data : originalEntities) {
      var entity = EntityType.loadEntityRecursive(
          TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), data.copy()),
          this, new EntitySpawnRequest(EntitySpawnReason.LOAD, false), value -> value);
      if (entity != null) entity.getSelfAndPassengers().forEach(this::addFreshEntity);
    }
    particles.clearEffects();
    PonderIndex.forEachPlugin(plugin -> plugin.onPonderLevelRestore(this));
  }

  private void restoreBlockEntity(BlockPos globalPos, CompoundTag data) {
    var state = originalBlocks.get(globalPos.subtract(anchor));
    if (state == null) return;
    var savedType = data.get("id");
    if (savedType == null) throw new IllegalArgumentException("Missing Ponder BlockEntity type at " + globalPos);
    var type = BuiltInRegistries.BLOCK_ENTITY_TYPE.byNameCodec().parse(NbtOps.INSTANCE, savedType).getOrThrow();
    if (!type.isValid(state))
      throw new IllegalArgumentException("Saved Ponder BlockEntity type does not match " + state);
    var entity = type.create(globalPos, state);
    if (entity == null) throw new IllegalStateException("Cannot create saved Ponder BlockEntity at " + globalPos);
    // Bind the fresh local owner and mark it virtual before native NBT reads Speed/network data.
    setBlockEntity(entity);
    try {
      entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registryAccess(), data.copy()));
    } catch (RuntimeException | Error failure) {
      if (blockEntities.get(globalPos) == entity) removeBlockEntity(globalPos);
      else entity.setRemoved();
      throw failure;
    }
  }

  public void restoreBlocks(Selection selection) {
    selection.forEach(pos -> {
      var local = pos.subtract(anchor);
      if (!originalBlocks.containsKey(local)) return;
      setBlock(pos, originalBlocks.get(local), 0);
      var saved = originalBlockEntities.get(pos);
      if (saved != null) { removeBlockEntity(pos); restoreBlockEntity(pos, saved); }
    });
    redraw();
  }

  private void redraw() {
    if (scene != null) scene.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw);
  }

  public void pushFakeLight(int light) { overrideLight = light; }
  public void popLight() { overrideLight = -1; }
  @Override public int getBrightness(LightLayer layer, BlockPos pos) { return overrideLight == -1 ? 15 : overrideLight; }
  public void setMask(Selection mask) { this.mask = mask; }
  public void clearMask() { mask = null; }

  @Override
  public BlockState getBlockState(BlockPos globalPos) {
    if (mask != null && !mask.test(globalPos.subtract(anchor))) return Blocks.AIR.defaultBlockState();
    if (currentlyTickingEntities && globalPos.getY() < anchor.getY()) return Blocks.AIR.defaultBlockState();
    return super.getBlockState(globalPos);
  }

  @Override public BlockGetter getChunkForCollisions(int x, int z) { return this; }

  public void tick() {
    simulationTicks=(simulationTicks+1)%1_728_000;
    currentlyTickingEntities = true;
    try {
      particles.tick();
      for (Iterator<Entity> iterator = entities.iterator(); iterator.hasNext();) {
        var entity = iterator.next();
        entity.tickCount++;
        entity.xOld = entity.getX(); entity.yOld = entity.getY(); entity.zOld = entity.getZ();
        entity.tick();
        if (entity.getY() <= anchor.getY() - .5f) entity.discard();
        if (!entity.isAlive()) iterator.remove();
      }
    } finally { currentlyTickingEntities = false; }
  }

  @Override
  public void addParticle(ParticleOptions data, double x,double y,double z,double dx,double dy,double dz) {
    particles.create(data,x,y,z,dx,dy,dz);
  }

  @Override
  public void addAlwaysVisibleParticle(ParticleOptions data, double x,double y,double z,double dx,double dy,double dz) {
    addParticle(data,x,y,z,dx,dy,dz);
  }

  public void addParticle(Particle particle) { particles.addParticle(particle); }
  public ParticlesRenderState extractParticles(Frustum frustum,Camera camera,float partial) {
    return particles.extract(frustum,camera,partial);
  }

  public void setBlockBreakingProgress(BlockPos pos,int damage) {
    if(damage==0)blockBreakingProgressions.remove(pos);
    else blockBreakingProgressions.put(pos.immutable(),damage-1);
  }

  public Map<BlockPos,Integer> getBlockBreakingProgressions() { return blockBreakingProgressions; }

  public void addBlockDestroyEffects(BlockPos pos,BlockState state) {
    var shape=state.getShape(this,pos);
    if(shape.isEmpty())return;
    AABB box=shape.bounds();
    double sx=Math.min(1,box.maxX-box.minX), sy=Math.min(1,box.maxY-box.minY), sz=Math.min(1,box.maxZ-box.minZ);
    int nx=Math.max(2,Mth.ceil(sx/.25)), ny=Math.max(2,Mth.ceil(sy/.25)), nz=Math.max(2,Mth.ceil(sz/.25));
    for(int x=0;x<nx;x++)for(int y=0;y<ny;y++)for(int z=0;z<nz;z++) {
      double px=(x+.5)/nx, py=(y+.5)/ny, pz=(z+.5)/nz;
      addParticle(new BlockParticleOption(ParticleTypes.BLOCK,state),
          pos.getX()+px*sx+box.minX,pos.getY()+py*sy+box.minY,pos.getZ()+pz*sz+box.minZ,
          px-.5,py-.5,pz-.5);
    }
  }

  @Override protected BlockState processBlockStateForPrinting(BlockState state) { return state; }
  @Override public boolean hasChunkAt(BlockPos pos) { return true; }
  @Override public boolean hasChunk(int x,int z) { return true; }
  @Override public boolean isLoaded(BlockPos pos) { return true; }
  @Override public boolean hasNearbyAlivePlayer(double x,double y,double z,double range) { return true; }
}
