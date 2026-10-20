package com.simibubi.create;

import com.simibubi.create.content.logistics.box.PackageEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.*;

public final class AllEntityTypes {
  private static final ResourceKey<EntityType<?>> BLUEPRINT_KEY=ResourceKey.create(Registries.ENTITY_TYPE,Create.asResource("crafting_blueprint"));
  public static final EntityType<com.simibubi.create.content.equipment.blueprint.BlueprintEntity> CRAFTING_BLUEPRINT=Registry.register(BuiltInRegistries.ENTITY_TYPE,BLUEPRINT_KEY,EntityType.Builder.<com.simibubi.create.content.equipment.blueprint.BlueprintEntity>of(com.simibubi.create.content.equipment.blueprint.BlueprintEntity::new,MobCategory.MISC).fireImmune().dontTrackDeltas().clientTrackingRange(10).updateInterval(Integer.MAX_VALUE).noLootTable().build(BLUEPRINT_KEY));
  private static final ResourceKey<EntityType<?>> POTATO_KEY = ResourceKey.create(Registries.ENTITY_TYPE,Create.asResource("potato_projectile"));
  public static final EntityType<com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity> POTATO_PROJECTILE = Registry.register(BuiltInRegistries.ENTITY_TYPE,POTATO_KEY,EntityType.Builder.<com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity>of(com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity::new,MobCategory.MISC).sized(.25f,.25f).clientTrackingRange(4).updateInterval(20).noLootTable().build(POTATO_KEY));

  private static final ResourceKey<EntityType<?>> CARRIAGE_KEY =
      ResourceKey.create(Registries.ENTITY_TYPE, Create.asResource("carriage_contraption"));
  public static final EntityType<
          com.simibubi.create.content.trains.entity.CarriageContraptionEntity>
      CARRIAGE_CONTRAPTION =
          Registry.register(
              BuiltInRegistries.ENTITY_TYPE,
              CARRIAGE_KEY,
              EntityType.Builder
                  .<com.simibubi.create.content.trains.entity.CarriageContraptionEntity>of(
                      com.simibubi.create.content.trains.entity.CarriageContraptionEntity::new,
                      MobCategory.MISC)
                  .sized(1, 1)
                  .fireImmune()
                  .clientTrackingRange(15)
                  .updateInterval(3)
                  .noLootTable()
                  .build(CARRIAGE_KEY));
  private static final ResourceKey<EntityType<?>> GANTRY_KEY =
      ResourceKey.create(Registries.ENTITY_TYPE, Create.asResource("gantry_contraption"));
  public static final EntityType<
          com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity>
      GANTRY_CONTRAPTION =
          Registry.register(
              BuiltInRegistries.ENTITY_TYPE,
              GANTRY_KEY,
              EntityType.Builder
                  .<com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity>of(
                      com.simibubi.create.content.contraptions.gantry.GantryContraptionEntity::new,
                      MobCategory.MISC)
                  .sized(1, 1)
                  .fireImmune()
                  .dontTrackDeltas()
                  .clientTrackingRange(10)
                  .updateInterval(40)
                  .noLootTable()
                  .build(GANTRY_KEY));

