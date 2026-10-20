package com.simibubi.create.test;

import com.mojang.serialization.Lifecycle;
import com.simibubi.create.api.registrate.CreateRegistrateRegistrationCallback;
import com.simibubi.create.api.registry.SimpleRegistry;
import com.simibubi.create.api.registry.registrate.SimpleBuilder;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Actual Fabric test-mod bootstrap: native custom registry and genuine native entry publication. UNRUN. */
public final class NativeRegistrationFixtures implements ModInitializer {
    static final ResourceKey<Registry<SoundEvent>> KEY = ResourceKey.createRegistryKey(id("registration_callbacks"));
    static final Identifier EARLY_ID=id("before_registry"), LATER_ID=id("after_registry"), BUILDER_ID=id("native_builder"), TAG_ID=id("native_tag_builder");
    static Registry<SoundEvent> registry;
    static SoundEvent early, later, built, tagBuilt;
    static SimpleBuilder<SoundEvent,SoundEvent,String> builder;
    static final AtomicInteger beforeOwner=new AtomicInteger(),beforeEntry=new AtomicInteger(),afterEntry=new AtomicInteger(),directOwner=new AtomicInteger();
    static final AtomicInteger factoryCalls=new AtomicInteger(),publishedCallbacks=new AtomicInteger(),lateCallbacks=new AtomicInteger(),reentrantBuilderRejections=new AtomicInteger();
    static final AtomicInteger missingDefaultedCallback=new AtomicInteger(),duplicateBuilderRejections=new AtomicInteger(),duplicateOwnerRejections=new AtomicInteger(),unsupportedAssociations=new AtomicInteger(),unpublishedGetRejections=new AtomicInteger();
    static final AtomicReference<SoundEvent> observedEarly=new AtomicReference<>();
    static final SimpleRegistry.Multi<Block,SoundEvent> BY_BLOCK=SimpleRegistry.Multi.create();
    static final SimpleRegistry<EntityType<?>,SoundEvent> BY_ENTITY=SimpleRegistry.create();
    static final SimpleRegistry<BlockEntityType<?>,SoundEvent> BY_BE=SimpleRegistry.create();
    static final SimpleRegistry<Fluid,SoundEvent> BY_FLUID=SimpleRegistry.create();
    static final SimpleRegistry<Block,SoundEvent> BY_TAG=SimpleRegistry.create();
    static final SimpleRegistry<Block,SoundEvent> DIRECT_ASSOCIATION=SimpleRegistry.create();
    static String actualParent;
    static boolean bootstrapCompleted;
    @Override public void onInitialize() {
        CreateRegistrateRegistrationCallback.<SoundEvent,SoundEvent>register(KEY,EARLY_ID,value->{beforeOwner.incrementAndGet();observedEarly.set(value);});
        registry=FabricRegistryBuilder.create(KEY).buildAndRegister();
        early=SoundEvent.createVariableRangeEvent(EARLY_ID);Registry.register(registry,EARLY_ID,early);
        CreateRegistrateRegistrationCallback.<SoundEvent,SoundEvent>register(KEY,LATER_ID,value->{require(value==registry.getValue(LATER_ID),"Entry callback preceded actual binding");beforeEntry.incrementAndGet();});
        later=SoundEvent.createVariableRangeEvent(LATER_ID);Registry.register(registry,LATER_ID,later);
        CreateRegistrateRegistrationCallback.<SoundEvent,SoundEvent>register(KEY,EARLY_ID,value->{require(value==early,"Existing callback got another value");afterEntry.incrementAndGet();});
        CreateRegistrateRegistrationCallback.<SoundEvent,SoundEvent>register(registry,EARLY_ID,value->{DIRECT_ASSOCIATION.register(Blocks.STONE,value);directOwner.incrementAndGet();});
        CreateRegistrateRegistrationCallback.provideRegistry(registry);
        // The same ID in another real registry must not publish a BLOCK callback or fallback AIR.
        CreateRegistrateRegistrationCallback.<Block,Block>register(Registries.BLOCK,EARLY_ID,value->missingDefaultedCallback.incrementAndGet());
        builder=new SimpleBuilder<>(registry,"source-parent",BUILDER_ID,()->{
            factoryCalls.incrementAndGet();
            require(!registry.containsKey(BUILDER_ID)&&BY_BLOCK.get(Blocks.COBBLESTONE).isEmpty(),"Builder factory published associations before native registration");
            return SoundEvent.createVariableRangeEvent(BUILDER_ID);
        });
        builder.byBlock(BY_BLOCK).associate(Blocks.COBBLESTONE).associate(Blocks.COBBLESTONE)
            .byEntity(BY_ENTITY).associate(EntityTypes.PIG).byBlockEntity(BY_BE).associate(BlockEntityTypes.BARREL)
            .byFluid(BY_FLUID).associate(Fluids.WATER).onRegister(value->{
                require(registry.getValue(BUILDER_ID)==value&&builder.get()==value,"Builder callback did not witness its genuine native entry");
                require(BY_BLOCK.get(Blocks.COBBLESTONE).size()==2,"Source associations did not publish before the later source callback");
                try{builder.register();throw new AssertionError("Native publication callback reentered builder registration");}catch(IllegalStateException expected){reentrantBuilderRejections.incrementAndGet();}
                publishedCallbacks.incrementAndGet();
            });
        try{builder.get();throw new AssertionError("Unpublished native builder returned a value");}catch(IllegalStateException expected){unpublishedGetRejections.incrementAndGet();}
        built=builder.register();
        builder.onRegister(value->{require(value==built&&value==registry.getValue(BUILDER_ID),"Late callback lost genuine native value");lateCallbacks.incrementAndGet();});
        try{builder.register();throw new AssertionError("Native builder constructed twice");}catch(IllegalStateException expected){duplicateBuilderRejections.incrementAndGet();}
        try{CreateRegistrateRegistrationCallback.provideRegistry(new MappedRegistry<>(KEY,Lifecycle.stable()));throw new AssertionError("Conflicting native owner was accepted");}catch(IllegalArgumentException expected){duplicateOwnerRejections.incrementAndGet();}
        var tagBuilder=SimpleBuilder.create(registry,TAG_ID,()->SoundEvent.createVariableRangeEvent(TAG_ID));
        tagBuilt=tagBuilder.byBlock(BY_TAG).associateBlockTag(BlockTags.MINEABLE_WITH_PICKAXE).register();
        var unsupported=SimpleBuilder.create(registry,id("unsupported_association"),()->SoundEvent.createVariableRangeEvent(id("unsupported_association")));
        try{unsupported.associate(Blocks.DIRT);throw new AssertionError("Unsupported source association accepted");}catch(IllegalStateException expected){unsupportedAssociations.incrementAndGet();}
        actualParent=new SimpleBuilder<>(registry,"actual-parent",id("native_parent_builder"),()->SoundEvent.createVariableRangeEvent(id("native_parent_builder"))).build();
        require(BuiltInRegistries.REGISTRY.getValue(KEY.identifier())==registry,"Fixture did not register the actual native owner");
        bootstrapCompleted=true;
    }
    static Identifier id(String path){return Identifier.fromNamespaceAndPath("create_port_test",path);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
