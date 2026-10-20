package com.simibubi.create.content.schematics.client.tools;

/** Source placement-tool defaults leave unrelated native item input available. */
public abstract class PlacementToolBase extends SchematicToolBase {
    @Override public boolean handleRightClick(){return false;}
    @Override public boolean handleMouseWheel(double delta){return false;}
}
