package com.simibubi.create.content.contraptions.bearing;

import com.simibubi.create.content.contraptions.IControlContraption;

/** Shared native renderer/controller contract; configuration UI is registered independently. */
public interface IBearingBlockEntity extends IControlContraption {
    float getInterpolatedAngle(float partial);
    boolean isWoodenTop();
    void setAngle(float angle);
}
