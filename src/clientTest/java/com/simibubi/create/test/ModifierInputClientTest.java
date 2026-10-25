package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.display.DisplaySourceConfigurationWidgets;
import com.simibubi.create.content.contraptions.chassis.*;
import com.simibubi.create.content.redstone.displayLink.*;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.*;

/** One focused physical-input/menu/C2S case. No synthetic modifier flags or direct packet apply. */
public final class ModifierInputClientTest implements FabricClientGameTest {
    private static final int CTRL = InputConstants.KEY_G, SHIFT = InputConstants.KEY_H, ALT = InputConstants.KEY_J;
    private static final BlockPos LINK = new BlockPos(32,5,32), SOURCE = LINK.below(), TARGET = LINK.east(3),
        P = new BlockPos(32,5,34), Q = P.east(), SECONDARY = P.west();
    private static final AtomicReference<ScrollInput> INPUT = new AtomicReference<>();
    private static final AtomicReference<Sample> SAMPLE = new AtomicReference<>();
    private record Sample(boolean control, boolean shift, int prior, boolean forward) {}
    private static final class Report {
        String status = "UNRUN", backend, failure;
        boolean sodiumLoaded, registeredOptions, defaultSharedKeysWorkInGui, ordinaryRemapsWorkInGui,
            vanillaKeysNoLongerSubstitute, filterSummaryUsesMappedShift, fixedActivateToolPreserved,
            actualMenuRecipient, singleChassisPacket, bulkChassisPacket, mappingsRestored;
        int authoritativeMenuValue, firstRange, peerRange, excludedRange;
    }

