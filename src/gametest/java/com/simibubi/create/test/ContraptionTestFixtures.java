package com.simibubi.create.test;

import com.simibubi.create.api.contraption.transformable.*;
import com.simibubi.create.content.contraptions.StructureTransform;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class ContraptionTestFixtures implements ModInitializer {
    public static TransformBlock BLOCK;
    public static Block ENTITY_BLOCK,ACTOR,ALWAYS_TICK_ACTOR;
    public static BlockEntityType<TransformBE> TYPE;
    private static ResourceKey<Block> key(String path){return ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test",path));}
    @Override public void onInitialize(){
        var block=key("transform_extension");BLOCK=Registry.register(BuiltInRegistries.BLOCK,block,new TransformBlock(BlockBehaviour.Properties.of().setId(block)));
        var entity=key("transform_entity_extension");ENTITY_BLOCK=Registry.register(BuiltInRegistries.BLOCK,entity,new EntityTestBlock(BlockBehaviour.Properties.of().setId(entity)));
        var actor=key("movement_actor");ACTOR=Registry.register(BuiltInRegistries.BLOCK,actor,new Block(BlockBehaviour.Properties.of().setId(actor)));
        var always=key("always_ticking_actor");ALWAYS_TICK_ACTOR=Registry.register(BuiltInRegistries.BLOCK,always,new Block(BlockBehaviour.Properties.of().setId(always)));
        com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.register(ACTOR,new RecordingActor(false));
        com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.register(ALWAYS_TICK_ACTOR,new RecordingActor(true));
        TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,Identifier.fromNamespaceAndPath("create_test","transform_entity_extension"),FabricBlockEntityTypeBuilder.create(TransformBE::new,ENTITY_BLOCK).build());
    }
    public static final class RecordingActor implements com.simibubi.create.api.behaviour.movement.MovementBehaviour {
        private final boolean always;RecordingActor(boolean always){this.always=always;}
        private void count(com.simibubi.create.content.contraptions.behaviour.MovementContext ctx,String key){ctx.data.putInt(key,ctx.data.getIntOr(key,0)+1);}
        @Override public void startMoving(com.simibubi.create.content.contraptions.behaviour.MovementContext ctx){count(ctx,"Starts");}
        @Override public void tick(com.simibubi.create.content.contraptions.behaviour.MovementContext ctx){count(ctx,"Ticks");if(ctx.data.getBooleanOr("RemoveWhileTicking",false)){ctx.data.putBoolean("InsideTick",true);ctx.contraption.entity.discard();ctx.data.putBoolean("InsideTick",false);}}
        @Override public void visitNewPosition(com.simibubi.create.content.contraptions.behaviour.MovementContext ctx,BlockPos pos){count(ctx,"Visits");ctx.data.store("Visited",BlockPos.CODEC,pos);}
        @Override public void onSpeedChanged(com.simibubi.create.content.contraptions.behaviour.MovementContext ctx,net.minecraft.world.phys.Vec3 previous,net.minecraft.world.phys.Vec3 motion){count(ctx,"SpeedChanges");}
        @Override public void stopMoving(com.simibubi.create.content.contraptions.behaviour.MovementContext ctx){count(ctx,"Stops");ctx.data.putBoolean("StoppedInsideTick",ctx.data.getBooleanOr("InsideTick",false));}
        @Override public void writeExtraData(com.simibubi.create.content.contraptions.behaviour.MovementContext ctx){ctx.data.putBoolean("ExtraWritten",true);}
        @Override public boolean mustTickWhileDisabled(){return always;}
    }
    public static final class TransformBlock extends Block implements TransformableBlock {
        public int calls;
        public TransformBlock(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(BlockStateProperties.FACING,Direction.NORTH));}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.FACING);}
        @Override public BlockState transform(BlockState state,StructureTransform transform){calls++;return state.setValue(BlockStateProperties.FACING,Direction.EAST);}
    }
    private static final class EntityTestBlock extends Block implements EntityBlock {
        EntityTestBlock(Properties properties){super(properties);}
        @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TransformBE(pos,state);}
    }
    public static final class TransformBE extends BlockEntity implements TransformableBlockEntity {
        public int calls;
        public TransformBE(BlockPos pos,BlockState state){super(TYPE,pos,state);}
        @Override public void transform(BlockEntity be,StructureTransform transform){calls++;}
    }
}
