package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.logistics.box.*;
import com.simibubi.create.content.logistics.packagePort.*;
import com.simibubi.create.content.logistics.packagePort.frogport.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

final class FrogportRenderingTest {
    private static final BlockPos P=new BlockPos(2603,8,5),Q=P.east(10),A=new BlockPos(2603,4,7),B=A.east(10);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 2608 11 14");context.waitFor(client->Math.abs(client.player.getX()-2608.5)<.1,300);
        context.waitFor(client->client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(P.getX()>>4,0))&&client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(Q.getX()>>4,0)),300);
        server.runCommand("fill 2598 3 0 2618 3 15 minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(P,AllBlocks.CHAIN_CONVEYOR.defaultBlockState());level.setBlockAndUpdate(Q,AllBlocks.CHAIN_CONVEYOR.defaultBlockState());((ChainConveyorBlockEntity)level.getBlockEntity(P)).connectTo((ChainConveyorBlockEntity)level.getBlockEntity(Q),null);level.setBlockAndUpdate(P.below(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.UP));((KineticBlockEntity)level.getBlockEntity(P.below())).setGeneratedSpeed(0);var player=level.getServer().getPlayerList().getPlayers().getFirst();player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.PACKAGE_FROGPORT,2));player.inventoryMenu.broadcastFullState();});
        server.runCommand("gamemode survival @p");place(context,world,P,A);
        // Move a real parcel through the native menu in both directions before power is enabled.
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.getInventory().setItem(9,box());player.containerMenu.broadcastChanges();});
        context.waitFor(client->client.gui.screen() instanceof PackagePortScreen screen&&!screen.getMenu().getSlot(18).getItem().isEmpty(),200);
        context.runOnClient(client->{var menu=((PackagePortScreen)client.gui.screen()).getMenu();client.gameMode.handleContainerInput(menu.containerId,18,0,ContainerInput.QUICK_MOVE,client.player);});
        context.waitFor(client->((PackagePortScreen)client.gui.screen()).getMenu().getSlot(0).hasItem(),200);
        context.runOnClient(client->{var menu=((PackagePortScreen)client.gui.screen()).getMenu();client.gameMode.handleContainerInput(menu.containerId,0,0,ContainerInput.QUICK_MOVE,client.player);});
        context.waitFor(client->!((PackagePortScreen)client.gui.screen()).getMenu().getSlot(0).hasItem(),200);
        context.runOnClient(client->((PackagePortScreen)client.gui.screen()).addressBox().setValue("Sender"));context.clickScreenButton("→");context.takeScreenshot("create-vulkan-frogport-settings");context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null&&client.level.getBlockEntity(A) instanceof FrogportBlockEntity port&&port.addressFilter.equals("Sender")&&!port.acceptsPackages&&port.inventory.isEmpty(),200);
        server.runOnServer(ignored->{var port=(FrogportBlockEntity)world.getConnection().getServerLevel().getBlockEntity(A);if(port.acceptsPackages||!port.addressFilter.equals("Sender")||!port.inventory.isEmpty())throw new AssertionError("Actual source menu: address="+port.addressFilter+", accepts="+port.acceptsPackages+", empty="+port.inventory.isEmpty());});
        place(context,world,Q,B);context.runOnClient(client->((PackagePortScreen)client.gui.screen()).addressBox().setValue("Market"));context.clickScreenButton("✓");context.waitFor(client->client.gui.screen()==null&&client.level.getBlockEntity(B) instanceof FrogportBlockEntity port&&port.addressFilter.equals("Market"),200);
        // Goggles are a source decoration and do not consume the held item.
        server.runOnServer(ignored->world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.GOGGLES)));
        context.waitFor(client->client.player.getMainHandItem().is(AllItems.GOGGLES),200);look(context,Vec3.atCenterOf(B));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(B),200);context.waitTicks(5);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->((FrogportBlockEntity)client.level.getBlockEntity(B)).goggles,200);
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();if(player.getMainHandItem().getCount()!=1)throw new AssertionError("Frogport decoration consumed goggles");});
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 2608 10 14");context.waitFor(client->client.player.isSpectator(),200);look(context,new Vec3(2608,6,6));world.getConnection().waitForChunksRender();
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(A.below(),Blocks.BARREL.defaultBlockState());level.setBlockAndUpdate(B.below(),Blocks.BARREL.defaultBlockState());((BarrelBlockEntity)level.getBlockEntity(A.below())).setItem(0,box());((KineticBlockEntity)level.getBlockEntity(P.below())).setGeneratedSpeed(128);});
        context.waitFor(client->client.level.getBlockEntity(A) instanceof FrogportBlockEntity port&&port.isAnimationInProgress()&&port.currentlyDepositing&&port.getAnimationTicks()>=4&&port.getAnimationTicks()<=6,200);
        context.runOnClient(client->{var state=state(client,A);audit(state);if(state.tongueLength<=0||!state.showPackage||!state.showRig)throw new AssertionError("Outgoing tongue/parcel/rigging not extracted");});context.takeScreenshot("create-vulkan-frogport-deposit");
        context.waitFor(client->client.level.getBlockEntity(B) instanceof FrogportBlockEntity port&&port.isAnimationInProgress()&&!port.currentlyDepositing,200);context.runOnClient(client->{audit(state(client,B));if(state(client,B).tongueLength<=0)throw new AssertionError("Catching tongue missing");});context.takeScreenshot("create-vulkan-frogport-catch");
        waitDelivery(context,world,1);
        // Keep the first delivered parcel and block every remaining destination slot.
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var barrel=(BarrelBlockEntity)level.getBlockEntity(B.below());for(int i=1;i<barrel.getContainerSize();i++)barrel.setItem(i,new ItemStack(Items.STONE,64));((BarrelBlockEntity)level.getBlockEntity(A.below())).setItem(0,box());});
        context.waitFor(client->client.level.getBlockEntity(B) instanceof FrogportBlockEntity port&&port.failedLastExport&&!port.inventory.isEmpty(),300);
        server.runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(P.below())).setGeneratedSpeed(0));
        var oldHead=new AtomicReference<Object>();context.runOnClient(client->oldHead.set(state(client,B).head.mesh));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);
        context.runOnClient(client->{audit(state(client,A));audit(state(client,B));if(state(client,B).head.mesh==oldHead.get())throw new AssertionError("Frog head/goggles mesh did not reload");});
        server.runOnServer(ignored->world.getConnection().getServerLevel().getChunkSource().addTicket(new Ticket(TicketType.FORCED,33),ChunkPos.containing(P)));server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();
        for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var source=(FrogportBlockEntity)LoadedLevelAccess.blockEntity(level,A);source.tick();unloaded.set(level.getChunkSource().getChunkNow(Q.getX()>>4,Q.getZ()>>4)==null);});}
        if(!unloaded.get())throw new AssertionError("Frogport polling kept remote chunk loaded");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var source=(FrogportBlockEntity)LoadedLevelAccess.blockEntity(level,A);var conveyor=(ChainConveyorBlockEntity)LoadedLevelAccess.blockEntity(level,P);for(int i=0;i<100;i++){source.tick();source.tryPushingToAdjacentInventories();source.tryPullingFromOwnAndAdjacentInventories();ChainConveyorBlockEntity.tick(level,P,conveyor.getBlockState(),conveyor);KineticNetworks.tick(level);}if(level.getChunkSource().getChunkNow(Q.getX()>>4,Q.getZ()>>4)!=null)throw new AssertionError("Postal lookups loaded remote chunk");});
        server.runCommand("tp @p 2608 10 14");context.waitFor(client->Math.abs(client.player.getX()-2608.5)<.1&&client.level.getBlockEntity(B) instanceof FrogportBlockEntity port&&!port.inventory.isEmpty(),400);
        var returned=new AtomicBoolean();for(int i=0;i<300&&!returned.get();i++){server.runOnServer(ignored->returned.set(LoadedLevelAccess.blockEntity(world.getConnection().getServerLevel(),B) instanceof FrogportBlockEntity));if(!returned.get())context.waitTicks(1);}if(!returned.get())throw new AssertionError("Receiver server chunk did not return");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var port=(FrogportBlockEntity)LoadedLevelAccess.blockEntity(level,B);var item=port.inventory.getItem(0);if(!PackageItem.getAddress(item).equals("Market")||PackageItem.getContents(item).getFirst().getCount()!=7)throw new AssertionError("Backed-up mail did not survive unload");((BarrelBlockEntity)level.getBlockEntity(B.below())).setItem(1,ItemStack.EMPTY);level.getChunkSource().removeTicketWithRadius(TicketType.FORCED,ChunkPos.containing(P),0);});
        waitDelivery(context,world,2);look(context,new Vec3(2608,6,6));world.getConnection().waitForChunksRender();context.takeScreenshot("create-vulkan-frogport-chunk-return");
    }
    private static void place(ClientGameTestContext context,TestSingleplayerContext world,BlockPos conveyor,BlockPos port){
        var server=world.getServer();server.runCommand("tp @p "+(port.getX()+.5)+" 4 10.5");context.waitFor(client->Math.abs(client.player.getX()-port.getX()-.5)<.1&&client.player.getMainHandItem().is(AllBlocks.PACKAGE_FROGPORT.asItem()),200);
        look(context,Vec3.atBottomCenterOf(conveyor).add(0,6/16d,.875));context.waitTicks(5);context.getInput().pressKey(options->options.keyUse);context.waitTicks(5);
        look(context,Vec3.atLowerCornerOf(port).add(.5,-.001,.5));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(port.below()),200);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof PackagePortScreen,200);
        server.runOnServer(ignored->{var be=(FrogportBlockEntity)world.getConnection().getServerLevel().getBlockEntity(port);if(!(be.target instanceof PackagePortTarget.ChainConveyorFrogportTarget target)||!target.relativePos.equals(conveyor.subtract(port)))throw new AssertionError("Actual native placement did not use server-validated chain target");});
    }
    private static void waitDelivery(ClientGameTestContext context,TestSingleplayerContext world,int expected){var delivered=new AtomicBoolean();for(int i=0;i<300&&!delivered.get();i++){context.waitTicks(1);world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var barrel=(BarrelBlockEntity)level.getBlockEntity(B.below());int count=0;for(var item:barrel)if(PackageItem.isPackage(item)){if(!PackageItem.getAddress(item).equals("Market")||PackageItem.getContents(item).getFirst().getCount()!=7)throw new AssertionError("Postal delivery changed item components");count+=item.getCount();}if(count>expected)throw new AssertionError("Duplicate postal delivery: "+count);delivered.set(count==expected);});}if(!delivered.get())throw new AssertionError("Actual frogports failed to deliver "+expected+" parcels");}
    private static ItemStack box(){var item=PackageStyles.getDefaultBox();item.set(AllDataComponents.PACKAGE_CONTENTS,net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,7))));PackageItem.addAddress(item,"Market");return item;}
    private static FrogportRenderer.State state(net.minecraft.client.Minecraft client,BlockPos pos){return (FrogportRenderer.State)client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos),.5f,null,true);}
    private static void audit(FrogportRenderer.State state){if(state==null||state.body.mesh==null||state.body.mesh.size()==0||state.head.mesh==null||state.head.mesh.size()==0||state.tongue.mesh==null||state.tongue.mesh.size()==0||!Float.isFinite(state.tongueLength)||state.tongueLength>PackagePortBlockEntity.RANGE+2)throw new AssertionError("Frogport native mesh or tongue transform invalid");}
    private static void look(ClientGameTestContext context,Vec3 target){var rotation=context.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(rotation.x,rotation.y);}
}
