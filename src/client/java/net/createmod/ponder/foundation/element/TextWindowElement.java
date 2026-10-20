// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.element;

import java.util.List;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.theme.Color;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.TextElementBuilder;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.PonderScene;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class TextWindowElement extends AnimatedOverlayElementBase {

	public static final Couple<Color> COLOR_WINDOW_BORDER = Couple.create(
		new Color(0x607a6000, true),
		new Color(0x207a6000, true)
	).map(Color::setImmutable);

	Supplier<String> textGetter = () -> "(?) No text was provided";
	@Nullable
	String bakedText;

	// from 0 to 200
	int y;

	@Nullable
	Vec3 vec;

	boolean nearScene = false;
	PonderPalette palette = PonderPalette.WHITE;

	public TextElementBuilder builder(PonderScene scene) {
		return new Builder(scene);
	}

	private class Builder implements TextElementBuilder {

		private final PonderScene scene;

		public Builder(PonderScene scene) {
			this.scene = scene;
		}

		@Override
		public Builder colored(PonderPalette color) {
			TextWindowElement.this.palette = color;
			return this;
		}

		@Override
		public Builder pointAt(Vec3 vec) {
			TextWindowElement.this.vec = vec;
			return this;
		}

		@Override
		public Builder independent(int y) {
			TextWindowElement.this.y = y;
			return this;
		}

		@Override
		public Builder text(String defaultText) {
			textGetter = scene.registerText(defaultText);
			return this;
		}

		@Override
		public TextElementBuilder text(String defaultText, Object... params) {
			textGetter = scene.registerText(defaultText, params);
			return this;
		}

		@Override
		public Builder sharedText(Identifier key) {
			textGetter = () -> PonderIndex.getLangAccess().getShared(key);
			return this;
		}

		@Override
		public TextElementBuilder sharedText(Identifier key, Object... params) {
			textGetter = () -> PonderIndex.getLangAccess().getShared(key, params);
			return this;
		}

		@Override
		public Builder sharedText(String key) {
			return sharedText(Identifier.fromNamespaceAndPath(scene.getNamespace(), key));
		}

		@Override
		public TextElementBuilder sharedText(String key, Object... params) {
			return sharedText(Identifier.fromNamespaceAndPath(scene.getNamespace(), key), params);
		}

		@Override
		public Builder placeNearTarget() {
			TextWindowElement.this.nearScene = true;
			return this;
		}

		@Override
		public Builder attachKeyFrame() {
			scene.builder()
				.addLazyKeyframe();
			return this;
		}
	}

	public PonderPalette getPalette() {
		return palette;
	}

  public net.createmod.ponder.foundation.render.PonderSceneFrame.TextOverlay snapshot(float partial) {
    if (bakedText == null) bakedText = textGetter.get();
    return new net.createmod.ponder.foundation.render.PonderSceneFrame.TextOverlay(
        bakedText, palette, vec, y, nearScene, getFade(partial));
  }

}
