package net.createmod.ponder.test.mixin;

import java.util.List;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the actual ordered source scene owners; no setters or authored scene programs. */
@Mixin(PonderUI.class)
public interface PonderNavigationAccessor {
    @Accessor("scenes") List<PonderScene> create$actualScenes();
}
