package com.simibubi.create.api.equipment.potatoCannon;

import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.equipment.potatoCannon.PotatoProjectileEntity;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public interface PotatoProjectileRenderMode {
	Codec<PotatoProjectileRenderMode> CODEC = CreateBuiltInRegistries.POTATO_PROJECTILE_RENDER_MODE.byNameCodec()
		.dispatch(PotatoProjectileRenderMode::codec, Function.identity());

	/**
	 * Extract the projectile's item transform into a client-owned matrix. The actual
	 * camera entity's interpolated eye position is supplied by the renderer, so
	 * codecs and custom modes can also be loaded on a dedicated server.
	 */
	void transform(Matrix4f matrix, PotatoProjectileEntity entity, float pt, @Nullable Vec3 cameraEye);

	MapCodec<? extends PotatoProjectileRenderMode> codec();
}
