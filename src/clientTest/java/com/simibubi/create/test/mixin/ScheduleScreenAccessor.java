package com.simibubi.create.test.mixin;

import com.simibubi.create.content.trains.schedule.*;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.gui.widget.*;
import java.util.List;
import net.createmod.catnip.animation.LerpedFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only genuine editor/model/geometry probe. Every mutation in the scene is native physical input. */
@Mixin(ScheduleScreen.class)
public interface ScheduleScreenAccessor {
    @Accessor("schedule") Schedule create$getSchedule();
    @Accessor("scroll") LerpedFloat create$getScroll();
    @Accessor("editingDestination") ScheduleInstruction create$getEditingInstruction();
    @Accessor("editingCondition") ScheduleWaitCondition create$getEditingCondition();
    @Accessor("scrollInput") SelectionScrollInput create$getTypeSelector();
    @Accessor("editorConfirm") IconButton create$getEditorConfirm();
    @Accessor("confirmButton") IconButton create$getConfirm();
    @Accessor("cyclicButton") IconButton create$getCyclic();
    @Invoker("getConditionColumnWidth") int create$getConditionColumnWidth(List<ScheduleWaitCondition> conditions);
    @Invoker("getConditionScroll") float create$getConditionScroll(ScheduleEntry entry,float partial,int index);
}
