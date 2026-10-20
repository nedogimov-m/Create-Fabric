package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.actors.seat.*;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.*;
import com.simibubi.create.content.logistics.stockTicker.*;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.core.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;

final class StockTickerRenderingTest {
    private static final BlockPos T=new BlockPos(2124,4,6),B=T.east(),S=new BlockPos(2120,4,3);
    static void run(ClientGameTestContext context,TestSingleplayerContext world) {
        var server=world.getServer();var network=UUID.randomUUID();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 2124 8 12");
        context.waitFor(client->Math.abs(client.player.getX()-2124.5)<.1,300);waitChunks(context,world,new BlockPos(2112,3,0),new BlockPos(2143,3,15));server.runCommand("fill 2112 3 0 2143 3 15 minecraft:smooth_quartz");
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();
            level.setBlockAndUpdate(S.north(),Blocks.BARREL.defaultBlockState());var barrel=(BarrelBlockEntity)level.getBlockEntity(S.north());barrel.setItem(0,new ItemStack(Items.APPLE,20));barrel.setItem(1,new ItemStack(Items.DIAMOND,4));
            level.setBlockAndUpdate(S,AllBlocks.PACKAGER.defaultBlockState().setValue(PackagerBlock.FACING,Direction.SOUTH));level.setBlockAndUpdate(S.above(),AllBlocks.STOCK_LINK.defaultBlockState());var link=(PackagerLinkBlockEntity)level.getBlockEntity(S.above());link.placedBy=player.getUUID();link.behaviour.setFrequency(network);link.initialize();
            level.setBlockAndUpdate(T,AllBlocks.STOCK_TICKER.defaultBlockState());var ticker=(StockTickerBlockEntity)level.getBlockEntity(T);ticker.behaviour.setFrequency(network);ticker.tick();
            level.setBlockAndUpdate(B,AllBlocks.BLAZE_BURNER.defaultBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL,BlazeBurnerBlock.HeatLevel.SMOULDERING));player.getInventory().setItem(9,FilterGameTests.filter(false,false,new ItemStack(Items.APPLE)));player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        });
        server.runCommand("gamemode creative @p");server.runCommand("tp @p 2124.5 4 10.5");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().isEmpty()&&Math.abs(client.player.getZ()-10.5)<.1&&Math.abs(client.player.getY()-4)<.1,200);lookBlock(context,T);
        open(context,true);click(context,screen(context).categoryAddPosition(),InputConstants.MOUSE_BUTTON_LEFT,0);
        context.waitFor(client->((StockTickerScreen)client.gui.screen()).getMenu().editing==-1,200);
        context.waitTicks(2);
        context.runOnClient(client->{var screen=(StockTickerScreen)client.gui.screen();var point=screen.slotPosition(1);mouse(screen,point,InputConstants.MOUSE_BUTTON_LEFT,InputConstants.MOD_SHIFT);});
        context.waitFor(client->((StockTickerScreen)client.gui.screen()).getMenu().proxy.getItem(0).is(AllItems.FILTER),200);
        context.waitTicks(2);
        context.runOnClient(client->((StockTickerScreen)client.gui.screen()).categoryNameBox().setValue("Fruit"));click(context,screen(context).categorySavePosition(),InputConstants.MOUSE_BUTTON_LEFT,0);
        context.waitFor(client->{var menu=((StockTickerScreen)client.gui.screen()).getMenu();return menu.editing==StockTickerMenu.IDLE&&menu.owner.categories.size()==1&&menu.owner.categories.getFirst().getHoverName().getString().equals("Fruit");},200);
        context.takeScreenshot("create-vulkan-stock-ticker-categories");close(context);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();if(!player.getInventory().getItem(9).isEmpty())throw new AssertionError("Category duplicated the real filter");});

        server.runCommand("tp @p 2126.5 4 10.5");context.waitFor(client->Math.abs(client.player.getX()-2126.5)<.1,200);lookBlock(context,B);open(context,false);
        context.waitFor(client->((StockTickerScreen)client.gui.screen()).getMenu().stock.size()==2,200);
        context.runOnClient(client->((StockTickerScreen)client.gui.screen()).searchBox().setValue("apple"));click(context,screen(context).stockPosition(1,0),InputConstants.MOUSE_BUTTON_LEFT,0);
        context.runOnClient(client->{var screen=(StockTickerScreen)client.gui.screen();var point=screen.stockPosition(1,0);screen.mouseScrolled(point.x,point.y,0,4);if(screen.selectedAmount(0)!=5)throw new AssertionError("Native stock quantity wheel failed");screen.addressBox().setValue("Ticker test");});
        context.waitFor(client->((StockTickerScreen)client.gui.screen()).keeperPortrait.equals("burner"),200);context.takeScreenshot("create-vulkan-stock-ticker-order");click(context,screen(context).sendPosition(),InputConstants.MOUSE_BUTTON_LEFT,0);
        var sent=new AtomicBoolean();for(int i=0;i<100&&!sent.get();i++){context.waitTicks(1);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var packager=(PackagerBlockEntity)level.getBlockEntity(S);sent.set(!packager.heldBox.isEmpty()&&PackageItem.getAddress(packager.heldBox).equals("Ticker test"));});}if(!sent.get())throw new AssertionError("Ticker request did not create a real package");
        server.runOnServer(ignored->{var barrel=(BarrelBlockEntity)world.getConnection().getServerLevel().getBlockEntity(S.north());if(barrel.getItem(0).getCount()!=15)throw new AssertionError("Ticker order did not extract exactly five apples");});close(context);

        server.runOnServer(ignored->{var player=world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.REDSTONE_REQUESTER));});
        context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.REDSTONE_REQUESTER.asItem()),200);lookBlock(context,B);open(context,false);context.waitFor(client->((StockTickerScreen)client.gui.screen()).getMenu().stock.size()==2,200);
        context.runOnClient(client->((StockTickerScreen)client.gui.screen()).searchBox().setValue("apple"));var point=screen(context).stockPosition(1,0);for(int i=0;i<3;i++)click(context,point,InputConstants.MOUSE_BUTTON_LEFT,0);
        context.runOnClient(client->((StockTickerScreen)client.gui.screen()).addressBox().setValue("Programmed"));click(context,screen(context).sendPosition(),InputConstants.MOUSE_BUTTON_LEFT,0);
        context.waitFor(client->client.gui.screen()==null&&client.player.getMainHandItem().has(AllDataComponents.AUTO_REQUEST_DATA),200);
        context.runOnClient(client->{var data=client.player.getMainHandItem().get(AllDataComponents.AUTO_REQUEST_DATA);if(data.encodedRequest().stacks().getFirst().count!=3||!data.encodedTargetAddress().equals("Programmed")||!network.equals(LogisticallyLinkedBlockItem.networkFromStack(client.player.getMainHandItem())))throw new AssertionError("Ticker programming lost request or network");});

        NativeContraptionFixtures.awaitEntityReady(context,world,300,T.west());
        var keeper=new AtomicReference<UUID>();server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(B,Blocks.AIR.defaultBlockState());level.setBlockAndUpdate(T.west(),AllBlocks.SEATS.get(DyeColor.BLUE).defaultBlockState());var cow=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);cow.setNoAi(true);cow.setPersistenceRequired();cow.setPos(Vec3.atCenterOf(T.west()));level.addFreshEntity(cow);NativeContraptionFixtures.sitDown(level,T.west(),cow);keeper.set(cow.getUUID());level.getServer().getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);});
        server.runCommand("tp @p 2121.5 4 9.5");context.waitFor(client->Math.abs(client.player.getX()-2121.5)<.1&&client.player.getMainHandItem().isEmpty()&&client.level.getEntitiesOfClass(SeatEntity.class,new AABB(T.west())).stream().anyMatch(Entity::isVehicle),300);look(context,Vec3.atLowerCornerOf(T.west()).add(.5,.9,.5));
        context.waitFor(client->client.hitResult instanceof EntityHitResult hit&&hit.getEntity().getUUID().equals(keeper.get()),200);open(context,false);context.waitFor(client->((StockTickerScreen)client.gui.screen()).getMenu().stock.size()==2,200);context.waitFor(client->((StockTickerScreen)client.gui.screen()).keeperPortrait.equals("entity"),200);context.takeScreenshot("create-vulkan-stock-ticker-seated-keeper");
        crafting(context,world,network,keeper.get());
        server.runCommand("gamemode spectator @p");server.runCommand("tp @p 2124 8 12");context.waitFor(client->client.player.isSpectator(),200);
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);
        context.runOnClient(client->{var state=client.level.getBlockState(T);var model=client.getModelManager().getBlockStateModelSet().get(state);var parts=new ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();model.collectParts(net.minecraft.util.RandomSource.create(42),parts);if(parts.isEmpty()||parts.stream().allMatch(part->part.getQuads(null).isEmpty()&&Arrays.stream(Direction.values()).allMatch(side->part.getQuads(side).isEmpty())))throw new AssertionError("Ticker model missing after reload");});
        server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);server.runOnServer(ignored->unloaded.set(!world.getConnection().getServerLevel().isLoaded(T)));}if(!unloaded.get())throw new AssertionError("Stock ticker kept its scene loaded");
        server.runCommand("tp @p 2124 8 12");context.waitFor(client->Math.abs(client.player.getX()-2124.5)<.1&&client.level.getBlockEntity(T) instanceof StockTickerBlockEntity,400);waitChunks(context,world,T,S);waitKeeperAndLink(context,world,keeper.get(),network);
        server.runOnServer(ignored->{var ticker=(StockTickerBlockEntity)LoadedLevelAccess.blockEntity(world.getConnection().getServerLevel(),T);if(!ticker.behaviour.freqId.equals(network)||ticker.getAccurateSummary().getCountOf(new ItemStack(Items.APPLE))!=15||ticker.categories.size()!=1||!ticker.categories.getFirst().getHoverName().getString().equals("Fruit")||!ticker.isKeeperPresent())throw new AssertionError("Ticker stock/categories/keeper did not recover after chunk return: network="+ticker.behaviour.freqId+", apples="+ticker.getAccurateSummary().getCountOf(new ItemStack(Items.APPLE))+", categories="+ticker.categories+", keeper="+ticker.isKeeperPresent());});
        look(context,Vec3.atCenterOf(T));world.getConnection().waitForChunksRender();context.takeScreenshot("create-vulkan-stock-ticker-after-chunk-return");
    }
    private static void crafting(ClientGameTestContext context,TestSingleplayerContext world,UUID network,UUID keeper){
        var server=world.getServer();var stick=net.minecraft.resources.Identifier.withDefaultNamespace("stick");
        context.waitTicks(25);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var source=(PackagerBlockEntity)level.getBlockEntity(S);var player=level.getServer().getPlayerList().getPlayers().getFirst();var parcel=source.heldBox.copy();if(!parcel.isEmpty())try(var tx=net.fabricmc.fabric.api.transfer.v1.transaction.Transaction.openOuter()){if(source.inventory.extract(net.fabricmc.fabric.api.transfer.v1.item.ItemVariant.of(parcel),1,tx)!=1)throw new AssertionError("Old order parcel not available for real extraction");tx.commit();player.getInventory().setItem(10,parcel);}((BarrelBlockEntity)level.getBlockEntity(S.north())).setItem(2,new ItemStack(Items.OAK_PLANKS,64));source.triggerStockCheck();});
        context.waitFor(client->((StockTickerScreen)client.gui.screen()).getMenu().stock.stream().anyMatch(entry->entry.stack.is(Items.OAK_PLANKS)),200);
        click(context,screen(context).recipeModePosition(),InputConstants.MOUSE_BUTTON_LEFT,0);
        context.waitFor(client->((StockTickerScreen)client.gui.screen()).getMenu().craftingChoices.stream().anyMatch(choice->choice.recipe().equals(stick)),300);
        context.runOnClient(client->((StockTickerScreen)client.gui.screen()).searchBox().setValue("stick"));context.waitTicks(2);
        var point=context.computeOnClient(client->{var screen=(StockTickerScreen)client.gui.screen();int index=screen.recipeIndex(stick);if(index<0)throw new AssertionError("Native stick recipe missing");return screen.recipePosition(index);});
        click(context,point,InputConstants.MOUSE_BUTTON_LEFT,0);context.runOnClient(client->((StockTickerScreen)client.gui.screen()).mouseScrolled(point.x,point.y,0,1));
        context.waitFor(client->{var menu=((StockTickerScreen)client.gui.screen()).getMenu();return menu.craftingCart.selections().size()==1&&menu.craftingCart.selections().getFirst().crafts()==2&&menu.craftingCart.order().orderedStacksMatchOrderedRecipes();},200);
        context.getInput().holdKey(options->options.keySprint);context.waitTicks(2);
        try{
            context.runOnClient(client->{if(!client.hasControlDown())throw new AssertionError("Native Control modifier not pressed");((StockTickerScreen)client.gui.screen()).mouseScrolled(point.x,point.y,0,1);});
            context.waitFor(client->((StockTickerScreen)client.gui.screen()).getMenu().craftingCart.selections().getFirst().crafts()==5,200);
            context.runOnClient(client->((StockTickerScreen)client.gui.screen()).mouseScrolled(point.x,point.y,0,-1));
            context.waitFor(client->((StockTickerScreen)client.gui.screen()).getMenu().craftingCart.selections().getFirst().crafts()==2,200);
        }finally{context.getInput().releaseKey(options->options.keySprint);}
        var material=screen(context).stockPosition(6,0);
        context.runOnClient(client->((StockTickerScreen)client.gui.screen()).mouseScrolled(material.x,material.y,0,-1));
        context.waitFor(client->{var cart=((StockTickerScreen)client.gui.screen()).getMenu().craftingCart;return cart.selections().size()==1&&cart.selections().getFirst().crafts()==1&&cart.order().stacks().getFirst().count==3&&cart.manual().size()==1&&cart.manual().getFirst().count==1&&cart.order().orderedCrafts().isEmpty();},200);
        context.waitTicks(2);context.takeScreenshot("create-vulkan-stock-ticker-edited-materials");
        context.runOnClient(client->{var screen=(StockTickerScreen)client.gui.screen();screen.mouseScrolled(material.x,material.y,0,-1);screen.mouseScrolled(point.x,point.y,0,1);});
        context.waitFor(client->{var cart=((StockTickerScreen)client.gui.screen()).getMenu().craftingCart;return cart.selections().size()==1&&cart.selections().getFirst().crafts()==2&&cart.order().stacks().getFirst().count==4&&cart.manual().isEmpty()&&cart.order().orderedStacksMatchOrderedRecipes();},200);
        context.runOnClient(client->((StockTickerScreen)client.gui.screen()).addressBox().setValue("Crafted"));context.waitTicks(2);context.takeScreenshot("create-vulkan-stock-ticker-crafting-preview");click(context,screen(context).sendPosition(),InputConstants.MOUSE_BUTTON_LEFT,0);
        var sent=new AtomicBoolean();for(int i=0;i<100&&!sent.get();i++){context.waitTicks(1);server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var box=((PackagerBlockEntity)level.getBlockEntity(S)).heldBox;var order=PackageItem.getOrderContext(box);sent.set(order!=null&&order.orderedStacksMatchOrderedRecipes()&&PackageItem.getAddress(box).equals("Crafted"));});}if(!sent.get())throw new AssertionError("Crafting UI did not dispatch a real ordered parcel");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();if(((BarrelBlockEntity)level.getBlockEntity(S.north())).getItem(2).getCount()!=60)throw new AssertionError("Recipe order did not extract exactly four planks");var contextOrder=PackageItem.getOrderContext(((PackagerBlockEntity)level.getBlockEntity(S)).heldBox);if(contextOrder.orderedCrafts().getFirst().count()!=2)throw new AssertionError("Craft count lost in actual parcel");});close(context);
        server.runOnServer(ignored->world.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.REDSTONE_REQUESTER)));
        context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.REDSTONE_REQUESTER.asItem()),200);look(context,Vec3.atLowerCornerOf(T.west()).add(.5,.9,.5));context.waitFor(client->client.hitResult instanceof EntityHitResult hit&&hit.getEntity().getUUID().equals(keeper),200);open(context,false);
        click(context,screen(context).recipeModePosition(),InputConstants.MOUSE_BUTTON_LEFT,0);context.waitFor(client->!((StockTickerScreen)client.gui.screen()).getMenu().craftingChoices.isEmpty(),300);context.runOnClient(client->((StockTickerScreen)client.gui.screen()).searchBox().setValue("stick"));context.waitTicks(2);
        var encoded=context.computeOnClient(client->{var screen=(StockTickerScreen)client.gui.screen();return screen.recipePosition(screen.recipeIndex(stick));});click(context,encoded,InputConstants.MOUSE_BUTTON_LEFT,0);
        context.waitFor(client->((StockTickerScreen)client.gui.screen()).hasCraftingContext(),200);
        var encodedMaterial=screen(context).stockPosition(6,0);context.runOnClient(client->((StockTickerScreen)client.gui.screen()).mouseScrolled(encodedMaterial.x,encodedMaterial.y,0,1));
        context.waitFor(client->{var cart=((StockTickerScreen)client.gui.screen()).getMenu().craftingCart;return cart.selections().size()==1&&cart.order().stacks().getFirst().count==3&&cart.manual().size()==1&&cart.order().orderedCrafts().isEmpty();},200);
        context.runOnClient(client->{var screen=(StockTickerScreen)client.gui.screen();screen.mouseScrolled(encodedMaterial.x,encodedMaterial.y,0,-1);screen.addressBox().setValue("Craft code");screen.sendOrder();});
        context.waitFor(client->client.gui.screen()==null&&client.player.getMainHandItem().has(AllDataComponents.AUTO_REQUEST_DATA),200);
        context.runOnClient(client->{var stack=client.player.getMainHandItem();var data=stack.get(AllDataComponents.AUTO_REQUEST_DATA);if(!data.encodedRequest().orderedStacksMatchOrderedRecipes()||data.encodedRequest().orderedCrafts().getFirst().count()!=1||!data.encodedTargetAddress().equals("Craft code")||!network.equals(LogisticallyLinkedBlockItem.networkFromStack(stack)))throw new AssertionError("Crafting Requester program lost recipe, address or frequency");});
    }
    private static void waitKeeperAndLink(ClientGameTestContext context,TestSingleplayerContext world,UUID keeper,UUID network){
        // A FULL block chunk does not mean its persisted entities or live links are published yet.
        var ready=new AtomicBoolean();
        for(int i=0;i<300&&!ready.get();i++){
            world.getServer().runOnServer(ignored->{
                var level=world.getConnection().getServerLevel();var cow=level.getEntity(keeper);
                boolean seated=cow!=null&&level.getEntitiesOfClass(SeatEntity.class,new AABB(T.west())).stream().anyMatch(seat->seat.hasPassenger(cow));
                boolean linked=LogisticallyLinkedBehaviour.getAllPresent(level,network,false).stream().anyMatch(link->link.getPos().equals(S.above()));
                ready.set(seated&&linked);
            });
            if(!ready.get())context.waitTicks(1);
        }
        if(!ready.get())throw new AssertionError("Ticker saved keeper/link did not become queryable after chunk return");
        Create.LOGGER.info("Ticker original seated keeper and live source link returned before stock assertions");
    }
    private static StockTickerScreen screen(ClientGameTestContext context){return context.computeOnClient(client->(StockTickerScreen)client.gui.screen());}
    private static void open(ClientGameTestContext context,boolean category){context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof StockTickerScreen screen&&screen.getMenu().categoryMode==category,200);context.waitTicks(2);}
    private static void close(ClientGameTestContext context){context.runOnClient(client->((StockTickerScreen)client.gui.screen()).onClose());context.waitFor(client->client.gui.screen()==null,200);}
    private static void click(ClientGameTestContext context,Vec2 point,int button,int modifiers){context.runOnClient(client->mouse((StockTickerScreen)client.gui.screen(),point,button,modifiers));}
    private static void mouse(StockTickerScreen screen,Vec2 point,int button,int modifiers){var event=new net.minecraft.client.input.MouseButtonEvent(point.x,point.y,new net.minecraft.client.input.MouseButtonInfo(button,modifiers));screen.mouseClicked(event,false);screen.mouseReleased(event);}
    private static void look(ClientGameTestContext context,Vec3 point){var rotation=context.computeOnClient(client->{var delta=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});context.getInput().lookAt(rotation.x,rotation.y);}
    private static void lookBlock(ClientGameTestContext context,BlockPos pos){look(context,Vec3.atCenterOf(pos).add(0,.2,0));context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(pos),200);}
    private static void waitChunks(ClientGameTestContext context,TestSingleplayerContext world,BlockPos a,BlockPos b){var ready=new AtomicBoolean();for(int i=0;i<300&&!ready.get();i++){world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();ready.set(level.isLoaded(a)&&level.isLoaded(b));});if(!ready.get())context.waitTicks(1);}if(!ready.get())throw new AssertionError("Stock ticker scene chunks unavailable");}
}
