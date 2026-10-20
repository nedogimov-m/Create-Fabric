package com.simibubi.create.foundation.mixin;

import com.simibubi.create.content.equipment.toolbox.ToolboxBindings;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads actual pre-Fabric saved player bindings into persistent native data attachments once. */
@Mixin(Player.class)
abstract class ToolboxPlayerDataMixin {
    @Inject(method="readAdditionalSaveData",at=@At("TAIL"))
    private void create$toolboxBindings(ValueInput input,CallbackInfo callback){ToolboxBindings.migrate((Player)(Object)this,input);}
}
