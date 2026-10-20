package com.simibubi.create.api.registry;

import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import net.fabricmc.fabric.api.event.registry.*;
import net.minecraft.core.Registry;

public final class CreateBuiltInRegistries {
    public static final Registry<com.simibubi.create.content.kinetics.fan.processing.FanProcessingType> FAN_PROCESSING_TYPE=FabricRegistryBuilder.create(CreateRegistries.FAN_PROCESSING_TYPE).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.simibubi.create.api.contraption.storage.item.MountedItemStorageType<?>> MOUNTED_ITEM_STORAGE_TYPE=FabricRegistryBuilder.create(CreateRegistries.MOUNTED_ITEM_STORAGE_TYPE).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType<?>> MOUNTED_FLUID_STORAGE_TYPE=FabricRegistryBuilder.create(CreateRegistries.MOUNTED_FLUID_STORAGE_TYPE).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.simibubi.create.api.contraption.ContraptionType> CONTRAPTION_TYPE=FabricRegistryBuilder.create(CreateRegistries.CONTRAPTION_TYPE).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.simibubi.create.content.logistics.packagePort.PackagePortTargetType> PACKAGE_PORT_TARGET_TYPE=FabricRegistryBuilder.create(CreateRegistries.PACKAGE_PORT_TARGET_TYPE).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<ItemAttributeType> ITEM_ATTRIBUTE_TYPE=FabricRegistryBuilder.create(CreateRegistries.ITEM_ATTRIBUTE_TYPE).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.simibubi.create.api.behaviour.display.DisplaySource> DISPLAY_SOURCE=FabricRegistryBuilder.create(CreateRegistries.DISPLAY_SOURCE).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.simibubi.create.api.behaviour.display.DisplayTarget> DISPLAY_TARGET=FabricRegistryBuilder.create(CreateRegistries.DISPLAY_TARGET).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.mojang.serialization.MapCodec<? extends com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileRenderMode>> POTATO_PROJECTILE_RENDER_MODE=FabricRegistryBuilder.create(CreateRegistries.POTATO_PROJECTILE_RENDER_MODE).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.mojang.serialization.MapCodec<? extends com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileEntityHitAction>> POTATO_PROJECTILE_ENTITY_HIT_ACTION=FabricRegistryBuilder.create(CreateRegistries.POTATO_PROJECTILE_ENTITY_HIT_ACTION).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.mojang.serialization.MapCodec<? extends com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileBlockHitAction>> POTATO_PROJECTILE_BLOCK_HIT_ACTION=FabricRegistryBuilder.create(CreateRegistries.POTATO_PROJECTILE_BLOCK_HIT_ACTION).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    public static final Registry<com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType> ARM_INTERACTION_POINT_TYPE=FabricRegistryBuilder.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE).attribute(RegistryAttribute.SYNCED).buildAndRegister();
    private CreateBuiltInRegistries(){}
}
