package com.simibubi.create.infrastructure.config;


import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.nio.file.Path;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public class CWorldGen extends NativeConfigBase {
    @Override protected ConfigScope defaultScope(){return ConfigScope.COMMON;}

	public final ConfigBool disable = b(false, "disableWorldGen", Comments.disable);

	@Override
	public String getName() {
		return "worldgen";
	}

    public record Values(boolean disableWorldGen){public static final Codec<Values> CODEC=RecordCodecBuilder.create(i->i.group(Codec.BOOL.optionalFieldOf("disableWorldGen",false).forGetter(Values::disableWorldGen)).apply(i,Values::new));}
    public static final Values DEFAULTS=new Values(false);
    public static Values get(){return new Values(AllConfigs.common().worldGen.disable.getServer());}
    public static void reload(){reload(FabricLoader.getInstance().getConfigDir().resolve("create-worldgen.json"));}
    public static void reload(Path path){NativeConfigFiles.reloadSection(AllConfigs.common().worldGen,path,Set.of("disableWorldGen"));}

	private static class Comments {
		static String disable = "Prevents all worldgen added by Create from taking effect";
	}

}
