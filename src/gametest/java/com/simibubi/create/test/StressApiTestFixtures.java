package com.simibubi.create.test;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Independently registered addon keys; normal inherited stress consumers and real shaft ports. */
public final class StressApiTestFixtures implements ModInitializer {
    public static StressBlock SOURCE, LOAD, PROVIDER_SOURCE, PROVIDER_LOAD, TAG_LOAD;
    public static BlockEntityType<KineticBlockEntity> LOAD_TYPE;
    public static BlockEntityType<Source> SOURCE_TYPE;

    @Override public void onInitialize() {
        SOURCE=block("stress_direct_source",true);
        LOAD=block("stress_direct_load",false);
        PROVIDER_SOURCE=block("stress_provider_source",true);
        PROVIDER_LOAD=block("stress_provider_load",false);
        TAG_LOAD=block("stress_tag_load",false);
        LOAD_TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id("stress_load"),
            FabricBlockEntityTypeBuilder.create((pos,state)->new KineticBlockEntity(LOAD_TYPE,pos,state),LOAD,PROVIDER_LOAD,TAG_LOAD).build());
        SOURCE_TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id("stress_source"),
            FabricBlockEntityTypeBuilder.create(Source::new,SOURCE,PROVIDER_SOURCE).build());
    }
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("create_test",path); }
    private static StressBlock block(String path,boolean source) {
        var key=ResourceKey.create(Registries.BLOCK,id(path));
        return Registry.register(BuiltInRegistries.BLOCK,key,
            new StressBlock(BlockBehaviour.Properties.of().setId(key),source));
    }
    public static final class StressBlock extends KineticBlock {
        private final boolean source;
        private StressBlock(Properties properties,boolean source) { super(properties); this.source=source; }
        @Override public Direction.Axis getRotationAxis(BlockState state) { return Direction.Axis.Y; }
        @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction face) {
            return face.getAxis()==Direction.Axis.Y;
        }
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state) {
            return source?new Source(pos,state):new KineticBlockEntity(LOAD_TYPE,pos,state);
        }
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type) {
            return null;
        }
    }
    public static final class Source extends GeneratingKineticBlockEntity {
        private Source(BlockPos pos,BlockState state) { super(SOURCE_TYPE,pos,state); }
        @Override public float getGeneratedSpeed() { return 16; }
        @Override public void initialize() { super.initialize(); updateGeneratedRotation(); }
    }
}