  private static final ResourceKey<EntityType<?>> ORIENTED_KEY =
      ResourceKey.create(Registries.ENTITY_TYPE, Create.asResource("oriented_contraption"));
  public static final EntityType<com.simibubi.create.content.contraptions.OrientedContraptionEntity>
      ORIENTED_CONTRAPTION =
          Registry.register(
              BuiltInRegistries.ENTITY_TYPE,
              ORIENTED_KEY,
              EntityType.Builder
                  .<com.simibubi.create.content.contraptions.OrientedContraptionEntity>of(
                      com.simibubi.create.content.contraptions.OrientedContraptionEntity::new,
                      MobCategory.MISC)
                  .sized(1, 1)
                  .fireImmune()
                  .dontTrackDeltas()
                  .clientTrackingRange(16)
                  .updateInterval(1)
                  .noLootTable()
                  .build(ORIENTED_KEY));
  private static final ResourceKey<EntityType<?>> LINEAR_KEY =
      ResourceKey.create(Registries.ENTITY_TYPE, Create.asResource("linear_contraption"));
  public static final EntityType<com.simibubi.create.content.contraptions.LinearContraptionEntity>
      LINEAR_CONTRAPTION =
          Registry.register(
              BuiltInRegistries.ENTITY_TYPE,
              LINEAR_KEY,
              EntityType.Builder
                  .<com.simibubi.create.content.contraptions.LinearContraptionEntity>of(
                      com.simibubi.create.content.contraptions.LinearContraptionEntity::new,
                      MobCategory.MISC)
                  .sized(1, 1)
                  .fireImmune()
                  .clientTrackingRange(16)
                  .updateInterval(1)
                  .noLootTable()
                  .build(LINEAR_KEY));
  private static final ResourceKey<EntityType<?>> CONTROLLED_KEY =
      ResourceKey.create(Registries.ENTITY_TYPE, Create.asResource("controlled_contraption"));
  public static final EntityType<
          com.simibubi.create.content.contraptions.ControlledContraptionEntity>
      CONTROLLED_CONTRAPTION =
          Registry.register(
              BuiltInRegistries.ENTITY_TYPE,
              CONTROLLED_KEY,
              EntityType.Builder
                  .<com.simibubi.create.content.contraptions.ControlledContraptionEntity>of(
                      com.simibubi.create.content.contraptions.ControlledContraptionEntity::new,
                      MobCategory.MISC)
                  .sized(1, 1)
                  .fireImmune()
                  .dontTrackDeltas()
                  .clientTrackingRange(16)
                  .updateInterval(1)
                  .noLootTable()
                  .build(CONTROLLED_KEY));
  private static final ResourceKey<EntityType<?>> GLUE_KEY =
      ResourceKey.create(Registries.ENTITY_TYPE, Create.asResource("super_glue"));
  public static final EntityType<com.simibubi.create.content.contraptions.glue.SuperGlueEntity>
      SUPER_GLUE =
          Registry.register(
              BuiltInRegistries.ENTITY_TYPE,
              GLUE_KEY,
              EntityType.Builder.<com.simibubi.create.content.contraptions.glue.SuperGlueEntity>of(
                      com.simibubi.create.content.contraptions.glue.SuperGlueEntity::new,
                      MobCategory.MISC)
                  .sized(0, 0)
                  .fireImmune()
                  .clientTrackingRange(10)
                  .updateInterval(Integer.MAX_VALUE)
                  .noLootTable()
                  .build(GLUE_KEY));
  private static final ResourceKey<EntityType<?>> SEAT_KEY =
      ResourceKey.create(Registries.ENTITY_TYPE, Create.asResource("seat"));
  public static final EntityType<com.simibubi.create.content.contraptions.actors.seat.SeatEntity>
      SEAT =
          Registry.register(
              BuiltInRegistries.ENTITY_TYPE,
              SEAT_KEY,
              EntityType.Builder
                  .<com.simibubi.create.content.contraptions.actors.seat.SeatEntity>of(
                      com.simibubi.create.content.contraptions.actors.seat.SeatEntity::new,
                      MobCategory.MISC)
                  .sized(.25f, .35f)
                  .fireImmune()
                  .dontTrackDeltas()
                  .clientTrackingRange(5)
                  .updateInterval(Integer.MAX_VALUE)
                  .noLootTable()
                  .build(SEAT_KEY));
  private static final ResourceKey<EntityType<?>> PACKAGE_KEY =
      ResourceKey.create(Registries.ENTITY_TYPE, Create.asResource("package"));
  public static final EntityType<PackageEntity> PACKAGE =
      Registry.register(
          BuiltInRegistries.ENTITY_TYPE,
          PACKAGE_KEY,
          EntityType.Builder.<PackageEntity>of(PackageEntity::new, MobCategory.MISC)
              .sized(1, 1)
              .clientTrackingRange(10)
              .updateInterval(3)
              .noLootTable()
              .build(PACKAGE_KEY));

  public static void register() {
    FabricDefaultAttributeRegistry.register(PACKAGE, PackageEntity.createPackageAttributes());
  }

  private AllEntityTypes() {}
}
