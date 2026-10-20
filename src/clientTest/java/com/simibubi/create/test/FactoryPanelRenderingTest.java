package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.logistics.factoryBoard.*;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelBlock.PanelSlot;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.*;

final class FactoryPanelRenderingTest {
    private static final BlockPos FLOOR=new BlockPos(1702,4,6),WALL=new BlockPos(1712,5,5),OTHER=new BlockPos(1714,5,5),CEILING=new BlockPos(1722,7,5),STATION=new BlockPos(1700,4,3);
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();var id=UUID.randomUUID();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 1710 10 14");context.waitFor(client->Math.abs(client.player.getX()-1710.5)<.1,300);waitChunks(context,world,new BlockPos(1696,3,0),new BlockPos(1743,3,15));server.runCommand("fill 1696 3 0 1743 3 15 minecraft:smooth_quartz");
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(STATION.north(),Blocks.BARREL.defaultBlockState());((BarrelBlockEntity)level.getBlockEntity(STATION.north())).setItem(0,new ItemStack(Items.APPLE,50));level.setBlockAndUpdate(STATION,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));level.setBlockAndUpdate(STATION.above(),AllBlocks.STOCK_LINK.defaultBlockState());var link=(PackagerLinkBlockEntity)level.getBlockEntity(STATION.above());link.behaviour.setFrequency(id);link.initialize();
            var floor=place(level,FLOOR,AttachFace.FLOOR,id,true);place(level,CEILING,AttachFace.CEILING,id,true);var wall=place(level,WALL,AttachFace.WALL,id,false);var other=place(level,OTHER,AttachFace.WALL,id,false);other.panels.get(PanelSlot.TOP_LEFT).setFilter(new ItemStack(Items.IRON_INGOT));
            var input=floor.panels.get(PanelSlot.TOP_LEFT);input.setFilter(new ItemStack(Items.APPLE));input.setAmount(0,true);var output=floor.panels.get(PanelSlot.TOP_RIGHT);output.setFilter(new ItemStack(Items.DIAMOND));output.setAmount(1,true);output.recipeAddress="Vulkan recipe";output.addConnection(input.getPanelPosition());output.targetedBy.get(input.getPanelPosition()).amount=2;floor.notifyUpdate();
        });
        context.waitFor(client->client.level.getBlockEntity(FLOOR) instanceof FactoryPanelBlockEntity gauge&&gauge.activePanels()==4&&client.level.getBlockEntity(WALL) instanceof FactoryPanelBlockEntity,300);world.getConnection().waitForChunksRender();context.getInput().lookAt(new BlockPos(1710,5,5));
        server.runOnServer(ignored->{var gauge=(FactoryPanelBlockEntity)world.getConnection().getServerLevel().getBlockEntity(CEILING);var target=gauge.panels.get(PanelSlot.TOP_RIGHT);target.addConnection(gauge.panels.get(PanelSlot.TOP_LEFT).getPanelPosition());target.addConnection(gauge.panels.get(PanelSlot.BOTTOM_LEFT).getPanelPosition());});
        context.waitFor(client->client.level.getBlockEntity(CEILING) instanceof FactoryPanelBlockEntity gauge&&gauge.panels.get(PanelSlot.TOP_RIGHT).targetedBy.size()==2,200);context.waitTicks(10);
        // Finish the real shared-network task before observing transient pulse colours.
        // Its stock/promise updates recreate the client's connections during native BE reads.
        awaitInitialTask(context,world);
        context.waitFor(client->client.level.getBlockEntity(CEILING) instanceof FactoryPanelBlockEntity gauge
            &&gauge.panels.get(PanelSlot.TOP_LEFT).getLevelInStorage()==48
            &&gauge.panels.get(PanelSlot.TOP_RIGHT).getPromised()==1,300);
        server.runOnServer(ignored->{var gauge=(FactoryPanelBlockEntity)world.getConnection().getServerLevel().getBlockEntity(CEILING);var target=gauge.panels.get(PanelSlot.TOP_RIGHT);target.pulse(gauge.panels.get(PanelSlot.TOP_LEFT).getPanelPosition(),true);target.pulse(gauge.panels.get(PanelSlot.BOTTOM_LEFT).getPanelPosition(),false);});
        awaitPulseColors(context,world);
        context.runOnClient(client->{for(var pos:List.of(FLOOR,WALL,CEILING)){var rendered=client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos),.5f,null,true);if(!(rendered instanceof FactoryPanelRenderer.State state)||state.panels.isEmpty()||state.panels.stream().anyMatch(panel->panel.base.mesh==null||panel.base.mesh.size()==0))throw new AssertionError("Factory panel native meshes missing: "+pos);}});context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-factory-gauge-four-slots-and-orientations");
        server.runCommand("tp @p 1713 4 8");context.waitFor(client->Math.abs(client.player.getX()-1713.5)<.1&&Math.abs(client.player.getY()-4)<.1,300);server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.resetFallDistance();player.setHealth(player.getMaxHealth());});server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with minecraft:apple 8");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().is(Items.APPLE),200);var point=context.computeOnClient(client->FactoryPanelSlotPositioning.getCenterOfSlot(client.level.getBlockState(WALL),PanelSlot.TOP_LEFT).add(Vec3.atLowerCornerOf(WALL)));lookAt(context,point);context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(WALL),200);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.level.getBlockEntity(WALL) instanceof FactoryPanelBlockEntity gauge&&gauge.panels.get(PanelSlot.TOP_LEFT).getFilter().is(Items.APPLE),200);context.runOnClient(client->{if(client.player.getMainHandItem().getCount()!=8)throw new AssertionError("Panel sample consumed apples");});context.waitTicks(5);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof FactoryPanelScreen,200);
        context.getInput().typeChars("Depot A");context.runOnClient(client->{var screen=(FactoryPanelScreen)client.gui.screen();screen.amountBox().setValue("16");});context.takeScreenshot("create-vulkan-factory-gauge-native-settings");verifyOriginalFooterAndTargetTooltip(context);context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null&&client.level.getBlockEntity(WALL) instanceof FactoryPanelBlockEntity gauge&&gauge.panels.get(PanelSlot.TOP_LEFT).count==16&&gauge.panels.get(PanelSlot.TOP_LEFT).recipeAddress.equals("Depot A"),200);
        context.waitTicks(5);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof FactoryPanelScreen,200);context.clickScreenButton("+");context.waitFor(client->client.gui.screen()==null,200);var otherPoint=context.computeOnClient(client->FactoryPanelSlotPositioning.getCenterOfSlot(client.level.getBlockState(OTHER),PanelSlot.TOP_LEFT).add(Vec3.atLowerCornerOf(OTHER)));lookAt(context,otherPoint);context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(OTHER),200);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.level.getBlockEntity(WALL) instanceof FactoryPanelBlockEntity gauge&&gauge.panels.get(PanelSlot.TOP_LEFT).targetedBy.containsKey(new FactoryPanelPosition(OTHER,PanelSlot.TOP_LEFT)),200);
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 1710 10 14");context.waitFor(client->client.player.isSpectator()&&Math.abs(client.player.getX()-1710.5)<.1,200);context.getInput().lookAt(new BlockPos(1711,5,5));world.getConnection().waitForChunksRender();context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot("create-vulkan-factory-gauge-connected-status");
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);context.runOnClient(client->{var rendered=client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(FLOOR),.5f,null,true);if(!(rendered instanceof FactoryPanelRenderer.State state)||state.panels.size()!=4||state.panels.getFirst().base.mesh==null)throw new AssertionError("Factory gauge meshes missing after reload");});
        // Retain the post-UI/reload duplicate check as well as the initial task observation.
        awaitInitialTask(context,world);
        server.runCommand("tp @p 1702 4 8");context.waitFor(client->Math.abs(client.player.getX()-1702.5)<.1&&Math.abs(client.player.getY()-4)<.1,200);server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond_pickaxe");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().is(Items.DIAMOND_PICKAXE),200);
        var breakPoint=context.computeOnClient(client->FactoryPanelSlotPositioning.getCenterOfSlot(client.level.getBlockState(FLOOR),PanelSlot.BOTTOM_RIGHT).add(Vec3.atLowerCornerOf(FLOOR)));lookAt(context,breakPoint);context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(FLOOR)&&FactoryPanelBlock.getTargetedSlot(FLOOR,client.level.getBlockState(FLOOR),hit.getLocation())==PanelSlot.BOTTOM_RIGHT,200);
        context.getInput().holdKey(options->options.keyAttack);
        try{context.waitFor(client->client.level.getBlockEntity(FLOOR) instanceof FactoryPanelBlockEntity gauge&&gauge.activePanels()==3,200);}
        catch(AssertionError failure){
            var details=new java.util.concurrent.atomic.AtomicReference<String>();
            server.runOnServer(ignored->{var gauge=world.getConnection().getServerLevel().getBlockEntity(FLOOR);details.set("server="+(gauge instanceof FactoryPanelBlockEntity panel?panel.activePanels():gauge));});
            var local=context.computeOnClient(client->{var gauge=client.level.getBlockEntity(FLOOR);return "client="+(gauge instanceof FactoryPanelBlockEntity panel?panel.activePanels():gauge)+", hit="+client.hitResult+", mining="+client.gameMode.isDestroying();});
            throw new AssertionError("Partial gauge mining failed: "+details.get()+", "+local,failure);
        }finally{context.getInput().releaseKey(options->options.keyAttack);}
        server.runOnServer(ignored->{var gauge=(FactoryPanelBlockEntity)world.getConnection().getServerLevel().getBlockEntity(FLOOR);if(gauge.activePanels()!=3||gauge.panels.get(PanelSlot.BOTTOM_RIGHT).active)throw new AssertionError("Native survival mining must remove only the targeted quarter");});
        server.runCommand("gamemode spectator @p");
        chunkUnload(context,world);
    }
    private static void awaitInitialTask(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();
        var ordered=new AtomicBoolean();for(int i=0;i<300&&!ordered.get();i++){
            server.runOnServer(ignored->{var packager=(PackagerBlockEntity)world.getConnection().getServerLevel().getBlockEntity(STATION);int amount=PackageItemCount(packager);if(amount>2)throw new AssertionError("Factory task duplicated its two-apple batch: "+amount);ordered.set(amount==2);});
            if(!ordered.get())context.waitTicks(1);
        }
        if(!ordered.get())throw new AssertionError("Factory task did not produce its two-apple batch within three request intervals");
    }
    private static void awaitPulseColors(ClientGameTestContext context,TestSingleplayerContext world) {
        try { context.waitFor(client->{var gauge=(FactoryPanelBlockEntity)client.level.getBlockEntity(CEILING);var target=gauge.panels.get(PanelSlot.TOP_RIGHT);return target.targetedBy.get(new FactoryPanelPosition(CEILING,PanelSlot.TOP_LEFT)).success&&!target.targetedBy.get(new FactoryPanelPosition(CEILING,PanelSlot.BOTTOM_LEFT)).success;},200); }
        catch(AssertionError failure) {
            try {
                var clientState=context.computeOnClient(client->{var chunk=client.level.getChunkSource().getChunkNow(CEILING.getX()>>4,CEILING.getZ()>>4);var be=chunk==null?null:chunk.getBlockEntities().get(CEILING);return "gameTime="+client.level.getGameTime()+", be="+be+", target="+(be instanceof FactoryPanelBlockEntity gauge?pulseState(gauge.panels.get(PanelSlot.TOP_RIGHT)):"absent");});
                Create.LOGGER.error("Actual native factory pulse client failure: {}",clientState);
                world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var chunk=level.getChunkSource().getChunkNow(CEILING.getX()>>4,CEILING.getZ()>>4);var be=chunk==null?null:chunk.getBlockEntities().get(CEILING);Create.LOGGER.error("Actual native factory pulse server failure: gameTime={}, be={}, target={}",level.getGameTime(),be,be instanceof FactoryPanelBlockEntity gauge?pulseState(gauge.panels.get(PanelSlot.TOP_RIGHT)):"absent");});
            } catch(Throwable observationFailure) { failure.addSuppressed(observationFailure); }
            throw failure;
        }
    }
    private static String pulseState(FactoryPanelBehaviour target) {
        if(target==null)return "absent";
        var data=new LinkedHashMap<String,Object>();data.put("identity",System.identityHashCode(target));data.put("active",target.active);data.put("network",target.network);data.put("recipeAddress",target.recipeAddress);data.put("filter",target.getFilter().toString());
        var connections=new LinkedHashMap<String,Object>();for(var entry:target.targetedBy.entrySet())connections.put(entry.getKey().toString(),"identity="+System.identityHashCode(entry.getValue())+", success="+entry.getValue().success+", amount="+entry.getValue().amount);data.put("connections",connections);
        for(var name:List.of("timer","lastReportedLevelInStorage","lastReportedPromises","bulb"))try{var field=FactoryPanelBehaviour.class.getDeclaredField(name);field.setAccessible(true);data.put(name,field.get(target));}catch(ReflectiveOperationException observation){data.put(name,observation.toString());}
        return data.toString();
    }
    private static int PackageItemCount(PackagerBlockEntity packager) {int amount=com.simibubi.create.content.logistics.box.PackageItem.getContents(packager.heldBox).stream().mapToInt(ItemStack::getCount).sum();for(var entry:packager.queuedExitingPackages)amount+=entry.count*com.simibubi.create.content.logistics.box.PackageItem.getContents(entry.stack).stream().mapToInt(ItemStack::getCount).sum();return amount;}
    private static FactoryPanelBlockEntity place(net.minecraft.server.level.ServerLevel level,BlockPos pos,AttachFace face,UUID id,boolean four) {var state=AllBlocks.FACTORY_GAUGE.defaultBlockState().setValue(FactoryPanelBlock.FACE,face).setValue(FactoryPanelBlock.FACING,Direction.SOUTH);level.setBlockAndUpdate(pos.relative(FactoryPanelBlock.connectedDirection(state).getOpposite()),Blocks.SMOOTH_QUARTZ.defaultBlockState());level.setBlockAndUpdate(pos,state);var gauge=(FactoryPanelBlockEntity)level.getBlockEntity(pos);for(var slot:PanelSlot.values()){if(!four&&slot!=PanelSlot.TOP_LEFT)continue;gauge.addPanel(slot,id);var panel=gauge.panels.get(slot);if(four){panel.setFilter(new ItemStack(switch(slot){case TOP_LEFT->Items.APPLE;case TOP_RIGHT->Items.DIAMOND;case BOTTOM_LEFT->Items.IRON_INGOT;case BOTTOM_RIGHT->Items.CRAFTING_TABLE;}));panel.setAmount(slot.ordinal()*4,true);}}return gauge;}
    private static void chunkUnload(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();var pos=new BlockPos(1775,4,5);var input=pos.east();var id=UUID.randomUUID();server.runCommand("tp @p 1775 9 12");context.waitFor(client->Math.abs(client.player.getX()-1775.5)<.1,300);waitChunks(context,world,pos,input);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var a=place(level,input,AttachFace.FLOOR,id,false);a.panels.get(PanelSlot.TOP_LEFT).setFilter(new ItemStack(Items.APPLE));var b=place(level,pos,AttachFace.FLOOR,id,false);b.panels.get(PanelSlot.TOP_LEFT).setFilter(new ItemStack(Items.DIAMOND));if(!b.panels.get(PanelSlot.TOP_LEFT).addConnection(a.panels.get(PanelSlot.TOP_LEFT).getPanelPosition()))throw new AssertionError("Boundary gauge connection setup rejected");level.getChunkSource().addTicket(new net.minecraft.server.level.Ticket(net.minecraft.server.level.TicketType.FORCED,33),new net.minecraft.world.level.ChunkPos(pos.getX()>>4,pos.getZ()>>4));});server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();
        for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var gauge=(FactoryPanelBlockEntity)com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(level,pos);FactoryPanelBlockEntity.tick(level,pos,gauge.getBlockState(),gauge);var panel=gauge.panels.get(PanelSlot.TOP_LEFT);for(var connection:panel.targetedBy.values())FactoryPanelBehaviour.at(level,connection);unloaded.set(!level.isLoaded(input));});}if(!unloaded.get())throw new AssertionError("Factory panel source lookup kept neighboring chunk loaded");server.runCommand("tp @p 1775 9 12");context.waitFor(client->Math.abs(client.player.getX()-1775.5)<.1&&client.level.getBlockEntity(input) instanceof FactoryPanelBlockEntity,400);waitChunks(context,world,pos,input);context.waitTicks(5);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var panel=FactoryPanelBehaviour.at(level,new FactoryPanelPosition(pos,PanelSlot.TOP_LEFT));if(panel.targetedBy.size()!=1||FactoryPanelBehaviour.at(level,panel.targetedBy.values().iterator().next())==null)throw new AssertionError("Saved gauge connection did not recover: inputs="+panel.targetedBy.size()+", source="+FactoryPanelBehaviour.at(level,new FactoryPanelPosition(input,PanelSlot.TOP_LEFT)));level.getChunkSource().removeTicketWithRadius(net.minecraft.server.level.TicketType.FORCED,new net.minecraft.world.level.ChunkPos(pos.getX()>>4,pos.getZ()>>4),0);});
    }
    private static void waitChunks(ClientGameTestContext context,TestSingleplayerContext world,BlockPos a,BlockPos b) {var ready=new AtomicBoolean();for(int i=0;i<300&&!ready.get();i++){world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();ready.set(level.isLoaded(a)&&level.isLoaded(b));});if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError("Factory gauge chunks unavailable");}
    private static void lookAt(ClientGameTestContext context,Vec3 point) {var rotation=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(rotation.x,rotation.y);}

    private static void verifyOriginalFooterAndTargetTooltip(ClientGameTestContext context) {
        context.runOnClient(client->{
            var screen=client.gui.screen();if(!(screen instanceof FactoryPanelScreen))throw new AssertionError("Actual native Factory Gauge screen required for footer regression");
            var state=new net.minecraft.client.renderer.state.gui.GuiRenderState();var gui=new net.minecraft.client.gui.GuiGraphicsExtractor(client,state,0,0);screen.extractRenderState(gui,0,0,0);
            var target=net.minecraft.network.chat.Component.translatable("create.factory_panel.target_amount").getString();
            state.forEachText(text->{if(nativeGuiText(text).equals(target))throw new AssertionError("Factory gauge must not add a permanent label absent from the pinned source footer");});
        });
        var point=context.computeOnClient(client->{
            var screen=client.gui.screen();var widget=screen.children().stream().filter(net.minecraft.client.gui.components.Button.class::isInstance).map(net.minecraft.client.gui.components.Button.class::cast).filter(button->button.active&&button.visible).filter(button->button.getMessage().getString().equals("#")||button.getMessage().getString().equals("▤")).findFirst().orElseThrow();
            return new Vec2(widget.getX()+widget.getWidth()/2f,widget.getY()+widget.getHeight()/2f);
        });
        var cursor=context.computeOnClient(client->{var window=client.getWindow();return new Vec2(point.x*window.getScreenWidth()/window.getGuiScaledWidth(),point.y*window.getScreenHeight()/window.getGuiScaledHeight());});
        context.getInput().setCursorPos(cursor.x,cursor.y);context.waitTicks(3);
        context.runOnClient(client->{
            var screen=client.gui.screen();var state=new net.minecraft.client.renderer.state.gui.GuiRenderState();var gui=new net.minecraft.client.gui.GuiGraphicsExtractor(client,state,(int)point.x,(int)point.y);screen.extractRenderState(gui,(int)point.x,(int)point.y,0);
            var before=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.minecraft.client.renderer.state.gui.GuiTextRenderState,Boolean>());state.forEachText(before::add);gui.extractDeferredElements((int)point.x,(int)point.y,0);
            var tooltip=new java.util.ArrayList<net.minecraft.client.renderer.state.gui.GuiTextRenderState>();state.forEachText(text->{if(!before.contains(text))tooltip.add(text);});
            var actual=new StringBuilder();for(var text:tooltip){var value=nativeGuiText(text);actual.append(value).append(' ');var bounds=text.bounds();if(!value.isEmpty()&&(bounds==null||bounds.left()<0||bounds.top()<0||bounds.right()>screen.width||bounds.bottom()>screen.height))throw new AssertionError("Factory gauge tooltip text exceeds the actual native screen bounds: "+bounds);}
            var expected=net.minecraft.network.chat.Component.translatable("create.factory_panel.target_amount").getString();if(tooltip.isEmpty()||!actual.toString().replaceAll("\\s+","").contains(expected.replaceAll("\\s+","")))throw new AssertionError("Factory gauge native target-amount tooltip did not render its translated title: "+actual);
            Create.LOGGER.info("Actual native Factory Gauge target-amount tooltip verified: text={}, lines={}, window={}x{}",actual,tooltip.size(),screen.width,screen.height);
        });
        context.takeScreenshot("create-vulkan-factory-gauge-target-amount-native-tooltip");
    }
    private static String nativeGuiText(net.minecraft.client.renderer.state.gui.GuiTextRenderState text) {
        try{var field=net.minecraft.client.renderer.state.gui.GuiTextRenderState.class.getDeclaredField("text");field.setAccessible(true);var result=new StringBuilder();((net.minecraft.util.FormattedCharSequence)field.get(text)).accept((index,style,codepoint)->{result.appendCodePoint(codepoint);return true;});return result.toString();}
        catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
    }
}
