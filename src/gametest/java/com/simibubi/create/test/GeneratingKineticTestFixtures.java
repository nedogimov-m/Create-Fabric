package com.simibubi.create.test;

import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.storage.*;

/** Actual registered addon generator; no native hard-coded block or energy side channel. */
public final class GeneratingKineticTestFixtures implements ModInitializer {
    public static GeneratorBlock BLOCK;
    public static BlockEntityType<Generator> TYPE;
    @Override public void onInitialize(){
        var id=Identifier.fromNamespaceAndPath("create_test","fractional_generator");
        var key=ResourceKey.create(Registries.BLOCK,id);
        BLOCK=Registry.register(BuiltInRegistries.BLOCK,key,new GeneratorBlock(BlockBehaviour.Properties.of().setId(key)));
        TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,id,FabricBlockEntityTypeBuilder.create(Generator::new,BLOCK).build());
    }
    public static final class GeneratorBlock extends KineticBlock {
        GeneratorBlock(Properties properties){super(properties);}
        @Override public Direction.Axis getRotationAxis(BlockState state){return Direction.Axis.Y;}
        @Override public boolean hasShaftTowards(LevelReader level,BlockPos pos,BlockState state,Direction face){return face.getAxis()==Direction.Axis.Y;}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new Generator(pos,state);}
        @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return null;}
    }
    public static final class Generator extends GeneratingKineticBlockEntity {
        private float requested=2.5f,capacity=40;
        public int initialized, speedCallbacks;
        public float previousEffectiveSpeed;
        public BlockPos customTarget;
        public float customRatio;
        @Override public void onSpeedChanged(float previous) { super.onSpeedChanged(previous); speedCallbacks++; previousEffectiveSpeed=previous; }
        @Override public java.util.List<BlockPos> addPropagationLocations(com.simibubi.create.content.kinetics.base.IRotate block,BlockState state,java.util.List<BlockPos> neighbours) {
            var result=super.addPropagationLocations(block,state,neighbours);
            if(customTarget!=null) result.add(customTarget);
            return result;
        }
        @Override public boolean isCustomConnection(com.simibubi.create.content.kinetics.base.KineticBlockEntity other,BlockState state,BlockState otherState) {
            return other.getBlockPos().equals(customTarget);
        }
        @Override public float propagateRotationTo(com.simibubi.create.content.kinetics.base.KineticBlockEntity target,BlockState from,BlockState to,BlockPos diff,boolean axes,boolean cogs) {
            return target.getBlockPos().equals(customTarget)?customRatio:0;
        }
        Generator(BlockPos pos,BlockState state){super(TYPE,pos,state);}
        @Override public float getGeneratedSpeed(){return requested;}
        @Override public float calculateAddedStressCapacity(){return lastCapacityProvided=capacity;}
        @Override public float calculateStressApplied(){return lastStressApplied=1;}
        @Override public void initialize(){super.initialize();initialized++;updateGeneratedRotation();}
        public void generate(float speed){requested=speed;updateGeneratedRotation();}
        public void notifyCapacity(float capacity){notifyStressCapacityChange(capacity);}
        @Override protected void write(ValueOutput out,boolean clientPacket){super.write(out,clientPacket);out.putFloat("SourceSpeed",requested);out.putFloat("SourceCapacity",capacity);}
        @Override protected void read(ValueInput in,boolean clientPacket){requested=in.getFloatOr("SourceSpeed",2.5f);capacity=in.getFloatOr("SourceCapacity",40);super.read(in,clientPacket);}
    }
}
