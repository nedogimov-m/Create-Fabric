package com.simibubi.create.foundation.mixin.client;

import com.simibubi.create.content.equipment.*;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import org.spongepowered.asm.mixin.*;

@Mixin(FirstPersonHandsAndItemsRenderState.class)
public abstract class EquipmentHandsStateMixin implements EquipmentHandsAccess {
    @Unique private final EquipmentHands.State create$equipment=new EquipmentHands.State();
    @Override public EquipmentHands.State create$equipmentHands(){return create$equipment;}
}
