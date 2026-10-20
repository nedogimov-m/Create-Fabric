package com.simibubi.create.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.*;
import com.simibubi.create.content.equipment.clipboard.*;
import com.simibubi.create.content.schematics.*;
import com.simibubi.create.content.schematics.cannon.*;
import com.simibubi.create.content.schematics.client.*;
import com.simibubi.create.content.schematics.client.tools.*;
import com.simibubi.create.content.schematics.table.*;
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.*;
import org.joml.Vector3f;

/** Proposed native input-to-world scenario. Uses physical stock, real files, menus and engine ticks. UNRUN. */
final class SchematicsRenderingTest {
    private static final BlockPos SOURCE=new BlockPos(58010,5,4),TABLE=new BlockPos(58010,4,12),
        CANNON=new BlockPos(58014,4,12),TARGET=new BlockPos(58010,5,20);
    private static final Component FEE=Component.literal("Original native blank schematic"),
        FUEL=Component.literal("Original native schematic fuel"),STOCK=Component.literal("Original native schematic materials");
    private static ItemStack named(Item item,int count,Component name){var stack=new ItemStack(item,count);stack.set(DataComponents.CUSTOM_NAME,name);return stack;}
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        String name="CreateNative"+UUID.randomUUID().toString().replace("-","").substring(0,12)+".nbt";
        var local=SchematicFiles.localRoot().resolve(name);var uploaded=new AtomicReference<Path>();
        boolean hidden=context.computeOnClient(client->client.gui.hud.isHidden());
        try{
            if(Files.exists(local,LinkOption.NOFOLLOW_LINKS))throw new AssertionError("Unique native fixture file already exists");
            context.runOnClient(client->{if(client.gui.hud.isHidden())client.gui.hud.toggle();});
            context.runOnClient(client->{if(CreateClient.SCHEMATIC_HANDLER!=SchematicsClient.HANDLER||CreateClient.SCHEMATIC_SENDER!=SchematicsClient.LOADER||CreateClient.SCHEMATIC_AND_QUILL_HANDLER!=SchematicsClient.QUILL)throw new AssertionError("Source public client aliases must retain the same actual native owners");});
            context.runOnClient(client->{if(client.getResourceManager().getResource(com.simibubi.create.foundation.gui.AllGuiTextures.HUD_BACKGROUND.getLocation()).isEmpty())throw new AssertionError("Actual native schematic HUD background source texture is absent");});
            SchematicPublicToolProbe.begin();
            prepare(context,world);quill(context,world,name,local);upload(context,world,name,uploaded);
            deploy(context,world);creativePrint(context,world);print(context,world);clipboard(context,world);
            context.runOnClient(client->{for(var type:ToolType.values())SchematicPublicToolProbe.requireLifecycle(type);for(var type:List.of(ToolType.DEPLOY,ToolType.MOVE,ToolType.MOVE_Y,ToolType.ROTATE))SchematicPublicToolProbe.require(type,"handleMouseWheel");for(var type:List.of(ToolType.DEPLOY,ToolType.FLIP,ToolType.PRINT))SchematicPublicToolProbe.require(type,"handleRightClick");if(Boolean.getBoolean("create.test.nativeTrace"))System.out.println("CREATE_SCHEMATIC_ACTUAL_PUBLIC_TOOL_BOUNDARIES "+SchematicPublicToolProbe.snapshot());});
            world.getServer().runCommand("gamemode spectator @p");
        }catch(java.io.IOException failure){throw new AssertionError("Actual native schematic fixture file failed",failure);}
        finally{
            SchematicPublicToolProbe.clear();
            context.getInput().releaseKey(InputConstants.KEY_LALT);context.getInput().releaseKey(InputConstants.KEY_LCONTROL);
            context.getInput().releaseKey(InputConstants.KEY_LSHIFT);
            context.runOnClient(client->{if(client.gui.hud.isHidden()!=hidden)client.gui.hud.toggle();});
            try{Files.deleteIfExists(local);if(uploaded.get()!=null)Files.deleteIfExists(uploaded.get());}
            catch(java.io.IOException failure){throw new AssertionError("Unable to remove unique native fixture exports",failure);}
        }
    }
    private static void prepare(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 58011.5 6 9.5");
        context.waitFor(client->Math.abs(client.player.getX()-58011.5)<.1&&client.level.isLoaded(SOURCE)&&client.level.isLoaded(TARGET),400);
        NativeScreenshotReadiness.beforeFloor(context, world, new BlockPos(58004, 3, 0), new BlockPos(58020, 3, 28), SOURCE.below(), TABLE, CANNON, TARGET.below());
        server.runCommand("fill 58004 3 0 58020 3 28 minecraft:smooth_quartz");
        NativeScreenshotReadiness.afterFloor(context, world, new BlockPos(58004, 3, 0), new BlockPos(58020, 3, 28));
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();level.setBlockAndUpdate(SOURCE,Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(SOURCE.east(),AllBlocks.SHAFT.defaultBlockState().setValue(BlockStateProperties.AXIS,Direction.Axis.X));
            level.setBlockAndUpdate(SOURCE.east(2),Blocks.GLASS.defaultBlockState());level.setBlockAndUpdate(TABLE,AllBlocks.SCHEMATIC_TABLE.defaultBlockState());
            level.setBlockAndUpdate(CANNON,AllBlocks.SCHEMATICANNON.defaultBlockState());level.setBlockAndUpdate(CANNON.east(),Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(TARGET.below(),Blocks.SMOOTH_QUARTZ.defaultBlockState());level.setBlockAndUpdate(TARGET.east(7).below(),Blocks.SMOOTH_QUARTZ.defaultBlockState());
            var chest=(ChestBlockEntity)level.getBlockEntity(CANNON.east());chest.setItem(0,named(Items.STONE,41,STOCK));
            chest.setItem(1,named(AllBlocks.SHAFT.asItem(),41,STOCK));chest.setItem(2,named(Items.GLASS,41,STOCK));
        });
        server.runCommand("tp @p 58011.5 4 8.5");server.runCommand("gamemode survival @p");
        context.waitFor(client->!client.player.isSpectator()&&!client.player.isCreative(),200);
        await(context,world,level->level.getServer().getPlayerList().getPlayers().getFirst().gameMode.getGameModeForPlayer()==net.minecraft.world.level.GameType.SURVIVAL,200,"Actual server player did not enter survival before hotbar normalization");
        context.getInput().pressKey(InputConstants.KEY_1);context.waitFor(client->client.player.getInventory().getSelectedSlot()==0,200);
        await(context,world,level->level.getServer().getPlayerList().getPlayers().getFirst().getInventory().getSelectedSlot()==0,200,"Real native survival hotbar selection was not acknowledged before finite source stock");
        server.runOnServer(ignored->{
            var level=world.getConnection().getServerLevel();
            var player=level.getServer().getPlayerList().getPlayers().getFirst();player.getInventory().clearContent();
            player.getInventory().setItem(0,new ItemStack(AllItems.SCHEMATIC_AND_QUILL));player.getInventory().setItem(1,new ItemStack(AllBlocks.CLIPBOARD));
            player.getInventory().setItem(9,named(AllItems.EMPTY_SCHEMATIC,3,FEE));player.getInventory().setItem(10,named(Items.GUNPOWDER,2,FUEL));
            player.getInventory().setItem(11,new ItemStack(AllBlocks.CLIPBOARD));player.inventoryMenu.broadcastChanges();
        });
        context.waitFor(client->client.player.getMainHandItem().is(AllItems.SCHEMATIC_AND_QUILL)&&LoadedEntityAccess.isReady(client.level,SOURCE)&&client.level.getBlockState(SOURCE.east(2)).is(Blocks.GLASS),300);
        NativeContraptionFixtures.awaitCaptureReady(context,world,400,SOURCE,SOURCE.east(2));
    }
    private static void quill(ClientGameTestContext context,TestSingleplayerContext world,String name,Path local){
        use(context,SOURCE,Vec3.atCenterOf(SOURCE));context.waitFor(client->SOURCE.equals(SchematicsClient.QUILL.firstPos),200);
        use(context,SOURCE.east(2),Vec3.atCenterOf(SOURCE.east(2)));context.waitFor(client->SOURCE.east(2).equals(SchematicsClient.QUILL.secondPos),200);
        screenshot(context,"create-vulkan-schematics-real-quill-corners-native-checkerboard");
        context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof SchematicPromptScreen,200);
        context.getInput().typeChars(name.substring(0,name.length()-4));screenshot(context,"create-vulkan-schematics-native-filename-typing");
        clickWidget(context,Component.translatable("create.action.saveToFile"));
        context.waitFor(client->client.gui.screen()==null&&Files.isRegularFile(local,LinkOption.NOFOLLOW_LINKS)&&SchematicsClient.QUILL.firstPos==null,300);
        context.runOnClient(client->{try{
            var template=SchematicFiles.read(client.level,local);var snapshot=SchematicStructure.load(client.level,template,BlockPos.ZERO,new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),true);
            if(!template.getSize().equals(new Vec3i(3,1,1))||snapshot.positions().size()!=3||!snapshot.getBlockState(BlockPos.ZERO).is(Blocks.STONE)||!snapshot.getBlockState(new BlockPos(1,0,0)).is(AllBlocks.SHAFT)||!snapshot.getBlockState(new BlockPos(2,0,0)).is(Blocks.GLASS))throw new AssertionError("Real quill export differs from the physical source");
        }catch(java.io.IOException failure){throw new AssertionError(failure);}});
    }
    private static void upload(ClientGameTestContext context,TestSingleplayerContext world,String name,AtomicReference<Path> uploaded){
        context.getInput().pressKey(InputConstants.KEY_3);context.waitFor(client->client.player.getMainHandItem().isEmpty(),200);
        stand(context,world,TABLE.south(2));use(context,TABLE,Vec3.atCenterOf(TABLE));context.waitFor(client->client.gui.screen() instanceof SchematicTableScreen,250);
        shiftSlot(context,2,214,197);context.waitFor(client->client.player.containerMenu.getSlot(0).getItem().is(AllItems.EMPTY_SCHEMATIC)&&client.player.getInventory().getItem(9).getCount()==2,200);
        int index=context.computeOnClient(client->{var files=SchematicsClient.LOADER.getAvailableSchematics();for(int i=0;i<files.size();i++)if(files.get(i).getString().equals(name))return i;throw new AssertionError("Real native file selector omitted actual quill export");});
        moveWidget(context,context.computeOnClient(client->client.gui.screen().children().stream().filter(SelectionScrollInput.class::isInstance).map(SelectionScrollInput.class::cast).findFirst().orElseThrow()));
        for(int i=0;i<index;i++){context.getInput().scroll(-1);context.waitTicks(2);}
        context.runOnClient(client->{var selection=(SelectionScrollInput)client.gui.screen().children().stream().filter(SelectionScrollInput.class::isInstance).findFirst().orElseThrow();if(selection.getState()!=index)throw new AssertionError("Actual native wheel failed source file selection");});
        screenshot(context,"create-vulkan-schematics-native-file-browser-original-blank-fee");clickWidget(context,Component.translatable("create.action.confirm"));
        context.waitFor(client->client.player.containerMenu instanceof SchematicTableMenu menu&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().is(AllItems.SCHEMATIC)&&name.equals(menu.getSlot(1).getItem().get(AllDataComponents.SCHEMATIC_FILE))&&!SchematicsClient.LOADER.isUploading(),400);
        world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();var menu=(SchematicTableMenu)player.containerMenu;var blueprint=menu.getSlot(1).getItem();
            if(!ItemStack.matches(player.getInventory().getItem(9),named(AllItems.EMPTY_SCHEMATIC,2,FEE))||menu.owner.isUploading)throw new AssertionError("Actual native upload failed exact original fee tail or session completion");
            var path=SchematicFiles.uploadedRoot().resolve(SchematicFiles.ownerName(blueprint.get(AllDataComponents.SCHEMATIC_OWNER))).resolve(name);
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))throw new AssertionError("Successful native upload has no actual completed server file");uploaded.set(path);
        });
        screenshot(context,"create-vulkan-schematics-real-ack-upload-completed-blueprint");shiftSlot(context,1,214,197);close(context);
        int selected=context.computeOnClient(client->{for(int i=0;i<9;i++)if(client.player.getInventory().getItem(i).is(AllItems.SCHEMATIC))return i;throw new AssertionError("Real native output transfer did not return the original blueprint");});
        context.getInput().pressKey(InputConstants.KEY_1+selected);context.waitFor(client->SchematicsClient.HANDLER.isActive()&&client.player.getMainHandItem().is(AllItems.SCHEMATIC),200);
    }
    private static void deploy(ClientGameTestContext context,TestSingleplayerContext world){
        stand(context,world,TARGET.south(4).below());look(context,Vec3.atCenterOf(TARGET.below()));
        context.getInput().holdKey(InputConstants.KEY_LCONTROL);try{context.waitTicks(3);context.getInput().scroll(1);context.waitTicks(4);}finally{context.getInput().releaseKey(InputConstants.KEY_LCONTROL);}
        use(context,TARGET.below(),Vec3.atCenterOf(TARGET.below()));
        context.waitFor(client->SchematicsClient.HANDLER.isDeployed()&&SchematicsClient.HANDLER.currentToolType()==ToolType.MOVE,200);
        context.waitFor(client->client.gameRenderer.mainCamera().isInitialized()&&client.gameRenderer.mainCamera().position().distanceToSqr(client.player.getEyePosition())<1,200);
        context.runOnClient(client->{
            var frame=SchematicsClient.HANDLER.extract(client.gameRenderer.mainCamera().position(),1);
            var model=frame.model();
            if(model==null||model.blockCount()!=2||model.blocks().size()!=2
                ||model.blocks().stream().noneMatch(part->!part.translucent&&!part.isEmpty())
                ||model.blocks().stream().noneMatch(part->part.translucent&&!part.isEmpty())
                ||model.blockEntities().size()!=1
                ||!model.blockEntities().getFirst().pos().equals(new BlockPos(1,0,0))
                ||!(model.blockEntities().getFirst().state() instanceof com.simibubi.create.content.kinetics.base.KineticRenderState shaft)
                ||shaft.model.isEmpty())
                throw new AssertionError("Actual deployed native preview lost stone/glass layers or the separately rendered source shaft: "+model);
        });
        chooseTool(context,ToolType.ROTATE);context.getInput().holdKey(InputConstants.KEY_LCONTROL);
        try{context.getInput().scroll(1);context.waitTicks(4);}finally{context.getInput().releaseKey(InputConstants.KEY_LCONTROL);}
        context.waitFor(client->client.player.getMainHandItem().getOrDefault(AllDataComponents.SCHEMATIC_ROTATION,Rotation.NONE)!=Rotation.NONE,200);
        context.waitFor(client->SchematicsClient.HANDLER.extract(Vec3.ZERO,1).rotationAxis()!=null,200);
        screenshot(context,"create-vulkan-schematics-original-alt-overlay-native-rotation-axis");
        chooseTool(context,ToolType.FLIP);look(context,previewCenter(context).add(0,-.4,0));
        try{context.waitFor(client->{var frame=SchematicsClient.HANDLER.extract(Vec3.ZERO,1);return frame.face()!=null&&frame.face().getAxis().isHorizontal()&&frame.mirrorPlane()!=null;},200);}
        catch(AssertionError failure){
            screenshot(context,"create-vulkan-schematics-actual-flip-ray-failure");
            throw new AssertionError(context.computeOnClient(client->{var handler=SchematicsClient.HANDLER;var transform=handler.getTransformation();var eye=client.player.getEyePosition();var end=eye.add(client.player.getLookAngle().scale(70));var tool=(SchematicToolBase)handler.currentToolType().getTool();return "Actual native flip ray: eye="+eye+", look="+client.player.getLookAngle()+", localStart="+transform.toLocalSpace(eye)+", localEnd="+transform.toLocalSpace(end)+", bounds="+handler.getBounds()+", anchor="+transform.getAnchor()+", face="+tool.getSelectedFace()+", selected="+tool.isSchematicSelected()+", selectedPos="+tool.getSelectedPos()+", rotation="+transform.getCurrentRotation()+", scales="+transform.getScaleFB().getValue(1)+","+transform.getScaleLR().getValue(1)+", tool="+handler.currentToolType();}),failure);
        }
        screenshot(context,"create-vulkan-schematics-native-source-flip-checker-plane");context.getInput().pressKey(options->options.keyUse);
        context.waitFor(client->client.player.getMainHandItem().getOrDefault(AllDataComponents.SCHEMATIC_MIRROR,Mirror.NONE)!=Mirror.NONE,200);
        chooseTool(context,ToolType.MOVE_Y);var before=context.computeOnClient(client->SchematicsClient.HANDLER.getTransformation().getAnchor());
        context.getInput().holdKey(InputConstants.KEY_LCONTROL);try{context.getInput().scroll(1);context.waitTicks(4);}finally{context.getInput().releaseKey(InputConstants.KEY_LCONTROL);}
        context.waitFor(client->SchematicsClient.HANDLER.getTransformation().getAnchor().getY()==before.getY()+1,200);
        chooseTool(context,ToolType.MOVE);look(context,previewCenter(context));
        context.waitFor(client->{var frame=SchematicsClient.HANDLER.extract(Vec3.ZERO,1);return frame.face()!=null&&frame.face().getAxis().isHorizontal();},200);
        var horizontal=context.computeOnClient(client->SchematicsClient.HANDLER.getTransformation().getAnchor());
        context.getInput().holdKey(InputConstants.KEY_LCONTROL);try{context.getInput().scroll(1);context.waitTicks(4);}finally{context.getInput().releaseKey(InputConstants.KEY_LCONTROL);}
        context.waitFor(client->{var anchor=SchematicsClient.HANDLER.getTransformation().getAnchor();return anchor.getY()==horizontal.getY()&&Math.abs(anchor.getX()-horizontal.getX())+Math.abs(anchor.getZ()-horizontal.getZ())==1;},200);
        screenshot(context,"create-vulkan-schematics-real-public-move-tool-native-face-one-axis-translation");
        context.waitTicks(15);var expected=context.computeOnClient(client->client.player.getMainHandItem().copy());
        await(context,world,level->{var actual=level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem();return ItemStack.matches(actual,expected);},200,"Actual native transform synchronization failed exact held blueprint settings");
        screenshot(context,"create-vulkan-schematics-native-deployed-mirrored-rotated-paid-blueprint");
    }
    private static void creativePrint(ClientGameTestContext context,TestSingleplayerContext world)throws java.io.IOException{
        var paidAnchor=context.computeOnClient(client->SchematicsClient.HANDLER.getTransformation().getAnchor());
        int blueprintSlot=context.computeOnClient(client->client.player.getInventory().getSelectedSlot());
        int empty=context.computeOnClient(client->{for(int i=0;i<9;i++)if(client.player.getInventory().getItem(i).isEmpty())return i;throw new AssertionError("Creative tool check requires a genuinely empty hotbar slot");});
        world.getServer().runCommand("gamemode creative @p");context.waitFor(client->client.player.isCreative(),200);
        await(context,world,level->level.getServer().getPlayerList().getPlayers().getFirst().isCreative(),200,"Actual server never acknowledged creative tool permission");
        reselectBlueprint(context,empty,blueprintSlot);chooseTool(context,ToolType.DEPLOY);
        stand(context,world,TARGET.east(7).south(4).below());use(context,TARGET.east(7).below(),Vec3.atCenterOf(TARGET.east(7).below()));
        context.waitFor(client->SchematicsClient.HANDLER.isDeployed(),200);
        var groundAnchor=context.computeOnClient(client->SchematicsClient.HANDLER.getTransformation().getAnchor());
        chooseTool(context,ToolType.MOVE_Y);context.getInput().holdKey(InputConstants.KEY_LCONTROL);
        try{context.getInput().scroll(1);context.waitTicks(4);}finally{context.getInput().releaseKey(InputConstants.KEY_LCONTROL);}
        context.waitFor(client->SchematicsClient.HANDLER.getTransformation().getAnchor().equals(groundAnchor.above()),200);context.waitTicks(15);
        var blueprint=context.computeOnClient(client->client.player.getMainHandItem().copy());
        await(context,world,level->ItemStack.matches(level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem(),blueprint),200,"Creative native deployment did not synchronize the exact original held blueprint");
        var expected=new AtomicReference<Map<BlockPos,BlockState>>();
        world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var item=level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem();var snapshot=SchematicStructure.load(level,SchematicItem.loadSchematic(level,item),item.get(AllDataComponents.SCHEMATIC_ANCHOR),SchematicItem.getSettings(item),true);var map=new HashMap<BlockPos,BlockState>();for(var pos:snapshot.positions()){if(!level.getBlockState(pos).isAir())throw new AssertionError("Creative test must publish source blocks into real empty positions, without touching paid targets");map.put(pos,snapshot.getBlockState(pos));}expected.set(Map.copyOf(map));});
        chooseTool(context,ToolType.PRINT);look(context,previewCenter(context));context.waitTicks(3);context.getInput().pressKey(options->options.keyUse);
        await(context,world,level->expected.get().entrySet().stream().allMatch(entry->level.getBlockState(entry.getKey()).equals(entry.getValue())),300,"Real public PlaceTool input never published the uploaded source to the authoritative native world");
        world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();var chest=(ChestBlockEntity)level.getBlockEntity(CANNON.east());for(int i=0;i<3;i++){var item=i==0?Items.STONE:i==1?AllBlocks.SHAFT.asItem():Items.GLASS;if(!ItemStack.matches(chest.getItem(i),named(item,41,STOCK)))throw new AssertionError("Creative print spent actual finite cannon material");}if(!ItemStack.matches(player.getInventory().getItem(9),named(AllItems.EMPTY_SCHEMATIC,2,FEE))||!ItemStack.matches(player.getInventory().getItem(10),named(Items.GUNPOWDER,2,FUEL)))throw new AssertionError("Creative public tool altered original fee/fuel tails");});
        context.waitFor(client->SchematicsClient.HANDLER.isActive()&&!SchematicsClient.HANDLER.isDeployed()&&SchematicsClient.HANDLER.currentToolType()==ToolType.DEPLOY,200);
        screenshot(context,"create-vulkan-schematics-real-public-place-tool-native-creative-world-unchanged-finite-stock");
        editAnchor(context,paidAnchor);world.getServer().runCommand("gamemode survival @p");context.waitFor(client->!client.player.isCreative()&&!client.player.isSpectator(),200);
        await(context,world,level->level.getServer().getPlayerList().getPlayers().getFirst().gameMode.getGameModeForPlayer()==net.minecraft.world.level.GameType.SURVIVAL,200,"Actual server did not restore survival before finite paid cannon print");
        reselectBlueprint(context,empty,blueprintSlot);context.waitFor(client->SchematicsClient.HANDLER.isDeployed()&&SchematicsClient.HANDLER.getTransformation().getAnchor().equals(paidAnchor)&&!SchematicsClient.HANDLER.toolTypes().contains(ToolType.PRINT),200);
        var restored=context.computeOnClient(client->client.player.getMainHandItem().copy());await(context,world,level->ItemStack.matches(level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem(),restored),200,"Native edit screen failed to restore the actual original paid target");
    }
    private static void reselectBlueprint(ClientGameTestContext context,int empty,int blueprint){context.getInput().pressKey(InputConstants.KEY_1+empty);context.waitFor(client->!SchematicsClient.HANDLER.isActive()&&client.player.getMainHandItem().isEmpty(),200);context.getInput().pressKey(InputConstants.KEY_1+blueprint);context.waitFor(client->SchematicsClient.HANDLER.isActive()&&client.player.getMainHandItem().is(AllItems.SCHEMATIC),200);}
    private static Vec3 previewCenter(ClientGameTestContext context){return context.computeOnClient(client->{var handler=SchematicsClient.HANDLER;var pose=new PoseStack();handler.getTransformation().applyTransformations(pose,Vec3.ZERO,1);var center=handler.getBounds().getCenter();var point=pose.last().pose().transformPosition(new Vector3f((float)center.x,(float)center.y,(float)center.z));return new Vec3(point.x,point.y,point.z);});}
    private static void editAnchor(ClientGameTestContext context,BlockPos anchor){
        context.getInput().holdKey(InputConstants.KEY_LSHIFT);try{context.waitFor(client->client.player.isShiftKeyDown(),200);context.getInput().pressKey(options->options.keyUse);}finally{context.getInput().releaseKey(InputConstants.KEY_LSHIFT);}
        context.waitFor(client->client.gui.screen() instanceof SchematicEditScreen,200);
        int[] values={anchor.getX(),anchor.getY(),anchor.getZ()};
        for(int i=0;i<3;i++){int index=i;var widget=context.computeOnClient(client->client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).sorted(Comparator.comparingInt(EditBox::getX)).toList().get(index));moveWidget(context,widget);context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);context.runOnClient(client->client.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_A,'a',net.minecraft.client.input.InputQuirks.EDIT_SHORTCUT_KEY_MODIFIER)));context.getInput().typeChars(Integer.toString(values[i]));}
        screenshot(context,"create-vulkan-schematics-public-tool-original-paid-anchor-native-coordinate-editor");
        var entered=context.computeOnClient(client->client.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).sorted(Comparator.comparingInt(EditBox::getX)).map(EditBox::getValue).toList());
        if(!entered.equals(java.util.Arrays.stream(values).mapToObj(Integer::toString).toList()))throw new AssertionError("Native editor keyboard input changed actual coordinate values: "+entered);
        clickWidget(context,Component.translatable("gui.done"));
        try{context.waitFor(client->client.gui.screen()==null&&SchematicsClient.HANDLER.isDeployed()&&SchematicsClient.HANDLER.getTransformation().getAnchor().equals(anchor),200);}
        catch(AssertionError failure){throw new AssertionError(context.computeOnClient(client->{var handler=SchematicsClient.HANDLER;var transform=handler.getTransformation();return "Actual native coordinate editor: expected="+anchor+", typed="+entered+", actual="+transform.getAnchor()+", screen="+client.gui.screen()+", deployed="+handler.isDeployed()+", settings="+transform.toSettings()+", heldAnchor="+client.player.getMainHandItem().get(AllDataComponents.SCHEMATIC_ANCHOR)+", sourceTarget="+transform.fromAnchor(anchor);}),failure);}
    }
    private static void print(ClientGameTestContext context,TestSingleplayerContext world)throws java.io.IOException{
        var expected=new AtomicReference<Map<BlockPos,BlockState>>();
        world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();var item=player.getMainHandItem();var snapshot=SchematicStructure.load(level,SchematicItem.loadSchematic(level,item),item.get(AllDataComponents.SCHEMATIC_ANCHOR),SchematicItem.getSettings(item),true);var map=new HashMap<BlockPos,BlockState>();for(var pos:snapshot.positions())map.put(pos,snapshot.getBlockState(pos));expected.set(Map.copyOf(map));});
        stand(context,world,CANNON.south(2));use(context,CANNON,Vec3.atCenterOf(CANNON));context.waitFor(client->client.gui.screen() instanceof SchematicannonScreen,250);
        int blueprint=context.computeOnClient(client->{var menu=client.player.containerMenu;for(int i=5;i<menu.slots.size();i++)if(menu.getSlot(i).getItem().is(AllItems.SCHEMATIC))return i;throw new AssertionError("Real player blueprint absent from native cannon menu");});
        shiftSlot(context,blueprint,213,251);context.waitFor(client->client.player.containerMenu.getSlot(0).getItem().is(AllItems.SCHEMATIC),200);
        shiftSlot(context,6,213,251);shiftSlot(context,7,213,251);
        context.waitFor(client->client.player.containerMenu.getSlot(4).getItem().is(Items.GUNPOWDER)&&client.player.containerMenu.getSlot(3).getItem().getOrDefault(AllDataComponents.CLIPBOARD_CONTENT,ClipboardContent.EMPTY).readOnly(),300);
        screenshot(context,"create-vulkan-schematics-native-cannon-fuel-original-stock-real-material-checklist");
        clickWidget(context,Component.translatable("create.gui.schematicannon.play"));
        await(context,world,level->!((SchematicannonBlockEntity)level.getBlockEntity(CANNON)).flyingBlocks.isEmpty(),250,"Actual play button did not reserve and launch real material");
        close(context);look(context,Vec3.atCenterOf(CANNON).add(0,1,-2));screenshot(context,"create-vulkan-schematics-native-connector-pipe-recoil-real-flying-blocks");
        await(context,world,level->{var cannon=(SchematicannonBlockEntity)level.getBlockEntity(CANNON);return cannon.statusMsg.equals("finished")&&cannon.flyingBlocks.isEmpty()&&expected.get().entrySet().stream().allMatch(entry->level.getBlockState(entry.getKey()).equals(entry.getValue()));},600,"Actual engine cannon failed to publish transformed source blocks");
        world.getServer().runOnServer(ignored->{var level=world.getConnection().getServerLevel();var cannon=(SchematicannonBlockEntity)level.getBlockEntity(CANNON);var chest=(ChestBlockEntity)level.getBlockEntity(CANNON.east());
            for(int i=0;i<3;i++){Item item=i==0?Items.STONE:i==1?AllBlocks.SHAFT.asItem():Items.GLASS;if(!ItemStack.matches(chest.getItem(i),named(item,40,STOCK)))throw new AssertionError("Real source material tail changed count/components or was spent twice");}
            if(cannon.remainingFuel!=cannon.getShotsPerGunpowder()-3||!ItemStack.matches(cannon.inventory.getItem(4),named(Items.GUNPOWDER,1,FUEL))||!cannon.inventory.getItem(0).isEmpty()||!cannon.inventory.getItem(1).is(AllItems.EMPTY_SCHEMATIC))throw new AssertionError("Actual cannon did not spend three shots, one powder and exactly one blueprint");
        });
        look(context,Vec3.atCenterOf(TARGET).add(0,1,0));screenshot(context,"create-vulkan-schematics-original-stock-exact-tails-complete-transformed-world");
    }
    private static void clipboard(ClientGameTestContext context,TestSingleplayerContext world){
        context.getInput().pressKey(InputConstants.KEY_2);context.waitFor(client->client.player.getMainHandItem().is(AllBlocks.CLIPBOARD.asItem()),200);
        context.getInput().lookAt(new BlockPos(58011,20,12));context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof ClipboardScreen,200);
        context.getInput().typeChars("Native Vulkan Notes");context.getInput().pressKey(InputConstants.KEY_RETURN);context.getInput().typeChars("Original material custody");
        screenshot(context,"create-vulkan-clipboard-real-native-text-cursor-two-original-entries");
        context.getInput().pressKey(InputConstants.KEY_PAGEDOWN);context.getInput().typeChars("Second native page");screenshot(context,"create-vulkan-clipboard-native-page-keyboard-editing");close(context);
        await(context,world,level->{var item=level.getServer().getPlayerList().getPlayers().getFirst().getMainHandItem();var content=item.get(AllDataComponents.CLIPBOARD_CONTENT);return content!=null&&!content.readOnly()&&content.pages().size()==2&&content.pages().getFirst().size()==2&&content.pages().getFirst().getFirst().text.getString().equals("Native Vulkan Notes")&&content.pages().getFirst().get(1).text.getString().equals("Original material custody")&&content.pages().get(1).getFirst().text.getString().equals("Second native page");},250,"Native Clipboard edits did not reach the actual selected source component");
        context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.gui.screen() instanceof ClipboardScreen,200);screenshot(context,"create-vulkan-clipboard-native-editor-reopened-durable-second-page");close(context);
    }
    private static void chooseTool(ClientGameTestContext context,ToolType wanted){
        int steps=context.computeOnClient(client->{var handler=SchematicsClient.HANDLER;var tools=handler.toolTypes();return Math.floorMod(tools.indexOf(wanted)-tools.indexOf(handler.currentToolType()),tools.size());});
        context.getInput().holdKey(InputConstants.KEY_LALT);try{context.waitTicks(3);for(int i=0;i<steps;i++){context.getInput().scroll(-1);context.waitTicks(2);}screenshot(context,"create-vulkan-schematics-native-alt-tool-selection-"+wanted.name().toLowerCase(Locale.ROOT));}finally{context.getInput().releaseKey(InputConstants.KEY_LALT);}
        context.waitFor(client->SchematicsClient.HANDLER.currentToolType()==wanted,200);
    }
    private static void stand(ClientGameTestContext context,TestSingleplayerContext world,BlockPos pos){world.getServer().runCommand("tp @p "+(pos.getX()+.5)+" "+pos.getY()+" "+(pos.getZ()+.5));context.waitFor(client->client.player.position().distanceToSqr(new Vec3(pos.getX()+.5,pos.getY(),pos.getZ()+.5))<.02,250);}
    private static void look(ClientGameTestContext context,Vec3 point){var angle=context.computeOnClient(client->{var d=point.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));});context.getInput().lookAt(angle.x,angle.y);}
    private static void use(ClientGameTestContext context,BlockPos target,Vec3 point){look(context,point);context.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(target),200);context.waitTicks(2);context.getInput().pressKey(options->options.keyUse);}
    private static void close(ClientGameTestContext context){context.getInput().pressKey(InputConstants.KEY_ESCAPE);context.waitFor(client->client.gui.screen()==null,200);}
    private static Vec2 nativePoint(Minecraft client,float x,float y){var window=client.getWindow();return new Vec2(x*window.getScreenWidth()/window.getGuiScaledWidth(),y*window.getScreenHeight()/window.getGuiScaledHeight());}
    private static void moveWidget(ClientGameTestContext context,AbstractWidget widget){var p=context.computeOnClient(client->nativePoint(client,widget.getX()+widget.getWidth()/2f,widget.getY()+widget.getHeight()/2f));context.getInput().setCursorPos(p.x,p.y);context.waitTicks(2);}
    private static void clickWidget(ClientGameTestContext context,Component message){var point=context.computeOnClient(client->{var widget=client.gui.screen().children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).filter(w->w.getMessage().equals(message)&&w.visible&&w.active).findFirst().orElseThrow();return nativePoint(client,widget.getX()+widget.getWidth()/2f,widget.getY()+widget.getHeight()/2f);});context.getInput().setCursorPos(point.x,point.y);context.waitTicks(2);context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);}
    // Fabric's TestInput mouse events omit Shift modifiers; dispatch the real native event, as other source menu scenes do.
    private static void shiftSlot(ClientGameTestContext context,int index,int width,int height){context.runOnClient(client->{var screen=client.gui.screen();var slot=client.player.containerMenu.getSlot(index);var event=new net.minecraft.client.input.MouseButtonEvent((screen.width-width)/2+slot.x+8,(screen.height-height)/2+slot.y+8,new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT,InputConstants.MOD_SHIFT));screen.mouseClicked(event,false);screen.mouseReleased(event);});context.waitTicks(2);}
    private static void await(ClientGameTestContext context,TestSingleplayerContext world,Predicate<ServerLevel> condition,int timeout,String message){for(int tick=0;tick<timeout;tick++){if(world.getServer().computeOnServer(ignored->condition.test(world.getConnection().getServerLevel())))return;context.waitTicks(1);}throw new AssertionError(message);}
    private static void screenshot(ClientGameTestContext context,String name){if(Boolean.getBoolean("create.test.nativeTrace"))System.out.println("CREATE_SCHEMATICS_NATIVE_STAGE "+name);context.waitTicks(3);context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.takeScreenshot(name);}
    private SchematicsRenderingTest(){}
}
