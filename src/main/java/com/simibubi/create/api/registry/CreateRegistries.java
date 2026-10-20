package com.simibubi.create.api.registry;

import com.simibubi.create.Create;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/** Public registry keys retain Create's namespace for Fabric addon registration. */
public final class CreateRegistries {
    public static final ResourceKey<Registry<com.simibubi.create.content.kinetics.fan.processing.FanProcessingType>> FAN_PROCESSING_TYPE=ResourceKey.createRegistryKey(Create.asResource("fan_processing_type"));
    public static final ResourceKey<Registry<com.simibubi.create.api.contraption.storage.item.MountedItemStorageType<?>>> MOUNTED_ITEM_STORAGE_TYPE=ResourceKey.createRegistryKey(Create.asResource("mounted_item_storage_type"));
    public static final ResourceKey<Registry<com.simibubi.create.api.contraption.storage.fluid.MountedFluidStorageType<?>>> MOUNTED_FLUID_STORAGE_TYPE=ResourceKey.createRegistryKey(Create.asResource("mounted_fluid_storage_type"));
    public static final ResourceKey<Registry<com.simibubi.create.api.contraption.ContraptionType>> CONTRAPTION_TYPE=ResourceKey.createRegistryKey(Create.asResource("contraption_type"));
    public static final ResourceKey<Registry<com.simibubi.create.content.logistics.packagePort.PackagePortTargetType>> PACKAGE_PORT_TARGET_TYPE=ResourceKey.createRegistryKey(Create.asResource("package_port_target_type"));
    public static final ResourceKey<Registry<ItemAttributeType>> ITEM_ATTRIBUTE_TYPE=ResourceKey.createRegistryKey(Create.asResource("item_attribute_type"));
    public static final ResourceKey<Registry<com.simibubi.create.api.behaviour.display.DisplaySource>> DISPLAY_SOURCE=ResourceKey.createRegistryKey(Create.asResource("display_source"));
    public static final ResourceKey<Registry<com.simibubi.create.api.behaviour.display.DisplayTarget>> DISPLAY_TARGET=ResourceKey.createRegistryKey(Create.asResource("display_target"));
    public static final ResourceKey<Registry<com.simibubi.create.api.equipment.potatoCannon.PotatoCannonProjectileType>> POTATO_PROJECTILE_TYPE=ResourceKey.createRegistryKey(Create.asResource("potato_projectile/type"));
    public static final ResourceKey<Registry<com.mojang.serialization.MapCodec<? extends com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileRenderMode>>> POTATO_PROJECTILE_RENDER_MODE=ResourceKey.createRegistryKey(Create.asResource("potato_projectile/render_mode"));
    public static final ResourceKey<Registry<com.mojang.serialization.MapCodec<? extends com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileEntityHitAction>>> POTATO_PROJECTILE_ENTITY_HIT_ACTION=ResourceKey.createRegistryKey(Create.asResource("potato_projectile/entity_hit_action"));
    public static final ResourceKey<Registry<com.mojang.serialization.MapCodec<? extends com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileBlockHitAction>>> POTATO_PROJECTILE_BLOCK_HIT_ACTION=ResourceKey.createRegistryKey(Create.asResource("potato_projectile/block_hit_action"));
    public static final ResourceKey<Registry<com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType>> ARM_INTERACTION_POINT_TYPE=ResourceKey.createRegistryKey(Create.asResource("arm_interaction_point_type"));
    private CreateRegistries(){}
}
