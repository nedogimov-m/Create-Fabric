// Genuine Ponder 1.0.82 source, adapted to native 26.3 common APIs. See licenses/Ponder-LICENSE.
package net.createmod.catnip.lang;

import java.text.NumberFormat;
import java.util.Locale;


import net.minecraft.util.Mth;

public class LangNumberFormat {

	private NumberFormat format = createFormat(Locale.ROOT);
	public static LangNumberFormat numberFormat = new LangNumberFormat();

	public NumberFormat get() {
		return format;
	}

	public void update() {
		format = createFormat(LangClientHooks.getCurrentLocale());
	}

	private static NumberFormat createFormat(Locale locale) {
		NumberFormat format = NumberFormat.getInstance(locale);
		format.setMaximumFractionDigits(2);
		format.setMinimumFractionDigits(0);
		format.setGroupingUsed(true);
		return format;
	}

	public static String format(double d) {
		if (Mth.equal(d, 0))
			d = 0;
		return numberFormat.get()
			.format(d)
			.replace("\u00A0", " ");
	}

}
