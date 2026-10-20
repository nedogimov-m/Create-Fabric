// Genuine Create6.0.10 Ponder source; native26.3 adaptation.
package com.simibubi.create.foundation.ponder.element;

import java.util.function.Supplier;

import net.createmod.ponder.api.element.ParrotPose;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.element.ParrotElementImpl;
import com.simibubi.create.content.trains.schedule.hat.ConductorHatState;
import net.createmod.ponder.api.element.ParrotElement;
import net.minecraft.world.phys.Vec3;

public class ExpandedParrotElement extends ParrotElementImpl {

    /** Construct the actual specialized element required by conductorBirb callbacks. */
    public static ParrotElement create(Vec3 location,Supplier<? extends ParrotPose> pose) {
        return new ExpandedParrotElement(location,pose);
    }

	protected boolean deferConductor = false;

	protected ExpandedParrotElement(Vec3 location, Supplier<? extends ParrotPose> pose) {
		super(location, pose);
	}

	@Override
	public void reset(PonderScene scene) {
		super.reset(scene);
		ConductorHatState.set(entity,false);
		deferConductor = false;
	}

	@Override
	public void tick(PonderScene scene) {
		boolean wasNull = entity == null;
		super.tick(scene);
		if (wasNull) {
			if (deferConductor) {
				setConductor(true);
			}
			deferConductor = false;
		}
	}

	public void setConductor(boolean isConductor) {
		if (entity == null) {
			deferConductor = isConductor;
			return;
		}
		ConductorHatState.set(entity,isConductor);
	}
}
