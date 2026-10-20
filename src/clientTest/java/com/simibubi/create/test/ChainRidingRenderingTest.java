package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.foundation.render.PlayerSkyhookRenderer;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Real input, normal player physics and native extract/submit on the Vulkan backend. */
final class ChainRidingRenderingTest {
    private static final BlockPos P=new BlockPos(2803,8,5),Q=P.east(10);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        boolean hudHidden=context.computeOnClient(client->client.gui.hud.isHidden());
        context.runOnClient(client->{if(client.gui.hud.isHidden())client.gui.hud.toggle();});
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 2808 11 14");
        context.waitFor(client->Math.abs(client.player.getX()-2808.5)<.1,300);
        context.waitFor(client->client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(P.getX()>>4,0))&&client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(Q.getX()>>4,0)),300);
        server.runCommand("fill 2798 3 0 2818 3 15 minecraft:smooth_quartz");
        server.runCommand("fill 2801 7 8 2805 7 11 minecraft:smooth_quartz");
        server.runCommand("fill 2811 7 8 2815 7 11 minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(P,AllBlocks.CHAIN_CONVEYOR.defaultBlockState());level.setBlockAndUpdate(Q,AllBlocks.CHAIN_CONVEYOR.defaultBlockState());level.setBlockAndUpdate(P.below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.UP));((KineticBlockEntity)level.getBlockEntity(P.below())).setGeneratedSpeed(0);var player=level.getServer().getPlayerList().getPlayers().getFirst();player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_CHAIN,10));player.inventoryMenu.broadcastFullState();});
        server.runCommand("gamemode survival @p");move(context,world,P,8,9.5);
        look(context,Vec3.atCenterOf(P));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P),200);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->P.equals(ChainConveyorOverlay.first()),200);
        move(context,world,Q,8,9.5);look(context,Vec3.atCenterOf(Q));
        context.waitFor(client->ChainConveyorOverlay.latest()!=null&&ChainConveyorOverlay.latest().cost()==4&&ChainConveyorOverlay.latest().valid()&&ChainConveyorOverlay.latest().lines()>=34,200);
        screenshot(context,"create-vulkan-chain-preview-valid");
        held(context,world,new ItemStack(Items.IRON_CHAIN),ItemStack.EMPTY);
        context.waitFor(client->ChainConveyorOverlay.latest().cost()==4&&!ChainConveyorOverlay.latest().valid(),200);screenshot(context,"create-vulkan-chain-preview-insufficient-chains");
        held(context,world,new ItemStack(Items.IRON_CHAIN,10),ItemStack.EMPTY);
        context.waitFor(client->ChainConveyorOverlay.latest().valid(),200);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->ChainConveyorOverlay.first()==null&&client.level.getBlockEntity(P) instanceof ChainConveyorBlockEntity conveyor&&conveyor.getConnections().contains(Q.subtract(P)),200);
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();if(player.getMainHandItem().getCount()!=6)throw new AssertionError("Confirmed preview must consume four actual chains");});
        // Selection cancellation also clears the server's first endpoint.
        context.getInput().pressKey(options->options.keyUse);context.waitFor(client->Q.equals(ChainConveyorOverlay.first()),200);
        context.getInput().holdKey(options->options.keyShift);
        try{context.waitTicks(2);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->ChainConveyorOverlay.first()==null,200);}finally{context.getInput().releaseKey(options->options.keyShift);}
        server.runCommand("fill 2801 7 8 2805 7 11 minecraft:air");
        board(context,world,false);var initial=context.computeOnClient(client->client.player.position());
        context.waitTicks(30);assertRide(context,world);
        context.runOnClient(client->{if(client.player.position().distanceToSqr(initial)<.25)throw new AssertionError("Elastic grip never moved the actual player");var target=ChainConveyorRidingHandler.state().target(client.level);if(target.distanceTo(ChainConveyorRidingState.grip(client.player))>1)throw new AssertionError("Stationary grip failed to settle under ordinary gravity");});
        var camera=context.computeOnClient(client->client.options.getCameraType());context.runOnClient(client->client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        context.runOnClient(client->pose(client,false,true));checkSettingsHud(context);screenshot(context,"create-vulkan-chain-riding-main-hand");
        // Keeping the tool in the other hand must preserve the same actual ride and raise that arm.
        held(context,world,ItemStack.EMPTY,new ItemStack(AllItems.WRENCH));context.waitTicks(3);assertRide(context,world);context.runOnClient(client->pose(client,true,true));screenshot(context,"create-vulkan-chain-riding-offhand");
        held(context,world,new ItemStack(AllItems.WRENCH),ItemStack.EMPTY);
        context.getInput().lookAt(-90,0);speed(world,64);
        context.waitFor(client->ChainConveyorRidingHandler.active()&&ChainConveyorRidingHandler.state().connection!=null&&ChainConveyorRidingHandler.state().anchor.equals(P)&&ChainConveyorRidingHandler.state().position>2,200);
        assertRide(context,world);speed(world,-64);
        context.waitFor(client->ChainConveyorRidingHandler.active()&&ChainConveyorRidingHandler.state().anchor.equals(Q)&&P.subtract(Q).equals(ChainConveyorRidingHandler.state().connection),200);
        speed(world,0);context.waitFor(client->((ChainConveyorBlockEntity)client.level.getBlockEntity(P)).getSpeed()==0,200);context.waitTicks(50);assertRide(context,world);
        context.runOnClient(client->pose(client,false,true));screenshot(context,"create-vulkan-chain-riding-reversed-stopped");
        speed(world,-64);context.waitFor(client->ChainConveyorRidingHandler.active()&&ChainConveyorRidingHandler.state().anchor.equals(P)&&ChainConveyorRidingHandler.state().connection==null,200);assertRide(context,world);speed(world,0);
        context.getInput().holdKey(options->options.keyShift);
        try{context.waitFor(client->!ChainConveyorRidingHandler.active()&&!ChainConveyorRidingHandler.hanging(client.player.getUUID()),200);}finally{context.getInput().releaseKey(options->options.keyShift);}
        context.waitTicks(5);assertStopped(context,world);context.runOnClient(client->pose(client,false,false));
        // A second real click can board again; removing the tool ends protection and the pose.
        context.runOnClient(client->client.options.setCameraType(camera));board(context,world,true);
        held(context,world,ItemStack.EMPTY,ItemStack.EMPTY);context.waitFor(client->!ChainConveyorRidingHandler.active(),200);assertStopped(context,world);
        board(context,world,false);server.runOnServer(ignored->world.getConnection().getServerLevel().destroyBlock(P,false));context.waitFor(client->!ChainConveyorRidingHandler.active(),200);assertStopped(context,world);
        // Rebuilding at the same coordinates must not resurrect the old session.
        server.runOnServer(ignored->world.getConnection().getServerLevel().setBlockAndUpdate(P,AllBlocks.CHAIN_CONVEYOR.defaultBlockState()));context.waitTicks(10);
        context.runOnClient(client->{if(ChainConveyorRidingHandler.active())throw new AssertionError("Rebuilt anchor resurrected stale ride");});
        // Real frogport target selection produces placement previews, including an out-of-range one.
        held(context,world,new ItemStack(AllBlocks.PACKAGE_FROGPORT,2),ItemStack.EMPTY);move(context,world,P,4,10.5);
        look(context,Vec3.atBottomCenterOf(P).add(0,.375,.875));context.waitFor(client->ChainConveyorOverlay.latest()!=null&&P.equals(ChainConveyorOverlay.latest().anchor()),200);context.waitTicks(3);context.getInput().pressKey(options->options.keyUse);context.waitTicks(3);
        look(context,new Vec3(2803.5,3.999,7.5));context.waitFor(client->ChainConveyorOverlay.latest()!=null&&ChainConveyorOverlay.latest().anchor()==null&&ChainConveyorOverlay.latest().lines()==25&&hint(client,"valid"),200);
        screenshot(context,"create-vulkan-frogport-placement-preview-valid");
        look(context,new Vec3(2800.5,3.999,8.5));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(new BlockPos(2800,3,8))&&ChainConveyorOverlay.latest().lines()==25&&hint(client,"too_far"),200);
        screenshot(context,"create-vulkan-frogport-placement-preview-out-of-range");
        context.getInput().lookAt(-90,-75);context.waitFor(client->client.hitResult.getType()==HitResult.Type.MISS&&ChainConveyorOverlay.latest().anchor()==null&&ChainConveyorOverlay.latest().lines()==12,200);
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 2808 11 14");context.waitFor(client->client.player.isSpectator(),200);
        look(context,new Vec3(2808,8,5));world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-chain-riding-after-reload");
        context.runOnClient(client->{if(client.gui.hud.isHidden()!=hudHidden)client.gui.hud.toggle();});
    }
    private static void checkSettingsHud(ClientGameTestContext context){
        context.runOnClient(client->{try{
            if(!ChainConveyorRidingHandler.active()||client.gui.screen()!=null||client.gui.hud.isHidden()
                ||!(client.hitResult instanceof BlockHitResult hit)||!hit.getBlockPos().equals(P.below())
                ||!(client.level.getBlockEntity(P.below()) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor))
                throw new AssertionError("HUD regression requires the actual ride and native motor hit");
            var window=client.getWindow();var state=new net.minecraft.client.renderer.state.gui.GuiRenderState();
            var gui=new net.minecraft.client.gui.GuiGraphicsExtractor(client,state,(int)client.mouseHandler.getScaledXPos(window),(int)client.mouseHandler.getScaledYPos(window));
            client.gui.hud.extractRenderState(gui,client.getDeltaTracker());
            var required=java.util.List.of(motor.generatedSpeed.label.getString(),net.minecraft.network.chat.Component.translatable("create.gui.value_settings.hold_to_edit").getString(),net.minecraft.network.chat.Component.translatable("mount.onboard",client.options.keyShift.getTranslatedKeyMessage()).getString());
            var actual=new java.util.HashMap<String,java.util.List<net.minecraft.client.gui.navigation.ScreenRectangle>>();
            var textField=net.minecraft.client.renderer.state.gui.GuiTextRenderState.class.getDeclaredField("text");textField.setAccessible(true);
            state.forEachText(text->{try{var value=new StringBuilder();((net.minecraft.util.FormattedCharSequence)textField.get(text)).accept((index,style,codepoint)->{value.appendCodePoint(codepoint);return true;});if(required.contains(value.toString()))actual.computeIfAbsent(value.toString(),ignored->new java.util.ArrayList<>()).add(text.bounds());}catch(IllegalAccessException failure){throw new AssertionError(failure);}});
            for(var message:required){var boxes=actual.get(message);if(boxes==null||boxes.size()!=1)throw new AssertionError("Actual native riding HUD lost or duplicated source/native text: "+message+" observed="+actual);var bounds=boxes.getFirst();if(bounds==null||bounds.left()<0||bounds.top()<0||bounds.right()>gui.guiWidth()||bounds.bottom()>gui.guiHeight())throw new AssertionError("Actual native riding HUD text escaped GUI bounds: "+message+" "+bounds);}
            for(int a=0;a<required.size();a++)for(int b=a+1;b<required.size();b++){var first=actual.get(required.get(a)).getFirst();var second=actual.get(required.get(b)).getFirst();if(first.left()<second.right()&&first.right()>second.left()&&first.top()<second.bottom()&&first.bottom()>second.top())throw new AssertionError("Actual native source hover HUD overlaps another source line or native dismount text: "+actual);}
            Create.LOGGER.info("Actual native chain-riding HUD source/native text bounds verified: {}",actual);
        }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}});
    }
    private static void board(ClientGameTestContext context,TestSingleplayerContext world,boolean offhand){
        held(context,world,offhand?ItemStack.EMPTY:new ItemStack(AllItems.WRENCH),offhand?new ItemStack(AllItems.WRENCH):ItemStack.EMPTY);
        // A temporary vanilla platform keeps the initial click stable. It is removed once aboard.
        world.getServer().runCommand("setblock 2803 5 9 minecraft:smooth_quartz");move(context,world,P,6,9.5);
        look(context,Vec3.atBottomCenterOf(P).add(0,.375,.875));
        context.waitFor(client->ChainConveyorOverlay.latest()!=null&&P.equals(ChainConveyorOverlay.latest().anchor())&&ChainConveyorOverlay.latest().lines()>0,200);
        context.getInput().pressKey(options->options.keyUse);context.waitFor(client->ChainConveyorRidingHandler.active()&&ChainConveyorRidingHandler.session()>0,200);
        world.getServer().runCommand("setblock 2803 5 9 minecraft:air");assertRide(context,world);
    }
    private static void speed(TestSingleplayerContext world,int rpm){world.getServer().runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(P.below())).setGeneratedSpeed(rpm));}
    private static void held(ClientGameTestContext context,TestSingleplayerContext world,ItemStack main,ItemStack off){world.getServer().runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.setItemInHand(InteractionHand.MAIN_HAND,main.copy());player.setItemInHand(InteractionHand.OFF_HAND,off.copy());player.inventoryMenu.broadcastFullState();});context.waitFor(client->ItemStack.isSameItemSameComponents(client.player.getMainHandItem(),main)&&ItemStack.isSameItemSameComponents(client.player.getOffhandItem(),off)&&client.player.getMainHandItem().getCount()==main.getCount(),200);}
    private static void move(ClientGameTestContext context,TestSingleplayerContext world,BlockPos conveyor,int y,double z){world.getServer().runCommand("tp @p "+(conveyor.getX()+.5)+" "+y+" "+z);context.waitFor(client->Math.abs(client.player.getX()-conveyor.getX()-.5)<.1&&Math.abs(client.player.getY()-y)<.1&&Math.abs(client.player.getZ()-z)<.1,200);}
    private static void assertRide(ClientGameTestContext context,TestSingleplayerContext world){var session=context.computeOnClient(client->{if(!ChainConveyorRidingHandler.active()||client.player.isPassenger()||client.player.isNoGravity()||client.player.getAbilities().flying)throw new AssertionError("Riding requires ordinary player physics");return ChainConveyorRidingHandler.session();});world.getServer().runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();var ride=ServerChainConveyorHandler.get(player);if(ride==null||ride.id!=session||player.isPassenger()||player.isNoGravity()||player.getAbilities().flying||player.fallDistance>1||player.getHealth()<20)throw new AssertionError("Actual server session/physics/fall protection invalid");});}
    private static void assertStopped(ClientGameTestContext context,TestSingleplayerContext world){var stopped=new AtomicBoolean();for(int i=0;i<50&&!stopped.get();i++){world.getServer().runOnServer(ignored->stopped.set(ServerChainConveyorHandler.get(world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst())==null));if(!stopped.get())context.waitTicks(1);}if(!stopped.get())throw new AssertionError("Client dismount left server protection active");}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void pose(Minecraft client,boolean left,boolean hanging){
        var renderer=(LivingEntityRenderer)client.getEntityRenderDispatcher().getRenderer(client.player);var state=(HumanoidRenderState)((EntityRenderer)renderer).createRenderState(client.player,.5f);var data=((FabricRenderState)state).getData(PlayerSkyhookRenderer.HANGING);
        if((data!=null)!=hanging||hanging&&data.left()!=left)throw new AssertionError("Player pose extraction does not match actual riding hand");
        var model=(HumanoidModel)renderer.getModel();model.setupAnim(state);var arm=left?model.leftArm:model.rightArm;
        if(hanging&&Math.abs(arm.xRot+(float)Math.toRadians(150))>.001f)throw new AssertionError("Native player model did not raise the hanging arm");
        if(!hanging&&Math.abs(model.rightArm.xRot+(float)Math.toRadians(150))<.001f)throw new AssertionError("Hanging model pose leaked after dismount");
        if(Math.abs(model.hat.x)+Math.abs(model.hat.y)+Math.abs(model.hat.z)+Math.abs(model.hat.xRot)+Math.abs(model.hat.yRot)+Math.abs(model.hat.zRot)>.001f)throw new AssertionError("Native child hat must inherit head transform exactly once");
    }
    private static void look(ClientGameTestContext context,Vec3 target){var rotation=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(rotation.x,rotation.y);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static boolean hint(Minecraft client,String key){try{var field=net.minecraft.client.gui.Hud.class.getDeclaredField("overlayMessageString");field.setAccessible(true);var text=(net.minecraft.network.chat.Component)field.get(client.gui.hud);return text!=null&&text.getString().equals(net.minecraft.network.chat.Component.translatable("create.package_port."+key).getString());}catch(ReflectiveOperationException error){throw new AssertionError(error);}}
    private ChainRidingRenderingTest(){}
}
