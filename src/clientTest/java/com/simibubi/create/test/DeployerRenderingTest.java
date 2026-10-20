package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.*;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;

/** Exercises native meshes, real manufacturing and component/recipe synchronization on Vulkan. */
final class DeployerRenderingTest {
    private static final BlockPos FIRST=new BlockPos(800,6,5),DISPLAY=new BlockPos(803,6,10);
    private static final BlockPos BELT=new BlockPos(825,4,5),OUTPUT=BELT.east(7);
    static void run(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("gamemode spectator @p");server.runCommand("tp @p 814 11 20");
        context.waitFor(client->Math.abs(client.player.getX()-814.5)<0.1&&client.level.isLoaded(OUTPUT),500);
        server.runCommand("fill 796 3 0 836 3 23 minecraft:smooth_quartz");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();int n=0;
            for(boolean axis:List.of(false,true))for(var face:Direction.values()){
                var pos=FIRST.east((n%6)*3).south(n/6*5);n++;
                var state=AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,face).setValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE,axis);level.setBlockAndUpdate(pos,state);
                var hand=(DeployerBlockEntity)level.getBlockEntity(pos);if(n%3==0){hand.setHeldItem(new ItemStack(Items.IRON_SWORD));hand.changeMode();}else if(n%3==2)hand.setHeldItem(new ItemStack(AllItems.BRASS_HAND));
                if(pos.equals(DISPLAY)){hand.setHeldItem(new ItemStack(Items.DIAMOND));continue;}
                var drive=Direction.get(Direction.AxisDirection.POSITIVE,AllBlocks.DEPLOYER.getRotationAxis(state));var motor=pos.relative(drive);
                level.setBlockAndUpdate(motor,AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,drive.getOpposite()));((KineticBlockEntity)level.getBlockEntity(motor)).setGeneratedSpeed(16);
            }
        });
        context.waitFor(client->client.level.getBlockEntity(FIRST) instanceof DeployerBlockEntity hand&&hand.getSpeed()!=0&&client.level.getBlockEntity(DISPLAY) instanceof DeployerBlockEntity,200);
        context.runOnClient(client->{var resolver=new BlockModelResolver(client.getModelManager());int n=0;
            for(var block:AllBlocks.DEPLOYER.getStateDefinition().getPossibleStates()){
                var baked=new BlockModelRenderState();resolver.update(baked,block,BlockDisplayContext.create());if(baked.isEmpty()||client.getModelManager().getBlockStateModelSet().get(block)==client.getModelManager().getBlockStateModelSet().missingModel())throw new AssertionError("Missing deployer casing: "+block);
            }
            for(boolean axis:List.of(false,true))for(var face:Direction.values()){
                var pos=FIRST.east(n%6*3).south(n/6*5);n++;var hand=(DeployerBlockEntity)client.level.getBlockEntity(pos);var state=extract(client,pos);
                if(state.facing!=face||state.axis!=AllBlocks.DEPLOYER.getRotationAxis(hand.getBlockState())||state.shaft.isEmpty()||state.pole.isEmpty()||state.hand.isEmpty()||state.pole.mesh==null||state.hand.mesh.size()==0||state.held.isEmpty()!=hand.getHeldItem().isEmpty())throw new AssertionError("Deployer orientation/held item extraction failed at "+pos);
                var expected=new PartialModelRenderState();AllPartialModels.extract(hand.getMode()==DeployerBlockEntity.Mode.PUNCH?AllPartialModels.DEPLOYER_HAND_PUNCHING:hand.getHeldItem().isEmpty()?AllPartialModels.DEPLOYER_HAND_POINTING:AllPartialModels.DEPLOYER_HAND_HOLDING,expected);if(state.hand.mesh!=expected.mesh)throw new AssertionError("Incorrect deployer hand pose");
            }
            if(!extract(client,DISPLAY).display)throw new AssertionError("Upward stopped hand does not display held item");
        });
        context.waitFor(client->{var hand=(DeployerBlockEntity)client.level.getBlockEntity(FIRST);return hand.getState()==DeployerBlockEntity.State.EXPANDING&&hand.getHandOffset(0.5f)>0.25f;},200);
        context.runOnClient(client->{var state=extract(client,FIRST);if(state.offset<=0.25f||!Float.isFinite(state.angle))throw new AssertionError("Missing animated hand extension");});
        context.getInput().lookAt(new BlockPos(808,6,7));world.getConnection().waitForChunksRender();screenshot(context,"create-vulkan-deployer-twelve-orientations");
        var oldMesh=new AtomicReference<Object>();context.runOnClient(client->oldMesh.set(extract(client,FIRST).pole.mesh));var reload=context.computeOnClient(client->client.reloadResourcePacks());context.waitFor(client->reload.isDone()&&client.gui.overlay()==null,600);context.waitTicks(5);
        context.runOnClient(client->{if(extract(client,FIRST).pole.mesh==oldMesh.get())throw new AssertionError("Deployer retained stale partial after resource reload");});
        manufacture(context,world);
        sturdySheet(context,world);
        manualApplication(context,world);
        chunkUnload(context,world);
    }
    private static void manualApplication(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var pos=new BlockPos(852,4,5);server.runCommand("tp @p 852 4 9");context.waitFor(client->Math.abs(client.player.getX()-852.5)<0.1&&client.level.isLoaded(pos),200);server.runCommand("fill 848 3 1 857 3 14 minecraft:smooth_quartz");server.runCommand("setblock 852 4 5 minecraft:oak_log[axis=x]");server.runCommand("gamemode survival @p");server.runCommand("item replace entity @p weapon.mainhand with minecraft:gold_nugget 2");context.waitFor(client->!client.player.isSpectator()&&client.player.getMainHandItem().is(Items.GOLD_NUGGET),200);
        context.getInput().lookAt(pos);context.waitFor(client->client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().equals(pos),200);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.MANGROVE_LOG),200);
        context.runOnClient(client->{if(client.level.getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)!=Direction.Axis.X||client.player.getMainHandItem().getCount()!=2)throw new AssertionError("Native manual application lost axis or consumed reusable material");});screenshot(context,"create-vulkan-manual-application-datapack");
        server.runCommand("setblock 852 4 5 minecraft:stripped_cherry_log");server.runCommand("item replace entity @p weapon.mainhand with create:andesite_alloy 2");context.waitFor(client->client.level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.STRIPPED_CHERRY_LOG)&&client.player.getMainHandItem().is(AllItems.ANDESITE_ALLOY),200);context.getInput().lookAt(pos);context.getInput().pressKey(options->options.keyUse);context.waitFor(client->client.level.getBlockState(pos).is(AllBlocks.ANDESITE_CASING)&&client.player.getMainHandItem().getCount()==1,200);screenshot(context,"create-vulkan-manual-application-casing");server.runCommand("gamemode spectator @p");
    }
    private static void sturdySheet(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();var depotPos=new BlockPos(842,4,5);var machine=depotPos.above(2);server.runCommand("tp @p 841 7 11");context.waitFor(client->Math.abs(client.player.getX()-841.5)<0.1&&client.level.isLoaded(machine),200);server.runCommand("fill 838 3 1 847 3 14 minecraft:smooth_quartz");server.runCommand("setblock 842 4 5 create:depot");server.runCommand("setblock 842 6 5 create:spout");
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();try(var tx=Transaction.openOuter()){((DepotBlockEntity)level.getBlockEntity(depotPos)).inventory.automation.insert(ItemVariant.of(AllItems.POWDERED_OBSIDIAN),1,tx);((com.simibubi.create.content.fluids.spout.SpoutBlockEntity)level.getBlockEntity(machine)).tank.insert(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(net.minecraft.world.level.material.Fluids.LAVA),40500,tx);tx.commit();}});
        context.getInput().lookAt(machine.below());world.getConnection().waitForChunksRender();context.waitFor(client->client.level.getBlockEntity(depotPos) instanceof DepotBlockEntity depot&&depot.getHeldItem().is(AllItems.UNPROCESSED_OBSIDIAN_SHEET),200);
        context.runOnClient(client->{var item=((DepotBlockEntity)client.level.getBlockEntity(depotPos)).getHeldItem();var lines=item.getTooltipLines(Item.TooltipContext.of(client.level),client.player,TooltipFlag.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList();if(lines.stream().noneMatch(line->line.contains("1/3"))||lines.stream().noneMatch(line->line.contains("Press")))throw new AssertionError("Filled assembly tooltip is missing next pressing steps: "+lines);});screenshot(context,"create-vulkan-sturdy-sheet-lava-filled");
        server.runCommand("setblock 842 6 5 create:mechanical_press[facing=east]");server.runCommand("setblock 841 6 5 create:creative_motor[facing=east]");server.runOnServer(ignored->((KineticBlockEntity)world.getConnection().getServerLevel().getBlockEntity(machine.west())).setGeneratedSpeed(128));context.waitFor(client->client.level.getBlockEntity(depotPos) instanceof DepotBlockEntity depot&&depot.getHeldItem().is(AllItems.STURDY_SHEET),300);screenshot(context,"create-vulkan-sturdy-sheet-pressed-twice");
    }
    private static DeployerRenderer.State extract(net.minecraft.client.Minecraft client,BlockPos pos){var state=client.getBlockEntityRenderDispatcher().tryExtractRenderState(client.level.getBlockEntity(pos),0.5f,null,true);if(!(state instanceof DeployerRenderer.State hand))throw new AssertionError("Missing native deployer render state");return hand;}
    private static void manufacture(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("tp @p 828 8 12");context.waitFor(client->Math.abs(client.player.getX()-828.5)<0.1,200);
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var end=BELT.east(6);
            for(var pos:List.of(BELT,end))level.setBlockAndUpdate(pos,AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,Direction.Axis.Z));
            if(!BeltConnectorItem.createBelts(level,BELT,end))throw new AssertionError("Assembly belt did not form");level.setBlockAndUpdate(BELT.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));((KineticBlockEntity)level.getBlockEntity(BELT.north())).setGeneratedSpeed(-128);
            for(int i=0;i<3;i++){var pos=BELT.east(1+i*2).above(2);level.setBlockAndUpdate(pos,AllBlocks.DEPLOYER.defaultBlockState().setValue(DeployerBlock.FACING,Direction.DOWN).setValue(DeployerBlock.AXIS_ALONG_FIRST_COORDINATE,false));level.setBlockAndUpdate(pos.north(),AllBlocks.CREATIVE_MOTOR.defaultBlockState().setValue(CreativeMotorBlock.FACING,Direction.SOUTH));((KineticBlockEntity)level.getBlockEntity(pos.north())).setGeneratedSpeed(256);((DeployerBlockEntity)level.getBlockEntity(pos)).setHeldItem(i==0?new ItemStack(AllBlocks.COGWHEEL,5):i==1?new ItemStack(AllBlocks.LARGE_COGWHEEL,5):new ItemStack(Items.IRON_NUGGET,5));}
            level.setBlockAndUpdate(OUTPUT,AllBlocks.DEPOT.defaultBlockState());try(var tx=Transaction.openOuter()){((BeltBlockEntity)level.getBlockEntity(BELT)).storage(Direction.UP).insert(ItemVariant.of(AllItems.GOLDEN_SHEET),1,tx);tx.commit();}
        });
        context.getInput().lookAt(BELT.east(3).above());world.getConnection().waitForChunksRender();
        for(int pass=1;pass<=5;pass++){
            int step=pass*3;
            context.waitFor(client->{if(!(client.level.getBlockEntity(OUTPUT) instanceof DepotBlockEntity depot)||depot.getHeldItem().isEmpty())return false;var progress=depot.getHeldItem().get(AllDataComponents.SEQUENCED_ASSEMBLY);return step==15?progress==null:progress!=null&&progress.step()==step;},500);
            if(pass==1){
                server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();player.setGameMode(GameType.CREATIVE);player.setItemInHand(InteractionHand.MAIN_HAND,((DepotBlockEntity)level.getBlockEntity(OUTPUT)).getHeldItem().copy());});
                context.waitFor(client->client.player.getMainHandItem().has(AllDataComponents.SEQUENCED_ASSEMBLY),200);
                context.runOnClient(client->{var item=client.player.getMainHandItem();if(!item.isBarVisible()||item.getBarWidth()!=3)throw new AssertionError("Assembly item progress bar not synchronized");var lines=item.getTooltipLines(Item.TooltipContext.of(client.level),client.player,TooltipFlag.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList();if(lines.stream().noneMatch(line->line.contains("3/15"))||lines.stream().noneMatch(line->line.contains("Cogwheel")))throw new AssertionError("Assembly recipe tooltip not synchronized: "+lines);});
                server.runCommand("gamemode spectator @p");screenshot(context,"create-vulkan-deployer-precision-assembly");
            }
            if(pass<5)server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();var depot=(DepotBlockEntity)level.getBlockEntity(OUTPUT);var item=depot.getHeldItem().copy();try(var tx=Transaction.openOuter()){if(((BeltBlockEntity)level.getBlockEntity(BELT)).storage(Direction.UP).insert(ItemVariant.of(item),1,tx)!=1||depot.inventory.automation.extract(ItemVariant.of(item),1,tx)!=1)throw new AssertionError("Could not return intermediate item");tx.commit();}});
        }
        server.runOnServer(ignored->{var level=world.getConnection().getServerLevel();for(int i=0;i<3;i++)if(!((DeployerBlockEntity)level.getBlockEntity(BELT.east(1+i*2).above(2))).getHeldItem().isEmpty())throw new AssertionError("Assembly did not consume exactly five of each tool");});
        context.getInput().lookAt(OUTPUT);screenshot(context,"create-vulkan-deployer-precision-finished");
    }
    private static void chunkUnload(ClientGameTestContext context,TestSingleplayerContext world){
        var server=world.getServer();server.runCommand("tp @p 5000 10 5000");var unloaded=new AtomicBoolean();for(int i=0;i<120&&!unloaded.get();i++){context.waitTicks(5);server.runOnServer(ignored->unloaded.set(!world.getConnection().getServerLevel().isLoaded(DISPLAY)));}if(!unloaded.get())throw new AssertionError("Deployer kept its chunk loaded");server.runCommand("tp @p 802 8 15");context.waitFor(client->Math.abs(client.player.getX()-802.5)<0.1&&client.level.getBlockEntity(DISPLAY) instanceof DeployerBlockEntity hand&&hand.getHeldItem().is(Items.DIAMOND),500);context.getInput().lookAt(DISPLAY);world.getConnection().waitForChunksRender();context.waitTicks(3);context.runOnClient(client->{if(extract(client,DISPLAY).held.isEmpty())throw new AssertionError("Held item did not recover after chunk unload");});screenshot(context,"create-vulkan-deployer-display-after-chunk-return");
    }
    private static void screenshot(ClientGameTestContext context,String name){context.runOnClient(client->client.gui.hud.getChat().clearMessages(false));context.waitTicks(2);context.takeScreenshot(name);}
}
