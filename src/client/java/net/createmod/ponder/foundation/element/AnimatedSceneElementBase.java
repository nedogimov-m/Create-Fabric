// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE.
package net.createmod.ponder.foundation.element;

import com.mojang.blaze3d.vertex.PoseStack;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.api.element.AnimatedSceneElement;
import net.createmod.ponder.api.level.PonderLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public abstract class AnimatedSceneElementBase extends PonderElementBase implements AnimatedSceneElement {

	protected Vec3 fadeVec;
	protected LerpedFloat fade;

	public AnimatedSceneElementBase() {
		fade = LerpedFloat.linear()
			.startWithValue(0);
	}

	public float getFade(float partial) { return fade.getValue(partial); }

	@Override
	public void forceApplyFade(float fade) {
		this.fade.startWithValue(fade);
	}

	@Override
	public void setFade(float fade) {
		this.fade.setValue(fade);
	}

	@Override
	public void setFadeVec(Vec3 fadeVec) {
		this.fadeVec = fadeVec;
	}

	public float applyFade(PoseStack ms, float pt) {
		float currentFade = fade.getValue(pt);
		if (fadeVec != null) {
			Vec3 scaled = fadeVec.scale(-1 + currentFade);
			ms.translate(scaled.x, scaled.y, scaled.z);
		}

		return currentFade;
	}

	public int lightCoordsFromFade(float fade) {
		int light = net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
		if (fade != 1) {
			light = (int) (Mth.lerp(fade, 5, 0xF));
			light = light << 20 | light << 4;
		}
		return light;
	}

}
