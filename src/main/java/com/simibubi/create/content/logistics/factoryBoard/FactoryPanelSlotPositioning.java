package com.simibubi.create.content.logistics.factoryBoard;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class FactoryPanelSlotPositioning {
    public static Vec3 getCenterOfSlot(BlockState state,FactoryPanelBlock.PanelSlot slot) { return FactoryPanelTransform.point(state,new Vec3(.25+slot.xOffset*.5,1.5/16d,.25+slot.yOffset*.5)); }
    private FactoryPanelSlotPositioning() {}
}
