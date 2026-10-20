package com.simibubi.create.content.contraptions;

import net.minecraft.core.BlockPos;

public interface IControlContraption {
    boolean isAttachedTo(AbstractContraptionEntity contraption);
    void attach(ControlledContraptionEntity contraption);
    void onStall();
    boolean isValid();
    BlockPos getBlockPosition();
    enum RotationMode { ROTATE_PLACE, ROTATE_PLACE_RETURNED, ROTATE_NEVER_PLACE }
    enum MovementMode { MOVE_PLACE, MOVE_PLACE_RETURNED, MOVE_NEVER_PLACE }
}
