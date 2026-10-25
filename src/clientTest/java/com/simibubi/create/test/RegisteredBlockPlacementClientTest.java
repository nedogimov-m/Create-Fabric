package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Every ordinary Create BlockItem is paid for and placed through actual native client input. */
public final class RegisteredBlockPlacementClientTest implements FabricClientGameTest {
    private record Result(String item,boolean placed,String state,int remaining,boolean missingModel){}

    @Override public void runTest(ClientGameTestContext c) {
        var results=new ArrayList<Result>();
        Set<String> selected=Arrays.stream(System.getProperty("create.test.blockUseIds","").split(",")).filter(s->!s.isBlank()).collect(Collectors.toSet());
        try(var w=c.worldBuilder().create()){
            var server=w.getServer();
            var items=c.computeOnClient(client->BuiltInRegistries.ITEM.stream()
                .filter(item->item.getClass()==BlockItem.class&&BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("create"))
                .filter(item->selected.isEmpty()||selected.contains(BuiltInRegistries.ITEM.getKey(item).toString()))
                .map(BlockItem.class::cast).sorted(Comparator.comparing(item->BuiltInRegistries.ITEM.getKey(item).toString())).toList());
            if(items.isEmpty())throw new AssertionError("No actual registered block items selected");
            server.runCommand("gamemode spectator @p");server.runCommand("tp @p 74.5 12 78.5");
            var min=new BlockPos(60,4,60);var max=new BlockPos(92,4,92);
            c.waitFor(client->client.level.isLoaded(min)&&client.level.isLoaded(max),400);
            NativeScreenshotReadiness.beforeFloor(c,w,min,max,new BlockPos(64,5,64),new BlockPos(80,5,80));
            server.runCommand("fill 60 4 60 92 4 92 minecraft:smooth_quartz");
            NativeScreenshotReadiness.afterFloor(c,w,min,max);server.runCommand("clear @p");
            c.getInput().pressKey(InputConstants.KEY_2);c.getInput().pressKey(InputConstants.KEY_1);
            for(int index=0;index<items.size();index++){
                if(index%25==0){server.runCommand("fill 60 5 60 92 29 92 minecraft:air");server.runCommand("fill 60 4 60 92 4 92 minecraft:smooth_quartz");}
                var item=items.get(index);var id=BuiltInRegistries.ITEM.getKey(item).toString();
                var pos=new BlockPos(64+(index%5)*4,5,64+((index%25)/5)*4);
                boolean wall=item.getBlock() instanceof LadderBlock
                    ||item.getBlock() instanceof com.simibubi.create.content.contraptions.actors.AttachedActorBlock
                    ||item.getBlock() instanceof com.simibubi.create.content.logistics.itemHatch.ItemHatchBlock;
                boolean carriage=item.getBlock() instanceof com.simibubi.create.content.contraptions.gantry.GantryCarriageBlock;
                boolean nozzle=item.getBlock() instanceof com.simibubi.create.content.kinetics.fan.NozzleBlock;
                boolean steam=item.getBlock() instanceof com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock
                    ||item.getBlock() instanceof com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
                if(wall)server.runOnServer(ignored->w.getConnection().getServerLevel().setBlockAndUpdate(pos.north(),Blocks.STONE.defaultBlockState()));
                if(carriage)server.runOnServer(ignored->w.getConnection().getServerLevel().setBlockAndUpdate(pos.below(),com.simibubi.create.AllBlocks.GANTRY_SHAFT.defaultBlockState().setValue(com.simibubi.create.content.kinetics.gantry.GantryShaftBlock.FACING,Direction.EAST)));
                if(nozzle)server.runOnServer(ignored->w.getConnection().getServerLevel().setBlockAndUpdate(pos.below(),com.simibubi.create.AllBlocks.ENCASED_FAN.defaultBlockState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING,Direction.UP)));
                if(steam)server.runOnServer(ignored->w.getConnection().getServerLevel().setBlockAndUpdate(pos.below(),com.simibubi.create.AllBlocks.FLUID_TANK.defaultBlockState()));
                server.runCommand("gamemode survival @p");
                server.runCommand("tp @p "+(pos.getX()+.5)+" 5 "+(pos.getZ()+3.5));
                c.waitFor(client->!client.player.isSpectator()&&client.player.onGround()&&client.player.distanceToSqr(pos.getX()+.5,5,pos.getZ()+3.5)<.01,250);
                server.runOnServer(ignored->{var player=w.getConnection().getServerLevel().getServer().getPlayerList().getPlayers().getFirst();
                    if(player.getInventory().getSelectedSlot()!=0)throw new AssertionError("Actual native selected slot changed during block placement");
                    player.getInventory().setItem(0,new ItemStack(item,2));player.containerMenu.broadcastChanges();});
                c.waitFor(client->client.player.getMainHandItem().is(item)&&client.player.getMainHandItem().getCount()==2,200);
                var support=wall?pos.north():pos.below();var side=wall?Direction.SOUTH:Direction.UP;
                c.waitFor(client->!client.level.getBlockState(support).isAir(),200);
                var target=c.computeOnClient(client->{var shape=client.level.getBlockState(support).getShape(client.level,support).bounds();return wall?Vec3.atCenterOf(support).add(0,0,.499):new Vec3(support.getX()+.5,support.getY()+shape.maxY-.001,support.getZ()+.5);});
                look(c,target);
                c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(support)&&hit.getDirection()==side,200);
                c.waitTicks(5);c.getInput().pressKey(options->options.keyUse);
                var placed=new AtomicBoolean();var result=new AtomicReference<Result>();
                for(int tick=0;tick<60&&!placed.get();tick++){
                    c.waitTicks(1);server.runOnServer(ignored->{var level=w.getConnection().getServerLevel();var player=level.getServer().getPlayerList().getPlayers().getFirst();
                        placed.set(level.getBlockState(pos).is(item.getBlock())&&player.getMainHandItem().is(item)&&player.getMainHandItem().getCount()==1);
                        result.set(new Result(id,placed.get(),level.getBlockState(pos).toString(),player.getMainHandItem().getCount(),false));});
                }
                boolean missing=false;
                if(placed.get()){
                    c.waitFor(client->client.level.getBlockState(pos).is(item.getBlock()),200);
                    missing=c.computeOnClient(client->{var models=client.getModelManager().getBlockStateModelSet();return models.get(client.level.getBlockState(pos))==models.missingModel();});
                }else c.takeScreenshot("create-native-block-placement-failed-"+id.substring(7));
                var row=result.get();results.add(new Result(row.item,row.placed,row.state,row.remaining,missing));
                if(index%25==24||index==items.size()-1){
                    server.runCommand("gamemode spectator @p");server.runCommand("tp @p 73.5 18 93.5");
                    c.waitFor(client->client.player.isSpectator()&&Math.abs(client.player.getY()-18)<.1,200);look(c,new Vec3(73.5,5,73.5));
                    c.runOnClient(client->{client.gui.hud.getChat().clearMessages(false);client.gui.toastManager().clear();});
                    c.takeScreenshot("create-native-block-placement-page-"+(index/25));
                    System.out.println("CREATE_NATIVE_BLOCK_PLACEMENT progress="+(index+1)+"/"+items.size());
                }
            }
        }finally{
            try{var file=Path.of("build/reports/registered-block-placement.json");Files.createDirectories(file.toAbsolutePath().getParent());Files.writeString(file,new GsonBuilder().setPrettyPrinting().create().toJson(results)+"\n");}
            catch(Exception failure){throw new AssertionError("Cannot save actual block placement results",failure);}
        }
        var failed=results.stream().filter(result->!result.placed||result.missingModel).toList();
        if(!failed.isEmpty())throw new AssertionError("Native registered block placement failed: "+failed);
    }
    private static void look(ClientGameTestContext c,Vec3 target){var angles=c.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});c.getInput().lookAt(angles.x,angles.y);}
}
