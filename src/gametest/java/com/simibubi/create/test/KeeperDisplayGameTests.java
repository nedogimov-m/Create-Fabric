package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.content.processing.burner.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class KeeperDisplayGameTests {
    private static final BlockPos T=new BlockPos(4,2,4),S=T.west();
    private static StockTickerBlockEntity ticker(GameTestHelper h){h.setBlock(T,AllBlocks.STOCK_TICKER);return h.getBlockEntity(T,StockTickerBlockEntity.class);}
    private static net.minecraft.world.entity.animal.cow.Cow cow(GameTestHelper h){h.setBlock(S,AllBlocks.SEATS.get(DyeColor.BLUE));var cow=EntityTypes.COW.create(h.getLevel(),EntitySpawnReason.COMMAND);cow.setPos(Vec3.atCenterOf(h.absolutePos(S)));h.getLevel().addFreshEntity(cow);SeatBlock.sitDown(h.getLevel(),h.absolutePos(S),cow);return cow;}
    @GameTest public void logisticsHatRequiresUniqueAdjacentStationAndDirectSeatWithoutHeadEquipment(GameTestHelper h){
        ticker(h);var cow=cow(h);h.assertTrue(StockTickerInteractionHandler.hasLogisticsHat(cow),"Unambiguous seated keeper receives hat");cow.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.DIAMOND_HELMET));h.assertFalse(StockTickerInteractionHandler.hasLogisticsHat(cow),"Head item takes precedence");cow.setItemSlot(EquipmentSlot.HEAD,ItemStack.EMPTY);h.setBlock(S.north(),AllBlocks.STOCK_TICKER);h.assertFalse(StockTickerInteractionHandler.hasLogisticsHat(cow),"Two stations do not select a hat");h.setBlock(S.north(),Blocks.AIR);h.assertTrue(StockTickerInteractionHandler.hasLogisticsHat(cow),"Unique station restored");cow.stopRiding();h.assertFalse(StockTickerInteractionHandler.hasLogisticsHat(cow),"Standing entity has no station hat");h.succeed();
    }
    @GameTest public void babyAndPlayerKeepersUseSameRulesButIndirectPassengersDoNotReceiveHat(GameTestHelper h){
        ticker(h);var cow=cow(h);cow.setBaby(true);h.assertTrue(StockTickerInteractionHandler.hasLogisticsHat(cow),"Baby keeper still receives hat");var pig=EntityTypes.PIG.create(h.getLevel(),EntitySpawnReason.COMMAND);h.getLevel().addFreshEntity(pig);pig.startRiding(cow,true,true);h.assertFalse(StockTickerInteractionHandler.hasLogisticsHat(pig),"Indirect root passenger does not receive hat");var seat=cow.getVehicle();cow.stopRiding();var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(h.absolutePos(S)));player.startRiding(seat,true,true);h.assertTrue(StockTickerInteractionHandler.hasLogisticsHat(player),"Seated player follows same hat rule");h.succeed();
    }
    @GameTest public void keeperPortraitSelectsFilledBurnerAndFallsBackToSeatedLivingKeeper(GameTestHelper h){
        var ticker=ticker(h);var cow=cow(h);h.setBlock(T.east(),AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));var found=StockTickerInteractionHandler.findKeeper(h.getLevel(),ticker.getBlockPos());h.assertTrue(found.burner()!=null,"Filled burner is preferred");h.setBlock(T.east(),AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.NONE));found=StockTickerInteractionHandler.findKeeper(h.getLevel(),ticker.getBlockPos());h.assertTrue(found.burner()==null&&found.entity()==cow,"Empty cage is not a keeper portrait");h.succeed();
    }
    @GameTest public void paymentTooltipIsReadOnlyAndGroupsExactComponentVariantsForAdministrator(GameTestHelper h){
        var ticker=ticker(h);var player=h.makeMockPlayer(GameType.SURVIVAL);var named=new ItemStack(Items.DIAMOND);named.set(DataComponents.CUSTOM_NAME,Component.literal("Marked payment"));try(var tx=Transaction.openOuter()){ticker.receivedPayments.inputs.get(0).insert(ItemVariant.of(Items.DIAMOND),3,tx);ticker.receivedPayments.inputs.get(1).insert(ItemVariant.of(Items.DIAMOND),4,tx);ticker.receivedPayments.inputs.get(2).insert(ItemVariant.of(named),2,tx);tx.commit();}
        var lines=new ArrayList<Component>();h.assertTrue(ticker.addToTooltip(lines,false,player),"Unowned network administrator can inspect without goggles");h.assertValueEqual(lines.size(),4,"Header, two exact variants and retrieval hint");h.assertTrue(lines.stream().anyMatch(line->line.getString().contains("x7"))&&lines.stream().anyMatch(line->line.getString().contains("Marked payment")&&line.getString().contains("x2")),"Counts group identical components only");h.assertValueEqual(ticker.receivedPayments.inputs.stream().mapToLong(slot->slot.getAmount()).sum(),9L,"Inspection cannot extract receipts");h.succeed();
    }
    @GameTest public void paymentInspectionUsesCurrentServerAdministratorAndValidatesRange(GameTestHelper h){
        var ticker=ticker(h);var player=h.makeMockPlayer(GameType.SURVIVAL);player.setPos(Vec3.atCenterOf(ticker.getBlockPos()));var network=new LogisticsNetwork(ticker.behaviour.freqId);network.owner=player.getUUID();GlobalLogisticsManager.get(h.getLevel()).logisticsNetworks.put(ticker.behaviour.freqId,network);var query=new StockTickerPaymentInspection.Query(ticker.getBlockPos());
        h.assertTrue(StockTickerPaymentInspection.query(player,query).administrator(),"Owner receives permission");network.owner=UUID.randomUUID();h.assertFalse(StockTickerPaymentInspection.query(player,query).administrator(),"Permission is re-evaluated when owner changes");h.assertTrue(StockTickerPaymentInspection.query(player,new StockTickerPaymentInspection.Query(ticker.getBlockPos().offset(200,0,0)))==null,"Far/unloaded block cannot be queried");h.succeed();
    }
    @GameTest public void foreignPlayerAndEmptyRegisterCannotProducePaymentTooltip(GameTestHelper h){
        var ticker=ticker(h);var player=h.makeMockPlayer(GameType.SURVIVAL);var lines=new ArrayList<Component>();h.assertFalse(ticker.addToTooltip(lines,false,player),"Empty register has no tooltip");try(var tx=Transaction.openOuter()){ticker.receivedPayments.inputs.getFirst().insert(ItemVariant.of(Items.EMERALD),1,tx);tx.commit();}var network=new LogisticsNetwork(ticker.behaviour.freqId);network.owner=UUID.randomUUID();GlobalLogisticsManager.get(h.getLevel()).logisticsNetworks.put(ticker.behaviour.freqId,network);h.assertFalse(ticker.addToTooltip(lines,false,player),"Foreign player receives no payment text");h.assertTrue(lines.isEmpty(),"Denied inspection does not append lines");h.succeed();
    }
    @GameTest public void paymentPermissionCodecRetainsStationFrequencyAndNegativeAnswer(GameTestHelper h){
        var ticker=ticker(h);var packet=new StockTickerPaymentInspection.Permission(ticker.getBlockPos(),UUID.randomUUID(),false);var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());try{StockTickerPaymentInspection.Permission.CODEC.encode(buffer,packet);h.assertValueEqual(StockTickerPaymentInspection.Permission.CODEC.decode(buffer),packet,"Exact permission response round trip");}finally{buffer.release();}h.succeed();
    }
}
