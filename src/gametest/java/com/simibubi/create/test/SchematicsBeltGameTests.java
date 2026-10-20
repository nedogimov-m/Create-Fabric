package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.content.schematics.cannon.*;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.*;

/** Actual item-built belt source, real native obstruction/casing callbacks and original paid receipts. UNRUN. */
public final class SchematicsBeltGameTests {
    private static final String STRUCTURE="create_test:reservoir";
    private static final BlockPos SOURCE=new BlockPos(4,6,4),COPY=new BlockPos(4,6,20);
    private record Fixture(LaunchedItem.ForBelt launch,List<ItemStack> reserve){}
    private static Fixture fixture(GameTestHelper h)throws java.io.IOException{
        var shaft=AllBlocks.SHAFT.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.Z);
        h.setBlock(SOURCE,shaft);h.setBlock(SOURCE.east(4),shaft);var player=h.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.BELT_CONNECTOR,7));
        for(var local:List.of(SOURCE,SOURCE.east(4))){var at=h.absolutePos(local);player.setPos(Vec3.atCenterOf(at).add(0,1,2));h.assertTrue(player.getMainHandItem().getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(at),Direction.SOUTH,at,false))).consumesAction(),"Actual native connector item selects/builds the physical source belt");}
        h.assertValueEqual(player.getMainHandItem().getCount(),6,"Physical original source connector was really spent once");
        h.getBlockEntity(SOURCE.east(),BeltBlockEntity.class).setCasingType(BeltBlockEntity.CasingType.ANDESITE);
        h.getBlockEntity(SOURCE.east(3),BeltBlockEntity.class).setCasingType(BeltBlockEntity.CasingType.BRASS);
        var level=h.getLevel();var template=SchematicFiles.decode(level,SchematicExport.capture(level,h.absolutePos(SOURCE),h.absolutePos(SOURCE.east(4))));
        var snapshot=SchematicStructure.load(level,template,h.absolutePos(COPY),new StructurePlaceSettings(),true);
        var casings=new BeltBlockEntity.CasingType[5];for(int i=0;i<5;i++)casings[i]=((BeltBlockEntity)snapshot.getBlockEntity(h.absolutePos(COPY.east(i)))).getCasingType();
        h.setBlock(COPY.east(4),shaft);var block=snapshot.getBlockState(h.absolutePos(COPY));var stock=new SchematicInventory(2,(slot,item)->true,slot->64,()->{},p->false);
        var shafts=new ItemStack(AllBlocks.SHAFT,41);shafts.set(DataComponents.CUSTOM_NAME,Component.literal("Original paid schematic shafts"));
        var connectors=new ItemStack(AllItems.BELT_CONNECTOR,9);connectors.set(DataComponents.CUSTOM_NAME,Component.literal("Original paid schematic connectors"));stock.setItem(0,shafts);stock.setItem(1,connectors);
        var launch=new LaunchedItem.ForBelt(h.absolutePos(COPY.south(3)),h.absolutePos(COPY),new ItemStack(AllItems.BELT_CONNECTOR),block,casings);
        try(var tx=Transaction.openOuter()){var payment=SchematicPayment.collect(ItemRequirement.of(block,snapshot.safeBlockEntity(h.absolutePos(COPY))),List.of(stock.storage),tx);h.assertTrue(payment.complete(),"Native source belt requirements are reserved from actual named stock");launch.paid(payment.receipt());tx.commit();}
        h.assertTrue(ItemStack.matches(stock.getItem(0),shafts.copyWithCount(40))&&ItemStack.matches(stock.getItem(1),connectors.copyWithCount(8)),"Native Fabric reservation preserves exact original stock tails");launch.totalTicks=launch.ticksRemaining=0;return new Fixture(launch,launch.paidOriginals());
    }
    private static LaunchedItem restore(GameTestHelper h,LaunchedItem launch){var level=h.getLevel();return LaunchedItem.fromNBT(launch.serializeNBT(level.registryAccess()),level.registryAccess(),level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK));}
    private static void sameReserve(GameTestHelper h,List<ItemStack> expected,LaunchedItem launch){var actual=launch.paidOriginals();h.assertValueEqual(actual.size(),expected.size(),"Same original reserve stack partition");for(int i=0;i<expected.size();i++)h.assertTrue(ItemStack.matches(actual.get(i),expected.get(i)),"Original native reserve count/components remain exact");}
    private static void complete(GameTestHelper h,LaunchedItem launch){h.assertTrue(launch.update(h.getLevel())&&launch.paidOriginals().isEmpty(),"Actual native belt publication consumes its original reservation exactly once");for(int i=0;i<5;i++){var be=h.getBlockEntity(COPY.east(i),BeltBlockEntity.class);h.assertTrue(be.beltLength==5&&be.index==i&&be.getController().equals(h.absolutePos(COPY)),"Every genuine native segment belongs to the accepted original chain");h.assertValueEqual(be.getCasingType(),i==1?BeltBlockEntity.CasingType.ANDESITE:i==3?BeltBlockEntity.CasingType.BRASS:BeltBlockEntity.CasingType.NONE,"Actual source casing survived native publication/finalization");}}
    @GameTest(structure=STRUCTURE) public void foreignNativeBarrelAndFullOriginalReceiptSurviveBeltPreflightAndRealRetry(GameTestHelper h)throws Exception{
        var fixture=fixture(h);var level=h.getLevel();var end=h.absolutePos(COPY.east(4));var originalShaft=SchematicWorldAccess.blockEntity(level,end);level.removeBlockEntity(end);
        try{h.assertTrue(originalShaft!=null&&SchematicWorldAccess.blockEntity(level,end)==null&&!fixture.launch.update(level)&&SchematicWorldAccess.blockEntity(level,end)==null,"Actual missing shaft owner stays unknown without native connector factory recreation");sameReserve(h,fixture.reserve,fixture.launch);}finally{originalShaft.clearRemoved();level.setBlockEntity(originalShaft);}
        h.setBlock(COPY.east(2),Blocks.BARREL);var barrel=h.getBlockEntity(COPY.east(2),BarrelBlockEntity.class);var cargo=new ItemStack(Items.DIAMOND,37);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Original belt obstruction cargo"));barrel.setItem(0,cargo);
        var saved=barrel.saveWithFullMetadata(h.getLevel().registryAccess());h.assertFalse(fixture.launch.update(h.getLevel()),"Actual native foreign BE refuses the entire source belt footprint");
        h.assertTrue(h.getBlockEntity(COPY.east(2),BarrelBlockEntity.class)==barrel&&barrel.saveWithFullMetadata(h.getLevel().registryAccess()).equals(saved)&&h.getBlockState(COPY).isAir()&&h.getBlockState(COPY.east(4)).is(AllBlocks.SHAFT),"Original native cargo/owner/state and untouched endpoint survive preflight");sameReserve(h,fixture.reserve,fixture.launch);
        var restored=restore(h,fixture.launch);h.setBlock(COPY.east(2),Blocks.AIR);complete(h,restored);h.succeed();
    }
    private static LaunchedItem interruptAfterPublication(GameTestHelper h,Fixture fixture){
        var callback=COPY.east().south();h.setBlock(callback,SchematicPublicationTestFixtures.THROW_NEIGHBOUR);SchematicPublicationTestFixtures.arm(h.getLevel(),h.absolutePos(callback));
        boolean failed=false;try{fixture.launch.update(h.getLevel());}catch(RuntimeException actual){failed=true;}
        h.assertTrue(failed&&fixture.launch.paidOriginals().isEmpty(),"Real native casing-neighbour callback failed after physical chain acceptance and exact custody consumption");
        for(int i=0;i<5;i++)h.assertTrue(h.getBlockState(COPY.east(i)).is(AllBlocks.BELT),"Accepted native segments remain physically present after callback refusal");return restore(h,fixture.launch);
    }
    @GameTest(structure=STRUCTURE) public void realPostPublicationCallbackFailureCanResumeSavedBeltWithoutReplacingCargoOrOwners(GameTestHelper h)throws Exception{
        var fixture=fixture(h);var restored=interruptAfterPublication(h,fixture);var owners=new ArrayList<BeltBlockEntity>();for(int i=0;i<5;i++)owners.add(h.getBlockEntity(COPY.east(i),BeltBlockEntity.class));
        var diamonds=new ItemStack(Items.DIAMOND,7);diamonds.set(DataComponents.CUSTOM_NAME,Component.literal("Original cargo added to accepted pending belt"));var inventory=owners.getFirst().storage(Direction.UP);
        try(var tx=Transaction.openOuter()){h.assertValueEqual(inventory.insert(ItemVariant.of(diamonds),7,tx),7L,"Actual accepted native controller admits original component-bearing cargo");tx.commit();}
        complete(h,restored);for(int i=0;i<5;i++)h.assertTrue(h.getBlockEntity(COPY.east(i),BeltBlockEntity.class)==owners.get(i),"Saved-job finalization retains literal native segment owners");
        try(var tx=Transaction.openOuter()){h.assertValueEqual(inventory.extract(ItemVariant.of(diamonds),100,tx),7L,"Original actual cargo count/components survive idempotent source finalization");}h.succeed();
    }
    @GameTest(structure=STRUCTURE) public void savedPublishedBeltCannotRecreateNativeGeometryAfterExternalDestruction(GameTestHelper h)throws Exception{
        var fixture=fixture(h);var restored=interruptAfterPublication(h,fixture);h.assertTrue(h.getLevel().destroyBlock(h.absolutePos(COPY.east(2)),false),"Actual native user destruction dismantles the accepted source belt");
        var before=new ArrayList<net.minecraft.world.level.block.state.BlockState>();for(int i=0;i<5;i++)before.add(h.getBlockState(COPY.east(i)));
        h.assertFalse(restored.update(h.getLevel()),"Published saved launch refuses missing physical segments");for(int i=0;i<5;i++)h.assertValueEqual(h.getBlockState(COPY.east(i)),before.get(i),"No free native shaft or belt recreation after external destruction");h.assertTrue(restored.paidOriginals().isEmpty(),"Already accepted material cannot become a second refund");h.succeed();
    }
}
