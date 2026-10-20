package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.equipment.toolbox.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;

/** Proposed physical paid placement/menu/radial/reload/return/disposal/pickup scene; native input only. UNRUN. */
final class ToolboxRenderingTest {
    private static final BlockPos P=new BlockPos(49010,5,8),Q=P.east(3);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        int width=context.computeOnClient(client->client.getWindow().getScreenWidth()),height=context.computeOnClient(client->client.getWindow().getScreenHeight()),gui=context.computeOnClient(client->client.options.guiScale().get()),nativeScale=context.computeOnClient(client->client.getWindow().getGuiScale());
        boolean fullscreen=context.computeOnClient(client->client.options.fullscreen().get()),hidden=context.computeOnClient(client->client.gui.hud.isHidden());
        try{
            context.runOnClient(client->{client.getWindow().setWindowed(Math.max(960,width),Math.max(640,height));client.options.guiScale().set(2);client.resizeGui();if(client.gui.hud.isHidden())client.gui.hud.toggle();});
            context.waitFor(client->client.getWindow().getScreenWidth()>=Math.max(960,width)&&client.getWindow().getScreenHeight()>=Math.max(640,height)&&client.getWindow().getGuiScale()==2&&client.getWindow().getGuiScaledWidth()>=480&&client.getWindow().getGuiScaledHeight()>=320,300);
            visible(context,world);
        }finally{
            context.getInput().releaseKey(InputConstants.KEY_LALT);context.getInput().releaseKey(InputConstants.KEY_LSHIFT);context.getInput().releaseKey(options->options.keyUse);context.getInput().releaseKey(options->options.keyAttack);
            context.runOnClient(client->{client.getWindow().setWindowed(width,height);client.options.fullscreen().set(fullscreen);client.getWindow().setFullscreen(fullscreen);client.getWindow().updateFullscreenIfChanged();client.options.guiScale().set(gui);client.resizeGui();if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});
            if(!fullscreen)context.waitFor(client->client.getWindow().getScreenWidth()==width&&client.getWindow().getScreenHeight()==height&&client.getWindow().getGuiScale()==nativeScale,300);
        }
    }
    private static void visible(ClientGameTestContext c,TestSingleplayerContext w){
        c.runOnClient(client->{var source=NativePartialTextureProbe.verify(client);if(source.models()!=44||source.faces()!=1726||source.sourceSprites().size()!=29)throw new AssertionError("Actual native source partial texture census changed: "+source);});
        var server=w.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 49012.5 9 12.5");c.waitFor(client->client.level.isLoaded(P)&&client.level.isLoaded(Q),400);NativeScreenshotReadiness.beforeFloor(c, w, new BlockPos(49000, 4, 0), new BlockPos(49024, 4, 16), P, Q);
        server.runCommand("fill 49000 4 0 49024 4 16 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(c, w, new BlockPos(49000, 4, 0), new BlockPos(49024, 4, 16));server.runCommand("clear @p");
        // Spectator number keys control its menu; reach real survival before selecting paid slot0.
        stand(c,w,P.getX()+.5,P.getZ()+3.5);
        // The real linked stock uses slot0; earlier aggregate scenes may select another slot.
        c.getInput().pressKey(InputConstants.KEY_1);c.waitFor(client->client.player.getInventory().getSelectedSlot()==0,200);
        await(c,w,level->actual(level).getInventory().getSelectedSlot()==0,200,"Actual native slot0 selection was not acknowledged before original paid Toolbox stock");
        server.runCommand("item replace entity @p weapon.mainhand with create:brown_toolbox 1");use(c,P.below(),Vec3.atCenterOf(P.below()).add(0,.499,0),Direction.UP);c.waitFor(client->client.player.getMainHandItem().isEmpty()&&client.level.getBlockEntity(P) instanceof ToolboxBlockEntity box&&box.isFullyInitialized(),250);
        UUID owner=server.computeOnServer(ignored->box(w.getConnection().getServerLevel()).getUniqueId());
        server.runCommand("item replace entity @p weapon.mainhand with create:cyan_toolbox 1");stand(c,w,Q.getX()+.5,Q.getZ()+3.5);use(c,Q.below(),Vec3.atCenterOf(Q.below()).add(0,.499,0),Direction.UP);c.waitFor(client->client.player.getMainHandItem().isEmpty()&&client.level.getBlockEntity(Q) instanceof ToolboxBlockEntity box&&box.isFullyInitialized(),250);
        stand(c,w,P.getX()+.5,P.getZ()+3.5);look(c,Vec3.atCenterOf(P).add(1.5,-.15,0));w.getConnection().waitForChunksRender();
        c.runOnClient(client->{for(var pos:List.of(P,Q)){var source=(ToolboxBlockEntity)client.level.getBlockEntity(pos);var state=(ToolboxRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(source,.5f,null,false);if(state==null||state.geometry==null||state.geometry.lid.isEmpty()||state.geometry.drawer.isEmpty()||state.geometry.lidAmount!=0)throw new AssertionError("Both actual paid color owners need complete closed native world geometry");}});
        screenshot(c,"create-vulkan-toolbox-two-paid-colors-native-world-lids-and-drawers");
        slot(w,9,diamonds(64));slot(w,10,diamonds(36));stand(c,w,P.getX()+.5,P.getZ()+3.5);open(c);clickSlot(c,32,true);clickSlot(c,33,true);
        await(c,w,level->box(level).inventory.totalCount(0)==100&&actual(level).getInventory().countItem(Items.DIAMOND)==0,200,"Real native shift clicks did not transfer the original100 component-bearing diamonds");
        c.waitFor(client->((ToolboxScreen)client.gui.screen()).getMenu().totalCountInCompartment(0)==100&&((ToolboxBlockEntity)client.level.getBlockEntity(P)).lid(1)>.9f,200);
        c.runOnClient(client->{var source=(ToolboxBlockEntity)client.level.getBlockEntity(P);var state=(ToolboxRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(source,.5f,null,false);if(state==null||state.geometry==null||state.geometry.lid.isEmpty()||state.geometry.drawer.isEmpty()||!owner.equals(source.getUniqueId()))throw new AssertionError("Actual native open toolbox world extraction lost its real owner/lid/drawer meshes");});
        screenshot(c,"create-vulkan-toolbox-paid-eight-compartment-menu-and-animated-native-preview");var reload=c.computeOnClient(Minecraft::reloadResourcePacks);c.waitFor(client->reload.isDone()&&client.gui.overlay()==null,650);reload.join();
        c.runOnClient(client->{var menu=((ToolboxScreen)client.gui.screen()).getMenu();if(menu.totalCountInCompartment(0)!=100||!ItemStack.isSameItemSameComponents(menu.getFilter(0),diamonds(1))||!owner.equals(menu.contentHolder.getUniqueId()))throw new AssertionError("Native resource reload lost paid toolbox filters/stock/UUID");});screenshot(c,"create-vulkan-toolbox-native-menu-and-pip-after-resource-reload");close(c);
        await(c,w,level->actual(level).containerMenu==actual(level).inventoryMenu,200,"Actual native Toolbox menu close was not acknowledged before unloading its stock owner");
        var oldBrown=server.computeOnServer(ignored->box(w.getConnection().getServerLevel()));
        var oldCyan=server.computeOnServer(ignored->(ToolboxBlockEntity)w.getConnection().getServerLevel().getBlockEntity(Q));
        UUID cyanOwner=oldCyan.getUniqueId();
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 50400 20 50400");
        await(c,w,level->level.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4)==null&&level.getChunkSource().getChunkNow(Q.getX()>>4,Q.getZ()>>4)==null,800,"Actual native Toolbox fixture chunk did not leave FULL status");
        NativeFixtureChunkUnload.finish(c,w,oldBrown,oldCyan);
        server.runCommand("tp @p 49012.5 9 12.5");
        await(c,w,level->box(level)!=null&&box(level)!=oldBrown&&level.getBlockEntity(Q) instanceof ToolboxBlockEntity cyan&&cyan!=oldCyan&&owner.equals(box(level).getUniqueId())&&cyanOwner.equals(cyan.getUniqueId())&&box(level).getColor()==DyeColor.BROWN&&cyan.getColor()==DyeColor.CYAN&&box(level).inventory.totalCount(0)==100&&ItemStack.isSameItemSameComponents(box(level).inventory.getFilter(0),diamonds(1))&&cyan.inventory.isEmpty()&&actual(level).getInventory().countItem(Items.DIAMOND)==0,600,"Actual fresh native Toolbox owners lost/reused original UUIDs, colors, retained filter or100 paid component-bearing diamonds");
        c.waitFor(client->client.level.getBlockEntity(P) instanceof ToolboxBlockEntity brown&&client.level.getBlockEntity(Q) instanceof ToolboxBlockEntity cyan&&owner.equals(brown.getUniqueId())&&cyanOwner.equals(cyan.getUniqueId())&&brown.inventory.totalCount(0)==100&&ItemStack.isSameItemSameComponents(brown.inventory.getFilter(0),diamonds(1)),400);
        stand(c,w,P.getX()+.5,P.getZ()+3.5);look(c,Vec3.atCenterOf(P).add(1.5,-.15,0));w.getConnection().waitForChunksRender();
        screenshot(c,"create-vulkan-toolbox-real-world-retirement-fresh-two-owners-and-original-hundred-diamonds");
        c.getInput().holdKey(InputConstants.KEY_LALT);c.waitFor(client->client.gui.screen() instanceof RadialToolboxMenu,200);screenshot(c,"create-vulkan-toolbox-native-alt-two-actual-box-selector");
        int selected=c.computeOnClient(client->{var boxes=new ArrayList<>(ToolboxHandler.getNearest(client.level,client.player,8));boxes.sort(Comparator.comparing(ToolboxBlockEntity::getUniqueId));for(int index=0;index<boxes.size();index++)if(boxes.get(index).getBlockPos().equals(P))return index;throw new AssertionError("Original paid box is missing from the actual native nearest-eight order");});
        clickRadial(c,selected);c.waitTicks(3);screenshot(c,"create-vulkan-toolbox-native-item-selector-original-filter");clickRadial(c,0);c.getInput().releaseKey(InputConstants.KEY_LALT);c.waitFor(client->client.gui.screen()==null&&client.player.getMainHandItem().getCount()==32&&ToolboxBindings.get(client.player,0)!=null,250);
        await(c,w,level->box(level).inventory.totalCount(0)==68&&actual(level).getInventory().getItem(0).getCount()==32&&ToolboxBindings.matches(actual(level),ToolboxBindings.get(actual(level),0),box(level)),200,"Actual radial equip/replenishment did not conserve original paid100 diamonds");screenshot(c,"create-vulkan-toolbox-real-linked-half-stack-and-hotbar-overlay");
        c.getInput().holdKey(InputConstants.KEY_LALT);c.waitFor(client->client.gui.screen() instanceof RadialToolboxMenu,200);move(c,c.computeOnClient(client->physical(client,client.gui.screen().width/2f,client.gui.screen().height/2f)));c.waitTicks(3);screenshot(c,"create-vulkan-toolbox-native-center-unequip");c.getInput().releaseKey(InputConstants.KEY_LALT);c.waitFor(client->client.gui.screen()==null&&client.player.getMainHandItem().isEmpty()&&ToolboxBindings.get(client.player,0)==null,250);
        await(c,w,level->box(level).inventory.totalCount(0)==100&&actual(level).getInventory().countItem(Items.DIAMOND)==0,200,"Actual native key-release unequip did not return original stock once");
        c.getInput().holdKey(InputConstants.KEY_LALT);c.waitFor(client->client.gui.screen() instanceof RadialToolboxMenu,200);c.getInput().pressKey(InputConstants.KEY_1+selected);c.waitTicks(2);c.getInput().pressKey(InputConstants.KEY_1);c.getInput().releaseKey(InputConstants.KEY_LALT);c.waitFor(client->client.gui.screen()==null&&client.player.getMainHandItem().getCount()==32,250);open(c);
        var deposit=c.computeOnClient(client->{var widget=client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(child->child.getMessage().equals(Component.translatable("create.toolbox.depositBox"))).findFirst().orElseThrow();return physical(client,widget.getX()+widget.getWidth()/2f,widget.getY()+widget.getHeight()/2f);});click(c,deposit);await(c,w,level->box(level).inventory.totalCount(0)==100&&actual(level).getInventory().countItem(Items.DIAMOND)==0&&ToolboxBindings.get(actual(level),0)==null,200,"Actual native deposit widget did not dispose real linked stock into its retained filter");screenshot(c,"create-vulkan-toolbox-real-native-deposit-restores-all-paid-stock");close(c);
        look(c,Vec3.atCenterOf(P).add(0,-.25,.25));c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),200);c.getInput().pressKey(options->options.keyAttack);c.waitFor(client->client.level.getBlockState(P).isAir()&&client.player.getMainHandItem().is(AllBlocks.TOOLBOXES.get(DyeColor.BROWN).asItem()),250);
        await(c,w,level->{var stack=actual(level).getMainHandItem();return level.getBlockState(P).isAir()&&stack.getCount()==1&&owner.equals(stack.get(AllDataComponents.TOOLBOX_UUID))&&stack.get(AllDataComponents.TOOLBOX_INVENTORY)!=null&&stack.get(AllDataComponents.TOOLBOX_INVENTORY).totalCount(0)==100;},200,"Actual native pickup did not return the same one whole paid box/UUID/filtered100 diamonds");screenshot(c,"create-vulkan-toolbox-native-pickup-retains-original-uuid-and-whole-stock");server.runCommand("gamemode spectator @p");
    }
    private static void stand(ClientGameTestContext c,TestSingleplayerContext w,double x,double z){w.getServer().runCommand("gamemode survival @p");w.getServer().runCommand("tp @p "+x+" 5 "+z);c.waitFor(client->client.player.onGround()&&!client.player.isSpectator()&&Math.abs(client.player.getX()-x)<.1&&Math.abs(client.player.getZ()-z)<.1&&client.gameRenderer.mainCamera().position().distanceToSqr(client.player.getEyePosition())<1,250);}
    private static void open(ClientGameTestContext c){use(c,P,Vec3.atCenterOf(P).add(0,-.25,.25),null);c.waitFor(client->client.gui.screen() instanceof ToolboxScreen&&client.player.containerMenu instanceof ToolboxMenu,250);c.waitTicks(3);}
    private static void use(ClientGameTestContext c,BlockPos pos,Vec3 target,net.minecraft.core.Direction side){look(c,target);c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos)&&(side==null||hit.getDirection()==side),200);c.waitTicks(5);c.getInput().pressKey(options->options.keyUse);}
    private static void look(ClientGameTestContext c,Vec3 target){var angle=c.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});c.getInput().lookAt(angle.x,angle.y);}
    private static void close(ClientGameTestContext c){c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitFor(client->client.gui.screen()==null,200);}
    private static void clickRadial(ClientGameTestContext c,int slot){click(c,c.computeOnClient(client->{var screen=client.gui.screen();if(!(screen instanceof RadialToolboxMenu))throw new AssertionError("Actual native Toolbox radial screen is required before a physical SDL click");double angle=Math.toRadians(slot*45-135);return physical(client,screen.width/2f+(float)Math.cos(angle)*40,screen.height/2f+(float)Math.sin(angle)*40);}));}
    private static void clickSlot(ClientGameTestContext c,int index,boolean shift){var at=c.computeOnClient(client->{var screen=client.gui.screen();var slot=client.player.containerMenu.getSlot(index);return physical(client,(screen.width-218)/2f-11+slot.x+8,(screen.height-255)/2f+slot.y+8);});if(shift)c.getInput().holdKey(InputConstants.KEY_LSHIFT);try{click(c,at);}finally{if(shift)c.getInput().releaseKey(InputConstants.KEY_LSHIFT);}c.waitTicks(3);}
    private static void move(ClientGameTestContext c,Vec2 point){c.getInput().setCursorPos(point.x,point.y);c.waitTicks(2);}
    private static void click(ClientGameTestContext c,Vec2 point){move(c,point);if(!c.computeOnClient(Minecraft::hasShiftDown))c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);else{
        // Fabric TestInput6.0.9 supplies modifiers0; pass the actual held Shift at the native MouseHandler boundary.
        c.runOnClient(client->client.mouseHandler.onButton(client.getWindow().handle(),new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,InputConstants.MOD_SHIFT),InputConstants.PRESS));c.waitTicks(1);
        c.runOnClient(client->client.mouseHandler.onButton(client.getWindow().handle(),new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,InputConstants.MOD_SHIFT),InputConstants.RELEASE));}}
    private static Vec2 physical(Minecraft client,float x,float y){var window=client.getWindow();int width=window.getGuiScaledWidth(),height=window.getGuiScaledHeight();if(width<480||height<320||window.getGuiScale()!=2||x<0||y<0||x>=width||y>=height)throw new AssertionError("Native Toolbox input lies outside the actual readable GUI");var point=new Vec2(x*window.getScreenWidth()/width,y*window.getScreenHeight()/height);if(Math.abs(net.minecraft.client.MouseHandler.getScaledXPos(window,point.x)-x)>.01||Math.abs(net.minecraft.client.MouseHandler.getScaledYPos(window,point.y)-y)>.01)throw new AssertionError("Actual native MouseHandler cannot invert window/GUI coordinates");return point;}
    private static ToolboxBlockEntity box(ServerLevel level){return level.getBlockEntity(P) instanceof ToolboxBlockEntity box?box:null;}
    private static ServerPlayer actual(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static ItemStack diamonds(int amount){var stack=new ItemStack(Items.DIAMOND,amount);stack.set(DataComponents.CUSTOM_NAME,Component.literal("Original native Toolbox diamonds"));return stack;}
    private static void slot(TestSingleplayerContext w,int slot,ItemStack item){w.getServer().runOnServer(ignored->{var player=actual(w.getConnection().getServerLevel());player.getInventory().setItem(slot,item.copy());player.connection.send(player.getInventory().createInventoryUpdatePacket(slot));});}
    private static void await(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> predicate,int ticks,String failure){var accepted=new AtomicBoolean();for(int tick=0;tick<ticks;tick++){w.getServer().runOnServer(ignored->accepted.set(predicate.test(w.getConnection().getServerLevel())));if(accepted.get())return;c.waitTicks(1);}throw new AssertionError(failure);}
    private static void screenshot(ClientGameTestContext c,String name){c.waitFor(client->client.gui.overlay()==null,250);c.runOnClient(client->{client.gui.hud.getChat().clearMessages(false);if(client.gui.screen()!=null&&(client.gui.screen().width<480||client.gui.screen().height<320||client.getWindow().getGuiScale()!=2))throw new AssertionError("Native Toolbox menu/radial screenshot clips original panels/animated extra preview");});c.takeScreenshot(name);}
    private ToolboxRenderingTest(){}
}
