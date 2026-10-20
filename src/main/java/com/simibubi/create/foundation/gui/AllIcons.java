package com.simibubi.create.foundation.gui;

import com.simibubi.create.Create;
import net.minecraft.resources.Identifier;

/** Pinned source icon atlas and coordinates, rendered through native GUI and model submissions. */
public class AllIcons {
    public static final Identifier ICON_ATLAS=Create.asResource("textures/gui/icons.png");
    public static final int ICON_ATLAS_SIZE=256;
    private static int x=0,y=-1;
    private final int iconX,iconY;
	public static final AllIcons
		I_ADD = newRow(),
		I_TRASH = next(),
		I_3x3 = next(),
		I_TARGET = next(),
		I_PRIORITY_VERY_LOW = next(),
		I_PRIORITY_LOW = next(),
		I_PRIORITY_HIGH = next(),
		I_PRIORITY_VERY_HIGH = next(),
		I_BLACKLIST = next(),
		I_WHITELIST = next(),
		I_WHITELIST_OR = next(),
		I_WHITELIST_AND = next(),
		I_WHITELIST_NOT = next(),
		I_RESPECT_NBT = next(),
		I_IGNORE_NBT = next();

	public static final AllIcons
		I_CONFIRM = newRow(),
		I_NONE = next(),
		I_OPEN_FOLDER = next(),
		I_REFRESH = next(),
		I_ACTIVE = next(),
		I_PASSIVE = next(),
		I_ROTATE_PLACE = next(),
		I_ROTATE_PLACE_RETURNED = next(),
		I_ROTATE_NEVER_PLACE = next(),
		I_MOVE_PLACE = next(),
		I_MOVE_PLACE_RETURNED = next(),
		I_MOVE_NEVER_PLACE = next(),
		I_CART_ROTATE = next(),
		I_CART_ROTATE_PAUSED = next(),
		I_CART_ROTATE_LOCKED = next();

	public static final AllIcons
		I_DONT_REPLACE = newRow(),
		I_REPLACE_SOLID = next(),
		I_REPLACE_ANY = next(),
		I_REPLACE_EMPTY = next(),
		I_CENTERED = next(),
		I_ATTACHED = next(),
		I_INSERTED = next(),
		I_FILL = next(),
		I_PLACE = next(),
		I_REPLACE = next(),
		I_CLEAR = next(),
		I_OVERLAY = next(),
		I_FLATTEN = next(),
		I_LMB = next(),
		I_SCROLL = next(),
		I_RMB = next();

	public static final AllIcons
		I_TOOL_DEPLOY = newRow(),
		I_SKIP_MISSING = next(),
		I_SKIP_BLOCK_ENTITIES = next(),
		I_DICE = next(),
		I_TUNNEL_SPLIT = next(),
		I_TUNNEL_FORCED_SPLIT = next(),
		I_TUNNEL_ROUND_ROBIN = next(),
		I_TUNNEL_FORCED_ROUND_ROBIN = next(),
		I_TUNNEL_PREFER_NEAREST = next(),
		I_TUNNEL_RANDOMIZE = next(),
		I_TUNNEL_SYNCHRONIZE = next(),
		I_TOOLBOX = next(),
		I_VIEW_SCHEDULE = next(),

		I_TOOL_MOVE_XZ = newRow(),
		I_TOOL_MOVE_Y = next(),
		I_TOOL_ROTATE = next(),
		I_TOOL_MIRROR = next(),
		I_ARM_ROUND_ROBIN = next(),
		I_ARM_FORCED_ROUND_ROBIN = next(),
		I_ARM_PREFER_FIRST = next(),

		I_ADD_INVERTED_ATTRIBUTE = next(),
		I_FLIP = next(),

		I_ROLLER_PAVE = next(),
		I_ROLLER_FILL = next(),
		I_ROLLER_WIDE_FILL = next(),

		I_PLAY = newRow(),
		I_PAUSE = next(),
		I_STOP = next(),
		I_PLACEMENT_SETTINGS = next(),
		I_ROTATE_CCW = next(),
		I_HOUR_HAND_FIRST = next(),
		I_MINUTE_HAND_FIRST = next(),
		I_HOUR_HAND_FIRST_24 = next(),

		I_PATTERN_SOLID = newRow(),
		I_PATTERN_CHECKERED = next(),
		I_PATTERN_CHECKERED_INVERSED = next(),
		I_PATTERN_CHANCE_25 = next(),

		I_PATTERN_CHANCE_50 = newRow(),
		I_PATTERN_CHANCE_75 = next(),
		I_FOLLOW_DIAGONAL = next(),
		I_FOLLOW_MATERIAL = next(),

		I_CLEAR_CHECKED = next(),

		I_SCHEMATIC = newRow(),
		I_SEQ_REPEAT = next(),
		VALUE_BOX_HOVER_6PX = next(),
		VALUE_BOX_HOVER_4PX = next(),
		VALUE_BOX_HOVER_8PX = next(),

		I_MTD_LEFT = newRow(),
		I_MTD_CLOSE = next(),
		I_MTD_RIGHT = next(),
		I_MTD_SCAN = next(),
		I_MTD_REPLAY = next(),
		I_MTD_USER_MODE = next(),
		I_MTD_SLOW_MODE = next(),

		I_CONFIG_UNLOCKED = newRow(),
		I_CONFIG_LOCKED = next(),
		I_CONFIG_DISCARD = next(),
		I_CONFIG_SAVE = next(),
		I_CONFIG_RESET = next(),
		I_CONFIG_BACK = next(),
		I_CONFIG_PREV = next(),
		I_CONFIG_NEXT = next(),
		I_DISABLE = next(),
		I_CONFIG_OPEN = next(),

		I_FX_SURFACE_OFF = newRow(),
		I_FX_SURFACE_ON = next(),
		I_FX_FIELD_OFF = next(),
		I_FX_FIELD_ON = next(),
		I_FX_BLEND = next(),
		I_FX_BLEND_OFF = next(),

		I_SEND_ONLY = newRow(),
		I_SEND_AND_RECEIVE = next(),
		I_PARTIAL_REQUESTS = next(),
		I_FULL_REQUESTS = next(),
		I_MOVE_GAUGE = next();
	;

    public AllIcons(int x,int y){iconX=x*16;iconY=y*16;}
    private static AllIcons next(){return new AllIcons(++x,y);}
    private static AllIcons newRow(){return new AllIcons(x=0,++y);}
    public int getIconX(){return iconX;}
    public int getIconY(){return iconY;}
}
