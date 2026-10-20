package com.simibubi.create.infrastructure.config;



public class CCommon extends NativeConfigBase {
    @Override protected ConfigScope defaultScope(){return ConfigScope.COMMON;}

	public final CWorldGen worldGen = nested(0, CWorldGen::new, Comments.worldGen);

	@Override
	public String getName() {
		return "common";
	}

	private static class Comments {
		static String worldGen = "Modify Create's impact on your terrain";
	}

}
