package com.simibubi.create.test;

import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import java.util.function.Consumer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** A genuine registered zero-inventory actor and narrowly scoped real lifecycle fault observers. */
public final class AssemblyBindingTestFixtures implements ModInitializer {
    public static Block ACTOR;
    public enum Boundary { ACTOR_START, BOUNDS }
    public static final class ScopedFault extends Error {}
    private record Hook(Boundary boundary,Consumer<AbstractContraptionEntity> observe,ScopedFault fault){}
    private static final ThreadLocal<Hook> ACTIVE=new ThreadLocal<>();
    public static AutoCloseable fault(Boundary boundary,Consumer<AbstractContraptionEntity> observe,ScopedFault exact){
        var previous=ACTIVE.get();ACTIVE.set(new Hook(boundary,observe,exact));return ()->{if(previous==null)ACTIVE.remove();else ACTIVE.set(previous);};
    }
    @Override public void onInitialize(){
        var key=ResourceKey.create(Registries.BLOCK,Identifier.fromNamespaceAndPath("create_test","assembly_binding_fault_actor"));
        ACTOR=Registry.register(BuiltInRegistries.BLOCK,key,new Block(BlockBehaviour.Properties.of().setId(key)));
        MovementBehaviour.REGISTRY.register(ACTOR,new MovementBehaviour(){
            @Override public void startMoving(MovementContext context){
                if(context.contraption.phase()==Contraption.Phase.REMOVED)context.data.putInt("ActualRemovedStartCalls",context.data.getIntOr("ActualRemovedStartCalls",0)+1);
                var hook=ACTIVE.get();if(hook==null||hook.boundary!=Boundary.ACTOR_START)return;
                if(context.contraption.phase()==Contraption.Phase.CAPTURED&&context.contraption.entity==null){
                    // Real Bearing/Pulley capture starts actors early. This registered extension
                    // deliberately leaves only its own zero-inventory context lazily uninitialized.
                    // The ordinary public binding path later creates it and calls this real method.
                    // No context or paid inventory is reconstructed by the test after failure.
                    for(var actor:context.contraption.getActors())if(actor.right==context){actor.setRight(null);return;}
                }
                var owner=context.contraption.entity;
                if(owner instanceof ControlledContraptionEntity&&owner.hasOwnedStructure()){
                    context.data.putBoolean("ActualOwnedStartObserved",true);
                    fire(hook,owner);
                }
            }
        });
    }
    public static void actualBounds(AbstractContraptionEntity owner){
        var hook=ACTIVE.get();if(hook!=null&&hook.boundary==Boundary.BOUNDS&&owner instanceof ControlledContraptionEntity&&owner.hasOwnedStructure())fire(hook,owner);
    }
    private static void fire(Hook hook,AbstractContraptionEntity owner){
        ACTIVE.remove();hook.observe.accept(owner);throw hook.fault;
    }
}
