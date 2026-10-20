package net.createmod.ponder.test.mixin;

import java.util.List;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = PonderScene.class, remap = false)
public interface PonderSceneScheduleAuditAccessor {
    @Accessor("activeSchedule") List<PonderInstruction> create$actualActiveSchedule();
}
