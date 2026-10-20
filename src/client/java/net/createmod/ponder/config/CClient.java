// Ponder/Catnip 1.0.82 source, MIT; native26.3 execution/extraction adaptation.
// See licenses/Ponder-LICENSE and ponder-source-provenance.json.
package net.createmod.ponder.config;

import com.simibubi.create.infrastructure.config.NativeConfigBase;
import com.simibubi.create.infrastructure.config.ConfigScope;

public class CClient extends NativeConfigBase {

	public final ConfigBool comfyReading = b(false, "comfyReading",
		Comments.comfyReading);
	public final ConfigBool editingMode = b(false, "editingMode",
		Comments.editingMode);

	//placement assist group
	public final ConfigGroup placementAssist = group(1, "placementAssist",
		Comments.placementAssist);
	public final ConfigEnum<PlacementIndicatorSetting> placementIndicator = e(PlacementIndicatorSetting.TEXTURE, "indicatorType",
		Comments.placementIndicator);
	public final ConfigFloat indicatorScale = f(1.0f, 0f, "indicatorScale",
		Comments.indicatorScale);

	public enum PlacementIndicatorSetting {
		TEXTURE, TRIANGLE, NONE
	}

	@Override protected ConfigScope defaultScope() { return ConfigScope.CLIENT; }

	@Override
	public String getName() {
		return "client";
	}

	private static class Comments {
		static String comfyReading = "Slow down a ponder scene whenever there is text on screen.";
		static String editingMode = "Show additional info in the ponder view and reload scene scripts more frequently.";

		static String placementAssist = "Settings for the Placement Assist";
		static String[] placementIndicator = new String[]{
			"What indicator should be used when showing where the assisted placement ends up relative to your crosshair",
			"Choose 'NONE' to disable the Indicator altogether"
		};
		static String indicatorScale = "Change the size of the Indicator by this multiplier";
	}
}
