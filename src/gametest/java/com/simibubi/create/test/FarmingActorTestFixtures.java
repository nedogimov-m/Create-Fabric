package com.simibubi.create.test;

import com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;

public class FarmingActorTestFixtures implements ModInitializer {
    public static TestCrop CROP,PROTECTED_CROP;
    public static Block SPECIAL;
    private static ResourceKey<Block> key(String name){return ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test",name));}
    private static TestCrop crop(String name){var key=key(name);var block=Registry.register(BuiltInRegistries.BLOCK,key,new TestCrop(BlockBehaviour.Properties.of().setId(key).noCollision()));var item=ResourceKey.create(Registries.ITEM,key.identifier());Registry.register(BuiltInRegistries.ITEM,item,new BlockItem(block,new Item.Properties().setId(item)));return block;}
    @Override public void onInitialize(){
        CROP=crop("actor_crop");PROTECTED_CROP=crop("protected_actor_crop");var key=key("special_actor_plant");SPECIAL=Registry.register(BuiltInRegistries.BLOCK,key,new Block(BlockBehaviour.Properties.of().setId(key).noCollision()){
            @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.POWERED);}
            @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){return List.of(new ItemStack(Items.DIAMOND));}
        });HarvesterMovementBehaviour.SPECIAL_PLANTS.register(SPECIAL,(level,pos,state)->state.getValue(BlockStateProperties.POWERED));
    }
    public static class TestCrop extends CropBlock {
        public TestCrop(Properties properties){super(properties);registerDefaultState(defaultBlockState().setValue(BlockStateProperties.PERSISTENT,false));}
        @Override protected IntegerProperty getAgeProperty(){return BlockStateProperties.AGE_3;}
        @Override public int getMaxAge(){return 3;}
        @Override protected boolean isRandomlyTicking(BlockState state){return false;}
        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.AGE_3,BlockStateProperties.PERSISTENT);}
        @Override protected List<ItemStack> getDrops(BlockState state,LootParams.Builder builder){return List.of(new ItemStack(this,3),new ItemStack(Items.WHEAT,2));}
    }
}
