package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.phys.*;

/** Proposed native SDL selection/paid placement/settings, original seven-item custody, real unload and Vulkan scene. UNRUN. */
final class ArmRenderingTest {
    private static final BlockPos P=new BlockPos(53010,5,8),INPUT=P.west(2),OUTPUT=P.east(2),MOTOR=P.west().below();
    private static final BlockPos CEILING=P.south(10).above(2),JUKEBOX=CEILING.west(2);
    static void run(ClientGameTestContext c,TestSingleplayerContext w){
        int width=c.computeOnClient(client->client.getWindow().getScreenWidth()),height=c.computeOnClient(client->client.getWindow().getScreenHeight()),gui=c.computeOnClient(client->client.options.guiScale().get());
        boolean fullscreen=c.computeOnClient(client->client.options.fullscreen().get()),hidden=c.computeOnClient(client->client.gui.hud.isHidden());
        try{
            c.runOnClient(client->{client.getWindow().setWindowed(Math.max(960,width),Math.max(640,height));client.options.guiScale().set(2);client.resizeGui();if(client.gui.hud.isHidden())client.gui.hud.toggle();});
            c.waitFor(client->client.getWindow().getScreenWidth()>=Math.max(960,width)&&client.getWindow().getScreenHeight()>=Math.max(640,height)&&client.getWindow().getGuiScale()==2,300);
            scene(c,w);
        }finally{
            c.getInput().releaseKey(options->options.keyUse);c.getInput().releaseKey(options->options.keyAttack);
            c.runOnClient(client->{client.getWindow().setWindowed(width,height);client.options.fullscreen().set(fullscreen);client.getWindow().setFullscreen(fullscreen);client.getWindow().updateFullscreenIfChanged();client.options.guiScale().set(gui);client.resizeGui();if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});
            if(!fullscreen)c.waitFor(client->client.getWindow().getScreenWidth()==width&&client.getWindow().getScreenHeight()==height,300);
        }
    }
    private static void scene(ClientGameTestContext c,TestSingleplayerContext w){
        var server=w.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 53010.5 10 12.5");c.waitFor(client->client.level.isLoaded(P)&&client.level.isLoaded(CEILING),400);
        await(c,w,level->{for(int chunkX=53000>>4;chunkX<=53032>>4;chunkX++)for(int chunkZ=0;chunkZ<=24>>4;chunkZ++)if(level.getChunkSource().getChunkNow(chunkX,chunkZ)==null)return false;return true;},500,"Actual native Arm floor footprint did not become FULL before initial fill");
        c.waitFor(client->NativeScreenshotReadiness.skyReady(client,new BlockPos(53001,5,8),new BlockPos(53017,5,8),new BlockPos(53032,5,8),new BlockPos(53001,5,24),new BlockPos(53017,5,24),new BlockPos(53032,5,24)),300);
        server.runCommand("fill 53000 4 0 53032 4 24 minecraft:smooth_quartz");
        await(c,w,level->{for(int x=53000;x<=53032;x++)for(int z=0;z<=24;z++)if(!LoadedLevelAccess.blockState(level,new BlockPos(x,4,z)).is(Blocks.SMOOTH_QUARTZ))return false;return true;},500,"Actual native Arm floor fill did not place all 825 original quartz blocks");
        server.runCommand("clear @p");
        server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();level.setBlockAndUpdate(INPUT,AllBlocks.DEPOT.defaultBlockState());level.setBlockAndUpdate(OUTPUT,AllBlocks.DEPOT.defaultBlockState());try(var tx=Transaction.openOuter()){if(((DepotBlockEntity)level.getBlockEntity(INPUT)).inventory.automation.insert(ItemVariant.of(diamonds()),7,tx)!=7)throw new AssertionError("Actual input cannot own the seven original named diamonds");tx.commit();}});
        stand(c,w,P.getX()+.5,P.getZ()+3.5);server.runCommand("item replace entity @p weapon.mainhand with create:mechanical_arm 1");c.waitFor(client->client.player.getMainHandItem().getItem() instanceof ArmItem,200);
        use(c,INPUT,Vec3.atCenterOf(INPUT));use(c,OUTPUT,Vec3.atCenterOf(OUTPUT));use(c,OUTPUT,Vec3.atCenterOf(OUTPUT));
        c.waitFor(client->ArmInteractionPointHandler.selection().size()==2&&ArmInteractionPointHandler.latest()!=null&&ArmInteractionPointHandler.latest().points()==2,200);
        look(c,Vec3.atCenterOf(INPUT));c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(INPUT),200);c.getInput().pressKey(options->options.keyAttack);
        c.waitFor(client->ArmInteractionPointHandler.selection().size()==1&&client.level.getBlockState(INPUT).is(AllBlocks.DEPOT),200);use(c,INPUT,Vec3.atCenterOf(INPUT));
        c.runOnClient(client->{var points=ArmInteractionPointHandler.selection();if(points.stream().filter(point->point.getMode()==ArmInteractionPoint.Mode.TAKE).count()!=1||points.stream().filter(point->point.getMode()==ArmInteractionPoint.Mode.DEPOSIT).count()!=1)throw new AssertionError("Actual right/left block input did not retain one blue source and one yellow destination");});
        screenshot(c,w,"create-vulkan-arm-actual-native-block-click-selection-blue-input-yellow-output");
        use(c,P.below(),Vec3.atCenterOf(P.below()).add(0,.499,0));c.waitFor(client->client.player.getMainHandItem().isEmpty()&&client.level.getBlockEntity(P) instanceof ArmBlockEntity arm&&arm.interactionPoints().size()==2&&ArmInteractionPointHandler.selection().isEmpty(),300);
        await(c,w,level->arm(level,P)!=null&&arm(level,P).interactionPoints().size()==2&&actual(level).getMainHandItem().isEmpty(),250,"Real survival placement did not pay one arm and apply its one-use selected target payload");
        verify(c,P,false,false);look(c,Vec3.atCenterOf(P));screenshot(c,w,"create-vulkan-arm-one-paid-physical-owner-source-joints-and-cog");
        use(c,P,Vec3.atLowerCornerOf(P).add(.5,3/16d,.999));c.waitFor(client->client.gui.screen() instanceof ArmSettingsScreen,250);
        clickWidget(c,Component.translatable(ArmBlockEntity.SelectionMode.PREFER_FIRST.getTranslationKey()));c.waitFor(client->((ArmSettingsScreen)client.gui.screen()).selected()==ArmBlockEntity.SelectionMode.PREFER_FIRST,100);
        screenshot(c,w,"create-vulkan-arm-native-distribution-settings-three-original-icons-and-sdl-click");clickWidget(c,Component.translatable("gui.done"));c.waitFor(client->client.gui.screen()==null,150);
        await(c,w,level->arm(level,P).getSelectionMode()==ArmBlockEntity.SelectionMode.PREFER_FIRST,200,"Actual native settings packet did not configure the real placed arm");
        server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();drive(level,P,32);});
        await(c,w,level->arm(level,P).getHeldItem().getCount()==7,300,"Real motor and cog mesh failed to acquire original seven named diamonds");
        server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();((KineticBlockEntity)level.getBlockEntity(MOTOR)).setGeneratedSpeed(0);});
        await(c,w,level->arm(level,P).getSpeed()==0&&ItemStack.matches(arm(level,P).getHeldItem(),diamonds()),200,"Real zero-RPM source failed to stop with its exact original held batch");
        c.waitFor(client->client.level.getBlockEntity(P) instanceof ArmBlockEntity arm&&arm.getSpeed()==0&&ItemStack.matches(arm.getHeldItem(),diamonds()),200);
        verify(c,P,true,false);look(c,Vec3.atCenterOf(P).add(0,.5,0));screenshot(c,w,"create-vulkan-arm-real-claw-holds-original-seven-named-diamonds-on-native-joint-poses");
        var beforeModel=c.computeOnClient(client->client.getModelManager().getModel(AllPartialModels.ARM_LOWER_BODY));var reload=c.computeOnClient(Minecraft::reloadResourcePacks);c.waitFor(client->reload.isDone()&&client.gui.overlay()==null,650);reload.join();
        c.runOnClient(client->{if(client.getModelManager().getModel(AllPartialModels.ARM_LOWER_BODY)==beforeModel)throw new AssertionError("Actual native resource reload retained a stale arm mesh model");});verify(c,P,true,false);screenshot(c,w,"create-vulkan-arm-native-resource-reload-keeps-genuine-held-item-and-refreshed-meshes");
        var old=new AtomicReference<ArmBlockEntity>();server.runOnServer(ignored->old.set(arm(w.getConnection().getServerLevel(),P)));server.runCommand("save-all flush");server.runCommand("gamemode spectator @p");server.runCommand("tp @p 54400.5 15 12.5");
        await(c,w,level->level.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4)==null,800,"Actual source arm chunk failed to unload after real native save and travel");
        NativeFixtureChunkUnload.finish(c,w,old.get());
        server.runCommand("tp @p 53010.5 10 12.5");await(c,w,level->LoadedLevelAccess.blockEntity(level,P) instanceof ArmBlockEntity saved&&saved!=old.get()&&ItemStack.matches(saved.getHeldItem(),diamonds())&&saved.interactionPoints().size()==2&&saved.getSelectionMode()==ArmBlockEntity.SelectionMode.PREFER_FIRST,600,"Fresh native chunk owner failed to restore exact hand, source points and distribution policy");
        stand(c,w,P.getX()+.5,P.getZ()+3.5);c.waitFor(client->client.level.getBlockEntity(P) instanceof ArmBlockEntity arm&&ItemStack.matches(arm.getHeldItem(),diamonds()),300);verify(c,P,true,false);look(c,Vec3.atCenterOf(P).add(0,.5,0));screenshot(c,w,"create-vulkan-arm-real-save-unload-fresh-owner-restores-the-same-seven-diamond-custody");
        server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();((KineticBlockEntity)level.getBlockEntity(MOTOR)).setGeneratedSpeed(256);});
        await(c,w,level->count(level,OUTPUT)==7&&count(level,INPUT)==0&&arm(level,P).getHeldItem().isEmpty(),300,"Restored true native arm did not deliver original seven diamonds exactly once");
        server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();if(!level.getEntitiesOfClass(ItemEntity.class,new AABB(P).inflate(5),entity->entity.getItem().is(Items.DIAMOND)).isEmpty())throw new AssertionError("Arm leaked original paid cargo into dropped copies");var owner=arm(level,P).advancementOwner();if(!actual(level).getUUID().equals(owner))throw new AssertionError("Real paid placement lost its native advancement owner");var advancement=level.getServer().getAdvancements().get(Create.asResource("mechanical_arm"));if(advancement==null||!actual(level).getAdvancements().getOrStartProgress(advancement).isDone())throw new AssertionError("Actual paid owner did not receive the source completion criterion after genuine delivery");});
        server.runCommand("item replace entity @p weapon.mainhand with create:wrench 1");look(c,Vec3.atCenterOf(P));c.waitFor(client->ArmInteractionPointHandler.latest()!=null&&P.equals(ArmInteractionPointHandler.latest().arm())&&ArmInteractionPointHandler.latest().points()==2,200);screenshot(c,w,"create-vulkan-arm-wrench-previews-real-saved-targets-after-paid-delivery");
        ceiling(c,w);server.runCommand("gamemode spectator @p");
    }
    private static void ceiling(ClientGameTestContext c,TestSingleplayerContext w){
        var server=w.getServer();server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();level.setBlockAndUpdate(CEILING.above(),Blocks.STONE.defaultBlockState());level.setBlockAndUpdate(JUKEBOX,Blocks.JUKEBOX.defaultBlockState());var jukebox=(JukeboxBlockEntity)level.getBlockEntity(JUKEBOX);try(var tx=Transaction.openOuter()){if(ContainerStorage.of(jukebox,null).insert(ItemVariant.of(Items.MUSIC_DISC_13),1,tx)!=1)throw new AssertionError("Actual native jukebox rejected its finite real record");tx.commit();}});
        stand(c,w,CEILING.getX()+.5,CEILING.getZ()+3.5);server.runCommand("item replace entity @p weapon.mainhand with create:mechanical_arm 1");c.waitFor(client->client.player.getMainHandItem().getItem() instanceof ArmItem,200);
        use(c,JUKEBOX,Vec3.atCenterOf(JUKEBOX));use(c,JUKEBOX,Vec3.atCenterOf(JUKEBOX));use(c,CEILING.above(),Vec3.atLowerCornerOf(CEILING.above()).add(.5,.001,.5));
        c.waitFor(client->client.level.getBlockEntity(CEILING) instanceof ArmBlockEntity arm&&arm.isOnCeiling()&&arm.interactionPoints().size()==1&&client.player.getMainHandItem().isEmpty(),300);
        server.runCommand("item replace entity @p weapon.mainhand with create:goggles 1");use(c,CEILING,Vec3.atCenterOf(CEILING));
        await(c,w,level->arm(level,CEILING).hasGoggles()&&actual(level).getMainHandItem().is(AllItems.GOGGLES)&&actual(level).getMainHandItem().getCount()==1,200,"Actual goggles interaction did not preserve the one source cosmetic item");
        server.runOnServer(ignored->drive(w.getConnection().getServerLevel(),CEILING,32));c.waitFor(client->client.level.getBlockEntity(CEILING) instanceof ArmBlockEntity arm&&arm.hasGoggles()&&arm.isOnCeiling()&&arm.getPhase()==ArmBlockEntity.Phase.DANCING&&arm.getSpeed()!=0,300);
        verify(c,CEILING,false,true);look(c,Vec3.atCenterOf(CEILING));screenshot(c,w,"create-vulkan-arm-second-paid-ceiling-owner-goggles-and-actual-jukebox-rave-tint");
    }
    private static void drive(ServerLevel level,BlockPos pos,int speed){level.setBlockAndUpdate(pos.west(),AllBlocks.COGWHEEL.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Y));level.setBlockAndUpdate(pos.west().below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.UP));((KineticBlockEntity)level.getBlockEntity(pos.west().below())).setGeneratedSpeed(speed);}
    private static void verify(ClientGameTestContext c,BlockPos pos,boolean held,boolean dance){c.runOnClient(client->{var source=(ArmBlockEntity)client.level.getBlockEntity(pos);var state=(ArmRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(source,.5f,null,true);if(state==null||state.cog.isEmpty()||state.base.isEmpty()||state.lower.isEmpty()||state.upper.isEmpty()||state.claw.isEmpty()||state.upperGrip.isEmpty()||state.lowerGrip.isEmpty()||state.hasItem!=held||state.held.isEmpty()==held||state.dancing!=dance)throw new AssertionError("Actual native arm extraction is missing source meshes, real held item or actual music state");for(float angle:new float[]{state.baseAngle,state.lowerAngle,state.upperAngle,state.headAngle})if(!Float.isFinite(angle))throw new AssertionError("Native arm target inverse kinematics produced a nonfinite source joint");if(dance&&(!state.ceiling||!state.goggles||state.color==0xFFFFFFFF))throw new AssertionError("Ceiling rave lost actual native goggles inversion and mesh color");});}
    private static void stand(ClientGameTestContext c,TestSingleplayerContext w,double x,double z){w.getServer().runCommand("gamemode survival @p");w.getServer().runCommand("tp @p "+x+" 5 "+z);c.waitFor(client->client.player.onGround()&&!client.player.isSpectator()&&Math.abs(client.player.getX()-x)<.1&&Math.abs(client.player.getZ()-z)<.1,250);}
    private static void look(ClientGameTestContext c,Vec3 target){var angle=c.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});c.getInput().lookAt(angle.x,angle.y);}
    private static void use(ClientGameTestContext c,BlockPos pos,Vec3 target){look(c,target);c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),200);c.waitTicks(3);c.getInput().pressKey(options->options.keyUse);c.waitTicks(3);}
    private static void clickWidget(ClientGameTestContext c,Component label){var point=c.computeOnClient(client->{var widget=client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(child->child.getMessage().equals(label)).findFirst().orElseThrow();var window=client.getWindow();float x=widget.getX()+widget.getWidth()/2f,y=widget.getY()+widget.getHeight()/2f;var nativePoint=new Vec2(x*window.getScreenWidth()/window.getGuiScaledWidth(),y*window.getScreenHeight()/window.getGuiScaledHeight());if(Math.abs(net.minecraft.client.MouseHandler.getScaledXPos(window,nativePoint.x)-x)>.01||Math.abs(net.minecraft.client.MouseHandler.getScaledYPos(window,nativePoint.y)-y)>.01)throw new AssertionError("Actual SDL window coordinates failed to invert native GUI positions");return nativePoint;});c.getInput().setCursorPos(point.x,point.y);c.waitTicks(2);c.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);}
    private static ArmBlockEntity arm(ServerLevel level,BlockPos pos){return LoadedLevelAccess.blockEntity(level,pos) instanceof ArmBlockEntity arm?arm:null;}
    private static net.minecraft.server.level.ServerPlayer actual(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static ItemStack diamonds(){var item=new ItemStack(Items.DIAMOND,7);item.set(DataComponents.CUSTOM_NAME,Component.literal("Original native mechanical-arm diamonds"));return item;}
    private static long count(ServerLevel level,BlockPos pos){if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof DepotBlockEntity depot))return -1;try(var tx=Transaction.openOuter()){return depot.inventory.automation.extract(ItemVariant.of(diamonds()),10000,tx);}}
    private static void await(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> predicate,int ticks,String failure){var ok=new AtomicBoolean();for(int tick=0;tick<ticks;tick++){w.getServer().runOnServer(ignored->ok.set(predicate.test(w.getConnection().getServerLevel())));if(ok.get())return;c.waitTicks(1);}var diagnostic=new AtomicReference<String>();w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();var owner=arm(level,P);var motor=LoadedLevelAccess.blockEntity(level,MOTOR);diagnostic.set("native owner="+owner+", removed="+(owner!=null&&owner.isRemoved())+", held="+(owner==null?null:owner.getHeldItem())+", points="+(owner==null?null:owner.interactionPoints())+", mode="+(owner==null?null:owner.getSelectionMode())+", phase="+(owner==null?null:owner.getPhase())+", progress="+(owner==null?null:owner.getMovementProgress())+", speed="+(owner==null?null:owner.getSpeed())+", motor="+(motor instanceof KineticBlockEntity kinetic?kinetic.getGeneratedSpeed():null)+", sourceCount="+count(level,INPUT)+", outputCount="+count(level,OUTPUT));});throw new AssertionError(failure+"; "+diagnostic.get());}
    private static void screenshot(ClientGameTestContext c,TestSingleplayerContext w,String name){NativeScreenshotReadiness.await(c,w);c.waitFor(client->client.gui.overlay()==null,250);c.runOnClient(client->{client.gui.hud.getChat().clearMessages(false);if(client.getWindow().getGuiScale()!=2||client.getWindow().getGuiScaledWidth()<480||client.getWindow().getGuiScaledHeight()<320)throw new AssertionError("Native Arm screenshot no longer has a readable source panel");});c.takeScreenshot(name);}
    private ArmRenderingTest(){}
}
