package com.simibubi.create.test;

import static com.simibubi.create.test.TrackSmoothingGameTests.*;
import com.simibubi.create.*;
import com.simibubi.create.content.trains.track.*;
import java.util.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.*;

/** Separate explicit source-bug-fix evidence: paid source cost survives fresh smoothing geometry and native save/load. */
public final class TrackSmoothingCostGameTests {
    @GameTest(structure=STRUCTURE)public void genuinePaidSlopeShowsSourceGeometryCostDriftButConservesActualCanonicalMaterials(GameTestHelper h){
        var f=gapFixture(h);var low=curve(h,f,f.low());h.assertTrue(f.originalLow().getTrackItemCost()!=low.getTrackItemCost(),"Real actual-item slope fixture reproduces source cached-versus-fresh-geometry item-cost drift");
        h.assertTrue(TrackCurveCosts.getTrackItemCost(low)==f.originalLow().getTrackItemCost()&&TrackCurveCosts.getGirderItemCost(low)==f.originalLow().getGirderItemCost(),"Separate real-data world item calculation recovers original paid source material quantities");
        h.assertTrue(Math.abs(low.getLength()-low.clone().getLength())<1e-10&&low.axes.getFirst().y!=0,"Pure Bezier geometry API/actual physics remain freshly smoothed");h.succeed();
    }
    @GameTest(structure=STRUCTURE)public void actualNativeSaveFactoryLoadRetainsCanonicalCostOnBothGenuineReciprocals(GameTestHelper h){
        var f=gapFixture(h);int low=f.originalLow().getTrackItemCost(),high=f.originalHigh().getTrackItemCost();double length=curve(h,f,f.low()).getLength();var beforeAngle=center(h,f).tilt.smoothingAngle;reload(h,f);
        var a=curve(h,f,f.low());var b=curve(h,f,f.high());var reverse=h.getBlockEntity(f.low(),TrackBlockEntity.class).getConnections().get(h.absolutePos(f.center()));
        h.assertTrue(center(h,f).isTilted()&&center(h,f).tilt.smoothingAngle.equals(beforeAngle)&&a.getLength()==length&&a.axes.getFirst().y!=0,"Native real save/load preserves actual smoothed pose/physics");
        h.assertTrue(TrackCurveCosts.getTrackItemCost(a)==low&&TrackCurveCosts.getTrackItemCost(reverse)==low&&TrackCurveCosts.getTrackItemCost(b)==high&&count(f.player(),AllBlocks.TRACK.asItem())+low+high==128&&count(f.player(),AllBlocks.METAL_GIRDER.asItem())+(low+high)*2==128,"Both native-decoded directions recover exact original real stock ledger without extra saved cost fields");h.succeed();
    }
    @GameTest(structure=STRUCTURE)public void nativeReloadThenActualEndpointWrenchReturnsAllOriginalPaidCurveMaterials(GameTestHelper h){
        var f=gapFixture(h);reload(h,f);holdWrench(f);var p=f.player();var pos=h.absolutePos(f.center());p.setPos(Vec3.atBottomCenterOf(pos).add(0,1,2));var context=new UseOnContext(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atBottomCenterOf(pos).add(0,.125,0),Direction.UP,pos,false));
        h.assertTrue(AllItems.WRENCH.useOn(context).consumesAction(),"Real native shift-wrench dispatch dismantles actual saved tilted endpoint");
        h.assertTrue(h.getLevel().getBlockState(pos).isAir()&&count(p,AllBlocks.TRACK.asItem())==129&&count(p,AllBlocks.METAL_GIRDER.asItem())==128&&!h.getBlockState(f.low()).getValue(TrackBlock.HAS_BE)&&!h.getBlockState(f.high()).getValue(TrackBlock.HAS_BE),"Native save/load plus genuine source endpoint operation returns all128paid tracks/128paid girders and the one original physical center block exactly once");h.succeed();
    }
    @GameTest(structure=STRUCTURE)public void nativeReloadThenOrdinaryRealEndpointBreakDropsExactPaidCurveCosts(GameTestHelper h){
        var f=gapFixture(h);reload(h,f);var before=new HashSet<UUID>();h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds().inflate(2)).forEach(e->before.add(e.getUUID()));h.getLevel().destroyBlock(h.absolutePos(f.center()),false);
        int tracks=count(f.player(),AllBlocks.TRACK.asItem()),girders=count(f.player(),AllBlocks.METAL_GIRDER.asItem());for(var item:h.getLevel().getEntitiesOfClass(ItemEntity.class,h.getBounds().inflate(2)))if(!before.contains(item.getUUID())){if(item.getItem().is(AllBlocks.TRACK.asItem()))tracks+=item.getItem().getCount();if(item.getItem().is(AllBlocks.METAL_GIRDER.asItem()))girders+=item.getItem().getCount();}
        h.assertTrue(tracks==128&&girders==128&&!h.getBlockState(f.low()).getValue(TrackBlock.HAS_BE)&&!h.getBlockState(f.high()).getValue(TrackBlock.HAS_BE),"Actual native world drops after source smoothed save/load conserve original paid material quantities");h.succeed();
    }
}
