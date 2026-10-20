package com.simibubi.create.test;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;

/** Real registered native placement callbacks; no replacement world or synthetic success hooks. */
public final class SchematicPublicationTestFixtures implements ModInitializer {
    public static Block THROW_PLACED,REPLACE_PLACED,THROW_ON_PLACE,RETIRE_CANNON,THROW_NEIGHBOUR,FILTER_READER;
    public static net.minecraft.world.level.block.entity.BlockEntityType<FilterReader> FILTER_TYPE;
    private static final Map<Target,BlockPos> RETIRE=new HashMap<>();
    public static void retireCannon(Level level,BlockPos target,BlockPos cannon){RETIRE.put(new Target(level,target.immutable()),cannon.immutable());}
    private record Target(Level level,BlockPos pos){}
    private static final Set<Target> ARMED=new HashSet<>();
    public static void arm(Level level,BlockPos pos){ARMED.add(new Target(level,pos.immutable()));}
    public static void clear(){ARMED.clear();RETIRE.clear();}
    private static boolean take(Level level,BlockPos pos){return ARMED.remove(new Target(level,pos));}
    private static Block register(String name,int kind){
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test",name));
        var block=Registry.register(BuiltInRegistries.BLOCK,key,new Block(BlockBehaviour.Properties.of().setId(key)){
            @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){if(kind==3){var owner=RETIRE.remove(new Target(level,pos));if(owner!=null)level.destroyBlock(owner,false);}if(kind<2&&take(level,pos)){if(kind==1)level.setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState());throw new IllegalStateException("Actual native source post-placement callback refused");}}
            @Override protected void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moved){super.onPlace(state,level,pos,old,moved);if(kind==2&&take(level,pos))throw new IllegalStateException("Actual native source onPlace callback failed after accepting its state");}
            @Override protected void neighborChanged(BlockState state,Level level,BlockPos pos,Block source,net.minecraft.world.level.redstone.Orientation orientation,boolean moved){if(kind==4&&take(level,pos))throw new IllegalStateException("Actual native source neighbour callback refused after belt publication");}
        });
        var item=ResourceKey.create(Registries.ITEM,key.identifier());Registry.register(BuiltInRegistries.ITEM,item,new BlockItem(block,new Item.Properties().setId(item)));return block;
    }
    @Override public void onInitialize(){THROW_PLACED=register("schematic_throw_placed",0);REPLACE_PLACED=register("schematic_replace_placed",1);THROW_ON_PLACE=register("schematic_throw_on_place",2);RETIRE_CANNON=register("schematic_retire_cannon",3);THROW_NEIGHBOUR=register("schematic_throw_neighbour",4);
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test","schematic_partial_filter_reader"));FILTER_READER=Registry.register(BuiltInRegistries.BLOCK,key,new FilterReaderBlock(BlockBehaviour.Properties.of().setId(key)));var item=ResourceKey.create(Registries.ITEM,key.identifier());Registry.register(BuiltInRegistries.ITEM,item,new BlockItem(FILTER_READER,new Item.Properties().setId(item)));FILTER_TYPE=Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,key.identifier(),net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(FilterReader::new,FILTER_READER).build());
    }
    private static final class FilterReaderBlock extends Block implements net.minecraft.world.level.block.EntityBlock {
        FilterReaderBlock(Properties properties){super(properties);}
        @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new FilterReader(pos,state);}
    }
    public static final class FilterReader extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements com.simibubi.create.foundation.blockEntity.IMergeableBE {
        public com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour filtering;
        public FilterReader(BlockPos pos,BlockState state){super(FILTER_TYPE,pos,state);}
        @Override public void addBehaviours(List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours){behaviours.add(filtering=new com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour(this));}
        @Override protected void read(net.minecraft.world.level.storage.ValueInput input,boolean clientPacket){super.read(input,clientPacket);if(level!=null&&take(level,worldPosition))throw new IllegalStateException("Actual native reader refuses after installing its original filter");}
        @Override public void accept(net.minecraft.world.level.block.entity.BlockEntity other){if(!(other instanceof FilterReader source))throw new IllegalArgumentException("Actual native merge requires its original source type");filtering.setFilter(source.filtering.getFilter());if(level!=null&&take(level,worldPosition))throw new IllegalStateException("Actual native merge refused after installing source filter");}
    }
}
