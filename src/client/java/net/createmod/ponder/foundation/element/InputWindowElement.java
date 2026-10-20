// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.element;

import org.jetbrains.annotations.Nullable;

import net.createmod.catnip.gui.element.ScreenElement;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.Ponder;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.InputElementBuilder;
import net.createmod.ponder.enums.PonderGuiTextures;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class InputWindowElement extends AnimatedOverlayElementBase {

	private final Vec3 sceneSpace;
	private final Pointing direction;
	@Nullable
	Identifier key;
	@Nullable
	ScreenElement icon;
	ItemStack item = ItemStack.EMPTY;

	public InputWindowElement(Vec3 sceneSpace, Pointing direction) {
		this.sceneSpace = sceneSpace;
		this.direction = direction;
	}

	public InputElementBuilder builder() {
		return new Builder();
	}

  public net.createmod.ponder.foundation.render.PonderSceneFrame.InputOverlay snapshot(float partial) {
    return new net.createmod.ponder.foundation.render.PonderSceneFrame.InputOverlay(
        sceneSpace, direction, key, key == null ? "" : PonderIndex.getLangAccess().getShared(key),
        icon, item, getFade(partial));
  }

	private class Builder implements InputElementBuilder {

		@Override
		public InputElementBuilder withItem(ItemStack stack) {
			item = stack;
			return this;
		}

		@Override
		public InputElementBuilder leftClick() {
			icon = PonderGuiTextures.ICON_LMB;
			return this;
		}

		@Override
		public InputElementBuilder scroll() {
			icon = PonderGuiTextures.ICON_SCROLL;
			return this;
		}

		@Override
		public InputElementBuilder rightClick() {
			icon = PonderGuiTextures.ICON_RMB;
			return this;
		}

		@Override
		public InputElementBuilder showing(ScreenElement icon) {
			InputWindowElement.this.icon = icon;
			return this;
		}

		@Override
		public InputElementBuilder whileSneaking() {
			key = Ponder.asResource("sneak_and");
			return this;
		}

		@Override
		public InputElementBuilder whileCTRL() {
			key = Ponder.asResource("ctrl_and");
			return this;
		}

	}

}
