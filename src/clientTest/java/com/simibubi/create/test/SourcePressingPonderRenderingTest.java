package com.simibubi.create.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.PressingBehaviour.Mode;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.createmod.catnip.levelWrappers.WrappedClientLevel;
import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.mixin.client.PonderParticleAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Actual Ponder world, native source head motion and actual native particle providers. UNCOMPILED / UNRUN. */
public final class SourcePressingPonderRenderingTest implements FabricClientGameTest {
    private static final BlockPos P=new BlockPos(2,4,2);
    /** Receipts retain actual ParticleEngine.createParticle results from the source world path. */
    private static final class SceneWorld extends PonderLevel {
        final List<Particle> created=new ArrayList<>();
        SceneWorld(Level host){super(BlockPos.ZERO,host);}
        @Override public void addParticle(ParticleOptions options,double x,double y,double z,double dx,double dy,double dz){
            var particle=particles.create(options,x,y,z,dx,dy,dz);if(particle!=null)created.add(particle);
        }
        void closeParticles(){particles.invalidateResources();}
    }
    @Override public void runTest(ClientGameTestContext context){
        try(var host=context.worldBuilder().create()){
            context.runOnClient(client->{
                var level=client.level;require(level!=null,"Actual joined native client world required");
                var block=level.getBlockState(P);var owner=level.getBlockEntity(P);var liveParticles=client.particleEngine.countParticles();
                var scene=new SceneWorld(level);
                try{
                    scene.setBlock(P,AllBlocks.MECHANICAL_PRESS.defaultBlockState(),0);
                    scene.setBlock(P.below(2),AllBlocks.DEPOT.defaultBlockState(),0);
                    var press=(MechanicalPressBlockEntity)scene.getBlockEntity(P);var depot=(DepotBlockEntity)scene.getBlockEntity(P.below(2));
                    require(press!=null&&press.getLevel()==scene&&press.isVirtual(),"Actual scene factory binds the new virtual owner before use");
                    try(var tx=Transaction.openOuter()){depot.inventory.automation.insert(ItemVariant.of(Items.IRON_INGOT),7,tx);tx.commit();}
                    press.setSpeed(64);var source=press.getPressingBehaviour();source.start(Mode.BELT);
                    MechanicalPressBlockEntity.tick(scene,P,press.getBlockState(),press);
                    require(source.runningTicks==source.getRunningTickSpeed()&&source==press.getBehaviour(BeltProcessingBehaviour.TYPE),"Actual native scene ticker consumes the one installed genuine source clock");
                    source.runningTicks=source.prevRunningTicks=120;
                    require(press.getHeadOffset(1)==19f/16f,"Actual native press head uses source BELT extent");
                    source.start(Mode.BASIN);source.runningTicks=source.prevRunningTicks=120;
                    require(press.getHeadOffset(1)==22f/16f,"Actual native press head uses source BASIN extent");
                    MechanicalPressBlockEntity.tick(scene,P,press.getBlockState(),press);
                    require(depot.getHeldItem().getCount()==7&&source.hasAppliedImpact(),"Actual virtual impact progresses while preserving the original local inventory");
                    var at=Vec3.atCenterOf(P.below(2));var copper=new ItemStack(Items.COPPER_INGOT);
                    source.makePressingParticleEffect(at,copper);source.makeCompactingParticleEffect(at,copper);
                    require(scene.created.size()==35,"Actual native particle providers materialize fifteen pressing and twenty compacting particles");
                    for(var particle:scene.created)require(((PonderParticleAccessor)particle).ponder$world() instanceof WrappedClientLevel wrapped&&wrapped.getWrappedLevel()==scene,"Every actual particle retains the actual scene world owner");
                    scene.tick();require(liveParticles.equals(client.particleEngine.countParticles()),"Scene particles never enter or tick the actual host particle engine");
                    scene.createBackup();source.runningTicks=5;scene.restore();
                    var restored=(MechanicalPressBlockEntity)scene.getBlockEntity(P);
                    require(restored!=press&&press.isRemoved()&&restored.isVirtual()&&restored.getPressingBehaviour().mode==Mode.BASIN&&restored.getPressingBehaviour().runningTicks>120,"Actual Ponder backup/factory restore preserves source mode/tick/impact state on a fresh virtual owner");
                    source.start(Mode.WORLD);require(restored.getPressingBehaviour().mode==Mode.BASIN,"Old source references cannot restart restored native scene owners");
                    require(level.getBlockState(P)==block&&level.getBlockEntity(P)==owner&&liveParticles.equals(client.particleEngine.countParticles()),"Native source animation/particles/replay preserve the host world and host engine");
                }finally{scene.closeParticles();}
            });
        }
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
