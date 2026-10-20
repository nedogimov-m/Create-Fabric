package com.simibubi.create.content.logistics.packagePort.postbox;

import com.simibubi.create.content.logistics.packagePort.*;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;

/** Actual native station selection and postbox placement; every target is checked again on the server. */
public final class PostboxPlacement {
  private static final Map<Player,GlobalPos> SELECTED=Collections.synchronizedMap(new WeakHashMap<>());
  public static boolean select(Player player,InteractionHand hand,BlockPos pos){
    var level=player.level();var held=player.getItemInHand(hand);
    if(level.isClientSide()||!player.mayBuild()||player.isSpectator()||player.isShiftKeyDown()||!player.isWithinBlockInteractionRange(pos,1)||!level.mayInteract(player,pos)
        ||!(held.getItem() instanceof BlockItem block)||!(block.getBlock() instanceof PostboxBlock)
        ||!(LoadedLevelAccess.blockEntity(level,pos) instanceof StationBlockEntity station)||station.getStation()==null)return false;
    SELECTED.put(player,GlobalPos.of(level.dimension(),pos));return true;
  }
  public static boolean placed(Player player,PostboxBlockEntity box){
    var selected=SELECTED.remove(player);var level=player.level();
    if(selected==null||level.isClientSide()||box.getLevel()!=level||!selected.dimension().equals(level.dimension())
        ||!player.mayBuild()||player.isSpectator()||!level.mayInteract(player,box.getBlockPos())
        ||!(LoadedLevelAccess.blockEntity(level,selected.pos()) instanceof StationBlockEntity station)||station.getStation()==null)return false;
    if(!box.setTarget(new PackagePortTarget.TrainStationFrogportTarget(selected.pos().subtract(box.getBlockPos()))))return false;
    PackagePortMenu.open(player,box);return true;
  }
  public static void register(){com.simibubi.create.foundation.utility.CoreUseBlockCallbacks.register((player,level,hand,hit)->{
    var item=player.getItemInHand(hand);
    if(!(item.getItem() instanceof BlockItem block)||!(block.getBlock() instanceof PostboxBlock)
        ||!(LoadedLevelAccess.blockEntity(level,hit.getBlockPos()) instanceof StationBlockEntity))return InteractionResult.PASS;
    if(level.isClientSide())return !player.isShiftKeyDown()?InteractionResult.SUCCESS:InteractionResult.PASS;
    return select(player,hand,hit.getBlockPos())?InteractionResult.SUCCESS:InteractionResult.PASS;
  });}
  private PostboxPlacement(){}
}
