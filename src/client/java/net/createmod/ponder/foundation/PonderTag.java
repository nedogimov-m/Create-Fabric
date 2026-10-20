// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation;

import org.jetbrains.annotations.Nullable;

import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.ponder.Ponder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class PonderTag implements ScreenElement {

	/**
	 * Highlight.ALL is a special PonderTag, used to indicate that all Tags
	 * for a certain Scene should be highlighted instead of selected single ones
	 */
	public static final class Highlight {
		public static final Identifier ALL = Ponder.asResource("_all");
	}

	private final Identifier id;
	@Nullable
	private final Identifier textureIconLocation;
	private final ItemStack itemIcon;
	private final ItemStack mainItem;

	public PonderTag(Identifier id, @Nullable Identifier textureIconLocation, ItemStack itemIcon,
					 ItemStack mainItem) {
		this.id = id;
		this.textureIconLocation = textureIconLocation;
		this.itemIcon = itemIcon;
		this.mainItem = mainItem;
	}

	public Identifier getId() {
		return id;
	}

	public ItemStack getMainItem() {
		return mainItem;
	}

	public String getTitle() {
		return PonderIndex.getLangAccess().getTagName(id);
	}

	public String getDescription() {
		return PonderIndex.getLangAccess().getTagDescription(id);
	}

	@Override
	public boolean equals(Object other) {
		if (this == other)
			return true;

		if (!(other instanceof PonderTag otherTag))
			return false;

		return getId().equals(otherTag.getId());
	}

  @Override public void render(net.minecraft.client.gui.GuiGraphicsExtractor gui,int x,int y) {
    if(textureIconLocation!=null) { gui.pose().pushMatrix(); gui.pose().translate(x,y); gui.pose().scale(.25f);
      gui.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,textureIconLocation,0,0,0,0,64,64,64,64,0xFFFFFFFF); gui.pose().popMatrix(); }
    else if(!itemIcon.isEmpty()) { gui.pose().pushMatrix(); gui.pose().translate(x-2,y-2); gui.pose().scale(1.25f); gui.item(itemIcon,0,0); gui.pose().popMatrix(); }
  }
}
