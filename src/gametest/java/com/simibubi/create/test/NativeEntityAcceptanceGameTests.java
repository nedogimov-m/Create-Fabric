package com.simibubi.create.test;

import com.simibubi.create.foundation.utility.NativeEntityAcceptance;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Real vanilla UUID reservation, section insertion and onCreated boundaries; runtime UNRUN. */
public class NativeEntityAcceptanceGameTests {
    private static ItemEntity endpoint(GameTestHelper h,ItemStack payload){
        var point=Vec3.atCenterOf(h.absolutePos(new BlockPos(2,2,2)));
        return new ItemEntity(h.getLevel(),point.x,point.y,point.z,payload,0,0,0);
    }
    private static ItemStack original(){
        var stack=new ItemStack(Items.DIAMOND,3);
        stack.set(DataComponents.CUSTOM_NAME,Component.literal("Original actual native custody"));return stack;
    }
    @GameTest public void changedActualSourceAfterHeadRefusesBeforeSectionAndSameEndpointCanRetry(GameTestHelper h)throws Exception{
        var original=original();var source=endpoint(h,original);
        h.assertTrue(h.getLevel().addFreshEntity(source),"Actual original source is world-owned");
        var later=new ItemStack(Items.EMERALD,7);var output=endpoint(h,new ItemStack(Items.APPLE));
        var commits=new AtomicInteger();var created=new AtomicInteger();
        try(var hook=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity==output,entity->source.setItem(later));
            var observer=CartAcceptanceTestFixtures.beforeCreated(entity->entity==output,entity->created.incrementAndGet());
            var admission=NativeEntityAcceptance.prepare(output,()->source.getItem()==original,proof->{proof.requireCommitting(output);commits.incrementAndGet();original.shrink(1);})){ 
            h.assertFalse(admission.add(),"Actual source changed after initial guard must refuse before native section insertion");
            h.assertTrue(admission.insertion()==null,"No insertion proof is manufactured for a refused endpoint");
        }
        h.assertTrue(commits.get()==0&&created.get()==0&&source.getItem()==later&&later.getCount()==7
            &&original.getCount()==3&&h.getLevel().getEntity(output.getUUID())==null&&!output.isRemoved(),
            "No output, debit, creation callback or copied restoration can overwrite the real changed source");
        try(var admission=NativeEntityAcceptance.prepare(output,()->source.getItem()==later,proof->{proof.requireCommitting(output);commits.incrementAndGet();later.shrink(1);})){ 
            h.assertTrue(admission.add()&&admission.insertion()!=null,"The exact original prospective entity can retry after its failed UUID reservation is released");
        }
        h.assertTrue(commits.get()==1&&later.getCount()==6&&h.getLevel().getEntity(output.getUUID())==output,
            "One real native endpoint has one actual source payment");h.succeed();
    }
    @GameTest public void changedProspectiveUuidReleasesOnlyTheUuidActuallyReservedByVanilla(GameTestHelper h)throws Exception{
        var output=endpoint(h,new ItemStack(Items.APPLE));var changed=UUID.randomUUID();var commits=new AtomicInteger();
        try(var hook=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity==output,entity->entity.setUUID(changed));
            var admission=NativeEntityAcceptance.prepare(output,()->true,proof->commits.incrementAndGet())){
            h.assertFalse(admission.add(),"Identity changed between initial validation and native UUID reservation is refused before section ownership");
        }
        var probe=endpoint(h,new ItemStack(Items.STICK));probe.setUUID(changed);
        h.assertTrue(commits.get()==0&&h.getLevel().getEntity(changed)==null&&h.getLevel().addFreshEntity(probe)
            &&h.getLevel().getEntity(changed)==probe,"The changed UUID actually reserved by vanilla was released; no stale reservation strands a genuine later endpoint");h.succeed();
    }
    @GameTest public void throwingFinalSourceValidatorRetainsOriginalFaultAndReleasesUnusedNativeUuid(GameTestHelper h)throws Exception{
        var source=endpoint(h,original());h.assertTrue(h.getLevel().addFreshEntity(source),"Actual original source admitted");
        var original=source.getItem();var output=endpoint(h,new ItemStack(Items.APPLE));var afterHead=new AtomicBoolean();
        var fault=new IllegalStateException("Actual final source validation fault");var commits=new AtomicInteger();boolean failed=false;
        try(var hook=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity==output,entity->afterHead.set(true));
            var admission=NativeEntityAcceptance.prepare(output,()->{if(afterHead.get())throw fault;return source.getItem()==original;},proof->commits.incrementAndGet())){
            try{admission.add();}catch(IllegalStateException actual){h.assertTrue(actual==fault,"Original validation fault identity is preserved");failed=true;}
        }
        var probe=endpoint(h,new ItemStack(Items.STICK));probe.setUUID(output.getUUID());
        h.assertTrue(failed&&commits.get()==0&&source.getItem()==original&&original.getCount()==3
            &&h.getLevel().getEntity(output.getUUID())==null&&h.getLevel().addFreshEntity(probe),
            "A throwing callback-free validator leaves original cargo untouched, admits no output and releases only its unused native UUID");h.succeed();
    }
    @GameTest public void reentrantNativeAddOfSameProspectiveEndpointCannotReserveOrCommitTwice(GameTestHelper h)throws Exception{
        var output=endpoint(h,new ItemStack(Items.APPLE));var nested=new AtomicBoolean(true);var commits=new AtomicInteger();
        try(var hook=CartAcceptanceTestFixtures.beforeNativeUuidCheck(entity->entity==output,entity->nested.set(h.getLevel().addFreshEntity(output)));
            var admission=NativeEntityAcceptance.prepare(output,()->true,proof->{proof.requireCommitting(output);commits.incrementAndGet();})){
            h.assertTrue(admission.add()&&admission.insertion()!=null,"Outer actual endpoint is still accepted after nested refusal");
        }
        h.assertTrue(!nested.get()&&commits.get()==1&&h.getLevel().getEntity(output.getUUID())==output,
            "Only one original invocation can reserve and commit this one physical endpoint");h.succeed();
    }
}
