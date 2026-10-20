package com.simibubi.create.content.kinetics.waterwheel;

import com.simibubi.create.AllBlocks;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.createmod.catnip.outliner.OutlinerRenderHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.*;

/** Exact source red3x3 error footprint and translated native overlay, reached only through the common Consumer. */
public final class LargeWaterWheelPlacementClient {
    public static Object boundsKey(BlockPos pos){return Pair.of("waterwheel",pos.immutable());}
    private static void showBounds(BlockPlaceContext context){
        var client=Minecraft.getInstance();
        if(!(context.getPlayer() instanceof LocalPlayer player)||player!=client.player||context.getLevel()!=client.level)return;
        var pos=context.getClickedPos();var axis=AllBlocks.LARGE_WATER_WHEEL.getAxisForPlacement(context);
        var contract=Direction.get(Direction.AxisDirection.POSITIVE,axis).getUnitVec3();
        Outliner.getInstance().showAABB(boundsKey(pos),new AABB(pos).inflate(1).deflate(contract.x,contract.y,contract.z)).colored(0xFFFF5D6C);
        player.sendOverlayMessage(Component.translatable("create.large_water_wheel.not_enough_space").withColor(0xFF5D6C));
    }
    public static void register(){OutlinerRenderHandler.register();LargeWaterWheelBlockItem.SHOW_BOUNDS=LargeWaterWheelPlacementClient::showBounds;}
    private LargeWaterWheelPlacementClient(){}
}
