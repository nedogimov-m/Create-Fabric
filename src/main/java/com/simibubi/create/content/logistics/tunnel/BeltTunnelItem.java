package com.simibubi.create.content.logistics.tunnel;

import com.simibubi.create.content.kinetics.belt.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;

public final class BeltTunnelItem extends BlockItem {
    public BeltTunnelItem(BeltTunnelBlock block,Properties properties){super(block,properties);}
    @Override protected boolean canPlace(BlockPlaceContext context,BlockState state){var player=context.getPlayer();return ((BeltTunnelBlock)getBlock()).isValidPositionForPlacement(state,context.getLevel(),context.getClickedPos())&&context.getLevel().isUnobstructed(state,context.getClickedPos(),player==null?CollisionContext.empty():CollisionContext.of(player));}
    @Override public net.minecraft.world.InteractionResult place(BlockPlaceContext context){var result=super.place(context);if(result.consumesAction()&&!context.getLevel().isClientSide()){var belt=BeltHelper.getSegmentBE(context.getLevel(),context.getClickedPos().below());if(belt!=null&&belt.getCasingType()==BeltBlockEntity.CasingType.NONE)belt.setCasingType(((BeltTunnelBlock)getBlock()).brass?BeltBlockEntity.CasingType.BRASS:BeltBlockEntity.CasingType.ANDESITE);}return result;}
}
