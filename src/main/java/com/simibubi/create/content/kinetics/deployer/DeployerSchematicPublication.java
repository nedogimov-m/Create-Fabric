package com.simibubi.create.content.kinetics.deployer;

import com.simibubi.create.content.schematics.SchematicPlacement;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** Actual chunk-section acceptance precedes every native source onPlace/neighbor callback. */
public final class DeployerSchematicPublication implements AutoCloseable {
    private static final ThreadLocal<ArrayDeque<DeployerSchematicPublication>> ACTIVE=ThreadLocal.withInitial(ArrayDeque::new);
    private final Level level;
    private final BlockPos target;
    private final SchematicPlacement.Publication publication;
    private boolean closed;
    private BlockState desired;
    private DeployerSchematicPublication(Level level,BlockPos target,SchematicPlacement.Publication publication){this.level=level;this.target=target.immutable();this.publication=publication;ACTIVE.get().push(this);}
    static DeployerSchematicPublication prepare(Level level,BlockPos target,SchematicPlacement.Publication publication){return new DeployerSchematicPublication(level,target,publication);}
    static void prepared(Level level,BlockPos target,BlockState desired){var scope=ACTIVE.get().peek();if(scope!=null&&!scope.closed&&scope.level==level&&scope.target.equals(target))scope.desired=desired;}
    public static void published(LevelChunk chunk,BlockPos pos,BlockState state,BlockState previous){
        var scope=ACTIVE.get().peek();if(scope==null||scope.closed||scope.level!=chunk.getLevel()||!scope.target.equals(pos)||state!=scope.desired||state==previous||chunk.getBlockState(pos)!=state)return;
        scope.publication.baseAccepted(state);
    }
    @Override public void close(){if(closed)return;if(ACTIVE.get().peek()!=this)throw new IllegalStateException("Moving schematic publication scopes must close in actual source order");ACTIVE.get().pop();closed=true;if(ACTIVE.get().isEmpty())ACTIVE.remove();}
}
