package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.decoration.girder.GirderBlock;
import com.simibubi.create.content.decoration.placard.*;
import com.simibubi.create.content.redstone.nixieTube.*;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;

/** Real block/item interactions, native text/data, exact sample matching and actual world redstone. STAGED, UNRUN. */
public class NixiePlacardGameTests {
    private static final BlockPos P=new BlockPos(4,4,4);
    private static Player user(GameTestHelper h,ItemStack item){var user=h.makeMockPlayer(GameType.SURVIVAL);user.setPos(Vec3.atCenterOf(h.absolutePos(P)).add(0,0,1));user.setItemInHand(InteractionHand.MAIN_HAND,item);return user;}
    private static BlockHitResult hit(GameTestHelper h){return new BlockHitResult(Vec3.atCenterOf(h.absolutePos(P)),Direction.UP,h.absolutePos(P),false);}
    private static InteractionResult use(GameTestHelper h,Player user){return h.getBlockState(P).useItemOn(user.getMainHandItem(),h.getLevel(),user,InteractionHand.MAIN_HAND,hit(h));}
    private static void tube(GameTestHelper h){h.setBlock(P,AllBlocks.ORANGE_NIXIE_TUBE.defaultBlockState().setValue(NixieTubeBlock.FACING,Direction.EAST));}
    private static NixieTubeBlockEntity tube(GameTestHelper h,BlockPos pos){return h.getBlockEntity(pos,NixieTubeBlockEntity.class);}
    private static void placard(GameTestHelper h){h.setBlock(P.below(),Blocks.STONE);h.setBlock(P,AllBlocks.PLACARD.defaultBlockState().setValue(PlacardBlock.FACE,AttachFace.FLOOR));}
    private static PlacardBlockEntity placard(GameTestHelper h,BlockPos pos){return h.getBlockEntity(pos,PlacardBlockEntity.class);}
    private static ItemStack named(Item item,int count,String name){var stack=new ItemStack(item,count);stack.set(DataComponents.CUSTOM_NAME,Component.literal(name));return stack;}
    private static Player namedRow(GameTestHelper h,String text){var user=user(h,named(Items.NAME_TAG,1,text));h.assertTrue(use(h,user).consumesAction(),"Actual source name-tag interaction");return user;}
    private static void chars(GameTestHelper h,BlockPos pos,String first,String second){var actual=tube(h,pos).getDisplayedStrings();h.assertTrue(actual.first().equals(first)&&actual.second().equals(second),"Actual Nixie glyphs at "+pos+": "+actual);}
    private static void sample(GameTestHelper h,ItemStack item){var user=user(h,item);h.assertTrue(use(h,user).consumesAction(),"Actual native sample insertion");}
    private static int inventory(Player player,Item item){int count=0;for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(stack.is(item))count+=stack.getCount();}return count;}

    @GameTest public void actualSixteenColorBlocksShareRealTypeButOnlyOrangeHasAnItem(GameTestHelper h){
        h.assertTrue(AllBlocks.NIXIE_TUBES.size()==16,"All real vanilla dye colors registered");for(var color:DyeColor.values()){var block=AllBlocks.NIXIE_TUBES.get(color);h.setBlock(P,block);h.assertTrue(tube(h,P).getType()==AllBlockEntityTypes.NIXIE_TUBE&&block.getColor()==color&&(block.asItem()!=Items.AIR)==(color==DyeColor.ORANGE),"Source colored block/type/item distinction for "+color);}h.succeed();
    }
    @GameTest public void initialNativeDigitsAreZeroPaddedAndFullTextRemainsNumeric(GameTestHelper h){tube(h);chars(h,P,"0","0");h.assertTrue(tube(h,P).getFullText().getString().equals("0"),"DisplayLink-facing real full text differs from two padded glyphs");h.succeed();}
    @GameTest(maxTicks=20)public void actualBackRedstoneInputChangesRealDisplayAndClearReturnsToZero(GameTestHelper h){tube(h);h.setBlock(P.north(),Blocks.REDSTONE_BLOCK);h.runAfterDelay(5,()->{chars(h,P,"1","5");h.assertValueEqual(tube(h,P).getRedstoneStrength(),15,"Real native neighbor signal");h.setBlock(P.north(),Blocks.AIR);});h.runAfterDelay(10,()->{chars(h,P,"0","0");h.succeed();});}
    @GameTest public void actualNamedTagDistributesTwoSourceCharactersPerPhysicalBlock(GameTestHelper h){
        tube(h);for(int i=1;i<3;i++)h.setBlock(P.east(i),h.getBlockState(P));var user=namedRow(h,"ABCD12");chars(h,P,"A","B");chars(h,P.east(),"C","D");chars(h,P.east(2),"1","2");h.assertTrue(user.getMainHandItem().getCount()==1&&tube(h,P.east()).getFullText().getString().equals("ABCD12"),"Original tag and complete real text retained");h.setBlock(P.west(),h.getBlockState(P));namedRow(h,"ABCD12");chars(h,P.west(),"A","B");chars(h,P,"C","D");chars(h,P.east(),"1","2");chars(h,P.east(2)," "," ");h.succeed();
    }
    @GameTest public void wallAndReversedRowsUseSourceOppositeVerticalOrders(GameTestHelper h){
        for(var face:new DoubleFaceAttachedBlock.DoubleAttachFace[]{DoubleFaceAttachedBlock.DoubleAttachFace.WALL,DoubleFaceAttachedBlock.DoubleAttachFace.WALL_REVERSED}){var state=AllBlocks.ORANGE_NIXIE_TUBE.defaultBlockState().setValue(NixieTubeBlock.FACE,face);var right=NixieTubeBlock.getRightNixieDirection(state);h.setBlock(P,state);h.setBlock(P.relative(right),state);namedRow(h,"ABCD");chars(h,P,"A","B");chars(h,P.relative(right),"C","D");h.setBlock(P.relative(right),Blocks.AIR);}h.succeed();
    }
    @GameTest public void actualDyeRecolorsTheWholeRowWithoutConsumingDyeOrLosingText(GameTestHelper h){
        tube(h);h.setBlock(P.east(),h.getBlockState(P));namedRow(h,"ABCD");var original=tube(h,P);var dye=new ItemStack(Items.DYE.pick(DyeColor.BLUE),2);var user=user(h,dye);h.assertTrue(use(h,user).consumesAction(),"Real native dye interaction");
        h.assertTrue(h.getBlockState(P).is(AllBlocks.NIXIE_TUBES.get(DyeColor.BLUE))&&h.getBlockState(P.east()).is(AllBlocks.NIXIE_TUBES.get(DyeColor.BLUE))&&dye.getCount()==2,"Source recoloring changes actual registered blocks and leaves dye count");chars(h,P,"A","B");chars(h,P.east(),"C","D");h.assertTrue(tube(h,P).getFullText().getString().equals("ABCD"),"Actual text survives native color BE replacement");h.succeed();
    }
    @GameTest(maxTicks=20)public void customTextSurvivesPhysicalSignalUntilActualEmptyHandClear(GameTestHelper h){
        tube(h);var user=namedRow(h,"HI");h.setBlock(P.north(),Blocks.REDSTONE_BLOCK);h.runAfterDelay(5,()->{chars(h,P,"H","I");user.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);h.assertTrue(h.getBlockState(P).useWithoutItem(h.getLevel(),user,hit(h)).consumesAction(),"Actual empty-hand custom clear");chars(h,P,"1","5");h.succeed();});
    }
    @GameTest public void rowEqualityIgnoresOnlyColorAndRejectsWaterOrOrientationMismatch(GameTestHelper h){
        var state=AllBlocks.ORANGE_NIXIE_TUBE.defaultBlockState();var blue=NixieTubeBlock.withColor(state,DyeColor.BLUE);h.assertTrue(NixieTubeBlock.areNixieBlocksEqual(state,blue)&&!NixieTubeBlock.areNixieBlocksEqual(state,blue.setValue(NixieTubeBlock.WATERLOGGED,true))&&!NixieTubeBlock.areNixieBlocksEqual(state,blue.setValue(NixieTubeBlock.FACING,state.getValue(NixieTubeBlock.FACING).getClockWise())),"Source row adjacency admits color only");h.succeed();
    }
    @GameTest public void actualColoredLootAndNativePickReturnOnlyTheOrangeItem(GameTestHelper h){
        for(var color:DyeColor.values()){h.setBlock(P,AllBlocks.NIXIE_TUBES.get(color));var state=h.getBlockState(P);var drops=Block.getDrops(state,h.getLevel(),h.absolutePos(P),tube(h,P));h.assertTrue(drops.size()==1&&drops.getFirst().is(AllBlocks.ORANGE_NIXIE_TUBE.asItem())&&state.getCloneItemStack(h.getLevel(),h.absolutePos(P),false).is(AllBlocks.ORANGE_NIXIE_TUBE.asItem()),"Source colored loot/pick roundtrip for "+color);}h.succeed();
    }
    @GameTest public void customLegacyJsonAndRowIndexRoundtripBeforeAWorldExists(GameTestHelper h){
        tube(h);h.setBlock(P.east(),h.getBlockState(P));namedRow(h,"DATA");var tag=tube(h,P.east()).saveWithFullMetadata(h.getLevel().registryAccess());h.assertTrue(tag.contains("RawCustomText")&&tag.contains("CustomText")&&tag.getIntOr("CustomTextIndex",0)==1,"Actual source raw/parsed/index keys");
        var copy=(NixieTubeBlockEntity)BlockEntity.loadStatic(h.absolutePos(P.east()),h.getBlockState(P.east()),tag,h.getLevel().registryAccess());h.assertTrue(copy!=null&&copy.getLevel()==null&&copy.getFullText().getString().equals("DATA")&&copy.getDisplayedStrings().first().equals("T")&&copy.getDisplayedStrings().second().equals("A"),"Registry-aware native decode has correct text before setLevel");var resaved=copy.saveWithFullMetadata(h.getLevel().registryAccess());h.assertTrue(resaved.getStringOr("RawCustomText","").equals(tag.getStringOr("RawCustomText",""))&&resaved.getStringOr("CustomText","").equals(tag.getStringOr("CustomText",""))&&resaved.getIntOr("CustomTextIndex",0)==1,"Native save before setLevel preserves source raw/resolved/index data without a world fallback");h.succeed();
    }
    @GameTest public void malformedLegacyComponentFallsBackToSavedRedstoneInsteadOfThrowing(GameTestHelper h){
        tube(h);var tag=tube(h,P).saveWithFullMetadata(h.getLevel().registryAccess());tag.putString("CustomText","{bad json");tag.putString("RawCustomText","{bad json");tag.putInt("RedstoneStrength",7);var copy=(NixieTubeBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),h.getBlockState(P),tag,h.getLevel().registryAccess());h.assertTrue(copy!=null&&copy.reactsToRedstone()&&copy.getDisplayedStrings().first().equals("0")&&copy.getDisplayedStrings().second().equals("7"),"Invalid legacy text does not crash world load or invent valid custom mode");h.succeed();
    }
    @GameTest public void sourceDynamicComponentResolvesActualLoadedBlockNbtInNativeContext(GameTestHelper h){
        tube(h);tube(h,P).updateRedstoneStrength(13);tube(h,P).displayCustomText("{\"nbt\":\"RedstoneStrength\",\"block\":\"~ ~ ~\"}",0);h.assertTrue(tube(h,P).getFullText().getString().equals("13"),"Native ResolutionContext evaluates real block data at source permission2/position");chars(h,P,"1","3");h.succeed();
    }
    @GameTest public void unloadedRowWalkCreatesNoTicketOrPartialCallback(GameTestHelper h){
        var far=new BlockPos(2600000,80,2600000);var calls=new int[1];h.assertFalse(NixieTubeBlock.walkNixies(h.getLevel(),far,(pos,index)->calls[0]++),"Unknown position is not a fabricated row");h.assertTrue(calls[0]==0&&h.getLevel().getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"No callback or terrain ticket");h.succeed();
    }
    @GameTest public void nativeNixieRecipeUsesTwoRealElectronTubesForFour(GameTestHelper h){
        var input=CraftingInput.of(2,1,List.of(new ItemStack(AllItems.ELECTRON_TUBE),new ItemStack(AllItems.ELECTRON_TUBE)));var output=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);h.assertTrue(output.is(AllBlocks.ORANGE_NIXIE_TUBE.asItem())&&output.getCount()==4,"Actual source recipe input and yield");h.succeed();
    }
    @GameTest public void actualNixieAndPlacardFacingMethodsDriveTheRealGirderBranches(GameTestHelper h){
        var beam=AllBlocks.METAL_GIRDER.defaultBlockState().setValue(GirderBlock.AXIS,Direction.Axis.X).setValue(GirderBlock.X,true);h.setBlock(P,beam);h.setBlock(P.east(),AllBlocks.ORANGE_NIXIE_TUBE.defaultBlockState().setValue(NixieTubeBlock.FACE,DoubleFaceAttachedBlock.DoubleAttachFace.WALL).setValue(NixieTubeBlock.FACING,Direction.EAST));h.assertTrue(GirderBlock.isConnected(h.getLevel(),h.absolutePos(P),h.getBlockState(P),Direction.EAST),"Real thin Nixie source-facing branch");
        h.setBlock(P.east(),AllBlocks.PLACARD.defaultBlockState().setValue(PlacardBlock.FACE,AttachFace.WALL).setValue(PlacardBlock.FACING,Direction.EAST));h.assertTrue(GirderBlock.isConnected(h.getLevel(),h.absolutePos(P),h.getBlockState(P),Direction.EAST),"Real thin Placard source-facing branch");h.succeed();
    }
    @GameTest public void nativePlacardPlacementUsesLenientGirderCollisionSupport(GameTestHelper h){
        h.setBlock(P.below(),AllBlocks.METAL_GIRDER);var stack=new ItemStack(AllBlocks.PLACARD,2);var user=user(h,stack);var support=h.absolutePos(P.below());var context=new BlockPlaceContext(new UseOnContext(user,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false)));var facing=context.getHorizontalDirection().getOpposite();h.assertTrue(((BlockItem)stack.getItem()).place(context).consumesAction()&&h.getBlockState(P).getValue(PlacardBlock.FACE)==AttachFace.FLOOR&&h.getBlockState(P).getValue(PlacardBlock.FACING)==facing&&stack.getCount()==1,"Real floor placement supports non-full girder and reverses source facing");h.succeed();
    }
    @GameTest public void realSampleInsertionMovesExactlyOneComponentBearingItem(GameTestHelper h){
        placard(h);var stack=named(Items.DIAMOND,2,"Exact sample");sample(h,stack);h.assertTrue(stack.getCount()==1&&placard(h,P).getHeldItem().getCount()==1&&ItemStack.isSameItemSameComponents(stack,placard(h,P).getHeldItem()),"One actual source item transferred, all components retained");h.succeed();
    }
    @GameTest public void changedComponentDeniesConfirmationWithoutConsumingThePresentedItem(GameTestHelper h){
        placard(h);sample(h,named(Items.DIAMOND,2,"Original"));var stack=named(Items.DIAMOND,2,"Other name");var user=user(h,stack);h.assertTrue(use(h,user).consumesAction()&&!h.getBlockState(P).getValue(PlacardBlock.POWERED)&&stack.getCount()==2&&placard(h,P).getPoweredTicks()==0,"Real component mismatch is a denial, with no paid item or fake pulse");h.succeed();
    }
    @GameTest(maxTicks=40)public void actualMatchingItemPowersARealLampForTheSourcePulseAndThenNotifiesOff(GameTestHelper h){
        placard(h);h.setBlock(P.east(),Blocks.REDSTONE_LAMP);sample(h,named(Items.DIAMOND,2,"Exact"));var stack=named(Items.DIAMOND,3,"Exact");var user=user(h,stack);h.assertTrue(use(h,user).consumesAction()&&placard(h,P).getPoweredTicks()==19&&stack.getCount()==3&&h.getBlockState(P.east()).getValue(RedstoneLampBlock.LIT),"Real source matching triggers19 remaining ticks without consuming presented item");
        h.runAfterDelay(8,()->h.assertTrue(h.getBlockState(P).getValue(PlacardBlock.POWERED)&&placard(h,P).getPoweredTicks()>0,"Source pulse actually remains active"));h.runAfterDelay(29,()->{h.assertTrue(!h.getBlockState(P).getValue(PlacardBlock.POWERED)&&placard(h,P).getPoweredTicks()==0&&!h.getBlockState(P.east()).getValue(RedstoneLampBlock.LIT),"Real BE timer completes and native lamp receives delayed off");h.succeed();});
    }
    @GameTest(maxTicks=30)public void repeatedMatchingInputCannotExtendAnAlreadyPoweredPulse(GameTestHelper h){
        placard(h);sample(h,new ItemStack(Items.APPLE,2));var user=user(h,new ItemStack(Items.APPLE,2));use(h,user);h.runAfterDelay(6,()->{int before=placard(h,P).getPoweredTicks();for(int i=0;i<5;i++)use(h,user);h.assertValueEqual(placard(h,P).getPoweredTicks(),before,"Held confirmation cannot restart source timer");});h.runAfterDelay(24,()->{h.assertFalse(h.getBlockState(P).getValue(PlacardBlock.POWERED),"Original pulse ends despite repeated real interactions");h.succeed();});
    }
    @GameTest public void actualStoredNativeListFilterConfirmsItsRealContainedSample(GameTestHelper h){
        placard(h);var filter=new ItemStack(AllItems.FILTER);filter.set(AllDataComponents.FILTER_ITEMS,ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE))));sample(h,filter);var user=user(h,new ItemStack(Items.APPLE,3));h.assertTrue(use(h,user).consumesAction()&&h.getBlockState(P).getValue(PlacardBlock.POWERED)&&user.getMainHandItem().getCount()==3,"Actual FilterItemStack executes stored allow-list instead of treating filter as a generic item");h.succeed();
    }
    @GameTest public void actualEmptyHandDoesNotDeleteOrConfirmTheHeldItem(GameTestHelper h){placard(h);sample(h,new ItemStack(Items.DIAMOND,2));var user=user(h,ItemStack.EMPTY);h.getBlockState(P).useWithoutItem(h.getLevel(),user,hit(h));h.assertTrue(placard(h,P).getHeldItem().is(Items.DIAMOND)&&placard(h,P).getPoweredTicks()==0,"Source empty hand is not a sample deletion or redstone confirmation");h.succeed();}
    @GameTest public void actualAttackReturnsTheExactOriginalItemOnlyOnce(GameTestHelper h){
        placard(h);sample(h,named(Items.DIAMOND,2,"Return exact"));var user=user(h,ItemStack.EMPTY);h.getBlockState(P).attack(h.getLevel(),h.absolutePos(P),user);h.assertTrue(inventory(user,Items.DIAMOND)==1&&placard(h,P).getHeldItem().isEmpty(),"Actual native attack returns real held item");h.getBlockState(P).attack(h.getLevel(),h.absolutePos(P),user);h.assertValueEqual(inventory(user,Items.DIAMOND),1,"Second attack cannot duplicate original sample");h.succeed();
    }
    @GameTest public void nativePlacardSaveRestoresExactItemAndPendingPulseBeforeSetLevel(GameTestHelper h){
        placard(h);sample(h,named(Items.DIAMOND,2,"Saved exact"));var user=user(h,named(Items.DIAMOND,2,"Saved exact"));use(h,user);var saved=placard(h,P).saveWithFullMetadata(h.getLevel().registryAccess());var copy=(PlacardBlockEntity)BlockEntity.loadStatic(h.absolutePos(P),h.getBlockState(P),saved,h.getLevel().registryAccess());h.assertTrue(copy!=null&&copy.getLevel()==null&&copy.getPoweredTicks()==19&&ItemStack.isSameItemSameComponents(copy.getHeldItem(),placard(h,P).getHeldItem())&&copy.getHeldItem().getCount()==1,"Native source PoweredTicks/Item are decoded without touching a world");h.succeed();
    }
    @GameTest public void nativePlacardSignalIsWeakAllSidesAndDirectOnlyTowardSupport(GameTestHelper h){
        placard(h);sample(h,new ItemStack(Items.APPLE,2));use(h,user(h,new ItemStack(Items.APPLE,2)));var state=h.getBlockState(P);for(var side:Direction.values())h.assertTrue(state.getSignal(h.getLevel(),h.absolutePos(P),side)==15&&state.getDirectSignal(h.getLevel(),h.absolutePos(P),side)==(side==Direction.UP?15:0),"Actual native source weak/direct faces "+side);h.assertValueEqual(state.getOwnSignal(h.getLevel(),h.absolutePos(P)),15,"Modern own signal preserves active source state");h.succeed();
    }
    @GameTest(maxTicks=80)public void ordinaryNativeRemovalDropsTheHeldComponentBearingItemExactlyOnce(GameTestHelper h){
        placard(h);sample(h,named(Items.DIAMOND,2,"World drop"));var pos=h.absolutePos(P);h.startSequence().thenWaitUntil(()->h.assertTrue(LoadedEntityAccess.isReady(h.getLevel(),pos),"Actual native item entity sections ready"))
                .thenExecute(()->h.getLevel().destroyBlock(pos,false)).thenExecuteAfter(2,()->{var drops=h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));var matches=drops.stream().filter(entity->entity.getItem().is(Items.DIAMOND)).toList();h.assertTrue(matches.size()==1&&matches.getFirst().getItem().getCount()==1&&matches.getFirst().getItem().getHoverName().getString().equals("World drop"),"Native BE removal conserves one exact owned sample");}).thenSucceed();
    }
    @GameTest(maxTicks=80)public void nativeMovingRemovalFlagsSuppressItemDropAndRetainTheRealCapturedPayload(GameTestHelper h){
        placard(h);sample(h,named(Items.DIAMOND,2,"Captured exact"));var pos=h.absolutePos(P);var state=h.getBlockState(P);var saved=placard(h,P).saveWithFullMetadata(h.getLevel().registryAccess());h.startSequence().thenWaitUntil(()->h.assertTrue(LoadedEntityAccess.isReady(h.getLevel(),pos),"Actual native sections ready"))
                .thenExecute(()->h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE|Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS)).thenExecuteAfter(2,()->{h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2)).stream().noneMatch(entity->entity.getItem().is(Items.DIAMOND)),"Native contraption removal flag does not duplicate held sample in the world");var copy=(PlacardBlockEntity)BlockEntity.loadStatic(pos,state,saved,h.getLevel().registryAccess());h.assertTrue(copy!=null&&copy.getHeldItem().getCount()==1&&copy.getHeldItem().getHoverName().getString().equals("Captured exact"),"Actual serialized BE payload still holds the one original sample");}).thenSucceed();
    }
    @GameTest public void nativePlacardRecipeUsesRealFrameAndBrassPlate(GameTestHelper h){
        var input=CraftingInput.of(2,1,List.of(new ItemStack(Items.ITEM_FRAME),new ItemStack(AllItems.BRASS_SHEET)));var output=h.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,h.getLevel()).orElseThrow().value().assemble(input);h.assertTrue(output.is(AllBlocks.PLACARD.asItem())&&output.getCount()==1,"Actual native source frame/brass recipe");h.succeed();
    }
}
