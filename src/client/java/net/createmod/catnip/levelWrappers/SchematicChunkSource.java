// Ponder/Catnip 1.0.82 source, MIT; native26.3 source adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.catnip.levelWrappers;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/** Native chunks route reads and writes into their actual owning schematic maps. */
public class SchematicChunkSource extends ChunkSource {
  private final SchematicLevel world;
  private final Map<Long, LevelChunk> chunks = new HashMap<>();
  private final LevelLightEngine lighting;

  public SchematicChunkSource(Level world) {
    if (!(world instanceof SchematicLevel schematic))
      throw new IllegalArgumentException("Schematic chunks require their actual SchematicLevel owner");
    this.world = schematic;
    lighting = new LevelLightEngine(this, false, false);
  }

  public LevelChunk ensureChunk(int x, int z) {
    return chunks.computeIfAbsent(ChunkPos.pack(x, z), ignored -> new LocalChunk(world, x, z));
  }

  @Override
  public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) {
    return create ? ensureChunk(x, z) : chunks.get(ChunkPos.pack(x, z));
  }

  public ChunkAccess getChunk(int x, int z) {
    return ensureChunk(x, z);
  }

  @Override
  public BlockGetter getLevel() {
    return world;
  }

  @Override
  public LevelLightEngine getLightEngine() {
    return lighting;
  }

  @Override
  public String gatherStats() {
    return "Ponder local schematic chunks: " + chunks.size();
  }

  @Override
  public int getLoadedChunksCount() {
    return chunks.size();
  }

  @Override
  public void tick(BooleanSupplier budget, boolean tickChunks) {
    // Source schematic chunks do not tick the host world or run server simulation.
  }

  private static final class LocalChunk extends LevelChunk {
    private final SchematicLevel owner;

    LocalChunk(SchematicLevel owner, int x, int z) {
      super(owner, new ChunkPos(x, z));
      this.owner = owner;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
      return owner.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
      return owner.getFluidState(pos);
    }

    @Override
    public BlockState setBlockState(BlockPos pos, BlockState state, int flags) {
      var previous = getBlockState(pos);
      return owner.setBlock(pos, state, flags) ? previous : null;
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
      return owner.getBlockEntity(pos);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos, EntityCreationType type) {
      return owner.getBlockEntity(pos);
    }

    @Override
    public void setBlockEntity(BlockEntity entity) {
      owner.setBlockEntity(entity);
    }

    @Override
    public void addAndRegisterBlockEntity(BlockEntity entity) {
      owner.setBlockEntity(entity);
    }

    @Override
    public void removeBlockEntity(BlockPos pos) {
      owner.removeBlockEntity(pos);
    }
  }
}
