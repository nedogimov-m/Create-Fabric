package com.simibubi.create.test;

import com.google.gson.GsonBuilder;
import com.mojang.blaze3d.platform.InputConstants;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Native held-use, release, sneak-use and scraping with actual server-acknowledged finite stock. */
public final class ConsumableAndHandToolsUseClientTest implements FabricClientGameTest {
    private final List<String> passed=new ArrayList<>();
    private static final BlockPos P=new BlockPos(48,5,54),COPPER=new BlockPos(48,5,51);

    @Override public void runTest(ClientGameTestContext c) {
        try(var w=c.worldBuilder().create()) {
            var server=w.getServer();
            server.runCommand("gamemode spectator @p");server.runCommand("tp @p 48.5 10 54.5");
            var min=new BlockPos(32,4,32);var max=new BlockPos(64,4,64);
            c.waitFor(client->client.level.isLoaded(min)&&client.level.isLoaded(max),400);
            NativeScreenshotReadiness.beforeFloor(c,w,min,max,P,COPPER);
            server.runCommand("fill 32 5 32 64 30 64 minecraft:air");
            server.runCommand("fill 32 4 32 64 4 64 minecraft:smooth_quartz");
            NativeScreenshotReadiness.afterFloor(c,w,min,max);
            server.runCommand("gamemode survival @p");server.runCommand("tp @p 48.5 5 54.5");
            c.waitFor(client->client.player.onGround()&&!client.player.isSpectator()&&client.player.distanceToSqr(48.5,5,54.5)<.01,250);
            c.getInput().pressKey(InputConstants.KEY_2);c.getInput().pressKey(InputConstants.KEY_1);
            await(c,w,level->player(level).getInventory().getSelectedSlot()==0,"Native first slot selection");
            c.getInput().lookAt(0,-80);
            if(Boolean.getBoolean("create.test.onlyFluidBuckets")){buckets(c,w);return;}
            for(var food:List.of(AllItems.BAR_OF_CHOCOLATE,AllItems.SWEET_ROLL,AllItems.CHOCOLATE_BERRIES,AllItems.HONEYED_APPLE,AllItems.BUILDERS_TEA))eat(c,w,food);
            buckets(c,w);
            experience(c,w);polish(c,w,AllItems.SAND_PAPER,false);polish(c,w,AllItems.RED_SAND_PAPER,false);
            polish(c,w,AllItems.SAND_PAPER,true);cancelPolishing(c,w);scrape(c,w);
        } finally {
            c.getInput().releaseKey(options->options.keyUse);c.getInput().releaseKey(options->options.keyShift);
            try{var path=Path.of("build/reports/consumable-hand-tools-use.json");Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(passed)+"\n");}
            catch(Exception failure){throw new AssertionError("Cannot save native use results",failure);}
        }
    }

    private void eat(ClientGameTestContext c,TestSingleplayerContext w,Item food) {
        supply(c,w,new ItemStack(food,2),ItemStack.EMPTY);
        boolean tea=food==AllItems.BUILDERS_TEA;
        w.getServer().runOnServer(ignored->{var player=player(w.getConnection().getServerLevel());player.getFoodData().setFoodLevel(tea?20:4);player.getFoodData().setSaturation(0);});
        c.waitFor(client->client.player.getFoodData().getFoodLevel()==(tea?20:4),200);
        c.getInput().lookAt(0,-80);c.getInput().holdKey(options->options.keyUse);
        try{await(c,w,level->player(level).getMainHandItem().getCount()==1,"Physical food consumption: "+food);}
        finally{c.getInput().releaseKey(options->options.keyUse);}
        c.waitTicks(3);
        w.getServer().runOnServer(ignored->{var player=player(w.getConnection().getServerLevel());
            int expected=tea?20:4+food.components().get(net.minecraft.core.component.DataComponents.FOOD).nutrition();
            if(player.getMainHandItem().getCount()!=1||player.getFoodData().getFoodLevel()!=expected)throw new AssertionError("Food stock/nutrition mismatch: "+food);
            if(tea&&(!player.hasEffect(MobEffects.HASTE)||player.getInventory().countItem(Items.GLASS_BOTTLE)!=1))throw new AssertionError("Tea failed to supply haste and exactly one empty bottle");
        });
        passed.add("Physical eating/drinking, nutrition and finite stock: "+food);c.takeScreenshot("create-native-consume-"+passed.size());
    }

    private void experience(ClientGameTestContext c,TestSingleplayerContext w) {
        supply(c,w,new ItemStack(AllItems.EXP_NUGGET,5),ItemStack.EMPTY);
        w.getServer().runOnServer(ignored->{var player=player(w.getConnection().getServerLevel());player.totalExperience=0;player.experienceLevel=0;player.experienceProgress=0;});
        c.getInput().lookAt(0,45);c.getInput().holdKey(options->options.keyShift);
        await(c,w,level->player(level).isShiftKeyDown(),"Actual sneak key acknowledged");
        c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->player(level).getMainHandItem().getCount()==4,"Sneak use consumes exactly one nugget");
        c.getInput().releaseKey(options->options.keyShift);
        await(c,w,level->player(level).totalExperience==3,"One actual nugget supplies three collected XP");
        await(c,w,level->!player(level).isShiftKeyDown(),"Actual sneak key released");c.waitTicks(5);
        c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->player(level).getMainHandItem().isEmpty()&&player(level).totalExperience==15,"Normal use consumes the remaining four nuggets for twelve more XP");
        passed.add("Physical sneak/normal nugget use and pickup: five original nuggets -> fifteen XP");
    }
    private void buckets(ClientGameTestContext c,TestSingleplayerContext w){
        var source=new BlockPos(48,5,51);
        for(var bucket:List.of(AllItems.HONEY_BUCKET,AllItems.CHOCOLATE_BUCKET)){
            var fluid=bucket==AllItems.HONEY_BUCKET?com.simibubi.create.AllFluids.HONEY:com.simibubi.create.AllFluids.CHOCOLATE;
            w.getServer().runCommand("fill 47 5 50 49 6 52 minecraft:air");supply(c,w,new ItemStack(bucket),ItemStack.EMPTY);
            look(c,Vec3.atBottomCenterOf(source).add(0,-.001,0));c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(source.below()),200);c.getInput().pressKey(options->options.keyUse);
            await(c,w,level->level.getFluidState(source).is(fluid)&&level.getFluidState(source).isSource()&&player(level).getMainHandItem().is(Items.BUCKET),"Native bucket use must place the original custom liquid and return one empty bucket: "+bucket);
            c.waitFor(client->client.level.getFluidState(source).is(fluid)&&client.player.getMainHandItem().is(Items.BUCKET),200);w.getConnection().waitForChunksRender();c.takeScreenshot("create-native-bucket-source-"+(bucket==AllItems.HONEY_BUCKET?"honey":"chocolate"));
            look(c,Vec3.atCenterOf(source));c.waitTicks(5);c.getInput().pressKey(options->options.keyUse);
            await(c,w,level->player(level).getMainHandItem().is(bucket)&&player(level).getInventory().countItem(bucket)==1&&player(level).getInventory().countItem(Items.BUCKET)==0&&!level.getFluidState(source).isSource(),"Native empty bucket must pick up the exact custom liquid source without another bucket: "+bucket);
            passed.add("Physical custom bucket placement, world rendering and source pickup preserve one original bucket: "+bucket);
        }
        w.getServer().runCommand("fill 47 5 50 49 6 52 minecraft:air");
    }

    private void polish(ClientGameTestContext c,TestSingleplayerContext w,Item paper,boolean ground) {
        supply(c,w,new ItemStack(paper),ground?ItemStack.EMPTY:new ItemStack(AllItems.ROSE_QUARTZ,3));
        if(ground){
            w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();var item=new ItemEntity(level,48.5,5.1,52,new ItemStack(AllItems.ROSE_QUARTZ,2));item.setPickUpDelay(6000);level.addFreshEntity(item);});
            look(c,new Vec3(48.5,5.01,52));
        }else c.getInput().lookAt(0,-80);
        c.getInput().holdKey(options->options.keyUse);
        try{await(c,w,level->player(level).getInventory().countItem(AllItems.POLISHED_ROSE_QUARTZ)==1,"Native sandpaper held-use output: "+paper+", ground="+ground);}
        finally{c.getInput().releaseKey(options->options.keyUse);}
        c.waitTicks(3);
        w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();var player=player(level);
            if(!player.getMainHandItem().is(paper)||player.getMainHandItem().getDamageValue()!=1||player.getMainHandItem().has(AllDataComponents.SAND_PAPER_POLISHING)||player.getInventory().countItem(AllItems.POLISHED_ROSE_QUARTZ)!=1)throw new AssertionError("Physical polishing lost output, tool charge or in-progress custody");
            int remaining=player.getInventory().countItem(AllItems.ROSE_QUARTZ)+level.getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(8),entity->entity.getItem().is(AllItems.ROSE_QUARTZ)).stream().mapToInt(entity->entity.getItem().getCount()).sum();
            if(remaining!=(ground?1:2))throw new AssertionError("Polishing duplicated or lost original input: "+remaining);
            level.getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(8)).forEach(ItemEntity::discard);
        });
        passed.add("Physical polishing with one tool charge and one input: "+paper+", ground="+ground);c.takeScreenshot("create-native-polishing-"+passed.size());
    }

    private void cancelPolishing(ClientGameTestContext c,TestSingleplayerContext w) {
        supply(c,w,new ItemStack(AllItems.RED_SAND_PAPER),new ItemStack(AllItems.ROSE_QUARTZ,3));c.getInput().lookAt(0,-80);
        c.getInput().holdKey(options->options.keyUse);
        try{await(c,w,level->player(level).getMainHandItem().has(AllDataComponents.SAND_PAPER_POLISHING),"Native polishing began before cancellation");c.waitTicks(6);}
        finally{c.getInput().releaseKey(options->options.keyUse);}
        await(c,w,level->!player(level).getMainHandItem().has(AllDataComponents.SAND_PAPER_POLISHING)&&!player(level).isUsingItem(),"Native release returns original polishing input");
        w.getServer().runOnServer(ignored->{var player=player(w.getConnection().getServerLevel());if(player.getInventory().countItem(AllItems.ROSE_QUARTZ)!=3||player.getMainHandItem().getDamageValue()!=0||player.getInventory().countItem(AllItems.POLISHED_ROSE_QUARTZ)!=0)throw new AssertionError("Cancelled use changed original input/tool stock");});
        passed.add("Physical release cancels polishing and returns exact original input without tool wear");
    }

    private void scrape(ClientGameTestContext c,TestSingleplayerContext w) {
        var oxidized=Blocks.COPPER_BLOCK.weathering().oxidized();var weathered=Blocks.COPPER_BLOCK.weathering().weathered();
        supply(c,w,new ItemStack(AllItems.SAND_PAPER),ItemStack.EMPTY);
        w.getServer().runOnServer(ignored->w.getConnection().getServerLevel().setBlockAndUpdate(COPPER,oxidized.defaultBlockState()));
        c.waitFor(client->client.level.getBlockState(COPPER).is(oxidized),200);look(c,Vec3.atCenterOf(COPPER).add(0,0,.49));
        c.waitFor(client->client.hitResult instanceof BlockHitResult hit&&hit.getBlockPos().equals(COPPER),200);c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->level.getBlockState(COPPER).is(weathered)&&player(level).getMainHandItem().getDamageValue()==1,"Actual sandpaper removes one oxidation stage");
        w.getServer().runOnServer(ignored->{var level=w.getConnection().getServerLevel();level.setBlockAndUpdate(COPPER,HoneycombItem.WAXABLES.get().get(oxidized).defaultBlockState());});
        c.waitFor(client->client.level.getBlockState(COPPER).getBlock()==HoneycombItem.WAXABLES.get().get(oxidized),200);c.waitTicks(5);c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->level.getBlockState(COPPER).is(oxidized)&&player(level).getMainHandItem().getDamageValue()==2,"Actual sandpaper removes wax before oxidation");
        var worn=new ItemStack(AllItems.RED_SAND_PAPER);worn.setDamageValue(7);supply(c,w,worn,ItemStack.EMPTY);c.waitTicks(5);c.getInput().pressKey(options->options.keyUse);
        await(c,w,level->level.getBlockState(COPPER).is(weathered)&&player(level).getMainHandItem().isEmpty(),"Eighth native sandpaper charge breaks the original tool");
        passed.add("Physical copper oxidation removal, wax removal, and actual eighth-use tool break");c.takeScreenshot("create-native-sandpaper-copper-final");
    }

    private static void supply(ClientGameTestContext c,TestSingleplayerContext w,ItemStack main,ItemStack off) {
        c.getInput().releaseKey(options->options.keyUse);w.getServer().runCommand("clear @p");
        w.getServer().runOnServer(ignored->{var player=player(w.getConnection().getServerLevel());player.getInventory().setItem(0,main);player.setItemInHand(InteractionHand.OFF_HAND,off);player.containerMenu.broadcastChanges();});
        c.waitFor(client->ItemStack.matches(client.player.getMainHandItem(),main)&&ItemStack.matches(client.player.getOffhandItem(),off)&&!client.player.isUsingItem(),200);c.waitTicks(5);
    }
    private static ServerPlayer player(ServerLevel level){return level.getServer().getPlayerList().getPlayers().getFirst();}
    private static void await(ClientGameTestContext c,TestSingleplayerContext w,Predicate<ServerLevel> condition,String message){var ok=new AtomicBoolean();for(int tick=0;tick<400;tick++){w.getServer().runOnServer(ignored->ok.set(condition.test(w.getConnection().getServerLevel())));if(ok.get())return;c.waitTicks(1);}throw new AssertionError(message);}
    private static void look(ClientGameTestContext c,Vec3 target){var angles=c.computeOnClient(client->{var delta=target.subtract(client.player.getEyePosition());return new Vec2((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));});c.getInput().lookAt(angles.x,angles.y);}
}
