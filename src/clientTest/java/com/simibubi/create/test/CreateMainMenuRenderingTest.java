package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.Create;
import com.simibubi.create.infrastructure.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;

/** Actual native title-screen input, Create panorama/four PIP cog submissions, animation and resource reload. */
final class CreateMainMenuRenderingTest {
    static void run(ClientGameTestContext context){
        context.runOnClient(client->{if(!com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().backendName().toLowerCase(java.util.Locale.ROOT).contains("vulkan"))throw new AssertionError("Native Create menu proof requires the actual Vulkan backend");});
        context.waitFor(client->client.gui.overlay()==null,600);
        context.runOnClient(client->{if(!(client.gui.screen() instanceof TitleScreen))client.gui.setScreen(new TitleScreen());});
        context.waitTicks(3);click(context,OpenCreateMenuButton.class,null);context.waitFor(client->client.gui.screen() instanceof CreateMainMenuScreen,200);
        context.runOnClient(client->{for(int face=0;face<6;face++)if(client.getResourceManager().getResource(Create.asResource("textures/gui/title/background/panorama_"+face+".png")).isEmpty())throw new AssertionError("Actual source Create panorama face is missing");if(client.getResourceManager().getResource(Create.asResource("textures/gui/logo.png")).isEmpty())throw new AssertionError("Actual source Create logo is missing");var widgets=client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).toList();if(widgets.size()!=7||widgets.stream().filter(w->w.getMessage().equals(Component.translatable("create.menu.ponder_index"))).anyMatch(w->w.active))throw new AssertionError("Title menu changed original existing buttons or enabled in-game Ponder without a world");});
        var first=capture(context,"create-vulkan-native-main-menu-original-panorama-logo-and-four-3d-cogs");context.waitTicks(24);
        var animated=capture(context,"create-vulkan-native-main-menu-original-counterrotating-gears-after-intro");
        if(animated.state().intro()!=1||animated.state().time()<=first.state().time()||animated.state().gears().getFirst().transform().equals(first.state().gears().getFirst().transform())||animated.panoramaYaw()==first.panoramaYaw())throw new AssertionError("Actual native menu did not advance source intro, panorama and cog animation");
        var oldMesh=animated.state().gears().getFirst().model().mesh;
        var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,700);reload.join();
        var refreshed=capture(context,"create-vulkan-native-main-menu-panorama-and-gears-after-resource-reload");if(refreshed.state().gears().getFirst().model().mesh==oldMesh)throw new AssertionError("Menu retained stale native cog geometry after actual resource reload");
        click(context,AbstractWidget.class,Component.translatable("create.menu.configure"));context.waitFor(client->client.gui.screen() instanceof CreateConfigScreen,200);checkConfigTooltips(context);context.waitTicks(6);context.takeScreenshot("create-vulkan-native-config-screen-background-after-real-menu-click");context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.waitFor(client->client.gui.screen() instanceof CreateMainMenuScreen,200);
        click(context,AbstractWidget.class,Component.translatable("create.menu.return"));context.waitFor(client->client.gui.screen() instanceof TitleScreen,200);context.waitTicks(4);
    }
    private static void checkConfigTooltips(ClientGameTestContext context){
        for(boolean bottom:new boolean[]{false,true}){
            var definition=new java.util.concurrent.atomic.AtomicReference<com.simibubi.create.infrastructure.config.NativeConfigBase.CValue<?>>();
            var point=context.computeOnClient(client->{
                var screen=client.gui.screen();if(!(screen instanceof CreateConfigScreen))throw new AssertionError("Actual native configuration screen required for tooltip regression");
                var widgets=screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.active&&w.visible).filter(w->bottom?w instanceof net.minecraft.client.gui.components.EditBox&&!w.getMessage().equals(Component.translatable("create.config.ui.search")):w.getMessage().equals(Component.translatable("options.on"))||w.getMessage().equals(Component.translatable("options.off"))).toList();
                var order=java.util.Comparator.comparingInt(AbstractWidget::getY);var widget=(bottom?widgets.stream().max(order):widgets.stream().min(order)).orElseThrow();
                if(bottom){
                    var title=(net.minecraft.network.chat.contents.TranslatableContents)widget.getMessage().getContents();
                    definition.set(com.simibubi.create.infrastructure.config.AllConfigs.client().directValues().stream()
                        .filter(value->("create.config."+value.key()).equals(title.getKey())).findFirst().orElseThrow());
                }else definition.set(com.simibubi.create.infrastructure.config.AllConfigs.client().tooltips);
                return new Vec2(widget.getX()-10,widget.getY()+widget.getHeight()/2f);
            });
            var cursor=context.computeOnClient(client->{var window=client.getWindow();return new Vec2(point.x*window.getScreenWidth()/window.getGuiScaledWidth(),point.y*window.getScreenHeight()/window.getGuiScaledHeight());});
            context.getInput().setCursorPos(cursor.x,cursor.y);context.waitTicks(3);
            context.runOnClient(client->{try{
                var screen=client.gui.screen();var state=new net.minecraft.client.renderer.state.gui.GuiRenderState();var gui=new net.minecraft.client.gui.GuiGraphicsExtractor(client,state,(int)point.x,(int)point.y);screen.extractRenderState(gui,(int)point.x,(int)point.y,0);
                var before=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<net.minecraft.client.renderer.state.gui.GuiTextRenderState,Boolean>());state.forEachText(before::add);gui.extractDeferredElements((int)point.x,(int)point.y,0);
                var tooltip=new java.util.ArrayList<net.minecraft.client.renderer.state.gui.GuiTextRenderState>();state.forEachText(text->{if(!before.contains(text))tooltip.add(text);});if(tooltip.size()<3)throw new AssertionError("Native configuration tooltip did not preserve separate wrapped title/description/default lines");
                var textField=net.minecraft.client.renderer.state.gui.GuiTextRenderState.class.getDeclaredField("text");textField.setAccessible(true);var joined=new StringBuilder();
                for(var text:tooltip){var value=new StringBuilder();((net.minecraft.util.FormattedCharSequence)textField.get(text)).accept((index,style,codepoint)->{if(Character.isISOControl(codepoint))throw new AssertionError("Native configuration tooltip retained a newline/control glyph instead of a real line break");value.appendCodePoint(codepoint);return true;});joined.append(value).append(' ');var bounds=text.bounds();if(!value.isEmpty()&&(bounds==null||bounds.left()<0||bounds.top()<0||bounds.right()>screen.width||bounds.bottom()>screen.height))throw new AssertionError("Actual native configuration tooltip text escaped GUI bounds: "+bounds+" in "+screen.width+"x"+screen.height);}
                var actual=joined.toString();var compact=actual.replaceAll("\\s+","");for(var description:definition.get().comments())if(!compact.contains(description.replaceAll("\\s+","")))throw new AssertionError("Native wrapped configuration tooltip lost its source description: "+actual);if(actual.contains(definition.get().key()))throw new AssertionError("Native user tooltip exposed its internal implementation key");
                Create.LOGGER.info("Actual native configuration tooltip bounds verified: bottom={}, lines={}, window={}x{}",bottom,tooltip.size(),screen.width,screen.height);
            }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}});
            context.takeScreenshot(bottom ? "create-vulkan-native-config-tooltip-bottom-wrapped-within-bounds" : "create-vulkan-native-config-tooltip-first-wrapped-within-bounds");
        }
    }
    private static CreateMenuNativeProbe.Receipt capture(ClientGameTestContext context,String name){context.runOnClient(client->CreateMenuNativeProbe.begin());context.waitTicks(4);context.takeScreenshot(name);return context.computeOnClient(client->CreateMenuNativeProbe.end());}
    private static void click(ClientGameTestContext context,Class<? extends AbstractWidget> type,Component label){
        var point=context.computeOnClient(client->{var widget=client.gui.screen().children().stream().filter(type::isInstance).map(AbstractWidget.class::cast).filter(w->label==null||w.getMessage().equals(label)).filter(w->w.active&&w.visible).findFirst().orElseThrow();var window=client.getWindow();return new Vec2((widget.getX()+widget.getWidth()/2f)*window.getScreenWidth()/window.getGuiScaledWidth(),(widget.getY()+widget.getHeight()/2f)*window.getScreenHeight()/window.getGuiScaledHeight());});
        context.getInput().setCursorPos(point.x,point.y);context.waitTicks(2);context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
    }
    private CreateMainMenuRenderingTest(){}
}
