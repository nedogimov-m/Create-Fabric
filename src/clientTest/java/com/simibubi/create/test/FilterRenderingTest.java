package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.logistics.filter.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.*;

final class FilterRenderingTest {
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 870 6 12");context.waitFor(client->Math.abs(client.player.getX()-870.5)<0.1&&client.level.isLoaded(new BlockPos(870,4,5)),300);server.runCommand("fill 865 3 1 878 3 16 minecraft:smooth_quartz");server.runCommand("tp @p 870 4 12");server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with create:filter");
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.getInventory().setItem(9,new ItemStack(Items.DIAMOND,16));player.inventoryMenu.broadcastChanges();});context.waitFor(client->client.player.getMainHandItem().is(AllItems.FILTER)&&client.player.getInventory().getItem(9).getCount()==16,200);
        context.getInput().lookAt(new BlockPos(870,15,5));open(context);shiftClickSlot(context,0);context.waitFor(client->client.player.containerMenu instanceof FilterMenu menu&&menu.getSlot(36).getItem().is(Items.DIAMOND),200);
        context.clickScreenButton("−");context.clickScreenButton("=");context.waitFor(client->client.player.containerMenu instanceof FilterMenu menu&&menu.blacklist()&&menu.respectData(),200);
        context.runOnClient(client->{var menu=(FilterMenu)client.player.containerMenu;if(!menu.getCarried().isEmpty()||menu.getSlot(36).getItem().getCount()!=1||client.player.getInventory().getItem(9).getCount()!=16)throw new AssertionError("Native shift copy consumed or duplicated real items");});context.takeScreenshot("create-vulkan-filter-native-editor");
        context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.waitFor(client->client.gui.screen()==null&&client.player.getMainHandItem().getOrDefault(AllDataComponents.FILTER_ITEMS_BLACKLIST,false)&&client.player.getMainHandItem().getOrDefault(AllDataComponents.FILTER_ITEMS_RESPECT_NBT,false),200);
        open(context);context.waitFor(client->client.player.containerMenu instanceof FilterMenu menu&&menu.getSlot(36).getItem().is(Items.DIAMOND)&&menu.blacklist()&&menu.respectData(),200);clickSlot(context,36);context.waitFor(client->client.player.containerMenu.getSlot(36).getItem().isEmpty(),200);context.runOnClient(client->{if(!client.player.containerMenu.getCarried().isEmpty())throw new AssertionError("Ghost was picked up as a real item");});
        context.clickScreenButton("+");context.clickScreenButton("*");context.waitFor(client->client.player.containerMenu instanceof FilterMenu menu&&!menu.blacklist()&&!menu.respectData(),200);shiftClickSlot(context,0);context.waitFor(client->client.player.containerMenu.getSlot(36).getItem().is(Items.DIAMOND),200);context.clickScreenButton("×");context.waitFor(client->client.player.containerMenu.getSlot(36).getItem().isEmpty(),200);context.takeScreenshot("create-vulkan-filter-cleared");context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null&&!client.player.getMainHandItem().has(AllDataComponents.FILTER_ITEMS),200);
        context.runOnClient(client->{if(client.player.getInventory().getItem(9).getCount()!=16)throw new AssertionError("Editing a filter changed inventory item count");});server.runCommand("gamemode spectator @p");
    }
    private static void open(ClientGameTestContext context){context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof FilterScreen,200);context.waitTicks(2);}
    // Fabric 6.0.9's TestInput emits modifiers=0 for all mouse buttons; use a real native event for Shift.
    private static void shiftClickSlot(ClientGameTestContext context,int slot){context.runOnClient(client->{var screen=(FilterScreen)client.gui.screen();var point=screen.slotPosition(slot);var event=new net.minecraft.client.input.MouseButtonEvent(point.x,point.y,new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,InputConstants.MOD_SHIFT));screen.mouseClicked(event,false);screen.mouseReleased(event);});}
    private static void clickSlot(ClientGameTestContext context,int slot){var point=context.computeOnClient(client->{var pointInGui=((FilterScreen)client.gui.screen()).slotPosition(slot);float scaleX=(float)client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth();float scaleY=(float)client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight();return new net.minecraft.world.phys.Vec2(pointInGui.x*scaleX,pointInGui.y*scaleY);});context.getInput().setCursorPos(point.x,point.y);context.waitTicks(2);context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);}
}