    @Override public void runTest(ClientGameTestContext context) {
        var report = new Report();
        var saved = new LinkedHashMap<KeyMapping,InputConstants.Key>();
        INPUT.set(null); SAMPLE.set(null);
        try {
        context.runOnClient(client -> {
            report.backend = RenderSystem.getDevice().getDeviceInfo().backendName();
            report.sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium");
            require(report.backend.toLowerCase(Locale.ROOT).contains("vulkan") && !report.sodiumLoaded,
                "Actual focused modifier case requires native Vulkan without Sodium");
            for (var key : List.of(AllKeys.CTRL_MODIFIER, AllKeys.SHIFT_MODIFIER, AllKeys.ALT_MODIFIER)) {
                var mapping = key.getKeybind();
                require(Arrays.asList(client.options.keyMappings).contains(mapping), "Real Controls/options entry missing: " + key);
                saved.put(mapping, KeyMappingHelper.getBoundKeyOf(mapping));
                mapping.setKey(mapping.getDefaultKey());
            }
            KeyMapping.resetMapping(); report.registeredOptions = true;
            DisplaySourceConfigurationWidgets.REGISTRY.register(ModifierInputTestFixtures.SOURCE, widgets -> {
                var label = new Label(widgets.x()+5,widgets.y()+4,Component.empty()).withShadow();
                var input = new ScrollInput(widgets.x(),widgets.y(),100,18);
                input.withRange(0,101).withShiftStep(5).titled(Component.literal("Threshold"))
                    .setState(widgets.draft().getIntOr(ModifierInputTestFixtures.VALUE,20))
                    .writingTo(label).calling(value -> widgets.draft().putInt(ModifierInputTestFixtures.VALUE,value));
                // Observe the real public StepContext while retaining the widget's source standardStep.
                var standard = input.standardStep();
                input.withStepFunction(step -> { SAMPLE.set(new Sample(step.control,step.shift,step.currentValue,step.forward)); return standard.apply(step); });
                widgets.add(label); widgets.add(input); INPUT.set(input);
            });
        });
        try (var world = context.worldBuilder().adjustSettings(settings ->
                settings.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL)).create()) {
            report.status = "RUNNING";
            world.getServer().runCommand("gamemode spectator @p");
            world.getServer().runCommand("tp @p 32.5 5 37.5");
            NativeScreenshotReadiness.beforeFloor(context,world,new BlockPos(30,4,30),new BlockPos(38,4,39),new BlockPos(32,5,37));
            world.getServer().runCommand("fill 30 4 30 38 4 39 minecraft:smooth_quartz");
            NativeScreenshotReadiness.afterFloor(context,world,new BlockPos(30,4,30),new BlockPos(38,4,39));
            world.getServer().runCommand("gamemode survival @p");world.getServer().runCommand("tp @p 32.5 5 37.5");
            context.waitFor(client -> client.level!=null && client.level.isLoaded(TARGET) && client.level.isLoaded(Q)
                &&!client.player.isSpectator()&&client.player.onGround()&&client.player.position().distanceToSqr(new Vec3(32.5,5,37.5))<.01,400);
            world.getServer().runOnServer(ignored -> {
                var level=world.getConnection().getServerLevel(); var player=level.getServer().getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
                level.setBlockAndUpdate(SOURCE,Blocks.EMERALD_BLOCK.defaultBlockState());
                level.setBlockAndUpdate(TARGET.below(),Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(TARGET,Blocks.OAK_SIGN.defaultBlockState());
                level.setBlockAndUpdate(LINK,AllBlocks.DISPLAY_LINK.defaultBlockState().setValue(DisplayLinkBlock.FACING,Direction.UP));
                var link=link(level); link.target(TARGET); var initial=new net.minecraft.nbt.CompoundTag();
                initial.putString("Id",ModifierInputTestFixtures.ID.toString());
                require(link.configure(initial,0),"Actual source/sign must configure before menu open");
                for(var pos:List.of(P,Q)) { level.setBlockAndUpdate(pos,AllBlocks.LINEAR_CHASSIS.defaultBlockState()); chassis(level,pos).setRange(1); }
                level.setBlockAndUpdate(SECONDARY,AllBlocks.SECONDARY_LINEAR_CHASSIS.defaultBlockState()); chassis(level,SECONDARY).setRange(4);
                DisplayLinkMenu.open(player,link);
            });
            context.waitFor(client -> client.gui.screen() instanceof DisplayLinkScreen && INPUT.get()!=null,300);
            context.runOnClient(client -> require(((DisplayLinkScreen)client.gui.screen()).children().contains(INPUT.get()),
                "Physical wheel must reach actual menu child produced by the source widget API"));
            hover(context);
            scroll(context,new int[]{InputConstants.KEY_LCONTROL},true,false,20,21);
            scroll(context,new int[]{InputConstants.KEY_LSHIFT},false,true,21,25);
            report.defaultSharedKeysWorkInGui=true;
            context.runOnClient(client -> {
                AllKeys.CTRL_MODIFIER.getKeybind().setKey(InputConstants.Type.KEYBOARD.getOrCreate(CTRL));
                AllKeys.SHIFT_MODIFIER.getKeybind().setKey(InputConstants.Type.KEYBOARD.getOrCreate(SHIFT));
                AllKeys.ALT_MODIFIER.getKeybind().setKey(InputConstants.Type.KEYBOARD.getOrCreate(ALT));
                KeyMapping.resetMapping();
                require(AllKeys.CTRL_MODIFIER.getKeybind().saveString().equals(InputConstants.Type.KEYBOARD.getOrCreate(CTRL).getName())
                    && AllKeys.SHIFT_MODIFIER.getKeybind().saveString().equals(InputConstants.Type.KEYBOARD.getOrCreate(SHIFT).getName()),
                    "Actual options mappings must own the ordinary assigned keys");
            });
            scroll(context,new int[]{InputConstants.KEY_LCONTROL},false,false,25,26);
            scroll(context,new int[]{InputConstants.KEY_LSHIFT},false,false,26,27);
            report.vanillaKeysNoLongerSubstitute=true;
            scroll(context,new int[]{CTRL},true,false,27,28);
            scroll(context,new int[]{SHIFT},false,true,28,30);
            scroll(context,new int[]{CTRL,SHIFT},true,true,30,35);
            report.ordinaryRemapsWorkInGui=true;
            context.getInput().holdKey(ALT);
            try { context.runOnClient(client -> require(AllKeys.altDown(),"The actual registered Alt modifier must also poll its assigned ordinary key")); }
            finally { context.getInput().releaseKey(ALT); }
            context.getInput().holdKey(InputConstants.KEY_LCONTROL);
            try { context.runOnClient(client -> { report.fixedActivateToolPreserved=AllKeys.ACTIVATE_TOOL.isPressed()&&!AllKeys.ctrlDown(); require(report.fixedActivateToolPreserved,"Fixed source ACTIVATE_TOOL remains physical left Ctrl after Create Ctrl remap"); }); }
            finally { context.getInput().releaseKey(InputConstants.KEY_LCONTROL); }
            filterSummary(context,SHIFT,false); filterSummary(context,InputConstants.KEY_LSHIFT,true);
            report.filterSummaryUsesMappedShift=true;
            context.takeScreenshot("create-vulkan-remapped-create-modifiers-real-scrollinput-menu");
            context.clickScreenButton("✓"); context.waitFor(client -> client.gui.screen()==null,200);
            await(context,world,level -> link(level).getSourceConfig().getIntOr(ModifierInputTestFixtures.VALUE,-1)==35
                && sign(level).equals("modifier=35"),"Actual menu-bound C2S did not publish exact scroll callback value/sign");
            report.authoritativeMenuValue=35; report.actualMenuRecipient=true;
            world.getServer().runCommand("item replace entity @p weapon.mainhand with create:wrench");
            context.waitFor(client -> client.player.onGround()&&client.player.getMainHandItem().is(AllItems.WRENCH),200);
            context.waitFor(client -> client.level.getBlockEntity(P) instanceof ChassisBlockEntity
                && client.level.getBlockEntity(Q) instanceof ChassisBlockEntity,200);
            editRange(context,2,InputConstants.KEY_LCONTROL,false);
            await(context,world,level -> chassis(level,P).getRange()==2&&chassis(level,Q).getRange()==1&&chassis(level,SECONDARY).getRange()==4,
                "Vanilla Ctrl alone must commit only the actual selected chassis after Create Ctrl remap");
            report.singleChassisPacket=true;
            editRange(context,3,CTRL,true);
            await(context,world,level -> chassis(level,P).getRange()==3&&chassis(level,Q).getRange()==3&&chassis(level,SECONDARY).getRange()==4,
                "Ordinary remapped Ctrl must commit the actual compatible chassis group through C2S");
            world.getServer().runOnServer(ignored -> { var level=world.getConnection().getServerLevel(); report.firstRange=chassis(level,P).getRange(); report.peerRange=chassis(level,Q).getRange(); report.excludedRange=chassis(level,SECONDARY).getRange(); });
            report.bulkChassisPacket=true; report.status="PASS";
            System.out.println("CREATE_MODIFIER_NATIVE_INPUT default=shared-physical ordinary=G,H,J vanilla-substitute=false menu-sign=35 chassis=3,3,4");
        }
        } catch (Throwable failure) {
            report.status="FAIL"; report.failure=failure.toString();
            if(failure instanceof AssertionError assertion)throw assertion;
            throw new AssertionError(failure);
        } finally {
            for(int key:new int[]{CTRL,SHIFT,ALT,InputConstants.KEY_LCONTROL,InputConstants.KEY_LSHIFT})context.getInput().releaseKey(key);
            context.getInput().releaseKey(options -> options.keyUse);
            context.runOnClient(client -> {
                for(var row:saved.entrySet())row.getKey().setKey(row.getValue());
                KeyMapping.resetMapping();
                report.mappingsRestored=saved.entrySet().stream().allMatch(row -> KeyMappingHelper.getBoundKeyOf(row.getKey()).equals(row.getValue()));
                client.options.save();
                require(report.mappingsRestored,"Restore every actual modifier mapping/options in finally");
            });
            write(report);
        }
    }
    private static void scroll(ClientGameTestContext context,int[] keys,boolean control,boolean shift,int before,int expected) {
        for(int key:keys)context.getInput().holdKey(key);
        try {
            context.runOnClient(client -> { require(client.gui.screen() instanceof DisplayLinkScreen,"Actual source menu must retain GUI focus: "+client.gui.screen()); require(INPUT.get().getState()==before,"Unexpected prior widget value"); SAMPLE.set(null); });
            context.getInput().scroll(1); context.waitTicks(2);
            context.runOnClient(client -> {
                var sample=SAMPLE.get(); require(sample!=null&&sample.control()==control&&sample.shift()==shift&&sample.prior()==before&&sample.forward(),"Actual physical modifier StepContext mismatch: "+sample);
                require(INPUT.get().getState()==expected&&((DisplayLinkScreen)client.gui.screen()).draft().getIntOr(ModifierInputTestFixtures.VALUE,-1)==expected,
                    "Actual source step/rounding/callback did not update menu draft to "+expected);
            });
        } finally { for(int key:keys)context.getInput().releaseKey(key); }
    }
    private static void filterSummary(ClientGameTestContext context,int held,boolean expected) {
        context.getInput().holdKey(held);
        try { context.runOnClient(client -> {
            var filter=new ItemStack(AllItems.FILTER); filter.set(AllDataComponents.FILTER_ITEMS,ItemContainerContents.fromItems(List.of(new ItemStack(Items.APPLE))));
            var summary="- "+new ItemStack(Items.APPLE).getHoverName().getString();
            boolean present=filter.getTooltipLines(Item.TooltipContext.of(client.level),client.player,TooltipFlag.NORMAL).stream().anyMatch(line -> line.getString().equals(summary));
            require(present==expected,"Actual common filter hover summary must use only assigned Create Shift");
        }); } finally { context.getInput().releaseKey(held); }
    }
    private static void hover(ClientGameTestContext context) {
        var p=context.computeOnClient(client -> { var input=INPUT.get(); return screenPixel(client,new Vec2(input.getX()+input.getWidth()/2f,input.getY()+9)); });
        move(context,p);
        context.runOnClient(client -> require(INPUT.get().isMouseOver(
            client.mouseHandler.getScaledXPos(client.getWindow()),client.mouseHandler.getScaledYPos(client.getWindow())),
            "Actual native pointer must reach source widget bounds before wheel input"));
    }
    private static void move(ClientGameTestContext context,Vec2 point) {
        // Native input may discard its first movement after a GUI transition.
        context.getInput().setCursorPos(0,0); context.waitTicks(1);
        context.getInput().setCursorPos(point.x,point.y); context.waitTicks(2);
    }
    private static Vec2 screenPixel(net.minecraft.client.Minecraft client,Vec2 point) {
        return new Vec2(point.x*client.getWindow().getScreenWidth()/client.getWindow().getGuiScaledWidth(),point.y*client.getWindow().getScreenHeight()/client.getWindow().getGuiScaledHeight());
    }
    private static void editRange(ClientGameTestContext context,int value,int modifier,boolean bulk) {
        var angle=context.computeOnClient(client -> { var d=Vec3.atCenterOf(P).subtract(client.player.getEyePosition()); return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)))); });
        context.getInput().lookAt(angle.x,angle.y);
        context.waitFor(client -> client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(P)&&ChassisSettings.panelHit(P,hit),200);
        context.getInput().holdKey(modifier);
        try {
            context.getInput().holdKey(options -> options.keyUse);
            context.waitFor(client -> client.gui.screen() instanceof ChassisSettingsClient.RangeScreen screen&&screen.position().equals(P),200);
            var point=context.computeOnClient(client -> screenPixel(client,((ChassisSettingsClient.RangeScreen)client.gui.screen()).coordinate(value)));
            move(context,point);
            context.waitFor(client -> client.gui.screen() instanceof ChassisSettingsClient.RangeScreen screen&&screen.selection()==value&&screen.bulk()==bulk,120);
            context.getInput().releaseKey(options -> options.keyUse);
            context.waitFor(client -> client.gui.screen()==null,200);
        } finally { context.getInput().releaseKey(options -> options.keyUse); context.getInput().releaseKey(modifier); }
    }
    private static DisplayLinkBlockEntity link(ServerLevel level) { return (DisplayLinkBlockEntity)level.getBlockEntity(LINK); }
    private static ChassisBlockEntity chassis(ServerLevel level,BlockPos pos) { return (ChassisBlockEntity)level.getBlockEntity(pos); }
    private static String sign(ServerLevel level) { return ((SignBlockEntity)level.getBlockEntity(TARGET)).getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString(); }
    private static void await(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,String message) {
        for(int i=0;i<200;i++) { if(world.getServer().computeOnServer(ignored -> condition.test(world.getConnection().getServerLevel())))return; context.waitTicks(1); } throw new AssertionError(message);
    }
    private static void require(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
    private static void write(Report report) {
        var path=Path.of(System.getProperty("create.test.modifierInputReport","build/reports/create-modifier-input/native-client-runtime.json")).toAbsolutePath();
        try { Files.createDirectories(path.getParent()); Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch(java.io.IOException failure) { throw new AssertionError("Cannot write focused modifier input receipt",failure); }
    }
}
