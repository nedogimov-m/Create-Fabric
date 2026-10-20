package net.createmod.ponder.mixin.client;
import net.minecraft.world.entity.animal.parrot.Parrot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(Parrot.class)
public interface PonderParrotAccessor {
  @Invoker("setVariant") void ponder$setVariant(Parrot.Variant variant);
}
