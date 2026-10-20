package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.content.contraptions.mounted.CartAssemblerBlockEntity.CartMovementMode;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.*;

/** Real held-use input, selection and authoritative server modes on both rail axes. */
public final class CartAssemblerModeClientTest implements FabricClientGameTest {
    private static final BlockPos P=new BlockPos(32,5,32);
    @Override public void runTest(ClientGameTestContext context){
        try(var world=context.worldBuilder().adjustSettings(settings->settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE)).create()){
            context.runOnClient(client->{client.options.framerateLimit().set(60);client.options.renderDistance().set(5);client.options.simulationDistance().set(5);});
            var server=world.getServer();server.runCommand("fill 28 4 28 37 4 37 minecraft:smooth_quartz");
            for(var rail:java.util.List.of(RailShape.NORTH_SOUTH,RailShape.EAST_WEST)){
                var side=rail==RailShape.NORTH_SOUTH?Direction.EAST:Direction.SOUTH;
                var stand=Vec3.atLowerCornerOf(P).add(.5,0,.5).add(side.getUnitVec3().scale(3));
                server.runCommand("tp @p "+stand.x+" "+stand.y+" "+stand.z);
                context.waitFor(client->client.player!=null&&client.level!=null&&client.level.isLoaded(P)&&client.player.position().distanceToSqr(stand)<.1,400);
                server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);level.setBlockAndUpdate(P,AllBlocks.CART_ASSEMBLER.defaultBlockState().setValue(CartAssemblerBlock.RAIL_SHAPE,rail));});
                context.waitFor(client->client.level.getBlockEntity(P) instanceof CartAssemblerBlockEntity&&client.level.getBlockState(P).getValue(CartAssemblerBlock.RAIL_SHAPE)==rail,200);
                for(var mode:CartMovementMode.values()){
                    var point=Vec3.atLowerCornerOf(P).add(.5,7/16d,.5).add(side.getUnitVec3().scale(9.5/16d));
                    var angle=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});
                    context.getInput().lookAt(angle.x,angle.y);
                    context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P)&&CartAssemblerSettings.panelHit(client.level.getBlockState(P),P,hit),200);
                    context.getInput().holdKey(options->options.keyUse);
                    try{
                        context.waitFor(client->client.gui.screen() instanceof CartAssemblerSettingsClient.ModeScreen,150);
                        var cursor=context.computeOnClient(client->{var at=((CartAssemblerSettingsClient.ModeScreen)client.gui.screen()).coordinate(mode);var window=client.getWindow();return new Vec2(at.x*window.getScreenWidth()/window.getGuiScaledWidth(),at.y*window.getScreenHeight()/window.getGuiScaledHeight());});
                        context.getInput().setCursorPos(cursor.x,cursor.y);
                        context.waitTicks(2);
                    }finally{context.getInput().releaseKey(options->options.keyUse);}
                    context.waitFor(client->client.gui.screen()==null&&client.level.getBlockEntity(P) instanceof CartAssemblerBlockEntity assembler&&assembler.getMovementMode()==mode,200);
                    server.runOnServer(ignored->{if(((CartAssemblerBlockEntity)world.getConnection().getServerLevel().getBlockEntity(P)).getMovementMode()!=mode)throw new AssertionError("Real mode packet did not reach server");});
                }
            }
            System.out.println("CART_ASSEMBLER_REAL_MODE_INPUT_PASS");
        }
    }
}
