package com.simibubi.create.test.mixin;

import com.simibubi.create.content.logistics.packagePort.postbox.PostboxPlacement;
import java.util.Map;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read the real server selection acknowledgement; tests never change the selection map. */
@Mixin(value = PostboxPlacement.class, remap = false)
public interface PostboxSelectionAccessor {
  @Accessor("SELECTED")
  static Map<Player, GlobalPos> create$getSelected() {
    throw new AssertionError();
  }
}
