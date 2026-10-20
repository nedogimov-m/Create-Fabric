package com.simibubi.create.content.schematics.client.tools;

import com.simibubi.create.foundation.gui.AllIcons;
import java.util.*;
import net.minecraft.network.chat.*;

/** The genuine public source tool enum owns concrete, stateful tool implementations. */
public enum ToolType {
    DEPLOY(new DeployTool(),AllIcons.I_TOOL_DEPLOY),MOVE(new MoveTool(),AllIcons.I_TOOL_MOVE_XZ),
    MOVE_Y(new MoveVerticalTool(),AllIcons.I_TOOL_MOVE_Y),ROTATE(new RotateTool(),AllIcons.I_TOOL_ROTATE),
    FLIP(new FlipTool(),AllIcons.I_TOOL_MIRROR),PRINT(new PlaceTool(),AllIcons.I_CONFIRM);
    private final ISchematicTool tool;private final AllIcons icon;
    ToolType(ISchematicTool tool,AllIcons icon){this.tool=tool;this.icon=icon;}
    public ISchematicTool getTool(){return tool;}
    public MutableComponent getDisplayName(){return Component.translatable("create.schematic.tool."+name().toLowerCase(Locale.ROOT));}
    public AllIcons getIcon(){return icon;}
    public static List<ToolType> getTools(boolean creative){var result=new ArrayList<ToolType>(List.of(MOVE,MOVE_Y,DEPLOY,ROTATE,FLIP));if(creative)result.add(PRINT);return result;}
    public List<Component> getDescription(){return java.util.stream.IntStream.range(0,4).mapToObj(i->(Component)Component.translatable("create.schematic.tool."+name().toLowerCase(Locale.ROOT)+".description."+i)).toList();}
}
