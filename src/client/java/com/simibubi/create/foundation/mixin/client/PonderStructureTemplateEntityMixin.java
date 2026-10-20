package com.simibubi.create.foundation.mixin.client;

import java.util.Optional;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.TagValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native structure entities are constructed in the actual client schematic, never a live server. */
@Mixin(StructureTemplate.class)
public abstract class PonderStructureTemplateEntityMixin {
  @Inject(
      method = "createEntityIgnoreException(Lnet/minecraft/util/ProblemReporter;Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;",
      at = @At("HEAD"),
      cancellable = true)
  private static void create$ownSchematicEntity(
      ProblemReporter reporter,
      ServerLevelAccessor accessor,
      CompoundTag tag,
      CallbackInfoReturnable<Optional<Entity>> result) {
    if (!(accessor instanceof SchematicLevel world)) return;
    try {
      result.setReturnValue(
          EntityType.create(
              TagValueInput.create(reporter, world.registryAccess(), tag),
              world,
              new EntitySpawnRequest(EntitySpawnReason.STRUCTURE, false)));
    } catch (Exception malformed) {
      // Same native malformed-entity behavior as createEntityIgnoreException.
      result.setReturnValue(Optional.empty());
    }
  }
}
