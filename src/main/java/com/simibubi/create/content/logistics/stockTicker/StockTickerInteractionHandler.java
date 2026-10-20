package com.simibubi.create.content.logistics.stockTicker;

import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

public final class StockTickerInteractionHandler {
    public record Keeper(LivingEntity entity,com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity burner){}
    public static Keeper findKeeper(Level level,BlockPos ticker){
        LivingEntity found=null;
        for(int y=0;y<2;y++)for(var direction:Direction.Plane.HORIZONTAL){var pos=ticker.below(y).relative(direction);
            for(var seat:level.getEntitiesOfClass(SeatEntity.class,new net.minecraft.world.phys.AABB(pos)))if(seat.getFirstPassenger() instanceof LivingEntity living)found=living;
            if(y==0&&LoadedLevelAccess.blockEntity(level,pos) instanceof com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity burner&&burner.getBlockState().getValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL)!=com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.NONE)return new Keeper(found,burner);
        }return new Keeper(found,null);
    }
    public static boolean hasLogisticsHat(LivingEntity keeper){return keeper!=null&&keeper.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()&&keeper.getVehicle() instanceof SeatEntity&&getStockTickerPosition(keeper)!=null;}
    public static BlockPos getStockTickerPosition(Entity keeper) {
        if (!(keeper.getRootVehicle() instanceof SeatEntity) || !(keeper instanceof LivingEntity) || keeper instanceof PackageEntity) return null;
        BlockPos result = null;
        for (var direction : Direction.Plane.HORIZONTAL) for (int y = 0; y < 2; y++) {
            var pos = keeper.blockPosition().relative(direction).above(y);
            if (!(LoadedLevelAccess.blockState(keeper.level(), pos).getBlock() instanceof StockTickerBlock)) continue;
            if (result != null) return null; result = pos;
        }
        return result;
    }
    public static BlockPos getBurnerTicker(Level level, BlockPos pos) {
        for (var direction : Direction.Plane.HORIZONTAL) { var adjacent = pos.relative(direction); if (LoadedLevelAccess.blockEntity(level, adjacent) instanceof StockTickerBlockEntity) return adjacent; }
        return null;
    }
    public static boolean interactWithLogisticsManagerAt(Player player, Level level, BlockPos pos) {
        if (player.isSpectator() || player instanceof net.fabricmc.fabric.api.entity.FakePlayer || !level.mayInteract(player, pos)) return false;
        if (level.isClientSide()) return true;
        if (!(LoadedLevelAccess.blockEntity(level, pos) instanceof StockTickerBlockEntity ticker)) return false;
        if(player.getMainHandItem().is(com.simibubi.create.AllItems.SHOPPING_LIST)){com.simibubi.create.content.logistics.tableCloth.ShopTrade.purchase(player,ticker);return true;}
        if (ticker.behaviour.mayInteractMessage(player)) StockTickerMenu.open(player, ticker, false);
        return true;
    }
    public static void register() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> { var pos = getStockTickerPosition(entity); return pos != null && interactWithLogisticsManagerAt(player, level, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS; });
    }
    private StockTickerInteractionHandler() {}
}
