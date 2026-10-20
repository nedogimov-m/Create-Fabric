package com.simibubi.create.test;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import com.simibubi.create.content.contraptions.glue.*;
import com.simibubi.create.foundation.item.LoadedItemStorage;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.item.*;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.*;

/** World capture/disassembly API and native packets/rendering; moving entity/controller tests follow their implementation. */
final class ContraptionAssemblyRenderingTest {
    private static final BlockPos P=new BlockPos(3403,5,5),Q=P.east(),C=Q.south(),T=P.east(8);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 3406.5 9 12.5");context.waitFor(client->Math.abs(client.player.getX()-3406.5)<.1,300);
        context.waitFor(client->client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(P.getX()>>4,0))&&client.level.getLightEngine().lightOnInColumn(SectionPos.getZeroNode(T.getX()>>4,0)),300);
        server.runCommand("fill 3398 4 0 3418 4 15 minecraft:smooth_quartz");server.runCommand("setblock 3403 5 5 minecraft:chest[facing=north]");server.runCommand("setblock 3404 5 5 minecraft:barrel[facing=north]");server.runCommand("setblock 3404 5 6 minecraft:stone");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();((ChestBlockEntity)level.getBlockEntity(P)).setItem(0,payload());((BarrelBlockEntity)level.getBlockEntity(Q)).setItem(4,payload());var player=level.getServer().getPlayerList().getPlayers().getFirst();player.getInventory().clearContent();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.SUPER_GLUE));player.inventoryMenu.broadcastFullState();});
        server.runCommand("tp @p 3403.5 5 9.5");server.runCommand("gamemode survival @p");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().is(AllItems.SUPER_GLUE)&&Math.abs(client.player.getZ()-9.5)<.1,200);
        click(context,P);context.waitFor(client->P.equals(SuperGlueSelectionHandler.first()),200);look(context,Vec3.atCenterOf(C));context.waitFor(client->SuperGlueSelectionHandler.latest()!=null&&SuperGlueSelectionHandler.latest().valid()&&SuperGlueSelectionHandler.latest().cluster().size()==3,200);context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.level.getEntitiesOfClass(SuperGlueEntity.class,new AABB(P).inflate(4)).size()==1,200);world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-contraption-source-capture");
        var saved=new AtomicReference<CompoundTag>();var stale=new AtomicReference<Storage<ItemVariant>>();var owned=new AtomicReference<Contraption>();
        NativeContraptionFixtures.awaitCaptureReady(context,world,200,P,Q,C);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();stale.set(LoadedItemStorage.find(level,Q,null));var structure=new BearingContraption(false,Direction.EAST);try{if(!structure.assemble(level,P.west())||structure.getBlocks().size()!=3||!structure.tryRemoveBlocksFromWorld(level,BlockPos.ZERO))throw new AssertionError("Real structure capture failed");}catch(AssemblyException e){throw new AssertionError(e);}owned.set(structure);var player=level.getServer().getPlayerList().getPlayers().getFirst();var provider=structure.getStorage().createMenuProvider(structure,new BlockPos(1,0,0),p->p.distanceToSqr(Vec3.atCenterOf(Q))<64,p->{});if(provider==null||player.openMenu(provider).isEmpty())throw new AssertionError("Native mounted barrel menu did not open");});
        context.waitFor(client->client.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen&&client.player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu&&same(client.player.containerMenu.getSlot(4).getItem(),7),200);
        context.runOnClient(client->{if(client.gui.screen().getTitle().getString().contains("create.contraptions"))throw new AssertionError("Mounted menu title is not translated");});
        clickMenu(context,4,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT);
        context.waitFor(client->same(client.player.containerMenu.getCarried(),4)&&same(client.player.containerMenu.getSlot(4).getItem(),3),200);
        clickMenu(context,27,com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT);
        context.waitFor(client->client.player.containerMenu.getCarried().isEmpty()&&same(client.player.getInventory().getItem(9),4),200);
        screenshot(context,"create-vulkan-mounted-storage-native-menu");context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);context.waitFor(client->client.gui.screen()==null,200);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var structure=owned.getAndSet(null);var barrel=structure.getStorage().getAllItemStorages().get(new BlockPos(1,0,0));if(barrel.getStackInSlot(4).getCount()!=3)throw new AssertionError("Native menu clicks did not alter owned storage");barrel.setStackInSlot(8,new ItemStack(Items.GOLD_INGOT,5));saved.set(structure.writeNBT(level.registryAccess(),false));if(!saved.get().toString().contains("Owned during move"))throw new AssertionError("Captured items not owned by saved structure");structure.getStorage().setOwned(false);});
        context.waitFor(client->client.level.getBlockState(P).isAir()&&client.level.getBlockState(Q).isAir()&&client.level.getBlockState(C).isAir(),200);
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();
        for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();unloaded.set(level.getChunkSource().getChunkNow(P.getX()>>4,0)==null&&level.getChunkSource().getChunkNow(T.getX()>>4,0)==null);});}
        if(!unloaded.get())throw new AssertionError("World capture or held storage kept source/destination chunks loaded");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();try(var tx=Transaction.openOuter()){if(stale.get().extract(ItemVariant.of(payload()),7,tx)!=0)throw new AssertionError("Removed world storage duplicated captured items");}var structure=Contraption.fromNBT(level,saved.get(),false);boolean refused=false;try{structure.tryAddBlocksToWorld(level,new StructureTransform(T,0,90,0));}catch(AssemblyException expected){refused=true;}if(!refused||structure.phase()!=Contraption.Phase.REMOVED||level.getChunkSource().getChunkNow(T.getX()>>4,0)!=null)throw new AssertionError("Unloaded placement changed ownership or loaded chunk");});
        server.runCommand("tp @p 3412.5 9 11.5");context.waitFor(client->Math.abs(client.player.getX()-3412.5)<.1&&client.level.isLoaded(T),400);
        // FULL block data can return before the native entity queries used by world restoration.
        var ready=new AtomicBoolean();for(int i=0;i<300&&!ready.get();i++){server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();ready.set(com.simibubi.create.foundation.utility.LoadedEntityAccess.isReady(level,T));});if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError("Destination native block/entity queries did not return");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var structure=Contraption.fromNBT(level,saved.get(),false);var old=structure.getStorage().getAllItems();try{if(!structure.tryAddBlocksToWorld(level,new StructureTransform(T,0,90,0)))throw new AssertionError("Restoration refused");}catch(AssemblyException e){throw new AssertionError(e);}var chest=(ChestBlockEntity)level.getBlockEntity(T);var barrel=(BarrelBlockEntity)level.getBlockEntity(T.north());if(chest==null||barrel==null||!same(chest.getItem(0),7)||!same(barrel.getItem(4),3)||!barrel.getItem(8).is(Items.GOLD_INGOT)||barrel.getItem(8).getCount()!=5||!level.getBlockState(T.east().north()).is(Blocks.STONE))throw new AssertionError("Rotated real world payload mismatch");try(var tx=Transaction.openOuter()){if(old.extract(ItemVariant.of(payload()),7,tx)!=0)throw new AssertionError("Returned owner still exposes mounted items");}if(!level.getBlockState(P).isAir()||!level.getBlockState(Q).isAir()||!level.getBlockState(C).isAir())throw new AssertionError("Source still has copied blocks");});
        context.waitFor(client->client.level.getBlockState(T).is(Blocks.CHEST)&&client.level.getBlockState(T.north()).is(Blocks.BARREL)&&client.level.getBlockState(T.east().north()).is(Blocks.STONE),300);look(context,Vec3.atCenterOf(T));world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-contraption-rotated-world");
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);
        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.inventoryMenu.broadcastFullState();});server.runCommand("tp @p 3411.5 5 1.5");server.runCommand("gamemode survival @p");
        context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().isEmpty()&&Math.abs(client.player.getZ()-1.5)<.1,200);click(context,T.north());context.waitFor(client->client.gui.screen()!=null&&client.player.containerMenu instanceof net.minecraft.world.inventory.ChestMenu&&same(client.player.containerMenu.getSlot(4).getItem(),3)&&client.player.containerMenu.getSlot(8).getItem().getCount()==5,200);screenshot(context,"create-vulkan-contraption-restored-barrel-menu");
        context.getInput().pressKey(com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE);context.waitFor(client->client.gui.screen()==null,200);server.runCommand("gamemode spectator @p");
    }
    private static ItemStack payload(){var item=new ItemStack(Items.DIAMOND,7);item.set(DataComponents.CUSTOM_NAME,Component.literal("Owned during move"));return item;}
    private static boolean same(ItemStack item,int amount){return item.getCount()==amount&&ItemStack.isSameItemSameComponents(item,payload());}
    private static void clickMenu(ClientGameTestContext context,int index,int button){var point=context.computeOnClient(client->{var screen=client.gui.screen();var menu=(net.minecraft.world.inventory.ChestMenu)client.player.containerMenu;var slot=menu.getSlot(index);float scaleX=(float)client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth();float scaleY=(float)client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight();return new Vec2(((screen.width-176)/2+slot.x+8)*scaleX,((screen.height-(114+menu.getRowCount()*18))/2+slot.y+8)*scaleY);});context.getInput().setCursorPos(point.x,point.y);context.waitTicks(2);context.getInput().pressMouse(button);}
    private static void click(ClientGameTestContext context,BlockPos pos){look(context,Vec3.atCenterOf(pos));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),200);context.waitTicks(4);context.getInput().pressKey(options->options.keyUse);}
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(angle.x,angle.y);}
}
