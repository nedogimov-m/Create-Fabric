// Genuine Create6.0.10 Ponder source; native26.3 adaptation.
package com.simibubi.create.foundation.ponder;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.content.kinetics.crank.ValveHandleBlock;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlock;
import com.simibubi.create.content.logistics.tableCloth.TableClothBlock;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderScenes;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;

import net.createmod.ponder.api.level.PonderLevel;
import net.createmod.ponder.api.registration.IndexExclusionHelper;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.createmod.ponder.api.registration.SharedTextRegistrationHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

public class CreatePonderPlugin implements PonderPlugin {
    private static boolean registered;
    public static synchronized void register() {
        if(registered)return;
        registered=true;
        net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry.getInstance().register(
            com.simibubi.create.AllParticleTypes.ROTATION_INDICATOR,
            com.simibubi.create.content.kinetics.base.RotationIndicatorParticle.Factory::new);
        com.simibubi.create.content.fluids.particle.FluidParticleProviders.register();
        net.createmod.ponder.foundation.PonderIndex.addPlugin(new CreatePonderPlugin());
    }


	@Override
	public String getModId() {
		return Create.ID;
	}

	@Override
	public void registerScenes(PonderSceneRegistrationHelper<Identifier> helper) {
		AllCreatePonderScenes.register(helper);
	}

	@Override
	public void registerTags(PonderTagRegistrationHelper<Identifier> helper) {
		AllCreatePonderTags.register(helper);
	}

	@Override
	public void registerSharedText(SharedTextRegistrationHelper helper) {
		helper.registerSharedText("rpm8", "8 RPM");
		helper.registerSharedText("rpm16", "16 RPM");
		helper.registerSharedText("rpm16_source", "Source: 16 RPM");
		helper.registerSharedText("rpm32", "32 RPM");

		helper.registerSharedText("movement_anchors", "With the help of Super Glue, larger structures can be moved.");
		helper.registerSharedText("behaviour_modify_value_panel", "This behaviour can be modified using the value panel");
		helper.registerSharedText("storage_on_contraption", "Inventories attached to the Contraption will pick up their drops automatically");
	}

	@Override
	public void onPonderLevelRestore(PonderLevel ponderLevel) {
		PonderWorldBlockEntityFix.fixControllerBlockEntities(ponderLevel);
	}

	@Override
	public void indexExclusions(IndexExclusionHelper helper) {
		helper.excludeBlockVariants(ValveHandleBlock.class, AllBlocks.COPPER_VALVE_HANDLE);
		helper.excludeBlockVariants(PostboxBlock.class, AllBlocks.PACKAGE_POSTBOXES.get(DyeColor.WHITE));
		helper.excludeBlockVariants(TableClothBlock.class, AllBlocks.TABLE_CLOTHS.get(DyeColor.WHITE));
	}
}
