package net.createmod.ponder.test;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderIndexScreen;
import net.createmod.ponder.foundation.ui.PonderTagIndexScreen;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/** One actual native client→server command→typed S2C→source Ponder UI/reload route. */
public final class PonderCommandClientTest implements FabricClientGameTest {
    private static final Identifier SUBJECT=Identifier.fromNamespaceAndPath("create","cogwheel");
    private static StoryBoardEntry firstEntry(){return PonderIndex.getSceneAccess().getRegisteredEntries().stream().filter(entry->entry.getKey().equals(SUBJECT)).findFirst().orElseThrow().getValue();}
    private static void command(ClientGameTestContext context,String text){context.runOnClient(client->{require(client.player!=null&&client.getConnection()!=null,"Actual joined native client connection is required");client.getConnection().sendCommand(text);});}
    private static void close(ClientGameTestContext context){context.runOnClient(client->{if(client.gui.screen()!=null)client.gui.screen().onClose();ScreenOpener.clearStack();});context.waitFor(client->client.gui.screen()==null,80);}
    @Override public void runTest(ClientGameTestContext context){
        try(var joined=context.worldBuilder().create()){
            context.waitFor(client->client.level!=null&&client.player!=null&&PonderIndex.getSceneAccess().doScenesExistForId(SUBJECT),400);
            context.runOnClient(client->{require(RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(Locale.ROOT).contains("vulkan"),"Actual Ponder command client test uses native Vulkan");require(!FabricLoader.getInstance().isModLoaded("sodium"),"Source command actions run without Sodium");ScreenOpener.clearStack();});
            command(context,"ponder");context.waitFor(client->client.gui.screen() instanceof PonderTagIndexScreen,200);close(context);
            command(context,"ponder index");context.waitFor(client->client.gui.screen() instanceof PonderIndexScreen,200);close(context);
            command(context,"ponder tags");context.waitFor(client->client.gui.screen() instanceof PonderTagIndexScreen,200);close(context);
            command(context,"ponder "+SUBJECT);context.waitFor(client->client.gui.screen() instanceof PonderUI ui&&ui.getActiveScene().getLocation().equals(SUBJECT),250);
            var originalUi=context.computeOnClient(client->(PonderUI)client.gui.screen());
            var originalScene=context.computeOnClient(client->originalUi.getActiveScene());
            context.waitTicks(3);context.takeScreenshot("create-vulkan-ponder-native-server-command-opens-source-scene");

            var history=context.computeOnClient(client->ScreenOpener.getScreenHistory());
            command(context,"ponder create_test:missing_command_scene");
            // A second actual command supplies a round-trip barrier after the missing-scene action.
            var beforeReload=context.computeOnClient(client->firstEntry());
            command(context,"ponder reload");
            context.waitFor(client->firstEntry()!=beforeReload,250);
            context.runOnClient(client->{
                require(client.gui.screen()==originalUi&&originalUi.getActiveScene()==originalScene,"Missing scene keeps the exact existing screen/scene; reload index does not invent a screen transition");
                require(ScreenOpener.getScreenHistory().equals(history),"Missing scene/reload leave source navigation history intact");
                require(PonderIndex.getSceneAccess().doScenesExistForId(SUBJECT),"Actual typed RELOAD action rebuilds genuine source registrations");
            });
            close(context);
            command(context,"ponder "+SUBJECT);context.waitFor(client->client.gui.screen() instanceof PonderUI ui&&ui.getActiveScene().getLocation().equals(SUBJECT),250);
            context.runOnClient(client->{var ui=(PonderUI)client.gui.screen();require(ui!=originalUi&&ui.getActiveScene()!=originalScene,"Opening after real reload compiles fresh actual scene/UI owners");});
            close(context);
            joined.getServer().runCommand("execute as @p run ponder ponder:tags @s");
            context.waitFor(client->client.gui.screen() instanceof PonderTagIndexScreen,200);
            context.runOnClient(client->require(!PonderIndex.getSceneAccess().doScenesExistForId(Identifier.fromNamespaceAndPath("create_test","missing_command_scene")),"Missing command subject was never registered by an action"));
            close(context);
            System.out.println("CREATE_PONDER_NATIVE_COMMAND_ACTIONS self=tags,index,scene reload=fresh-registry target=typedS2C missing=preserved-original-screen");
        }finally{context.runOnClient(client->{ScreenOpener.clearStack();if(client.gui.screen() instanceof net.createmod.ponder.foundation.ui.AbstractPonderScreen)client.gui.setScreen(null);});}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
