package com.simibubi.create.test;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.OrientedContraptionEntity;
import com.simibubi.create.content.contraptions.minecart.*;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.contraptions.mounted.*;
import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.gui.screens.worldselection.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.flag.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Authored UNRUN: two genuine feature-flag worlds, native interactions, transport and Vulkan frames. */
public final class MinecartRenderingTest implements FabricClientGameTest {
    private static final BlockPos P=new BlockPos(34560,6,8),ASSEMBLER=P.east(9);
    private record Ids(UUID first,UUID second,UUID mounted,int firstId,int secondId){}
    @Override public void runTest(ClientGameTestContext context){
        if(Boolean.getBoolean("create.test.onlyPipes")&&!Boolean.getBoolean("create.test.onlyMinecart"))return;
        context.runOnClient(client->{var backend=RenderSystem.getDevice().getDeviceInfo().backendName();if(!backend.toLowerCase(Locale.ROOT).contains("vulkan"))throw new AssertionError("Actual native minecart scene needs Vulkan: "+backend);});
        scene(context,false);
        context.waitFor(client->client.level==null,300);
        scene(context,true);
    }
    private static void scene(ClientGameTestContext context,boolean modern){
        try(var world=context.worldBuilder().adjustSettings(creator->{
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            if(modern){var old=creator.getSettings();var data=old.dataConfiguration().expandFeatures(FeatureFlagSet.of(FeatureFlags.MINECART_IMPROVEMENTS));creator.setSettings(new WorldCreationContext(old.options(),old.datapackDimensions(),old.selectedDimensions(),old.worldgenRegistries(),old.dataPackResources(),data,old.initialWorldCreationOptions()));}
        }).create()){
            world.getServer().runCommand("tp @p 34562 6 10.3");
            context.waitFor(client->client.player!=null&&client.level.isLoaded(ASSEMBLER),400);
            world.getServer().runCommand("fill 34556 5 4 34575 5 14 minecraft:smooth_quartz");
            context.waitFor(client->client.player.isCreative()&&client.level.getBlockState(P.east(2).south(2).below()).is(Blocks.SMOOTH_QUARTZ),400);
            world.getServer().runCommand("tp @p 34562 6 10.3");
            waitServer(context,world,level->{var player=level.getServer().getPlayerList().getPlayers().getFirst();return player.isAlive()&&player.isCreative()&&player.onGround()&&player.position().distanceToSqr(new Vec3(34562.5,6,10.3))<.01&&level.getBlockState(P.east(2).south(2).below()).is(Blocks.SMOOTH_QUARTZ);},400,"Creative minecart fixture player did not naturally land on actual quartz before Survival");
            context.waitFor(client->client.player.isAlive()&&client.player.isCreative()&&client.player.onGround()&&client.player.position().distanceToSqr(new Vec3(34562.5,6,10.3))<.01&&client.level.getBlockState(P.east(2).south(2).below()).is(Blocks.SMOOTH_QUARTZ),400);
            var ids=new AtomicReference<Ids>();
            world.getServer().runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();
                for(int index=0;index<6;index++)level.setBlockAndUpdate(P.east(index),Blocks.RAIL.defaultBlockState().setValue(net.minecraft.world.level.block.RailBlock.SHAPE,net.minecraft.world.level.block.state.properties.RailShape.EAST_WEST));
                var first=NativeCartKind.RIDEABLE.createEmpty(level,Vec3.atLowerCornerOf(P).add(.5,0,.5));var second=NativeCartKind.RIDEABLE.createEmpty(level,Vec3.atLowerCornerOf(P.east(3)).add(.5,0,.5));
                if((first.getBehavior() instanceof NewMinecartBehavior)!=modern||(second.getBehavior() instanceof NewMinecartBehavior)!=modern)throw new AssertionError("Actual native world did not select its requested official minecart behavior");
                if(!level.addFreshEntity(first)||!level.addFreshEntity(second))throw new AssertionError("Actual native scene carts refused");
                level.setBlockAndUpdate(ASSEMBLER,AllBlocks.CART_ASSEMBLER.defaultBlockState().setValue(CartAssemblerBlock.POWERED,true).setValue(CartAssemblerBlock.RAIL_TYPE,CartAssembleRailType.REGULAR));level.setBlockAndUpdate(ASSEMBLER.above(),Blocks.STONE.defaultBlockState());
                var mounted=NativeCartKind.CHEST.createEmpty(level,Vec3.atLowerCornerOf(ASSEMBLER).add(.5,0,.5));if(!level.addFreshEntity(mounted))throw new AssertionError("Real mounted native base refused");((CartAssemblerBlockEntity)level.getBlockEntity(ASSEMBLER)).tryAssemble(mounted);
                var root=MinecartController.get(mounted).getMountedRoot();if(root==null||!(level.getEntity(root) instanceof OrientedContraptionEntity owner)||!owner.hasOwnedStructure())throw new AssertionError("Actual source assembler did not create one real owned root");
                ids.set(new Ids(first.getUUID(),second.getUUID(),root,first.getId(),second.getId()));var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.MINECART_COUPLING,3));player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client->{var target=ids.get();if(target==null)return false;var first=client.level.getEntity(target.firstId);var second=client.level.getEntity(target.secondId);return first instanceof AbstractMinecart a&&second instanceof AbstractMinecart b&&a.getUUID().equals(target.first)&&b.getUUID().equals(target.second)&&!client.player.isSpectator()&&client.player.getMainHandItem().is(AllItems.MINECART_COUPLING)&&!client.player.isCreative()&&client.player.isAlive()&&client.player.onGround()&&client.player.position().distanceToSqr(new Vec3(34562.5,6,10.3))<.01;},400);
            clickCart(context,ids.get().firstId);context.waitFor(client->CouplingHandlerClient.selectedCart()!=null&&CouplingHandlerClient.selectedCart().getUUID().equals(ids.get().first),150);
            clickCart(context,ids.get().secondId);
            waitServer(context,world,level->{var first=CapabilityMinecartController.getIfPresent(level,ids.get().first);var second=CapabilityMinecartController.getIfPresent(level,ids.get().second);return first!=null&&second!=null&&Objects.equals(first.getCoupledCart(true),ids.get().second)&&Objects.equals(second.getCoupledCart(false),ids.get().first)&&world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst().getMainHandItem().getCount()==2;},300,"Two genuine source interactions/payload did not pay and install one reciprocal edge");
            context.waitFor(client->CouplingRenderer.latest().couplings()==1&&CouplingRenderer.latest().pieces().size()==5&&find(client.level,ids.get().mounted) instanceof OrientedContraptionEntity,400);
            assertFrame(context,ids.get(),modern);
            look(context,Vec3.atCenterOf(P.east(2)));world.getConnection().waitForChunksRender();context.takeScreenshot("create-vulkan-native-minecart-"+(modern?"new":"old")+"-coupling-and-mounted-source");
            var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);context.waitFor(client->CouplingRenderer.latest().couplings()==1&&CouplingRenderer.latest().pieces().stream().noneMatch(piece->piece.model().isEmpty()),400);assertFrame(context,ids.get(),modern);
            world.getServer().runCommand("create coupling remove "+ids.get().first+" "+ids.get().second);
            waitServer(context,world,level->{var first=CapabilityMinecartController.getIfPresent(level,ids.get().first);return first!=null&&!first.isLeadingCoupling();},300,"Actual native command did not detach one paid edge");context.waitFor(client->CouplingRenderer.latest().couplings()==0,300);
        }
    }
    private static void assertFrame(ClientGameTestContext context,Ids ids,boolean modern){context.runOnClient(client->{
        for(var id:List.of(ids.firstId,ids.secondId)){var actual=(AbstractMinecart)client.level.getEntity(id);if((actual.getBehavior() instanceof NewMinecartBehavior)!=modern)throw new AssertionError("Client native feature-selected behavior differs from actual server");var pose=NativeCartRenderPose.extract(actual,.5f);if(pose.modern()!=modern||!finite(pose.position())||!finite(pose.adjustment()))throw new AssertionError("Native Old/New extraction returned invalid real interpolation");}
        var frame=CouplingRenderer.latest();if(frame.couplings()!=1||frame.pieces().size()!=5)throw new AssertionError("Actual native transported topology did not extract attachment/ring/connector pieces");for(var piece:frame.pieces())if(piece.model().isEmpty()||!piece.transform().isFinite()||!finite(piece.position()))throw new AssertionError("Actual Vulkan source model/transform is missing");
        var atlas=client.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.BLOCKS);var sprite=atlas.getSprite(Create.asResource("entity/coupling"));if(sprite==atlas.missingSprite())throw new AssertionError("Original source coupling texture did not stitch in real atlas");
        var root=(OrientedContraptionEntity)find(client.level,ids.mounted);var renderer=(ContraptionEntityRenderer)client.getEntityRenderDispatcher().getRenderer(root);var state=renderer.createRenderState(root,.5f);if(state.blocks.isEmpty()||!finite(state.anchorOffset))throw new AssertionError("Actual mounted native passenger source block failed extraction");
    });}
    private static boolean finite(Vec3 value){return Double.isFinite(value.x)&&Double.isFinite(value.y)&&Double.isFinite(value.z);}
    private static Entity find(net.minecraft.client.multiplayer.ClientLevel level,UUID id){for(var entity:level.entitiesForRendering())if(entity.getUUID().equals(id))return entity;return null;}
    private static void clickCart(ClientGameTestContext context,int id){var point=context.computeOnClient(client->client.level.getEntity(id).getBoundingBox().getCenter());look(context,point);context.waitFor(client->client.hitResult instanceof EntityHitResult hit&&hit.getEntity().getId()==id,200);context.getInput().pressKey(options->options.keyUse);context.waitTicks(3);}
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void waitServer(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int ticks,String message){var ready=new AtomicBoolean();for(int tick=0;tick<ticks&&!ready.get();tick++){world.getServer().runOnServer(ignored->ready.set(condition.test(world.getConnection().getServerLevel())));if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError(message);}
}
