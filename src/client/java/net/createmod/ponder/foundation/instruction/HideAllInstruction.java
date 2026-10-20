// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.foundation.instruction;

import net.createmod.ponder.api.element.AnimatedOverlayElement;
import net.createmod.ponder.api.element.AnimatedSceneElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.AnimatedOverlayElementBase;
import net.createmod.ponder.foundation.element.AnimatedSceneElementBase;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public class HideAllInstruction extends TickingInstruction {

	private final Direction fadeOutTo;

	public HideAllInstruction(int fadeOutTicks, Direction fadeOutTo) {
		super(false, fadeOutTicks);
		this.fadeOutTo = fadeOutTo;
	}

	@Override
	protected void firstTick(PonderScene scene) {
		super.firstTick(scene);
		scene.getElements()
			.forEach(element -> {
				if (element instanceof AnimatedSceneElementBase animatedSceneElement) {
					animatedSceneElement.setFade(1);
					animatedSceneElement
						.setFadeVec(fadeOutTo == null ? null : Vec3.atLowerCornerOf(fadeOutTo.getUnitVec3i()).scale(.5f));
				} else if (element instanceof AnimatedOverlayElementBase animatedSceneElement) {
					animatedSceneElement.setFade(1);
				} else
					element.setVisible(false);
			});
	}

	@Override
	public void tick(PonderScene scene) {
		super.tick(scene);
		float fade = (remainingTicks / (float) totalTicks);

		scene.forEach(AnimatedSceneElementBase.class, ase -> {
			ase.setFade(fade * fade);
			if (remainingTicks == 0)
				ase.setFade(0);
		});

		scene.forEach(AnimatedOverlayElementBase.class, aoe -> {
			aoe.setFade(fade * fade);
			if (remainingTicks == 0)
				aoe.setFade(0);
		});
	}

}
