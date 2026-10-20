package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.PackagePortScreen;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.display.*;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.condition.*;
import com.simibubi.create.content.trains.schedule.destination.*;
import com.simibubi.create.content.trains.station.*;
import com.simibubi.create.content.trains.track.*;
import com.simibubi.create.foundation.gui.widget.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import com.simibubi.create.test.mixin.ScheduleScreenAccessor;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;

/** Proposed native Schedule editor/real conductor/mail/departure scene. Region47000..47128,z0..48; UNRUN. */
final class ScheduleRenderingTest {
    private static final BlockPos T=new BlockPos(47048,5,16),S=T.south(3),B=T.east().above(),
            C=B.east().above(),I=B.east(2),SECOND=B.east(6),LAST=B.east(7),PIG_SEAT=SECOND.above(),
            BOX=S.west(3).south(),BOARD=S.east(14).south(2).above(2),SIGN=S.east(4).south(2),
            SUMMARY=S.above(),STATUS=S.south();
    private static final String NAME="Native Schedule Platform 7",FILTER="Native Schedule Platform *",
            MISSING="Native Schedule Unbuilt",HUB="Schedule Hub",REMOTE="Schedule Remote";
    private static final String APPLES="Original Schedule apples";
    private record Fixture(Train train,UUID body,UUID conductor,ItemStack originalMail) {}

    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        int clientView=context.computeOnClient(client->client.options.renderDistance().get());
        int serverView=world.getServer().computeOnServer(server->server.getPlayerList().getViewDistance());
        int gui=context.computeOnClient(client->client.options.guiScale().get());
        int nativeGuiScale=context.computeOnClient(client->client.getWindow().getGuiScale());
        int windowWidth=context.computeOnClient(client->client.getWindow().getScreenWidth());
        int windowHeight=context.computeOnClient(client->client.getWindow().getScreenHeight());
        boolean fullscreen=context.computeOnClient(client->client.options.fullscreen().get());
        boolean hidden=context.computeOnClient(client->client.gui.hud.isHidden());
        try {
            context.runOnClient(client->{client.options.renderDistance().set(6);client.options.broadcastOptions();client.getWindow().setWindowed(Math.max(960,windowWidth),Math.max(640,windowHeight));client.options.guiScale().set(2);client.resizeGui();if(client.gui.hud.isHidden())client.gui.hud.toggle();});
            context.waitFor(client->client.getWindow().getScreenWidth()>=Math.max(960,windowWidth)&&client.getWindow().getScreenHeight()>=Math.max(640,windowHeight)&&client.getWindow().getGuiScale()==2&&client.getWindow().getGuiScaledWidth()>=320&&client.getWindow().getGuiScaledHeight()>=260,300);
            world.getServer().runOnServer(server->server.getPlayerList().setViewDistance(6));
            waitServer(context,world,level->actual(level).requestedViewDistance()==6&&level.getServer().getPlayerList().getViewDistance()==6,300,"Real six-chunk native client-information acknowledgement absent");
            runVisible(context,world);
        } finally {
            context.getInput().releaseKey(InputConstants.KEY_LSHIFT);
            context.getInput().releaseKey(options->options.keyUse);
            world.getServer().runOnServer(server->server.getPlayerList().setViewDistance(serverView));
            context.runOnClient(client->{client.options.renderDistance().set(clientView);client.options.broadcastOptions();client.getWindow().setWindowed(windowWidth,windowHeight);client.options.fullscreen().set(fullscreen);client.getWindow().setFullscreen(fullscreen);client.getWindow().updateFullscreenIfChanged();client.options.guiScale().set(gui);client.resizeGui();if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});
            if(!fullscreen)context.waitFor(client->client.getWindow().getScreenWidth()==windowWidth&&client.getWindow().getScreenHeight()==windowHeight&&client.getWindow().getGuiScale()==nativeGuiScale,300);
        }
    }

    private static void runVisible(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 47064.5 18 24.5");
        context.waitFor(client->client.level.isLoaded(new BlockPos(47000,4,0))&&client.level.isLoaded(new BlockPos(47128,4,48)),500);
        waitServer(context,world,ScheduleRenderingTest::allChunks,600,"Actual native view did not load the complete physical fixture");
        NativeScreenshotReadiness.beforeFloor(context, world, new BlockPos(47000, 4, 0), new BlockPos(47128, 4, 48), BOX, T, BOARD.below(2), LAST.below());
        server.runCommand("fill 47000 4 0 47128 4 48 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(context, world, new BlockPos(47000, 4, 0), new BlockPos(47128, 4, 48));server.runCommand("fill 47008 5 16 47120 5 16 create:track[shape=xo]");
        waitServer(context,world,level->{for(int x=47008;x<=47120;x++){var p=new BlockPos(x,5,16);if(!TrackPropagator.reconcile(level,p,LoadedLevelAccess.blockState(level,p),false).complete())return false;}return true;},500,"Real rails did not complete genuine graph discovery");
        server.runCommand("clear @p");
        // Spectator number keys select spectator-menu entries. Reach actual survival first.
        stand(context,world,T.getX()+2.5,T.getZ()+1.5);
        // Exercise the real non-default incoming slot in focused and aggregate routes alike.
        context.getInput().pressKey(InputConstants.KEY_2);
        context.waitFor(client->client.player.getInventory().getSelectedSlot()==1,200);
        waitServer(context,world,level->actual(level).getInventory().getSelectedSlot()==1,200,"Native incoming-slot regression precondition was not acknowledged");
        context.getInput().pressKey(InputConstants.KEY_1);
        context.waitFor(client->client.player.getInventory().getSelectedSlot()==0,200);
        waitServer(context,world,level->actual(level).getInventory().getSelectedSlot()==0,200,"Actual native slot0 selection was not acknowledged before paid Schedule fixture stock");
        server.runCommand("item replace entity @p weapon.mainhand with create:track_station 1");
        useBlock(context,T,Vec3.atBottomCenterOf(T).add(0,.1,0),null);
        context.waitFor(client->client.player.getMainHandItem().has(AllDataComponents.TRACK_TARGETING_SELECTION),200);
        useBlock(context,S.below(),Vec3.atCenterOf(S.below()).add(0,.499,0),Direction.UP);
        waitServer(context,world,level->station(level)!=null&&station(level).getStation()!=null&&station(level).getStation().ownedBy(station(level)),300,"Paid native station placement did not create its real world-owned point");
        context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);
        stand(context,world,S.getX()+.5,S.getZ()+2.5);openStation(context);typeField(context,client->((StationScreen)client.gui.screen()).nameBox(),NAME);
        var typedStationSnapshot=context.computeOnClient(client->((StationScreen)client.gui.screen()).snapshot());
        context.waitFor(client->client.gui.screen() instanceof StationScreen screen&&screen.snapshot()!=typedStationSnapshot,150);
        context.runOnClient(client->{if(!((StationScreen)client.gui.screen()).nameBox().getValue().equals(NAME))throw new AssertionError("Actual passive native station snapshot discarded the unfinished physical name draft");});
        screenshot(context,"create-vulkan-schedule-station-draft-before-real-save-diagnostic");
        clickStation(context,StationScreen::saveCoordinate);
        context.runOnClient(client->{var screen=(StationScreen)client.gui.screen();System.out.println("CREATE_SCHEDULE_STATION_AFTER_SAVE field="+screen.nameBox().getValue()+" snapshotName="+screen.snapshot().name()+" revision="+screen.snapshot().revision()+" accepted="+screen.snapshot().accepted()+" focus="+screen.getFocused());});
        server.runOnServer(ignored->{var owner=station(world.getConnection().getServerLevel());System.out.println("CREATE_SCHEDULE_SERVER_STATION_AFTER_SAVE name="+owner.stationName()+" revision="+owner.configurationRevision());});
        waitServer(context,world,level->station(level).stationName().equals(NAME),250,"Actual station name/save input failed");close(context);
        ItemStack mail=mail();slot(world,9,apples(2));slot(world,10,mail);setupPostbox(context,world,mail);
        setupDisplays(context,world);
        var fixture=assemble(context,world,mail);
        waitServer(context,world,level->cargo(fixture.train,mail)==1&&postbox(level).items().stream().noneMatch(PackageItem::isPackage),400,"Natural real-station mail transfer did not debit the original bound postbox into actual Train custody");
        assertOwnership(world,fixture,11,2);
        server.runCommand("item replace entity @p weapon.mainhand with create:schedule 1");
        context.waitFor(client->client.player.getMainHandItem().is(AllItems.SCHEDULE)&&client.player.getMainHandItem().getCount()==1,200);
        openSchedule(context);editSchedule(context,world);
        var expected=context.computeOnClient(client->probe(client).create$getSchedule().write(client.player.registryAccess()));
        clickScheduleWidget(context,accessor->accessor.create$getConfirm());context.waitFor(client->client.gui.screen()==null,200);
        waitServer(context,world,level->actual(level).getMainHandItem().is(AllItems.SCHEDULE)&&actual(level).getMainHandItem().getCount()==1&&actual(level).getMainHandItem().get(AllDataComponents.TRAIN_SCHEDULE).equals(expected),250,"Actual menu-bound save did not write the full native editor data to the one original physical Schedule");
        openSchedule(context);var reloading=context.computeOnClient(Minecraft::reloadResourcePacks);
        context.waitFor(client->reloading.isDone()&&client.gui.overlay()==null,650);reloading.join();
        context.runOnClient(client->{if(!(client.gui.screen() instanceof ScheduleScreen)||!probe(client).create$getSchedule().write(client.player.registryAccess()).equals(expected))throw new AssertionError("Native resource reload lost original card/OR/ghost/noncyclic data");for(var texture:List.of(ScheduleTextures.SCHEDULE,ScheduleTextures.SCHEDULE_EDITOR))if(client.getResourceManager().getResource(texture.location).isEmpty())throw new AssertionError("Real native editor texture is missing after reload: "+texture.location);});
        screenshot(context,"create-vulkan-schedule-editor-saved-cards-or-clone-reorder-after-resource-reload");clickScheduleWidget(context,accessor->accessor.create$getConfirm());context.waitFor(client->client.gui.screen()==null,200);
        stand(context,world,PIG_SEAT.getX()+.5,PIG_SEAT.getZ()+3.5);clickConductor(context,fixture.conductor);
        waitServer(context,world,level->actual(level).getMainHandItem().isEmpty()&&fixture.train.runtime.getSchedule()!=null&&!fixture.train.runtime.isAutoSchedule&&fixture.train.runtime.getSchedule().write(level.registryAccess()).equals(expected),250,"Real native NPC interaction did not debit the one original paid edited Schedule into actual Train custody");
        server.runOnServer(ignored->traceNavigation(world.getConnection().getServerLevel(),fixture.train));
        waitServer(context,world,level->fixture.train.runtime.state==ScheduleRuntime.State.POST_TRANSIT&&fixture.train.runtime.currentEntry==0&&!fixture.train.runtime.paused&&fixture.train.getCurrentStation()==station(level).getStation()&&!fixture.train.runtime.conditionContext.isEmpty()&&fixture.train.runtime.conditionContext.getFirst().getIntOr("CurrentDisplay",-1)==11,500,"Genuine source destination/current-station navigation and cargo predicate never reached natural POST_TRANSIT; do not manufacture navigation/state");
        waitServer(context,world,level->boardText(level,0,2).equals(fixture.train.name.getString())&&boardText(level,0,4).equals("7")&&statusText(level).equals("Cargo: 11/13 Items")&&!fixture.train.runtime.displayLinkUpdateRequested,350,"Actual runtime departure/status-change notification did not reach real station links and native targets");
        server.runOnServer(ignored->assertLinkOwner(world.getConnection().getServerLevel(),true));
        verifyDisplays(context,fixture.train.name.getString(),"7");stand(context,world,BOARD.getX()+4,BOARD.getZ()+7.5);look(context,Vec3.atCenterOf(BOARD.east(3).below()));screenshot(context,"create-vulkan-schedule-real-conductor-natural-post-transit-cargo-eleven-and-departure-platform");
        assertOwnership(world,fixture,11,2);
        stand(context,world,I.getX()+.5,I.getZ()+3.5);clickMounted(context,world,fixture.body,I.subtract(B));
        context.waitFor(client->client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen&&client.player.containerMenu instanceof ChestMenu&&ItemStack.matches(client.player.containerMenu.getSlot(0).getItem(),apples(11)),250);
        clickContainer(context,27,176,168);context.waitFor(client->ItemStack.matches(client.player.containerMenu.getCarried(),apples(2)),150);clickContainer(context,0,176,168);
        context.waitFor(client->client.player.containerMenu.getCarried().isEmpty()&&ItemStack.matches(client.player.containerMenu.getSlot(0).getItem(),apples(13)),200);close(context);
        waitServer(context,world,level->cargo(fixture.train,apples(1))==13&&actual(level).getInventory().getItem(9).isEmpty()&&fixture.train.runtime.currentEntry==1&&fixture.train.runtime.state==ScheduleRuntime.State.PRE_TRANSIT,400,"Actual committed mounted-barrel stock did not satisfy the genuine source predicate and advance runtime naturally");
        waitServer(context,world,level->boardText(level,0,1).equals(" . "),350,"Actual departure cache/passive refresh did not replace a no-longer-matching departure with original padding");
        assertOwnership(world,fixture,13,0);verifyDisplays(context,"","");
        unloadAndReturn(context,world,fixture,expected);
        stand(context,world,S.getX()+.5,S.getZ()+2.5);openDisplay(context,SUMMARY);assertNumericDraft(context,72,8);screenshot(context,"create-vulkan-schedule-numeric-departure-options-retained-after-native-chunk-return");context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null,200);
        stand(context,world,PIG_SEAT.getX()+.5,PIG_SEAT.getZ()+3.5);clickConductor(context,fixture.conductor);
        waitServer(context,world,level->fixture.train.runtime.getSchedule()==null&&scheduleCount(actual(level))==1,250,"Actual empty-hand conductor interaction failed to return exactly one paid Schedule");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();ItemStack returned=ItemStack.EMPTY;for(int i=0;i<actual(level).getInventory().getContainerSize();i++)if(actual(level).getInventory().getItem(i).is(AllItems.SCHEDULE))returned=actual(level).getInventory().getItem(i);var data=returned.get(AllDataComponents.TRAIN_SCHEDULE).copy();if(data.getIntOr("Progress",-1)!=1)throw new AssertionError("Actual paid return lost genuine runtime progress");data.remove("Progress");var original=expected.copy();original.remove("Progress");if(!data.equals(original))throw new AssertionError("Returned paid Schedule lost original editor/ghost/OR/deep-clone data");});
        // Empty hand remains selected at slot0 unless vanilla inventory chose it for the returned item.
        selectEmpty(context,world);stand(context,world,S.getX()+.5,S.getZ()+2.5);openStation(context);context.waitFor(client->client.gui.screen() instanceof StationScreen screen&&screen.snapshot().canDisassemble(),300);clickStation(context,StationScreen::assemblyCoordinate);
        waitServer(context,world,level->TrainManager.get(level).get(fixture.train.id)==null&&fixture.train.carriages.stream().allMatch(Carriage::isWorldRestored),600,"Actual station GUI did not finish original physical Train restoration");close(context);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var barrel=(BarrelBlockEntity)level.getBlockEntity(I);if(barrel==null||!ItemStack.matches(barrel.getItem(0),apples(13))||restoredMail(level,fixture.originalMail)!=1||!(level.getEntity(fixture.conductor).getVehicle() instanceof SeatEntity seat)||!seat.blockPosition().equals(PIG_SEAT)||scheduleCount(actual(level))!=1||postbox(level).items().stream().anyMatch(PackageItem::isPackage))throw new AssertionError("Real restoration lost or duplicated original cargo/mail/conductor/paid Schedule");});
        stand(context,world,I.getX()+.5,I.getZ()+3.5);look(context,Vec3.atCenterOf(I));screenshot(context,"create-vulkan-schedule-real-paid-return-and-restored-original-mail-cargo-conductor");
    }

    private static void traceNavigation(ServerLevel level,Train train) {
        System.out.println("CREATE_SCHEDULE_ACTUAL_NAVIGATION currentStation="+train.currentStation+" physicalStation="+station(level).getStation().id+" forwardConductor="+train.hasForwardConductor()+" backwardConductor="+train.hasBackwardConductor()+" doubleEnded="+train.doubleEnded+" state="+train.runtime.state+" entry="+train.runtime.currentEntry+" speed="+train.speed);
        for(var row:train.carriages)for(var point:List.of(row.getLeadingPoint(),row.getTrailingPoint())) {
            System.out.println("CREATE_SCHEDULE_ACTUAL_WHEEL position="+point.position+" node1="+(point.node1==null?null:point.node1.getLocation())+" node2="+(point.node2==null?null:point.node2.getLocation())+" edgeLength="+(point.edge==null?null:point.edge.getLength()));
            if(point.edge!=null)for(var edgePoint:point.edge.getEdgeData().getPoints())System.out.println("CREATE_SCHEDULE_ACTUAL_EDGE_POINT id="+edgePoint.id+" type="+edgePoint.getClass().getSimpleName()+" location="+edgePoint.getLocationOn(point.edge)+" approach1="+(edgePoint instanceof GlobalStation target&&point.node1!=null?target.canApproachFrom(point.node1):null)+" approach2="+(edgePoint instanceof GlobalStation target&&point.node2!=null?target.canApproachFrom(point.node2):null));
        }
    }

    private static void setupPostbox(ClientGameTestContext context,TestSingleplayerContext world,ItemStack mail) {
        world.getServer().runCommand("item replace entity @p weapon.mainhand with create:white_postbox 1");stand(context,world,S.getX()+.5,S.getZ()+2.5);useBlock(context,S,Vec3.atCenterOf(S).add(0,.25,0),null);
        waitServer(context,world,level->GlobalPos.of(level.dimension(),S).equals(com.simibubi.create.test.mixin.PostboxSelectionAccessor.create$getSelected().get(actual(level))),250,"Actual native station selection was not accepted before moving the physical Postbox item");
        stand(context,world,BOX.getX()+.5,BOX.getZ()+2.5);useBlock(context,BOX.below(),Vec3.atCenterOf(BOX.below()).add(0,.499,0),Direction.UP);
        waitServer(context,world,level->postbox(level)!=null&&postbox(level).isBoundTo(station(level).getStation())&&actual(level).getMainHandItem().isEmpty(),250,"Native paid Postbox placement did not create and bind its actual original world owner");
        world.getServer().runOnServer(server->{var level=world.getConnection().getServerLevel();System.out.println("CREATE_SCHEDULE_ACTUAL_POSTBOX target="+postbox(level).target+" menu="+actual(level).containerMenu.getClass().getName()+" slot="+actual(level).containerMenu.containerId);});
        context.waitFor(client->client.gui.screen() instanceof PackagePortScreen,250);typeField(context,client->((PackagePortScreen)client.gui.screen()).addressBox(),HUB);
        clickPort(context,19);context.waitFor(client->ItemStack.isSameItemSameComponents(client.player.containerMenu.getCarried(),mail),150);clickPort(context,0);
        context.waitFor(client->client.player.containerMenu.getCarried().isEmpty()&&ItemStack.isSameItemSameComponents(client.player.containerMenu.getSlot(0).getItem(),mail),150);
        screenshot(context,"create-vulkan-schedule-real-postbox-station-selection-native-address-and-original-parcel");context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null,200);
        waitServer(context,world,level->postbox(level)!=null&&postbox(level).isBoundTo(station(level).getStation())&&postbox(level).addressFilter.equals(HUB)&&postbox(level).items().stream().anyMatch(item->ItemStack.matches(item,mail))&&station(level).getStation().ports().stream().anyMatch(port->port.pos().equals(BOX)&&port.data().address().equals(HUB)&&!port.data().primed()),300,"Actual paid postbox native address/menu did not register genuine station mail ownership");
    }

    private static void setupDisplays(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();for(int y=0;y<2;y++)for(int x=0;x<8;x++)block(world,BOARD.east(x).below(y),"create:display_board[facing=south,waterlogged=false]");block(world,BOARD.west(),"create:cogwheel[axis=z]");block(world,BOARD.west().north(),"create:creative_motor[facing=south]");block(world,SIGN,"minecraft:oak_sign[rotation=0]");
        server.runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(BOARD.west().north())).setGeneratedSpeed(64));
        waitServer(context,world,level->board(level)!=null&&board(level).isController&&board(level).xSize==8&&board(level).ySize==2&&board(level).isSpeedRequirementFulfilled(),300,"Actual native eight-by-two departure board lacks real cog/motor rotation");
        server.runCommand("item replace entity @p weapon.mainhand with create:display_link 2");stand(context,world,BOARD.getX()+1.5,BOARD.getZ()+3.5);useBlock(context,BOARD.east().below(),Vec3.atCenterOf(BOARD.east().below()).add(0,0,.3),null);
        context.waitFor(client->client.player.getMainHandItem().has(AllDataComponents.CLICK_TO_LINK_DATA),200);
        standOn(context,world,S.south(2));useBlock(context,S,Vec3.atCenterOf(S).add(.25,.499,0),Direction.UP);
        waitServer(context,world,level->link(level,SUMMARY)!=null&&link(level,SUMMARY).getSourcePosition().equals(S),250,"Actual selected/debited link item did not mount the station summary reader");
        context.waitFor(client->client.player.getMainHandItem().getCount()==1&&!client.player.getMainHandItem().has(AllDataComponents.CLICK_TO_LINK_DATA),200);
        server.runOnServer(ignored->assertLinkOwner(world.getConnection().getServerLevel(),false));
        // The remaining original physical link stays in slot0; native hotbar input selects an empty hand.
        context.getInput().pressKey(InputConstants.KEY_2);context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);
        openDisplay(context,SUMMARY);chooseDisplaySource(context,AllDisplaySources.STATION_SUMMARY.getId());assertNumericDraft(context,50,3);
        typeField(context,client->((DisplayLinkScreen)client.gui.screen()).children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow(),FILTER);
        wheelNumber(context,0,1,false);assertNumericDraft(context,51,3);wheelNumber(context,0,1,true);assertNumericDraft(context,60,3);
        for(int i=0;i<12;i++)wheelNumber(context,0,1,false);wheelNumber(context,0,1,false);wheelNumber(context,1,1,true);assertNumericDraft(context,72,4);for(int i=0;i<4;i++)wheelNumber(context,1,1,false);assertNumericDraft(context,72,8);
        screenshot(context,"create-vulkan-schedule-departure-native-numeric-name-seventy-two-platform-eight-and-station-filter");context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null,200);
        waitServer(context,world,level->link(level,SUMMARY).activeSource==AllDisplaySources.STATION_SUMMARY&&link(level,SUMMARY).getSourceConfig().getIntOr("NameColumn",-1)==72&&link(level,SUMMARY).getSourceConfig().getIntOr("PlatformColumn",-1)==8&&link(level,SUMMARY).getSourceConfig().getStringOr("Filter","").equals(FILTER),250,"Native bounded numeric draft did not persist actual source settings");
        block(world,S.south(2),"minecraft:air");context.getInput().pressKey(InputConstants.KEY_1);context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.DISPLAY_LINK.asItem())&&client.player.getMainHandItem().getCount()==1,200);
        stand(context,world,SIGN.getX()+.5,SIGN.getZ()+2.5);useBlock(context,SIGN,Vec3.atCenterOf(SIGN),null);context.waitFor(client->client.player.getMainHandItem().has(AllDataComponents.CLICK_TO_LINK_DATA),200);
        stand(context,world,S.getX()+.5,S.getZ()+3.5);useBlock(context,S,Vec3.atCenterOf(S).add(0,0,.499),Direction.SOUTH);
        waitServer(context,world,level->link(level,STATUS)!=null&&link(level,STATUS).getSourcePosition().equals(S)&&actual(level).getMainHandItem().isEmpty(),250,"Second original physical link did not place the true station status reader");
        openDisplay(context,STATUS);chooseDisplaySource(context,AllDisplaySources.TRAIN_STATUS.getId());context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null,200);
        waitServer(context,world,level->link(level,STATUS).activeSource==AllDisplaySources.TRAIN_STATUS,250,"Native source selector did not bind the actual station status consumer");
        context.getInput().pressKey(InputConstants.KEY_1);context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);
    }

    private static Fixture assemble(ClientGameTestContext context,TestSingleplayerContext world,ItemStack mail) {
        stand(context,world,S.getX()+.5,S.getZ()+2.5);openStation(context);clickStation(context,StationScreen::modeCoordinate);context.waitFor(client->client.gui.screen() instanceof StationScreen screen&&screen.snapshot().assembling(),200);close(context);
        world.getServer().runCommand("item replace entity @p weapon.mainhand with create:railway_casing 2");
        for(var bogey:List.of(B,SECOND)){stand(context,world,bogey.getX()+.5,bogey.getZ()+2.5);useBlock(context,bogey.below(),Vec3.atBottomCenterOf(bogey.below()).add(0,.1,0),null);waitServer(context,world,level->LoadedLevelAccess.blockState(level,bogey).is(AllBlocks.SMALL_BOGEY),250,"Actual casing item failed to place source bogey "+bogey);}
        NativeContraptionFixtures.awaitEntityReady(context,world,400,B,LAST,PIG_SEAT);
        var pigId=new AtomicReference<UUID>();
        world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(C,AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING,Direction.WEST));level.setBlockAndUpdate(B.east(5).above(),AllBlocks.TRAIN_CONTROLS.defaultBlockState().setValue(ControlsBlock.FACING,Direction.EAST));for(var p:List.of(B.east(),B.east(3),B.east(4),B.east(5)))level.setBlockAndUpdate(p,Blocks.OAK_PLANKS.defaultBlockState());for(var p:List.of(I,LAST))level.setBlockAndUpdate(p,Blocks.BARREL.defaultBlockState());((BarrelBlockEntity)level.getBlockEntity(I)).setItem(0,apples(11));level.setBlockAndUpdate(PIG_SEAT,AllBlocks.SEATS.get(DyeColor.RED).defaultBlockState());var pig=EntityTypes.PIG.create(level,EntitySpawnReason.COMMAND);if(pig==null)throw new AssertionError("Native pig factory refused real conductor");pig.setNoAi(true);pig.setPersistenceRequired();pig.setCustomName(Component.literal("Original Schedule conductor"));pig.setPos(Vec3.atCenterOf(PIG_SEAT).add(0,.5,0));if(!level.addFreshEntity(pig))throw new AssertionError("Native level refused original conductor");NativeContraptionFixtures.sitDown(level,PIG_SEAT,pig);pigId.set(pig.getUUID());if(!level.addFreshEntity(new SuperGlueEntity(level,SuperGlueEntity.span(B,LAST.above()))))throw new AssertionError("Actual physical carriage glue was refused");});
        NativeContraptionFixtures.awaitCaptureReady(context,world,400,B,LAST.above());world.getServer().runCommand("item replace entity @p weapon.mainhand with minecraft:air");stand(context,world,S.getX()+.5,S.getZ()+2.5);openStation(context);clickStation(context,StationScreen::preflightCoordinate);context.waitFor(client->client.gui.screen() instanceof StationScreen screen&&screen.snapshot().preflight()==StationAssemblyPreflight.Status.READY_FOR_CARRIAGE_CAPTURE,400);screenshot(context,"create-vulkan-schedule-physical-conductor-controls-cargo-and-real-station-preflight");clickStation(context,StationScreen::assemblyCoordinate);
        var train=new AtomicReference<Train>();var body=new AtomicReference<UUID>();
        waitServer(context,world,level->{var found=station(level).getStation().getPresentTrain();if(found==null||TrainManager.get(level).get(found.id)!=found)return false;var front=found.carriages.getFirst().anyAvailableEntity();if(front==null||!(front.getContraption() instanceof CarriageContraption cc)||level.getEntity(pigId.get()).getVehicle()!=front)return false;var actualSeat=cc.getSeatOf(pigId.get());if(actualSeat==null||cc.conductorSeats.get(actualSeat)==null||(!front.checkConductors().getFirst()||!found.hasForwardConductor()))return false;train.set(found);body.set(front.getUUID());return found.carriages.size()==1&&found.carriages.getFirst().isOnTwoBogeys()&&found.carriages.getFirst().storage.ownsContents()&&LoadedLevelAccess.blockEntity(level,I)==null;},500,"Only true GUI-captured/adopted Train with real seated NPC conductor is accepted");
        close(context);waitClientFront(context,body.get());return new Fixture(train.get(),body.get(),pigId.get(),mail.copy());
    }

    private static void editSchedule(ClientGameTestContext context,TestSingleplayerContext world) {
        context.runOnClient(client->{if(!probe(client).create$getSchedule().entries.isEmpty())throw new AssertionError("The original physical blank Schedule was not empty");});
        addCard(context);typeField(context,ScheduleRenderingTest::firstEditBox,"Native Schedule");screenshot(context,"create-vulkan-schedule-native-destination-typing-and-real-registered-station-suggestions");typeField(context,ScheduleRenderingTest::firstEditBox,NAME);confirmEditor(context);
        condition(context,0,0,0,false);chooseKind(context,Create.asResource("item_threshold"));typeField(context,ScheduleRenderingTest::firstEditBox,"12");
        context.getInput().holdKey(InputConstants.KEY_LSHIFT);try{clickContainer(context,0,256,226);}finally{context.getInput().releaseKey(InputConstants.KEY_LSHIFT);}
        context.waitFor(client->ItemStack.matches(((ScheduleMenu)client.player.containerMenu).ghostInventory.getItem(0),apples(1))&&ItemStack.matches(client.player.getInventory().getItem(9),apples(2))&&client.player.containerMenu.getCarried().isEmpty(),200);
        waitServer(context,world,level->actual(level).containerMenu instanceof ScheduleMenu menu&&ItemStack.matches(menu.ghostInventory.getItem(0),apples(1))&&ItemStack.matches(actual(level).getInventory().getItem(9),apples(2)),200,"Real native ghost-slot input debited sample stock or failed server synchronization");
        screenshot(context,"create-vulkan-schedule-native-cargo-threshold-typing-and-paid-physical-ghost-sample");confirmEditor(context);
        addAlternative(context,0);chooseKind(context,Create.asResource("powered"));confirmEditor(context);
        screenshot(context,"create-vulkan-schedule-real-or-column-and-original-cargo-sample");
        addCard(context);chooseKind(context,Create.asResource("package_delivery"));
        context.runOnClient(client->{if(!(probe(client).create$getEditingInstruction() instanceof DeliverPackagesInstruction)||((ScheduleMenu)client.player.containerMenu).targetSlotsActive!=0)throw new AssertionError("Real source delivery type did not replace native editor fields/slot policy");});
        chooseKind(context,Create.asResource("package_retrieval"));typeField(context,ScheduleRenderingTest::firstEditBox,REMOTE);
        screenshot(context,"create-vulkan-schedule-native-postal-instruction-type-and-real-mail-address-filter");confirmEditor(context);
        context.runOnClient(client->{var data=probe(client).create$getSchedule();if(!(data.entries.get(1).instruction instanceof FetchPackagesInstruction fetch)||!fetch.getFilter().equals(REMOTE))throw new AssertionError("Native postal type/address input failed actual Schedule data");});
        removeCard(context,1);
        cloneCard(context,0);instruction(context,0);typeField(context,ScheduleRenderingTest::firstEditBox,MISSING);confirmEditor(context);
        condition(context,0,0,0,false);typeField(context,ScheduleRenderingTest::firstEditBox,"99");confirmEditor(context);
        context.runOnClient(client->{var data=probe(client).create$getSchedule();if(data.entries.size()!=2||((ItemThresholdCondition)data.entries.get(1).conditions.getFirst().getFirst()).getThreshold()!=12||((ItemThresholdCondition)data.entries.getFirst().conditions.getFirst().getFirst()).getThreshold()!=99)throw new AssertionError("Actual clone edit mutated original card threshold; native clone must deeply preserve source data");});
        moveDown(context,0);clickScheduleWidget(context,accessor->accessor.create$getCyclic());
        context.runOnClient(client->{var data=probe(client).create$getSchedule();if(data.cyclic||data.entries.size()!=2||!((DestinationInstruction)data.entries.getFirst().instruction).getFilter().equals(NAME)||!((DestinationInstruction)data.entries.get(1).instruction).getFilter().equals(MISSING)||data.entries.getFirst().conditions.size()!=2||!(data.entries.getFirst().conditions.get(1).getFirst() instanceof StationPoweredCondition)||((ItemThresholdCondition)data.entries.getFirst().conditions.getFirst().getFirst()).getThreshold()!=12||!ItemStack.matches(data.entries.getFirst().conditions.getFirst().getFirst().getItem(0),apples(1)))throw new AssertionError("Native clone/reorder/OR/noncyclic inputs failed exact final source model");});
        screenshot(context,"create-vulkan-schedule-native-two-cards-or-deep-clone-reorder-and-noncyclic-save");
    }

    private static void unloadAndReturn(ClientGameTestContext context,TestSingleplayerContext world,Fixture f,CompoundTag schedule) {
        var oldFront=world.getServer().computeOnServer(server->world.getConnection().getServerLevel().getEntity(f.body));
        var oldConductor=world.getServer().computeOnServer(server->world.getConnection().getServerLevel().getEntity(f.conductor));
        var originals=world.getServer().computeOnServer(server->{var level=world.getConnection().getServerLevel();if(!server.saveEverything(true,true,true))throw new AssertionError("Native source save was refused");return new BlockEntity[]{station(level),link(level,SUMMARY),link(level,STATUS),board(level),postbox(level),(BlockEntity)level.getBlockEntity(SIGN)};});
        world.getServer().runCommand("gamemode spectator @p");world.getServer().runCommand("tp @p 48400.5 20 48400.5");
        waitServer(context,world,level->!anyChunk(level),900,"True native full fixture chunk absence did not occur after real far teleport");NativeFixtureChunkUnload.finish(context,world,originals);
        waitServer(context,world,level->oldFront.isRemoved()&&oldConductor.isRemoved()&&level.getEntity(f.body)==null&&level.getEntity(f.conductor)==null&&f.train.carriages.getFirst().anyAvailableEntity()==null,850,"Original carriage/conductor did not retire through genuine native chunk ownership");
        assertOwnership(world,f,13,0);
        world.getServer().runCommand("tp @p 47064.5 18 24.5");
        waitServer(context,world,level->{
            if(!allChunks(level))return false;
            var freshStation=station(level);var summary=link(level,SUMMARY);var status=link(level,STATUS);
            var freshBoard=board(level);var mail=postbox(level);var sign=level.getBlockEntity(SIGN);
            if(freshStation==null||freshStation==originals[0]||summary==null||summary==originals[1]
                ||status==null||status==originals[2]||freshBoard==null||freshBoard==originals[3]
                ||mail==null||mail==originals[4]||!(sign instanceof SignBlockEntity)||sign==originals[5])return false;
            // Fresh BEs can arrive before their genuine world-owned graph point is rebound.
            var point=freshStation.getStation();
            if(point==null||!point.ownedBy(freshStation)
                ||!freshStation.targeting().id().equals(((StationBlockEntity)originals[0]).targeting().id())
                ||!point.id.equals(freshStation.targeting().id()))return false;
            if(!(level.getEntity(f.body) instanceof CarriageContraptionEntity front)||front==oldFront)return false;
            var conductor=level.getEntity(f.conductor);if(conductor==null||conductor==oldConductor)return false;
            return front.getCarriage()==f.train.carriages.getFirst()&&conductor.getVehicle()==front
                &&TrainManager.get(level).get(f.train.id)==f.train&&f.train.runtime.getSchedule()!=null
                &&f.train.runtime.currentEntry==1&&f.train.runtime.getSchedule().write(level.registryAccess()).equals(schedule)
                &&summary.getSourceConfig().getIntOr("NameColumn",-1)==72&&summary.getSourceConfig().getIntOr("PlatformColumn",-1)==8
                &&summary.getSourceConfig().getStringOr("Filter","").equals(FILTER)
                &&summary.activeSource==AllDisplaySources.STATION_SUMMARY&&status.activeSource==AllDisplaySources.TRAIN_STATUS
                &&summary.getTargetPosition().equals(BOARD.east().below())&&status.getTargetPosition().equals(SIGN)
                &&mail.isBoundTo(point);
        },850,"Actual original-UUID native frontend/conductor/source/target/mail return lost genuine paid Schedule/settings ownership");
        waitClientFront(context,f.body);assertOwnership(world,f,13,0);verifyDisplays(context,"","");world.getConnection().waitForChunksRender();
    }

    private static void addCard(ClientGameTestContext c){schedulePoint(c,(screen,p)->{int y=25;for(var e:p.create$getSchedule().entries)y+=cardHeight(e)+10;return new Vec2(50,y+7-p.create$getScroll().getValue(0));},0);c.waitFor(client->probe(client).create$getEditingInstruction()!=null,200);}
    private static void instruction(ClientGameTestContext c,int card){schedulePoint(c,(screen,p)->new Vec2(75,cardY(p,card)+12),0);c.waitFor(client->probe(client).create$getEditingInstruction()!=null,200);}
    private static void condition(ClientGameTestContext c,int card,int column,int row,boolean right){schedulePoint(c,(screen,p)->{var entry=p.create$getSchedule().entries.get(card);float x=51-p.create$getConditionScroll(entry,0,card);for(int i=0;i<column;i++)x+=p.create$getConditionColumnWidth(entry.conditions.get(i))+10;return new Vec2(x+8,cardY(p,card)+35+row*18);},right?1:0);if(!right)c.waitFor(client->probe(client).create$getEditingCondition()!=null,200);}
    private static void addAlternative(ClientGameTestContext c,int card){schedulePoint(c,(screen,p)->{var entry=p.create$getSchedule().entries.get(card);float x=51-p.create$getConditionScroll(entry,0,card);for(var column:entry.conditions)x+=p.create$getConditionColumnWidth(column)+10;return new Vec2(x+4,cardY(p,card)+35);},0);c.waitFor(client->probe(client).create$getEditingCondition()!=null,200);}
    private static void cloneCard(ClientGameTestContext c,int card){int before=c.computeOnClient(client->probe(client).create$getSchedule().entries.size());schedulePoint(c,(screen,p)->new Vec2(211,cardY(p,card)+cardHeight(p.create$getSchedule().entries.get(card))-7),0);c.waitFor(client->probe(client).create$getSchedule().entries.size()==before+1,200);}
    private static void removeCard(ClientGameTestContext c,int card){int before=c.computeOnClient(client->probe(client).create$getSchedule().entries.size());schedulePoint(c,(screen,p)->new Vec2(211,cardY(p,card)+7),0);c.waitFor(client->probe(client).create$getSchedule().entries.size()==before-1,200);}
    private static void moveDown(ClientGameTestContext c,int card){schedulePoint(c,(screen,p)->new Vec2(224,cardY(p,card)+26),0);c.waitTicks(8);}
    private static int cardHeight(ScheduleEntry e){int rows=e.conditions.stream().mapToInt(List::size).max().orElse(0);return 22+(e.instruction.supportsConditions()?24+rows*18:4);}
    private static float cardY(ScheduleScreenAccessor p,int index){float y=25-p.create$getScroll().getValue(0);for(int i=0;i<index;i++)y+=cardHeight(p.create$getSchedule().entries.get(i))+10;return y;}
    private static void schedulePoint(ClientGameTestContext c,BiFunction<ScheduleScreen,ScheduleScreenAccessor,Vec2> position,int button){var point=c.computeOnClient(client->{var screen=(ScheduleScreen)client.gui.screen();var p=position.apply(screen,probe(client));if(p.y<25||p.y>=198||p.x<25||p.x>=230)throw new AssertionError("Actual source card coordinate is outside native clipped action area: "+p);return physical(client,(screen.width-256)/2f+p.x,(screen.height-226)/2f+p.y);});click(c,point,button);c.waitTicks(5);}
    private static void confirmEditor(ClientGameTestContext c){clickScheduleWidget(c,p->p.create$getEditorConfirm());c.waitFor(client->probe(client).create$getEditingCondition()==null&&probe(client).create$getEditingInstruction()==null,200);}
    private static void chooseKind(ClientGameTestContext c,net.minecraft.resources.Identifier target){int desired=c.computeOnClient(client->{var p=probe(client);var list=p.create$getEditingCondition()!=null?Schedule.CONDITION_TYPES:Schedule.INSTRUCTION_TYPES;for(int i=0;i<list.size();i++)if(list.get(i).getFirst().equals(target))return i;throw new AssertionError("Missing genuine source editor type: "+target);});for(int tries=0;tries<16;tries++){int state=c.computeOnClient(client->probe(client).create$getTypeSelector().getState());if(state==desired)return;var at=c.computeOnClient(client->center(client,probe(client).create$getTypeSelector()));c.getInput().setCursorPos(at.x,at.y);c.getInput().scroll(state<desired?-1:1);c.waitTicks(2);}throw new AssertionError("Native source type wheel did not select "+target);}
    private static void clickScheduleWidget(ClientGameTestContext c,Function<ScheduleScreenAccessor,AbstractWidget> widget){click(c,c.computeOnClient(client->center(client,widget.apply(probe(client)))),0);c.waitTicks(3);}
    private static ScheduleScreenAccessor probe(Minecraft client){return (ScheduleScreenAccessor)(Object)(ScheduleScreen)client.gui.screen();}
    private static EditBox firstEditBox(Minecraft client){var all=new ArrayList<EditBox>();for(var child:client.gui.screen().children())collectBoxes(child,all);if(all.size()!=1)throw new AssertionError("Expected exactly one genuine source text widget, got "+all.size());return all.getFirst();}
    private static void collectBoxes(GuiEventListener element,List<EditBox> out){if(element instanceof EditBox field)out.add(field);if(element instanceof ContainerEventHandler container)for(var child:container.children())collectBoxes(child,out);}
    private static void typeField(ClientGameTestContext c,Function<Minecraft,EditBox> getter,String text){int old=c.computeOnClient(client->getter.apply(client).getValue().length());click(c,c.computeOnClient(client->center(client,getter.apply(client))),0);c.getInput().pressKey(InputConstants.KEY_END);for(int i=0;i<old;i++)c.getInput().pressKey(InputConstants.KEY_BACKSPACE);c.getInput().typeChars(text);c.waitTicks(2);c.runOnClient(client->{var field=getter.apply(client);System.out.println("CREATE_SCHEDULE_ACTUAL_TEXT_EDIT requested="+text+" oldLength="+old+" actual="+field.getValue()+" cursor="+field.getCursorPosition()+" fieldFocused="+field.isFocused()+" screenFocused="+client.gui.screen().getFocused()+" fieldBounds="+field.getX()+","+field.getY()+","+field.getWidth()+","+field.getHeight());});if(text.equals("99"))screenshot(c,"create-vulkan-schedule-numeric-clone-real-typing-diagnostic");c.waitFor(client->getter.apply(client).getValue().equals(text),200);}
    private static void openSchedule(ClientGameTestContext c){c.getInput().lookAt(0,-65);c.waitTicks(4);c.getInput().pressKey(options->options.keyUse);c.waitFor(client->client.gui.screen() instanceof ScheduleScreen&&client.player.containerMenu instanceof ScheduleMenu,250);c.waitTicks(3);}
    private static void wheelNumber(ClientGameTestContext c,int index,int direction,boolean shift){var at=c.computeOnClient(client->center(client,((DisplayLinkScreen)client.gui.screen()).children().stream().filter(ScrollInput.class::isInstance).map(ScrollInput.class::cast).toList().get(index)));c.getInput().setCursorPos(at.x,at.y);c.waitTicks(2);c.runOnClient(client->{var w=client.getWindow();double x=client.mouseHandler.getScaledXPos(w),y=client.mouseHandler.getScaledYPos(w);System.out.println("CREATE_SCHEDULE_NATIVE_NUMBER_SCROLL index="+index+" direction="+direction+" actualGui="+x+","+y+" child="+((DisplayLinkScreen)client.gui.screen()).getChildAt(x,y).map(child->child.getClass().getName()).orElse("none"));});if(shift)c.getInput().holdKey(InputConstants.KEY_LSHIFT);try{c.getInput().scroll(direction);c.waitTicks(2);}finally{if(shift)c.getInput().releaseKey(InputConstants.KEY_LSHIFT);}}
    private static void assertNumericDraft(ClientGameTestContext c,int name,int platform){c.runOnClient(client->{var s=(DisplayLinkScreen)client.gui.screen();var inputs=s.children().stream().filter(ScrollInput.class::isInstance).map(ScrollInput.class::cast).toList();if(inputs.size()!=2||inputs.get(0).getState()!=name||inputs.get(1).getState()!=platform||s.draft().getIntOr("NameColumn",-1)!=name||s.draft().getIntOr("PlatformColumn",-1)!=platform||inputs.get(0).getY()!=inputs.get(1).getY()||inputs.get(0).getX()+inputs.get(0).getWidth()>inputs.get(1).getX()||inputs.get(1).getY()+inputs.get(1).getHeight()>((s.height-162)/2+105))throw new AssertionError("Actual ranged numeric widgets/draft/row bounds disagree with "+name+"/"+platform+" actualStates="+inputs.stream().map(ScrollInput::getState).toList()+" actualDraft="+s.draft()+" actualBounds="+inputs.stream().map(input->List.of(input.getX(),input.getY(),input.getWidth(),input.getHeight())).toList());});}
    private static void chooseDisplaySource(ClientGameTestContext c,net.minecraft.resources.Identifier id){for(int i=0;i<8;i++){if(c.computeOnClient(client->id.equals(((DisplayLinkScreen)client.gui.screen()).selectedSource())))return;click(c,c.computeOnClient(client->center(client,((DisplayLinkScreen)client.gui.screen()).children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getWidth()==135&&b.getY()==((client.gui.screen().height-162)/2+26)).findFirst().orElseThrow())),0);c.waitTicks(2);}throw new AssertionError("Actual source-selector clicks failed "+id);}
    private static void openDisplay(ClientGameTestContext c,BlockPos link){useBlock(c,link,Vec3.atCenterOf(link).add(0,-.25,0),null);c.waitFor(client->client.gui.screen() instanceof DisplayLinkScreen,250);c.waitTicks(3);}
    private static void verifyDisplays(ClientGameTestContext c,String expectedName,String expectedPlatform){
        c.waitFor(client->{
            if(!(client.level.getBlockEntity(BOARD) instanceof FlapDisplayBlockEntity board)||!board.isController||board.getLines().isEmpty())return false;
            var sections=board.getLines().getFirst().getSections();
            if(sections.size()!=5||sections.get(2).getText()==null||sections.get(4).getText()==null||!sections.get(2).getText().getString().strip().equals(expectedName)||!sections.get(4).getText().getString().strip().equals(expectedPlatform))return false;
            for(var section:sections)for(int i=0;i<section.flapCount();i++)if(section.isSpinning(i))return false;
            return true;
        },300);
        c.runOnClient(client->{
            var board=(FlapDisplayBlockEntity)client.level.getBlockEntity(BOARD);
            var render=(FlapDisplayRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(board,.5f,null,true);
            if(render==null||!render.running||render.width!=8||render.height!=2||render.glyphs.isEmpty())throw new AssertionError("Actual native departure board extraction lacks source platform glyphs/rotation");
            var visible=new StringBuilder();for(var glyph:render.glyphs)if(glyph.y()==4.5f)glyph.text().accept((index,style,codepoint)->{visible.appendCodePoint(codepoint);return true;});
            if(!visible.toString().contains(expectedName.toUpperCase(Locale.ROOT))||!visible.toString().contains(expectedPlatform)||expectedName.isEmpty()&&!visible.toString().contains(" . "))throw new AssertionError("Actual settled first-row Vulkan glyphs lost native train/platform text: "+visible);
            System.out.println("CREATE_SCHEDULE_SETTLED_NATIVE_GLYPHS "+visible);
        });
    }
    private static void clickPort(ClientGameTestContext c,int index){click(c,c.computeOnClient(client->{var p=((PackagePortScreen)client.gui.screen()).slotPosition(index);return physical(client,p.x,p.y);}),0);c.waitTicks(3);}
    private static void clickContainer(ClientGameTestContext c,int slot,int width,int height){click(c,c.computeOnClient(client->{var s=client.gui.screen();var nativeSlot=client.player.containerMenu.getSlot(slot);return physical(client,(s.width-width)/2f+nativeSlot.x+8,(s.height-height)/2f+nativeSlot.y+8);}),0);c.waitTicks(3);}
    private static void click(ClientGameTestContext c,Vec2 point,int button){c.getInput().setCursorPos(point.x,point.y);c.waitTicks(2);c.runOnClient(client->{if(client.gui.screen() instanceof StationScreen screen){var w=client.getWindow();double x=client.mouseHandler.getScaledXPos(w),y=client.mouseHandler.getScaledYPos(w);System.out.println("CREATE_SCHEDULE_NATIVE_CLICK requested="+point+" actualRaw="+client.mouseHandler.xpos()+","+client.mouseHandler.ypos()+" actualGui="+x+","+y+" window="+w.getScreenWidth()+"x"+w.getScreenHeight()+" framebuffer="+w.getWidth()+"x"+w.getHeight()+" gui="+w.getGuiScaledWidth()+"x"+w.getGuiScaledHeight()+" field="+screen.nameBox().getValue()+" hovered="+screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(widget->widget.isMouseOver(x,y)).map(widget->widget.getMessage().getString()).toList());}});int nativeButton=switch(button){case 0->InputConstants.MOUSE_BUTTON_LEFT;case 1->InputConstants.MOUSE_BUTTON_RIGHT;default->throw new AssertionError("Unsupported logical Schedule test mouse button: "+button);};boolean shift=c.computeOnClient(Minecraft::hasShiftDown);if(!shift)c.getInput().pressMouse(nativeButton);else{
        // Fabric TestInput6.0.9 always emits modifiers0. Deliver the actual held Shift at the native MouseHandler boundary.
        c.runOnClient(client->client.mouseHandler.onButton(client.getWindow().handle(),new net.minecraft.client.input.MouseButtonInfo(nativeButton,InputConstants.MOD_SHIFT),InputConstants.PRESS));c.waitTicks(1);c.runOnClient(client->client.mouseHandler.onButton(client.getWindow().handle(),new net.minecraft.client.input.MouseButtonInfo(nativeButton,InputConstants.MOD_SHIFT),InputConstants.RELEASE));}}
    private static Vec2 physical(Minecraft client,float x,float y){
        var window=client.getWindow();int width=window.getGuiScaledWidth(),height=window.getGuiScaledHeight();
        if(width<320||height<260||window.getGuiScale()!=2||x<0||y<0||x>=width||y>=height)throw new AssertionError("Actual native input point is outside the readable GUI: "+x+","+y+" in "+width+"x"+height);
        var point=new Vec2(x*window.getScreenWidth()/width,y*window.getScreenHeight()/height);
        if(Math.abs(net.minecraft.client.MouseHandler.getScaledXPos(window,point.x)-x)>.01||Math.abs(net.minecraft.client.MouseHandler.getScaledYPos(window,point.y)-y)>.01)throw new AssertionError("Actual native window/GUI input conversion failed its real MouseHandler inverse");
        return point;
    }
    private static Vec2 center(Minecraft client,AbstractWidget widget){if(widget==null||!widget.visible||!widget.active)throw new AssertionError("Actual native target widget is missing/inactive");if(widget.getX()<0||widget.getY()<0||widget.getX()+widget.getWidth()>client.getWindow().getGuiScaledWidth()||widget.getY()+widget.getHeight()>client.getWindow().getGuiScaledHeight())throw new AssertionError("Actual native target widget is clipped outside the readable window");return physical(client,widget.getX()+widget.getWidth()/2f,widget.getY()+widget.getHeight()/2f);}
    private static void openStation(ClientGameTestContext c){useBlock(c,S,Vec3.atCenterOf(S).add(0,.25,0),null);c.waitFor(client->client.gui.screen() instanceof StationScreen,250);c.waitTicks(2);}
    private static void clickStation(ClientGameTestContext c,Function<StationScreen,Vec2> point){click(c,c.computeOnClient(client->{var p=point.apply((StationScreen)client.gui.screen());return physical(client,p.x,p.y);}),0);c.waitTicks(3);}
    private static void stand(ClientGameTestContext c,TestSingleplayerContext w,double x,double z){w.getServer().runCommand("gamemode survival @p");w.getServer().runCommand("tp @p "+x+" 5 "+z);c.waitFor(client->!client.player.isSpectator()&&client.player.onGround()&&Math.abs(client.player.getX()-x)<.1&&Math.abs(client.player.getZ()-z)<.1,250);}
    private static void standOn(ClientGameTestContext c,TestSingleplayerContext w,BlockPos support){block(w,support,"minecraft:smooth_quartz");w.getServer().runCommand("gamemode survival @p");w.getServer().runCommand("tp @p "+(support.getX()+.5)+" "+(support.getY()+1)+" "+(support.getZ()+.5));c.waitFor(client->client.player.onGround()&&Math.abs(client.player.getY()-(support.getY()+1))<.1,250);}
    private static void useBlock(ClientGameTestContext c,BlockPos pos,Vec3 point,Direction side){look(c,point);c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos)&&(side==null||hit.getDirection()==side),200);c.waitTicks(6);c.getInput().pressKey(options->options.keyUse);}
    private static void look(ClientGameTestContext c,Vec3 target){var angle=c.computeOnClient(client->{var d=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});c.getInput().lookAt(angle.x,angle.y);}
    private static void clickConductor(ClientGameTestContext c,UUID id){look(c,c.computeOnClient(client->{var entity=find(client,id);if(entity==null)throw new AssertionError("Actual native conductor is absent");return entity.getBoundingBox().getCenter();}));c.waitFor(client->client.hitResult instanceof EntityHitResult hit&&hit.getEntity().getUUID().equals(id),250);c.waitTicks(6);c.getInput().pressKey(options->options.keyUse);}
    private static void clickMounted(ClientGameTestContext c,TestSingleplayerContext w,UUID body,BlockPos local){look(c,c.computeOnClient(client->((CarriageContraptionEntity)find(client,body)).toGlobalVector(Vec3.atCenterOf(local),1)));waitServer(c,w,level->{if(!(level.getEntity(body) instanceof CarriageContraptionEntity front))return false;var p=actual(level);var hit=front.rayTrace(p.getEyePosition(),p.getEyePosition().add(p.getViewVector(1).scale(5)),1);return hit!=null&&hit.getBlockPos().equals(local);},200,"Actual physical native player ray did not reach mounted cargo cell");c.waitTicks(6);c.getInput().pressKey(options->options.keyUse);}
    private static Entity find(Minecraft client,UUID id){for(var entity:client.level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private static void waitClientFront(ClientGameTestContext c,UUID body){c.waitFor(client->find(client,body) instanceof CarriageContraptionEntity front&&front.getCarriage()!=null&&front.getContraption()!=null&&front.validForRender&&!front.firstPositionUpdate,450);}
    private static void close(ClientGameTestContext c){c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitFor(client->client.gui.screen()==null,200);}
    private static void selectEmpty(ClientGameTestContext c,TestSingleplayerContext w){for(int i=0;i<9;i++){int slot=i;if(w.getServer().computeOnServer(server->actual(w.getConnection().getServerLevel()).getInventory().getItem(slot).isEmpty())){c.getInput().pressKey(InputConstants.KEY_1+i);c.waitFor(client->client.player.getMainHandItem().isEmpty(),200);return;}}throw new AssertionError("Actual native player has no empty hand slot");}
    private static void slot(TestSingleplayerContext w,int index,ItemStack item){w.getServer().runOnServer(server->{var p=actual(w.getConnection().getServerLevel());p.getInventory().setItem(index,item.copy());p.connection.send(p.getInventory().createInventoryUpdatePacket(index));});}
    private static ItemStack apples(int n){var item=new ItemStack(Items.APPLE,n);item.set(DataComponents.CUSTOM_NAME,Component.literal(APPLES));return item;}
    private static ItemStack mail(){var emeralds=new ItemStack(Items.EMERALD,3);emeralds.set(DataComponents.CUSTOM_NAME,Component.literal("Original Schedule mail contents"));var item=PackageItem.containing(List.of(emeralds));PackageItem.addAddress(item,REMOTE);return item;}
    private static long cargo(Train train,ItemStack stack){long n=0;var variant=ItemVariant.of(stack);for(var carriage:train.carriages)for(var view:carriage.storage.getAllItems())if(view.getResource().equals(variant))n+=view.getAmount();return n;}
    private static void assertOwnership(TestSingleplayerContext w,Fixture f,int trainApples,int playerApples){w.getServer().runOnServer(server->{var level=w.getConnection().getServerLevel();if(TrainManager.get(level).get(f.train.id)!=f.train||f.train.runtime.train!=f.train||!f.train.carriages.getFirst().storage.ownsContents()||cargo(f.train,apples(1))!=trainApples||actual(level).getInventory().getItem(9).getCount()!=playerApples||cargo(f.train,f.originalMail)!=1||retainedMail(level,f.train)||!PackageItem.matchAddress(f.originalMail,REMOTE)||PackageItem.matchAddress(f.originalMail,HUB))throw new AssertionError("Actual global/physical cargo/mail custody or real source/destination address predicate changed");TrainManager.get(level).validateCustody();});}
    private static boolean retainedMail(ServerLevel level,Train train){var box=postbox(level);if(box!=null)return box.items().stream().anyMatch(PackageItem::isPackage);return train.graph.getPoints(StationEdgePoints.STATION).stream().filter(point->point.getBlockEntityPos().equals(S)).flatMap(point->point.ports().stream()).flatMap(port->port.data().items().stream()).anyMatch(PackageItem::isPackage);}
    private static int restoredMail(ServerLevel level,ItemStack original){int count=0;for(var p:List.of(I,LAST)){if(!(level.getBlockEntity(p) instanceof BarrelBlockEntity barrel))throw new AssertionError("Actual restored original barrel missing");for(int i=0;i<barrel.getContainerSize();i++)if(ItemStack.isSameItemSameComponents(barrel.getItem(i),original))count+=barrel.getItem(i).getCount();}return count;}
    private static void assertLinkOwner(ServerLevel level,boolean earned){var link=link(level,SUMMARY);var manager=level.getServer().getAdvancements();var a=manager.get(Create.asResource("display_link"));var b=manager.get(Create.asResource("display_board_0"));if(a==null||b==null)throw new AssertionError("Actual original display advancement/parent criteria resources did not load");var player=actual(level);boolean done=player.getAdvancements().getOrStartProgress(a).isDone()&&player.getAdvancements().getOrStartProgress(b).isDone();if(earned&&!done||done&&link.advancementOwner()!=null||!done&&!player.getUUID().equals(link.advancementOwner()))throw new AssertionError("Actual native placed-player owner or genuine source transfer/departure criterion progress disagrees with original semantics");}
    private static int scheduleCount(ServerPlayer p){int n=0;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(AllItems.SCHEDULE))n+=p.getInventory().getItem(i).getCount();return n;}
    private static ServerPlayer actual(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static StationBlockEntity station(ServerLevel level){return LoadedLevelAccess.blockEntity(level,S) instanceof StationBlockEntity station?station:null;}
    private static PostboxBlockEntity postbox(ServerLevel level){return LoadedLevelAccess.blockEntity(level,BOX) instanceof PostboxBlockEntity box?box:null;}
    private static DisplayLinkBlockEntity link(ServerLevel level,BlockPos p){return LoadedLevelAccess.blockEntity(level,p) instanceof DisplayLinkBlockEntity link?link:null;}
    private static FlapDisplayBlockEntity board(ServerLevel level){return LoadedLevelAccess.blockEntity(level,BOARD) instanceof FlapDisplayBlockEntity board?board:null;}
    private static String boardText(ServerLevel level,int row,int column){var board=board(level);if(board==null||board.getLines().size()<=row||board.getLines().get(row).getSections().size()<=column)return "";var text=board.getLines().get(row).getSections().get(column).getText();return text==null?"":text.getString();}
    private static String statusText(ServerLevel level){return level.getBlockEntity(SIGN) instanceof SignBlockEntity sign?sign.getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString():"";}
    private static boolean allChunks(ServerLevel level){for(int x=47000>>4;x<=47128>>4;x++)for(int z=0;z<=48>>4;z++)if(level.getChunkSource().getChunkNow(x,z)==null)return false;return true;}
    private static boolean anyChunk(ServerLevel level){for(int x=47000>>4;x<=47128>>4;x++)for(int z=0;z<=48>>4;z++)if(level.getChunkSource().getChunkNow(x,z)!=null)return true;return false;}
    private static void block(TestSingleplayerContext w,BlockPos p,String state){w.getServer().runCommand("setblock "+p.getX()+" "+p.getY()+" "+p.getZ()+" "+state);}
    private static void waitServer(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> condition,int ticks,String message){var ready=new AtomicBoolean();for(int tick=0;tick<ticks;tick++){w.getServer().runOnServer(server->ready.set(condition.test(w.getConnection().getServerLevel())));if(ready.get())return;c.waitTicks(1);}throw new AssertionError(message);}
    private static void screenshot(ClientGameTestContext c,String name){c.waitFor(client->client.gui.overlay()==null,250);c.runOnClient(client->{client.gui.hud.getChat().clearMessages(false);var screen=client.gui.screen();if(screen!=null&&(screen.width<320||screen.height<260||client.getWindow().getGuiScale()<2))throw new AssertionError("Native screenshot cannot read the full original editor plus extra area at two physical pixels per GUI pixel");if(screen!=null){int width=screen instanceof ScheduleScreen?256:screen instanceof DisplayLinkScreen?235:screen instanceof PackagePortScreen?220:0;int height=screen instanceof ScheduleScreen?226:screen instanceof DisplayLinkScreen?162:screen instanceof PackagePortScreen?190:0;System.out.println("CREATE_SCHEDULE_UI_SCREENSHOT "+name+" class="+screen.getClass().getSimpleName()+" gui="+screen.width+"x"+screen.height+" scale="+client.getWindow().getGuiScale()+" window="+client.getWindow().getScreenWidth()+"x"+client.getWindow().getScreenHeight()+" framebuffer="+client.getWindow().getWidth()+"x"+client.getWindow().getHeight()+" nativePanel="+((screen.width-width)/2)+","+((screen.height-height)/2)+","+width+","+height+" field/card/OR/slot coordinates=actual read-only native widgets and source geometry");}});c.takeScreenshot(name);}
}
