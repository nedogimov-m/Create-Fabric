package com.simibubi.create.test;

import com.simibubi.create.*;
import com.simibubi.create.content.kinetics.deployer.*;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** Genuine manual applications and committed native depot processing; no trigger calls. UNRUN. */
public final class CasingAdvancementGameTests {
    private static final BlockPos P=new BlockPos(3,3,3),H=P.above(2);
    private static final String[] IDS={"andesite_casing","brass_casing","copper_casing","train_casing_00"};
    private static Block input(int index){return index==3?AllBlocks.BRASS_CASING:Blocks.STRIPPED_OAK_LOG;}
    private static Block output(int index){return switch(index){case 0->AllBlocks.ANDESITE_CASING;case 1->AllBlocks.BRASS_CASING;case 2->AllBlocks.COPPER_CASING;default->AllBlocks.RAILWAY_CASING;};}
    private static Item material(int index){return switch(index){case 0->AllItems.ANDESITE_ALLOY;case 1->AllItems.BRASS_INGOT;case 2->Items.COPPER_INGOT;default->AllItems.STURDY_SHEET;};}
    private static NativeSurvivalTestPlayer join(GameTestHelper h){return NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(P)).add(0,0,3));}
    private static boolean done(ServerPlayer player,String id){return GameplayAdvancementGameTests.done(player,id);}
    private static InteractionResult use(GameTestHelper h,ServerPlayer player,BlockPos pos,Direction side){
        var actual=h.absolutePos(pos);var point=Vec3.atCenterOf(actual).add(side.getUnitVec3().scale(.5));
        return player.gameMode.useItemOn(player,h.getLevel(),player.getMainHandItem(),InteractionHand.MAIN_HAND,new BlockHitResult(point,side,actual,false));
    }
    private static void manual(GameTestHelper h,int index){
        h.setBlock(P,input(index));
        try(var owner=join(h);var observer=join(h)){
            var player=owner.player();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(material(index),2));
            h.assertTrue(!done(player,IDS[index])&&!done(observer.player(),IDS[index]),"Fresh actual users have no casing progress");
            h.assertTrue(use(h,player,P,Direction.SOUTH).consumesAction(),"Actual native player application is accepted");
            h.assertBlockPresent(output(index),P);h.assertValueEqual(player.getMainHandItem().getCount(),1,"Only one physical material was paid");
            h.assertTrue(done(player,IDS[index])&&!done(observer.player(),IDS[index]),"Successful original casing transformation awards only the actual interactor");
        }h.succeed();
    }
    @GameTest public void realManualAndesiteApplicationAwardsActualInteractor(GameTestHelper h){manual(h,0);}
    @GameTest public void realManualBrassApplicationAwardsActualInteractor(GameTestHelper h){manual(h,1);}
    @GameTest public void realManualCopperApplicationAwardsActualInteractor(GameTestHelper h){manual(h,2);}
    @GameTest public void realManualRailwayApplicationAwardsActualInteractor(GameTestHelper h){manual(h,3);}

    private static long count(DepotBlockEntity depot,Item item){return java.util.stream.Stream.concat(depot.inventory.inputs.stream(),depot.inventory.outputs.stream()).filter(slot->slot.stack().is(item)).mapToLong(slot->slot.getAmount()).sum();}
    private static void cycles(GameTestHelper h,DeployerBlockEntity deployer,DepotBlockEntity depot,int ticks){
        for(int i=0;i<ticks;i++){
            DepotBlockEntity.tick(h.getLevel(),depot.getBlockPos(),depot.getBlockState(),depot);
            DeployerBlockEntity.tick(h.getLevel(),deployer.getBlockPos(),deployer.getBlockState(),deployer);
        }
    }
    private static void automated(GameTestHelper h,int index){
        h.setBlock(P,AllBlocks.DEPOT);
        try(var owner=join(h);var observer=join(h)){
            var player=owner.player();player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllBlocks.DEPLOYER,2));
            h.setBlock(H.east(),Blocks.STONE);player.setXRot(-90);
            h.assertTrue(use(h,player,H.east(),Direction.WEST).consumesAction(),"Actual survival BlockItem pays for manual downward placement beside a physical support");
            var deployer=h.getBlockEntity(H,DeployerBlockEntity.class);var depot=h.getBlockEntity(P,DepotBlockEntity.class);
            h.assertTrue(deployer.getBlockState().getValue(DeployerBlock.FACING)==Direction.DOWN&&player.getUUID().equals(deployer.owner),"Physical placement establishes the one actual downward machine and canonical survival Owner");
            h.assertValueEqual(player.getMainHandItem().getCount(),1,"Machine placement pays one actual item");
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(material(index)));
            h.assertTrue(use(h,player,H,Direction.DOWN).consumesAction()&&deployer.getHeldItem().is(material(index))&&player.getMainHandItem().isEmpty(),"Actual hand interaction transfers one finite original material to machine");
            try(var tx=Transaction.openOuter()){
                h.assertValueEqual(depot.inventory.automation.insert(ItemVariant.of(input(index).asItem()),1,tx),1L,"One finite original input enters actual native depot");tx.commit();
            }
            h.setBlock(H.east(),Blocks.REDSTONE_BLOCK);GameplayAdvancementGameTests.drive(deployer,256);cycles(h,deployer,depot,24);
            h.assertTrue(!done(player,IDS[index])&&count(depot,input(index).asItem())==1&&deployer.getHeldItem().is(material(index)),"A physically inhibited machine cannot award from inventory or recipe readiness");
            h.setBlock(H.east(),Blocks.AIR);GameplayAdvancementGameTests.drive(deployer,256);cycles(h,deployer,depot,100);
            h.assertTrue(count(depot,output(index).asItem())==1&&count(depot,input(index).asItem())==0&&deployer.getHeldItem().isEmpty(),"One complete native application commits its original input and tool to one actual casing output");
            h.assertTrue(done(player,IDS[index])&&!done(observer.player(),IDS[index])&&player.getUUID().equals(deployer.owner),"Committed casing output awards only canonical live machine owner without replacing durable actor authority");
        }h.succeed();
    }
    @GameTest public void realDeployerAndesiteOutputAwardsCanonicalOwner(GameTestHelper h){automated(h,0);}
    @GameTest public void realDeployerBrassOutputAwardsCanonicalOwner(GameTestHelper h){automated(h,1);}
    @GameTest public void realDeployerCopperOutputAwardsCanonicalOwner(GameTestHelper h){automated(h,2);}
    @GameTest public void realDeployerRailwayOutputAwardsCanonicalOwner(GameTestHelper h){automated(h,3);}

    @GameTest public void collectingCasingsAndRejectedManualUseDoNotAward(GameTestHelper h){
        h.setBlock(P,Blocks.STRIPPED_OAK_LOG);
        try(var session=join(h)){
            var player=session.player();for(int i=0;i<4;i++)player.getInventory().setItem(9+i,new ItemStack(output(i)));
            player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_NUGGET));use(h,player,P,Direction.SOUTH);
            h.assertBlockPresent(Blocks.STRIPPED_OAK_LOG,P);h.assertTrue(player.getMainHandItem().is(Items.IRON_NUGGET),"Rejected original application retains its finite material");
            for(var id:IDS)h.assertTrue(!done(player,id),"Inventory ownership and a refused world interaction cannot award "+id);
        }h.succeed();
    }
    @GameTest public void fakeManualApplicationCannotAwardRealOwnerOrFakeActor(GameTestHelper h){
        h.setBlock(P,Blocks.STRIPPED_OAK_LOG);
        try(var session=join(h)){
            var actor=new DeployerFakePlayer(h.getLevel(),UUID.randomUUID());actor.setPos(session.player().position());actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(AllItems.ANDESITE_ALLOY));
            h.assertTrue(use(h,actor,P,Direction.SOUTH).consumesAction(),"Actual original automated player may apply the physical world recipe");
            h.assertBlockPresent(AllBlocks.ANDESITE_CASING,P);
            h.assertTrue(!done(actor,IDS[0])&&!done(session.player(),IDS[0]),"Manual callback never manufactures advancement progress for fake or unrelated real players");
            actor.discard();
        }h.succeed();
    }
}
