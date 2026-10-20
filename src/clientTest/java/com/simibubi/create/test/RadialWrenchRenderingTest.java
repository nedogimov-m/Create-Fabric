package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.wrench.*;
import com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.sdl.SDLKeyboard;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

/** Real native key/mouse/release input, Vulkan ring/PIP and one genuine serverbound barrel rotation. Authored1 UNRUN. */
public final class RadialWrenchRenderingTest implements FabricClientGameTest {
    private static final BlockPos P=new BlockPos(32,5,32);
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{if(!RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(Locale.ROOT).contains("vulkan"))throw new AssertionError("Radial rotation must use the actual native Vulkan backend");});
        var oldKey=new AtomicReference<InputConstants.Key>();context.runOnClient(client->{oldKey.set(net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(RadialWrenchHandler.ROTATE_MENU));RadialWrenchHandler.ROTATE_MENU.setKey(InputConstants.Type.KEYBOARD.getOrCreate(InputConstants.KEY_F8));KeyMapping.resetMapping();});
        try(var world=context.worldBuilder().adjustSettings(creator->creator.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()){
            world.getServer().runCommand("tp @p 32.5 5 35.5");world.getServer().runCommand("fill 30 4 30 36 4 37 minecraft:smooth_quartz");
            context.waitFor(client->client.level!=null&&client.level.isLoaded(P)&&client.player.position().distanceToSqr(new Vec3(32.5,5,35.5))<1,400);
            world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();player.getInventory().setItem(player.getInventory().getSelectedSlot(),new ItemStack(AllItems.WRENCH));player.inventoryMenu.broadcastChanges();level.setBlockAndUpdate(P,Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,Direction.NORTH));var cargo=new ItemStack(Items.DIAMOND,11);cargo.set(DataComponents.CUSTOM_NAME,Component.literal("Eleven original Vulkan radial diamonds"));((BarrelBlockEntity)level.getBlockEntity(P)).setItem(0,cargo);});
            context.waitFor(client->client.player.getMainHandItem().is(AllItems.WRENCH)&&client.level.getBlockState(P).is(Blocks.BARREL),250);
            look(context,Vec3.atCenterOf(P));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),200);context.waitTicks(3);
            // Both actual native events run in one client turn, before deferred END_CLIENT_TICK activation.
            context.runOnClient(client->{
                int scan=InputConstants.KEY_F8;var key=new KeyEvent(scan,SDLKeyboard.SDL_GetKeyFromScancode(scan,(short)0,false),0);
                if(client.gui.screen()!=null||!RadialWrenchHandler.ROTATE_MENU.matches(key))throw new AssertionError("Actual quick-tap fixture needs the bound native key in real gameplay");
                client.keyboardHandler.keyPress(client.getWindow().handle(),1,key);
                client.keyboardHandler.keyPress(client.getWindow().handle(),0,key);
                if(RadialWrenchHandler.ROTATE_MENU.isDown())throw new AssertionError("Actual native tap release did not clear the held mapping");
            });
            context.waitTicks(3);context.runOnClient(client->{if(client.gui.screen()!=null||client.level.getBlockState(P).getValue(BarrelBlock.FACING)!=Direction.NORTH)throw new AssertionError("An already released native quick tap stranded the radial screen or changed the source");});
            await(context,world,level->level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.NORTH&&((BarrelBlockEntity)level.getBlockEntity(P)).getItem(0).getCount()==11,100,"Native quick tap must retain original real barrel state and cargo");
            context.getInput().holdKey(InputConstants.KEY_F8);context.waitFor(client->client.gui.screen() instanceof RadialWrenchMenu,200);
            select(context,Direction.EAST);context.waitTicks(3);context.takeScreenshot("create-vulkan-native-radial-wrench-six-facing-block-previews");context.getInput().releaseKey(InputConstants.KEY_F8);
            context.waitFor(client->client.gui.screen()==null&&client.level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.EAST,250);
            await(context,world,level->level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.EAST&&((BarrelBlockEntity)level.getBlockEntity(P)).getItem(0).getCount()==11,200,"Actual native key release and C2S packet failed real source rotation/cargo conservation");
            context.waitTicks(4);context.getInput().holdKey(InputConstants.KEY_F8);context.waitFor(client->client.gui.screen() instanceof RadialWrenchMenu,150);select(context,Direction.WEST);context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);context.getInput().releaseKey(InputConstants.KEY_F8);context.waitFor(client->client.gui.screen()==null,150);context.waitTicks(4);
            await(context,world,level->level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.EAST&&((BarrelBlockEntity)level.getBlockEntity(P)).getItem(0).getCount()==11,100,"Actual native right-click cancellation changed real state or cargo");
            context.getInput().holdKey(InputConstants.KEY_F8);context.waitFor(client->client.gui.screen() instanceof RadialWrenchMenu,150);select(context,Direction.WEST);context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.getInput().releaseKey(InputConstants.KEY_F8);context.waitFor(client->client.gui.screen()==null,150);context.waitTicks(4);
            await(context,world,level->level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.EAST,100,"Actual Escape cancellation submitted a rotation");
            context.waitTicks(4);context.runOnClient(client->{RadialWrenchHandler.ROTATE_MENU.setKey(InputConstants.Type.MOUSE.getOrCreate(4));KeyMapping.resetMapping();});
            context.getInput().holdMouse(4);context.waitFor(client->client.gui.screen() instanceof RadialWrenchMenu,150);select(context,Direction.WEST);context.getInput().releaseMouse(4);
            context.waitFor(client->client.gui.screen()==null&&client.level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.WEST,250);
            await(context,world,level->level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.WEST&&((BarrelBlockEntity)level.getBlockEntity(P)).getItem(0).getCount()==11,150,"Actual native mouse-bound release failed to commit while retaining real cargo");
            context.waitTicks(4);context.getInput().holdMouse(4);context.waitFor(client->client.gui.screen() instanceof RadialWrenchMenu,150);select(context,Direction.SOUTH);context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);context.getInput().releaseMouse(4);
            context.waitFor(client->client.gui.screen()==null&&client.level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.SOUTH,250);
            await(context,world,level->level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.SOUTH&&((BarrelBlockEntity)level.getBlockEntity(P)).getItem(0).getCount()==11,150,"Actual native left-click commit failed or duplicate mouse release changed source cargo");
            var second=P.east(2);world.getServer().runOnServer(ignored->world.getConnection().getServerLevel().setBlockAndUpdate(second,AllBlocks.SEQUENCED_GEARSHIFT.defaultBlockState().setValue(SequencedGearshiftBlock.VERTICAL,false)));
            context.waitFor(client->client.level.getBlockState(second).is(AllBlocks.SEQUENCED_GEARSHIFT),200);look(context,Vec3.atCenterOf(second));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(second),200);context.waitTicks(4);
            context.getInput().holdMouse(4);context.waitFor(client->client.gui.screen() instanceof RadialWrenchMenu,150);context.getInput().scroll(-1);
            var vertical=context.computeOnClient(client->{var states=RadialWrenchRotation.states(client.level.getBlockState(second),SequencedGearshiftBlock.VERTICAL);int index=-1;for(int i=0;i<states.size();i++)if(states.get(i).getValue(SequencedGearshiftBlock.VERTICAL))index=i;if(index<0)throw new AssertionError("Actual native VERTICAL property has no true choice");double angle=Math.toRadians(index*360d/states.size()-90),scale=client.getWindow().getGuiScale();return new Vec2((float)((client.gui.screen().width/2d+Math.cos(angle)*80)*scale),(float)((client.gui.screen().height/2d+Math.sin(angle)*80)*scale));});
            context.getInput().setCursorPos(vertical.x,vertical.y);context.waitTicks(3);context.takeScreenshot("create-vulkan-native-radial-wrench-property-scroll");context.getInput().releaseMouse(4);
            context.waitFor(client->client.gui.screen()==null&&client.level.getBlockState(second).getValue(SequencedGearshiftBlock.VERTICAL),250);
            await(context,world,level->level.getBlockState(second).getValue(SequencedGearshiftBlock.VERTICAL)&&level.getBlockState(P).getValue(BarrelBlock.FACING)==Direction.SOUTH&&((BarrelBlockEntity)level.getBlockEntity(P)).getItem(0).getCount()==11,150,"Actual native wheel property selection/release must commit VERTICAL and preserve the original barrel/cargo");
        }finally{context.getInput().releaseMouse(4);context.getInput().releaseKey(InputConstants.KEY_F8);context.runOnClient(client->{RadialWrenchHandler.ROTATE_MENU.setKey(oldKey.get());KeyMapping.resetMapping();});}
    }
    private static void select(ClientGameTestContext context,Direction direction){
        var point=context.computeOnClient(client->{var states=RadialWrenchRotation.states(client.level.getBlockState(P),BarrelBlock.FACING);int index=0;for(int i=0;i<states.size();i++)if(states.get(i).getValue(BarrelBlock.FACING)==direction)index=i;double angle=Math.toRadians(index*360d/states.size()-90),scale=client.getWindow().getGuiScale();return new Vec2((float)((client.gui.screen().width/2d+Math.cos(angle)*80)*scale),(float)((client.gui.screen().height/2d+Math.sin(angle)*80)*scale));});context.getInput().setCursorPos(point.x,point.y);context.waitTicks(2);
    }
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void await(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> predicate,int ticks,String failure){for(int i=0;i<ticks;i++){if(world.getServer().computeOnServer(ignored->predicate.test(world.getConnection().getServerLevel())))return;context.waitTicks(1);}throw new AssertionError(failure);}
}
