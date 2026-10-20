package com.simibubi.create;

import com.simibubi.create.content.contraptions.render.ActorRenderer;
import com.simibubi.create.content.kinetics.base.CuttingToolActorRenderer;

public final class AllMovementRenderers {
    public static void register(){ActorRenderer.REGISTRY.register(AllBlocks.MECHANICAL_BEARING,new com.simibubi.create.content.contraptions.bearing.StabilizedBearingActorRenderer());com.simibubi.create.content.processing.burner.BlazeBurnerActorRenderer.registerTick();ActorRenderer.REGISTRY.register(AllBlocks.BLAZE_BURNER,new com.simibubi.create.content.processing.burner.BlazeBurnerActorRenderer());ActorRenderer.REGISTRY.register(AllBlocks.MECHANICAL_ROLLER,new com.simibubi.create.content.contraptions.actors.roller.RollerActorRenderer());var interfaces=new com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceActorRenderer();ActorRenderer.REGISTRY.register(AllBlocks.PORTABLE_STORAGE_INTERFACE,interfaces);ActorRenderer.REGISTRY.register(AllBlocks.PORTABLE_FLUID_INTERFACE,interfaces);ActorRenderer.REGISTRY.register(AllBlocks.MECHANICAL_HARVESTER,new com.simibubi.create.content.contraptions.actors.harvester.HarvesterActorRenderer());var tools=new CuttingToolActorRenderer();ActorRenderer.REGISTRY.register(AllBlocks.MECHANICAL_DRILL,tools);ActorRenderer.REGISTRY.register(AllBlocks.MECHANICAL_SAW,tools);ActorRenderer.REGISTRY.register(AllBlocks.DEPLOYER,new com.simibubi.create.content.kinetics.deployer.DeployerActorRenderer());}
    private AllMovementRenderers(){}
}
