package net.createmod.ponder.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.createmod.ponder.foundation.PonderTooltipHandler;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.SessionSearchTrees;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.Vec2;

/** Actual native asynchronous search indexing must not mutate the live Ponder hover/weak text cache. */
public final class PonderTooltipConcurrencyClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext c){
        var backgroundCallbacks=new AtomicInteger();var visibleCallbacks=new AtomicInteger();
        Consumer<ItemStack> observer=stack->{if(!Minecraft.getInstance().isSameThread())backgroundCallbacks.incrementAndGet();else if(stack.is(AllBlocks.COGWHEEL.asItem()))visibleCallbacks.incrementAndGet();};
        c.runOnClient(client->{PonderTooltipHandler.reset();PonderTooltipHandler.registerHoveredPonderStackCallback(observer);});
        try(var w=c.worldBuilder().create()){
            w.getServer().runCommand("gamemode survival @p");w.getServer().runCommand("item replace entity @p hotbar.0 with create:cogwheel");
            c.waitFor(client->client.player.getInventory().getItem(0).is(AllBlocks.COGWHEEL.asItem()),200);
            c.getInput().pressKey(options->options.keyInventory);c.waitFor(client->client.gui.screen() instanceof InventoryScreen,200);
            var at=c.computeOnClient(client->{var screen=(InventoryScreen)client.gui.screen();var origin=(com.simibubi.create.test.mixin.NativeContainerScreenAccessor)screen;var slot=screen.getMenu().slots.stream().filter(s->s.container==client.player.getInventory()&&s.getContainerSlot()==0).findFirst().orElseThrow();var window=client.getWindow();return new Vec2((origin.create$getLeft()+slot.x+8f)*window.getScreenWidth()/window.getGuiScaledWidth(),(origin.create$getTop()+slot.y+8f)*window.getScreenHeight()/window.getGuiScaledHeight());});
            c.getInput().setCursorPos(at.x,at.y);c.waitFor(client->visibleCallbacks.get()>0,200);
            var itemContext=c.computeOnClient(client->Item.TooltipContext.of(client.level));
            var one=CompletableFuture.supplyAsync(()->new ItemStack(AllBlocks.FLUID_PIPE).getTooltipLines(itemContext,null,TooltipFlag.NORMAL));
            c.waitFor(client->one.isDone(),200);one.join();
            if(backgroundCallbacks.get()!=0)throw new AssertionError("Actual native background tooltip indexing invoked live Ponder hover callbacks: "+backgroundCallbacks.get());
            for(int round=0;round<3;round++){
                var futures=c.computeOnClient(client->{
                    var stacks=BuiltInRegistries.ITEM.stream().filter(item->BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("create")).map(ItemStack::new).toList();
                    var pending=new ArrayList<CompletableFuture<Boolean>>();
                    for(int worker=0;worker<8;worker++){
                        var trees=new SessionSearchTrees();trees.updateCreativeTooltips(client.level.registryAccess(),stacks);
                        pending.add(CompletableFuture.supplyAsync(()->trees.creativeNameSearch().search("create:cogwheel").stream().anyMatch(stack->stack.is(AllBlocks.COGWHEEL.asItem()))));
                    }
                    return List.copyOf(pending);
                });
                c.waitFor(client->futures.stream().allMatch(CompletableFuture::isDone),600);
                if(futures.stream().anyMatch(future->!future.join())||backgroundCallbacks.get()!=0)throw new AssertionError("Concurrent native search failed or changed visible Ponder hover state");
                c.runOnClient(client->{try{var field=PonderTooltipHandler.class.getDeclaredField("trackingStack");field.setAccessible(true);if(!((ItemStack)field.get(null)).is(AllBlocks.COGWHEEL.asItem()))throw new AssertionError("Background native search replaced the actually hovered cogwheel");}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}});
                c.takeScreenshot("create-ponder-native-concurrent-search-round-"+round);
                if(round<2){var reload=c.computeOnClient(Minecraft::reloadResourcePacks);c.waitFor(client->reload.isDone()&&client.gui.overlay()==null,900);reload.join();}
            }
            c.getInput().holdKey(InputConstants.KEY_W);
            try{c.waitFor(client->client.gui.screen() instanceof net.createmod.ponder.foundation.ui.PonderUI,200);}
            finally{c.getInput().releaseKey(InputConstants.KEY_W);}
            c.takeScreenshot("create-ponder-real-inventory-hold-after-concurrent-search");
            System.out.println("CREATE_PONDER_TOOLTIP_CONCURRENCY_PASS nativeIndexes=24 actualResourceReloads=2 backgroundHoverCallbacks="+backgroundCallbacks.get()+" visibleHoverCallbacks="+visibleCallbacks.get()+" nativeHoldOpenedPonder=true");
            c.getInput().pressKey(InputConstants.KEY_ESCAPE);c.waitFor(client->!(client.gui.screen() instanceof net.createmod.ponder.foundation.ui.PonderUI),200);
            if(c.computeOnClient(client->client.gui.screen()!=null))c.getInput().pressKey(InputConstants.KEY_ESCAPE);
            c.waitFor(client->client.gui.screen()==null,200);
        }finally{c.runOnClient(client->{PonderTooltipHandler.removeHoveredPonderStackCallback(observer);PonderTooltipHandler.reset();});}
    }
}
