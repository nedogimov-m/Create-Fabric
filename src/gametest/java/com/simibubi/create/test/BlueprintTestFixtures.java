package com.simibubi.create.test;

import com.simibubi.create.content.equipment.blueprint.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;

/** A genuine registered recipe result exercises the actual native Item.onCraftedBy callback. */
public final class BlueprintTestFixtures implements ModInitializer {
 public static Item OUTPUT,NATIVE_MILK;
 public static RecipeSerializer<NativeRemainingRecipe> REMAINING_SERIALIZER;
 public enum Mode{NORMAL,ERROR,EMPTY,TRANSFER,RECURSE,SAVE_TRANSFER,SAVE_ATTEMPTED,REMAINDER_ERROR,REPLACE_ON_CRAFTED,REPLACE_ON_CRAFTED_ERROR,REPLACE_ON_REMAINDER}
 public static final class NativeRefusal extends Error{public NativeRefusal(){super("Actual native blueprint crafted callback");}}
 public static final class Observation{public ItemStack result;public int calls,remainders,recursiveCrafts=-1;public net.minecraft.nbt.CompoundTag saved;}
 public record Scope(ServerPlayer player,BlueprintEntity.BlueprintSection section,Mode mode,NativeRefusal error,Observation observation){}
 private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
 public static void with(Scope scope,Runnable action){if(ACTIVE.get()!=null)throw new IllegalStateException("Nested blueprint fixture");ACTIVE.set(scope);try{action.run();}finally{ACTIVE.remove();}}
 public static void decorate(ItemStack stack){stack.set(DataComponents.CUSTOM_NAME,Component.literal("Native complete crafted cargo"));stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.EMERALD,7))));}
 @Override public void onInitialize(){var key=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("create_test","native_blueprint_output"));OUTPUT=Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().setId(key)){
  @Override public void onCraftedBy(ItemStack stack,Player player){var scope=Objects.requireNonNull(ACTIVE.get(),"Actual native crafting fixture must be armed");if(player!=scope.player)throw new IllegalStateException("Wrong actual blueprint crafting player");scope.observation.result=stack;scope.observation.calls++;decorate(stack);if(scope.mode==Mode.REPLACE_ON_CRAFTED||scope.mode==Mode.REPLACE_ON_CRAFTED_ERROR){scope.observation.saved=BlueprintDeliveryTestFixtures.capture(player.level());BlueprintDeliveryTestFixtures.installDecoded((net.minecraft.server.level.ServerLevel)player.level(),scope.observation.saved);if(scope.mode==Mode.REPLACE_ON_CRAFTED_ERROR)throw scope.error;return;}if(scope.mode==Mode.EMPTY){stack.shrink(stack.getCount());return;}if(scope.mode==Mode.TRANSFER||scope.mode==Mode.SAVE_TRANSFER){player.getInventory().setItem(20,stack);if(scope.mode==Mode.SAVE_TRANSFER)scope.observation.saved=com.simibubi.create.content.equipment.blueprint.BlueprintDeliveryTestFixtures.capture(player.level());return;}if(scope.mode==Mode.SAVE_ATTEMPTED)scope.observation.saved=com.simibubi.create.content.equipment.blueprint.BlueprintDeliveryTestFixtures.capture(player.level());if(scope.mode==Mode.RECURSE)scope.observation.recursiveCrafts=BlueprintCrafting.craft(player,scope.section,false);if(scope.mode==Mode.ERROR)throw scope.error;}
 });var input=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("create_test","native_blueprint_milk"));NATIVE_MILK=Registry.register(BuiltInRegistries.ITEM,input,new Item(new Item.Properties().setId(input).stacksTo(1).craftRemainder(Items.BUCKET)));
 REMAINING_SERIALIZER=Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,Identifier.fromNamespaceAndPath("create_test","native_blueprint_remaining_order"),new RecipeSerializer<>(com.mojang.serialization.MapCodec.unit(new NativeRemainingRecipe()),net.minecraft.network.codec.StreamCodec.of((buf,recipe)->{},buf->new NativeRemainingRecipe())));}
 public static final class NativeRemainingRecipe extends CustomRecipe {
  @Override public boolean matches(CraftingInput input,Level level){return input.size()==1&&input.getItem(0).is(NATIVE_MILK);}
  @Override public ItemStack assemble(CraftingInput input){return new ItemStack(OUTPUT,4);}
  @Override public RecipeSerializer<NativeRemainingRecipe> getSerializer(){return REMAINING_SERIALIZER;}
  @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input){var scope=ACTIVE.get();if(scope!=null){scope.observation.remainders++;if(scope.observation.calls==0)throw new AssertionError("Actual native remainder callback ran before source crafted hook");if(scope.mode==Mode.REMAINDER_ERROR)throw scope.error;if(scope.mode==Mode.REPLACE_ON_REMAINDER){scope.observation.saved=BlueprintDeliveryTestFixtures.capture(scope.player.level());BlueprintDeliveryTestFixtures.installDecoded(scope.player.level(),scope.observation.saved);}}return CraftingRecipe.defaultCraftingReminder(input);}
 }
}
