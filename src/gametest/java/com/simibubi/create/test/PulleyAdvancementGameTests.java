package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.contraptions.pulley.PulleyBlockEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import com.simibubi.create.content.kinetics.KineticGraph;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import java.util.HashSet;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Actual physical placement, rope motion and native advancement progress; proposed, UNRUN. */
public final class PulleyAdvancementGameTests {
    private static boolean done(ServerPlayer player,String id){
        var entry=player.level().getServer().getAdvancements().get(Create.asResource(id));
        if(entry==null)throw new AssertionError("Original native advancement resource missing: "+id);
        return player.getAdvancements().getOrStartProgress(entry).isDone();
    }
    private static NativeSurvivalTestPlayer join(GameTestHelper h,BlockPos p){
        h.setBlock(p.south(2).below(),Blocks.STONE);
        return NativeSurvivalTestPlayer.join(h,Vec3.atCenterOf(h.absolutePos(p.south(2))));
    }
    private static PulleyBlockEntity place(GameTestHelper h,BlockPos p,ServerPlayer player){
        h.setBlock(p.below(),Blocks.STONE);var stack=new ItemStack(AllBlocks.ROPE_PULLEY);player.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var support=h.absolutePos(p.below());var result=stack.useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(support).add(0,.5,0),Direction.UP,support,false)));
        h.assertTrue(result.consumesAction()&&stack.isEmpty(),"Actual native survival placement pays one original pulley");
        var pulley=h.getBlockEntity(p,PulleyBlockEntity.class);
        h.assertTrue(player.getUUID().equals(pulley.sourceAdvancements.uuid()),"Only the actual placing native player owns the device's criteria");
        return pulley;
    }
    private static void drive(PulleyBlockEntity pulley){pulley.setNetworkState(128,128,false,new KineticGraph.Stress(512,64,false),Map.of(Direction.WEST,128f));}
    private static void tick(GameTestHelper h,BlockPos p,PulleyBlockEntity pulley){PulleyBlockEntity.tick(h.getLevel(),h.absolutePos(p),h.getBlockState(p),pulley);}
    private static void physicalRopes(GameTestHelper h,BlockPos p){for(int i=1;i<199;i++)h.setBlock(p.below(i),AllBlocks.ROPE);h.setBlock(p.below(199),AllBlocks.PULLEY_MAGNET);}
    @GameTest public void genuinePlacementOwnerSurvivesNativePreLevelSaveAndOwnerlessSaveNeverAdoptsNearestPlayer(GameTestHelper h){
        var p=new BlockPos(4,5,4);
        try(var session=join(h,p)){
            var player=session.player();var pulley=place(h,p,player);var saved=pulley.saveWithFullMetadata(h.getLevel().registryAccess());
            var restored=(PulleyBlockEntity)BlockEntity.loadStatic(h.absolutePos(p),h.getBlockState(p),saved,h.getLevel().registryAccess());
            h.assertTrue(restored!=null&&restored.getLevel()==null&&player.getUUID().equals(restored.sourceAdvancements.uuid()),"Original Owner UUID survives decoding before actual native level assignment");
            h.assertTrue(restored.saveWithFullMetadata(h.getLevel().registryAccess()).read("Owner",UUIDUtil.CODEC).orElseThrow().equals(player.getUUID()),"Pre-level re-save retains that exact source UUID");
            saved.remove("Owner");restored=(PulleyBlockEntity)BlockEntity.loadStatic(h.absolutePos(p),h.getBlockState(p),saved,h.getLevel().registryAccess());
            h.assertTrue(restored.sourceAdvancements.uuid()==null&&!done(player,"pulley_maxed"),"Ownerless legacy data cannot award a nearby real player or progress on placement");
        }h.succeed();
    }
    @GameTest(structure="create_test:deep_pulley") public void physicalOneHundredNinetyNineRopeExtensionAwardsOnlyAfterActualTwoHundredCrossingAndReload(GameTestHelper h)throws Exception{
        var p=new BlockPos(4,220,4);
        try(var session=join(h,p)){
            var player=session.player();var pulley=place(h,p,player);physicalRopes(h,p);drive(pulley);
            h.assertTrue(pulley.assemble()&&pulley.getInitialRopeOffset()==199&&pulley.getMovedContraption()==null,"Native assembly removes the199 real source rope cells without inventing an empty cargo body");
            for(int i=0;i<12&&pulley.offset<199.5f;i++)tick(h,p,pulley);
            h.assertTrue(pulley.offset>=199.5f&&pulley.offset<200&&!done(player,"pulley_maxed"),"Actual below200 motion cannot earn the original threshold");
            var saved=pulley.saveWithFullMetadata(h.getLevel().registryAccess());
            var loaded=(PulleyBlockEntity)BlockEntity.loadStatic(h.absolutePos(p),h.getBlockState(p),saved,h.getLevel().registryAccess());
            h.getLevel().removeBlockEntity(h.absolutePos(p));h.getLevel().setBlockEntity(loaded);drive(loaded);
            for(int i=0;i<12&&loaded.offset<200;i++){h.assertFalse(done(player,"pulley_maxed"),"Native progress remains incomplete before the first real crossing");tick(h,p,loaded);}
            h.assertTrue(loaded.offset>=200&&done(player,"pulley_maxed"),"Same original saved placement owner receives the native criterion exactly on actual extension");
            h.assertTrue(player.getUUID().equals(loaded.sourceAdvancements.uuid()),"Owner remains for the source's still-unmet contraption-actor criterion");
            h.assertTrue(loaded.disassemble(),"Actual empty virtual rope restores physical world rope/magnet after the award");
        }h.succeed();
    }
    @GameTest(structure="create_test:deep_pulley") public void unownedRealTwoHundredCrossingCannotAwardNearbyNativePlayer(GameTestHelper h)throws Exception{
        var p=new BlockPos(4,220,4);
        try(var session=join(h,p)){
            h.setBlock(p,AllBlocks.ROPE_PULLEY);var pulley=h.getBlockEntity(p,PulleyBlockEntity.class);physicalRopes(h,p);drive(pulley);
            h.assertTrue(pulley.assemble(),"The actual unowned world-set pulley really assembles its199 ropes");
            for(int i=0;i<12&&pulley.offset<200;i++)tick(h,p,pulley);
            h.assertTrue(pulley.offset>=200&&pulley.sourceAdvancements.uuid()==null&&!done(session.player(),"pulley_maxed"),"A real physical crossing cannot invent a nearby advancement owner");
            h.assertTrue(pulley.disassemble(),"Unowned motion still restores its finite actual rope stock");
        }h.succeed();
    }
    @GameTest public void removedActualPulleyCannotUseItsOldOwnerToAwardANewReplacement(GameTestHelper h){
        var p=new BlockPos(4,5,4);
        try(var first=join(h,p);var second=join(h,p.east())){
            var old=place(h,p,first.player());h.setBlock(p,Blocks.AIR);var current=place(h,p,second.player());
            old.sourceAdvancements.award("pulley_maxed");
            h.assertTrue(second.player().getUUID().equals(current.sourceAdvancements.uuid())&&!done(first.player(),"pulley_maxed")&&!done(second.player(),"pulley_maxed"),"Exact retired BE identity cannot award either former or replacement player before real movement");
        }h.succeed();
    }
    @GameTest(structure="create_test:deep_pulley",maxTicks=100) public void actualDrillCaptureAndStalledBodyUseOriginalOwnerCriteriaWithoutPhantomDepth(GameTestHelper h){
        var p=new BlockPos(4,220,4);var body=p.below(200);var session=join(h,p);var player=session.player();var pulley=place(h,p,player);
        physicalRopes(h,p);h.setBlock(body,AllBlocks.MECHANICAL_DRILL);h.setBlock(body.south(),Blocks.BARREL);
        h.getBlockEntity(body.south(),BarrelBlockEntity.class).setItem(0,new ItemStack(Items.DIAMOND,7));
        h.assertTrue(h.getLevel().addFreshEntity(new SuperGlueEntity(h.getLevel(),SuperGlueEntity.span(h.absolutePos(body),h.absolutePos(body.south())))),"Real physical drill and seven-diamond barrel have one actual source glue bond");
        h.startSequence().thenWaitUntil(()->{
            var checked=new HashSet<Long>();
            try{LoadedEntityAccess.requireReady(h.getLevel(),h.absolutePos(p));LoadedEntityAccess.requireReady(h.getLevel(),h.absolutePos(body));LoadedEntityAccess.requireGlueQueriesReady(h.getLevel(),h.absolutePos(body),checked);}
            catch(com.simibubi.create.content.contraptions.AssemblyException pending){throw h.assertionException("Actual source/glue native readiness pending: "+pending.getMessage());}
        }).thenExecute(()->{
            drive(pulley);
            try{h.assertTrue(pulley.assemble(),"Real drill/barrel body is captured by the native owning forest");}
            catch(com.simibubi.create.content.contraptions.AssemblyException failure){throw new AssertionError("Actual ready drill capture failed",failure);}
            var owner=pulley.getMovedContraption();h.assertTrue(owner!=null&&owner.hasOwnedStructure()&&done(player,"contraption_actors")&&!done(player,"pulley_maxed"),"Actor award follows actual source capture;199 blocks cannot earn depth award");
            owner.setStalled(true);for(int i=0;i<12;i++)tick(h,p,pulley);
            h.assertTrue(pulley.offset==199&&!done(player,"pulley_maxed"),"A genuinely stalled actual body cannot fabricate200 blocks of travel");
            owner.setStalled(false);for(int i=0;i<12&&pulley.offset<200;i++)tick(h,p,pulley);
            h.assertTrue(pulley.offset>=200&&done(player,"pulley_maxed")&&pulley.sourceAdvancements.uuid()==null,"Only real resumed movement finishes both criteria and retires the completed native owner");
            var restored=owner.blockPosition().south();
            h.assertTrue(pulley.disassemble(),"Same actual body and storage restore after both awards");
            h.assertTrue(h.getLevel().getBlockEntity(restored) instanceof BarrelBlockEntity barrel&&barrel.getItem(0).is(Items.DIAMOND)&&barrel.getItem(0).getCount()==7,"Seven original diamonds restore once; advancement never manufactures cargo");
        }).thenExecute(session::close).thenSucceed();
    }
}
