package com.simibubi.create.content.equipment.toolbox;

import com.simibubi.create.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public final class ToolboxDyeingRecipe extends CustomRecipe {
    public static final RecipeSerializer<ToolboxDyeingRecipe> SERIALIZER=Registry.register(BuiltInRegistries.RECIPE_SERIALIZER,Create.asResource("toolbox_dyeing"),new RecipeSerializer<>(MapCodec.unit(ToolboxDyeingRecipe::new),StreamCodec.unit(new ToolboxDyeingRecipe())));
    @Override public boolean matches(CraftingInput input,Level level){int boxes=0,dyes=0;for(int i=0;i<input.size();i++){ItemStack stack=input.getItem(i);if(stack.isEmpty())continue;if(Block.byItem(stack.getItem()) instanceof ToolboxBlock)boxes++;else if(ToolboxBlock.dyeColor(stack)!=null)dyes++;else return false;if(boxes>1||dyes>1)return false;}return boxes==1&&dyes==1;}
    @Override public ItemStack assemble(CraftingInput input){ItemStack source=ItemStack.EMPTY;DyeColor color=null;for(int i=0;i<input.size();i++){ItemStack stack=input.getItem(i);if(Block.byItem(stack.getItem()) instanceof ToolboxBlock)source=stack;else if(!stack.isEmpty())color=ToolboxBlock.dyeColor(stack);}if(source.isEmpty()||color==null)return ItemStack.EMPTY;ItemStack result=new ItemStack(AllBlocks.TOOLBOXES.get(color));result.applyComponents(source.getComponentsPatch());ToolboxInventory contents=source.get(AllDataComponents.TOOLBOX_INVENTORY);if(contents!=null)result.set(AllDataComponents.TOOLBOX_INVENTORY,contents.frozenCopy());return result;}
    @Override public RecipeSerializer<ToolboxDyeingRecipe> getSerializer(){return SERIALIZER;}
    public static RecipeSerializer<ToolboxDyeingRecipe> register(){return SERIALIZER;}
}
