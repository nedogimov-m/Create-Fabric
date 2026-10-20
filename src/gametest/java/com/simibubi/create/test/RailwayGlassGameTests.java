package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.palettes.ConnectedGlassBlock;
import com.simibubi.create.foundation.block.connected.OmnidirectionalCT;
import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Actual native source blocks, collision/culling, recipes/loot, manual application and world CT. UNRUN. */
public class RailwayGlassGameTests {
    private static final BlockPos P=new BlockPos(4,4,4);
    private static net.minecraft.world.entity.player.Player player(GameTestHelper h){var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,1));return player;}
    private static BlockHitResult hit(GameTestHelper h){return new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.SOUTH,h.absolutePos(P),false);}
    @GameTest public void actualSourceBlockClassesAndPhysicalGlassProperties(GameTestHelper h){
        h.assertTrue(AllBlocks.RAILWAY_CASING instanceof CasingBlock&&AllBlocks.FRAMED_GLASS instanceof ConnectedGlassBlock,"Concrete source block types registered");h.setBlock(P,AllBlocks.FRAMED_GLASS);var state=h.getBlockState(P);var pos=h.absolutePos(P);
        h.assertTrue(Block.isShapeFullBlock(state.getCollisionShape(h.getLevel(),pos))&&!state.canOcclude()&&!state.isRedstoneConductor(h.getLevel(),pos)&&!state.isSuffocating(h.getLevel(),pos),"Connected glass retains full collision but not opaque/conductor/suffocation behavior");
        h.assertTrue(state.propagatesSkylightDown()&&state.getVisualShape(h.getLevel(),pos,net.minecraft.world.phys.shapes.CollisionContext.empty()).isEmpty(),"Actual transparent native visual/skylight rules");h.succeed();
    }
    @GameTest public void internalGlassFacesCullOnEveryNativeSideWithoutCullingStoneOrVanillaGlass(GameTestHelper h){var glass=AllBlocks.FRAMED_GLASS.defaultBlockState();for(var side:Direction.values())h.assertTrue(glass.skipRendering(glass,side)&&!glass.skipRendering(Blocks.STONE.defaultBlockState(),side)&&!glass.skipRendering(Blocks.GLASS.defaultBlockState(),side),"Source connected-family internal face rule on "+side);h.succeed();}
    @GameTest public void sourceSilkTouchLootDiffersFromOrdinaryGlassBreak(GameTestHelper h){
        h.setBlock(P,AllBlocks.FRAMED_GLASS);var normal=new ItemStack(Items.IRON_PICKAXE);var silk=normal.copy();silk.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);
        h.assertTrue(Block.getDrops(h.getBlockState(P),h.getLevel(),h.absolutePos(P),null,null,normal).isEmpty(),"Ordinary connected-glass break drops no block");var drops=Block.getDrops(h.getBlockState(P),h.getLevel(),h.absolutePos(P),null,null,silk);h.assertTrue(drops.size()==1&&drops.getFirst().is(AllBlocks.FRAMED_GLASS.asItem())&&drops.getFirst().getCount()==1,"Native silk-touch condition drops exact original glass item");h.succeed();
    }
    @GameTest public void casingWrenchRejectsRotationAndActualSneakPickupRefundsOne(GameTestHelper h){
        h.setBlock(P,AllBlocks.RAILWAY_CASING);var player=player(h);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.WRENCH));var context=new UseOnContext(player,InteractionHand.MAIN_HAND,hit(h));h.assertTrue(AllItems.WRENCH.useOn(context)==net.minecraft.world.InteractionResult.FAIL&&h.getBlockState(P).is(AllBlocks.RAILWAY_CASING),"Source casing refuses ordinary wrench rotation");player.setShiftKeyDown(true);h.assertTrue(AllItems.WRENCH.useOn(context).consumesAction()&&h.getBlockState(P).isAir()&&player.getInventory().contains(new ItemStack(AllBlocks.RAILWAY_CASING)),"Actual native sneak-wrench removes casing and refunds it to player inventory");h.succeed();
    }
    @GameTest public void realManualApplicationConsumesOneSturdySheetAndProducesRailwayCasing(GameTestHelper h){
        h.setBlock(P,AllBlocks.BRASS_CASING);var player=player(h);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.STURDY_SHEET,2));var result=UseBlockCallback.EVENT.invoker().interact(player,h.getLevel(),InteractionHand.MAIN_HAND,hit(h));h.assertTrue(result.consumesAction()&&h.getBlockState(P).is(AllBlocks.RAILWAY_CASING)&&player.getMainHandItem().getCount()==1,"Existing native manual application recipe uses real source brass casing + obsidian plate tag");h.succeed();
    }
    @GameTest public void sourceCasingAndColorlessGlassTagsHaveActualRegisteredItems(GameTestHelper h){
        h.assertTrue(AllBlocks.RAILWAY_CASING.defaultBlockState().is(TagKey.create(Registries.BLOCK,Create.asResource("casing")))&&new ItemStack(AllBlocks.RAILWAY_CASING).is(TagKey.create(Registries.ITEM,Create.asResource("casing"))),"Source casing block and item tags");h.assertTrue(AllBlocks.FRAMED_GLASS.defaultBlockState().is(BlockTags.IMPERMEABLE)&&new ItemStack(AllBlocks.FRAMED_GLASS).is(TagKey.create(Registries.ITEM,net.minecraft.resources.Identifier.parse("c:glass_blocks/colorless"))),"Source glass colorless item tag and impermeable block tag");h.succeed();
    }
    @GameTest public void sourceRailwayCasingLootReturnsTheActualCasing(GameTestHelper h){h.setBlock(P,AllBlocks.RAILWAY_CASING);var drops=Block.getDrops(h.getBlockState(P),h.getLevel(),h.absolutePos(P),null,null,new ItemStack(Items.IRON_PICKAXE));h.assertTrue(drops.size()==1&&drops.getFirst().is(AllBlocks.RAILWAY_CASING.asItem())&&drops.getFirst().getCount()==1,"Native casing block loot returns exact source item");h.succeed();}
    @GameTest public void realNativeStonecuttingProducesFramedGlassFromColorlessGlass(GameTestHelper h){var input=new SingleRecipeInput(new ItemStack(Items.GLASS));var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.STONECUTTING,input,h.getLevel()).orElseThrow();var result=recipe.value().assemble(input);h.assertTrue(result.is(AllBlocks.FRAMED_GLASS.asItem())&&result.getCount()==1,"Actual loaded native stonecutting recipe produces one framed glass");h.succeed();}
    @GameTest public void realNativeSmeltingReturnsOneGlassInFiftyTicks(GameTestHelper h){var input=new SingleRecipeInput(new ItemStack(AllBlocks.FRAMED_GLASS));var recipe=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.SMELTING,input,h.getLevel()).orElseThrow();var result=recipe.value().assemble(input);h.assertTrue(result.is(Items.GLASS)&&result.getCount()==1&&recipe.value().cookingTime()==50&&recipe.value().experience()==0,"Source glass recovery recipe preserves exact input/output/time/XP");h.succeed();}
    private static void doorRecipe(GameTestHelper h,String name,Block ingredient,Block result,Block wrong){
        var recipe=h.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,Create.asResource("crafting/kinetics/"+name))).orElseThrow();var input=CraftingInput.of(2,1,List.of(new ItemStack(Items.OAK_DOOR),new ItemStack(ingredient)));var matched=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow();var output=matched.value().assemble(input);
        h.assertTrue(output.is(result.asItem())&&output.getCount()==1&&recipe.value()instanceof CraftingRecipe,"Actual native door recipe uses source ingredient/yield");var source=(CraftingRecipe)recipe.value();h.assertFalse(source.matches(CraftingInput.of(2,1,List.of(new ItemStack(Items.OAK_DOOR),new ItemStack(wrong))),h.getLevel()),"Missing original ingredient cannot be substituted with another block");h.succeed();
    }
    @GameTest public void sourceTrainDoorRecipeActuallyRequiresRailwayCasing(GameTestHelper h){doorRecipe(h,"train_door",AllBlocks.RAILWAY_CASING,AllBlocks.TRAIN_DOOR,AllBlocks.BRASS_CASING);}
    @GameTest public void sourceFramedDoorRecipeActuallyRequiresFramedGlass(GameTestHelper h){doorRecipe(h,"framed_glass_door",AllBlocks.FRAMED_GLASS,AllBlocks.FRAMED_GLASS_DOOR,Blocks.GLASS);}
    private static void orientation(GameTestHelper h,Direction face){h.setBlock(P,AllBlocks.FRAMED_GLASS);var basis=OmnidirectionalCT.basis(face);h.setBlock(P.relative(basis.up()),AllBlocks.FRAMED_GLASS);var context=OmnidirectionalCT.build(h.getLevel(),h.absolutePos(P),h.getBlockState(P),face);h.assertTrue(context.up()&&!context.down()&&!context.left()&&!context.right()&&context.textureIndex()==1,"Pinned source up neighbor maps native "+face+" face to exact tile1");h.succeed();}
    @GameTest public void sourceContextNorthFace(GameTestHelper h){orientation(h,Direction.NORTH);}
    @GameTest public void sourceContextSouthFace(GameTestHelper h){orientation(h,Direction.SOUTH);}
    @GameTest public void sourceContextEastFace(GameTestHelper h){orientation(h,Direction.EAST);}
    @GameTest public void sourceContextWestFace(GameTestHelper h){orientation(h,Direction.WEST);}
    @GameTest public void sourceContextTopFace(GameTestHelper h){orientation(h,Direction.UP);}
    @GameTest public void sourceContextBottomFace(GameTestHelper h){orientation(h,Direction.DOWN);}
    @GameTest public void sourceDiagonalCornerChangesConnectedTileWithoutRemovingAnyEdge(GameTestHelper h){
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)h.setBlock(P.offset(x,y,0),AllBlocks.FRAMED_GLASS);var full=OmnidirectionalCT.build(h.getLevel(),h.absolutePos(P),h.getBlockState(P),Direction.SOUTH);h.assertValueEqual(full.textureIndex(),54,"Pinned source fully filled eight-neighbor tile54");h.setBlock(P.west().above(),Blocks.AIR);var concave=OmnidirectionalCT.build(h.getLevel(),h.absolutePos(P),h.getBlockState(P),Direction.SOUTH);h.assertTrue(concave.up()&&concave.down()&&concave.left()&&concave.right()&&!concave.topLeft()&&concave.textureIndex()==52,"Only top-left diagonal loss creates exact source concave tile52");h.succeed();
    }
    @GameTest public void fullSameMaterialFrontBlockerPreventsFalseSeamWhileDifferentStoneDoesNot(GameTestHelper h){
        h.setBlock(P,AllBlocks.RAILWAY_CASING);h.setBlock(P.east(),AllBlocks.RAILWAY_CASING);var pos=h.absolutePos(P);h.assertTrue(OmnidirectionalCT.connects(h.getLevel(),pos,h.getBlockState(P),h.absolutePos(P.east()),Direction.SOUTH),"Actual casing neighbors connect in same face plane");h.setBlock(P.east().south(),AllBlocks.RAILWAY_CASING);h.assertFalse(OmnidirectionalCT.connects(h.getLevel(),pos,h.getBlockState(P),h.absolutePos(P.east()),Direction.SOUTH),"Pinned source full same-material forward blocker breaks seam");h.setBlock(P.east().south(),Blocks.STONE);h.assertTrue(OmnidirectionalCT.connects(h.getLevel(),pos,h.getBlockState(P),h.absolutePos(P.east()),Direction.SOUTH),"Source recursive connection blocking does not treat every arbitrary stone as connected casing");h.succeed();
    }
    @GameTest public void unknownNativeChunkContextNeverLoadsOrGeneratesTerrain(GameTestHelper h){var far=new BlockPos(2600000,80,2600000);h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Unknown fixture is genuinely unloaded");for(var face:Direction.values())h.assertValueEqual(OmnidirectionalCT.build(h.getLevel(),far,AllBlocks.FRAMED_GLASS.defaultBlockState(),face).textureIndex(),0,"Unknown neighbors do not invent connections");h.assertTrue(h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"Source context lookup cannot create a chunk loading ticket");h.succeed();}
}
