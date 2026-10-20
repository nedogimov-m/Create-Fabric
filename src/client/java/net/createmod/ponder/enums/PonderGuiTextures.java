// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.enums;

import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.Ponder;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public enum PonderGuiTextures implements ScreenElement {

	//logo
	LOGO("logo", 0, 0, 32, 32, 32, 32),

	//widgets
	SPEECH_TOOLTIP_BACKGROUND("widgets", 0, 24, 8, 8),
	SPEECH_TOOLTIP_COLOR("widgets", 8, 24, 8, 8),

	//icons
	ICON_PONDER_LEFT("widgets", 0, 2),
	ICON_PONDER_CLOSE("widgets", 1, 2),
	ICON_PONDER_RIGHT("widgets", 2, 2),
	ICON_PONDER_IDENTIFY("widgets", 3, 2),
	ICON_PONDER_REPLAY("widgets", 4, 2),
	ICON_PONDER_USER_MODE("widgets", 5, 2),
	ICON_PONDER_SLOW_MODE("widgets", 6, 2),

	ICON_CONFIG_UNLOCKED("widgets", 0, 3),
	ICON_CONFIG_LOCKED("widgets", 1, 3),
	ICON_CONFIG_DISCARD("widgets", 2, 3),
	ICON_CONFIG_SAVE("widgets", 3, 3),
	ICON_CONFIG_RESET("widgets", 4, 3),
	ICON_CONFIG_BACK("widgets", 5, 3),
	ICON_CONFIG_PREV("widgets", 6, 3),
	ICON_CONFIG_NEXT("widgets", 7, 3),
	ICON_DISABLE("widgets", 8, 3),
	ICON_CONFIG_OPEN("widgets", 9, 3),
	ICON_CONFIRM("widgets", 10, 3),

	ICON_LMB("widgets", 0, 4),
	ICON_SCROLL("widgets", 1, 4),
	ICON_RMB("widgets", 2, 4),

	// PlacementIndicator
	PLACEMENT_INDICATOR_SHEET("placement_indicator", 0, 0, 16, 256),

	;

	public final Identifier location;
	private final int width;
	private final int height;
	private final int startX;
	private final int startY;
	private final int sheetWidth;
	private final int sheetHeight;

	PonderGuiTextures(String location, int iconColumn, int iconRow) {
		this(location, iconColumn * 16, iconRow * 16, 16, 16);
	}

	PonderGuiTextures(String location, int startX, int startY, int width, int height) {
		this(Ponder.MOD_ID, location, startX, startY, width, height, 256, 256);
	}

	PonderGuiTextures(String location, int startX, int startY, int width, int height, int sheetWidth, int sheetHeight) {
		this(Ponder.MOD_ID, location, startX, startY, width, height, sheetWidth, sheetHeight);
	}

	PonderGuiTextures(String namespace, String location, int startX, int startY, int width, int height, int sheetWidth, int sheetHeight) {
		this.location = Identifier.fromNamespaceAndPath(namespace, "textures/gui/" + location + ".png");
		this.width = width;
		this.height = height;
		this.startX = startX;
		this.startY = startY;
		this.sheetWidth = sheetWidth;
		this.sheetHeight = sheetHeight;
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, int x, int y) {
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,getLocation(),x,y,startX,startY,width,height,sheetWidth,sheetHeight,0xFFFFFFFF);
	}

	public void render(GuiGraphicsExtractor graphics, int x, int y, Color c) {
		graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,getLocation(),x,y,startX,startY,width,height,sheetWidth,sheetHeight,c.getRGB());
	}

	public Identifier getLocation() {
		return location;
	}

	public int getStartX() {
		return startX;
	}

	public int getStartY() {
		return startY;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

}
