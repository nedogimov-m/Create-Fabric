package com.simibubi.create.foundation.block.connected;

import net.minecraft.resources.Identifier;

/** Native material descriptor; sprites are resolved by the actual model baker on each resource reload. */
public record CTSpriteShiftEntry(Identifier original,Identifier connected,Mode mode) {
    public enum Mode { OMNIDIRECTIONAL, HORIZONTAL, VERTICAL }
    public int sheetSize() { return mode == Mode.OMNIDIRECTIONAL ? 8 : 2; }
    public int textureIndex(OmnidirectionalCT.Context context) {
        return switch (mode) {
            case OMNIDIRECTIONAL -> context.textureIndex();
            case HORIZONTAL -> (context.right()?1:0)+(context.left()?2:0);
            case VERTICAL -> (context.up()?1:0)+(context.down()?2:0);
        };
    }
}
