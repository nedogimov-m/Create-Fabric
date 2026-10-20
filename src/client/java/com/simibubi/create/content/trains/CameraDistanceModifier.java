package com.simibubi.create.content.trains;

import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.animation.LerpedFloat;

public class CameraDistanceModifier {

	private static final LerpedFloat multiplier = LerpedFloat.linear().startWithValue(1);
    private static net.minecraft.client.player.LocalPlayer observedPlayer;
    private static net.minecraft.world.entity.Entity observedVehicle;
    private static net.minecraft.client.multiplayer.ClientLevel observedLevel;
    private static float mountedSetting;
    private static boolean registered;

    public static void register() {
        if (registered) return;
        registered = true;
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null) {
                clear();
                return;
            }
            if (client.isPaused()) return;
            onMountChanged(client.player);
            if (observedVehicle instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity) {
                float configured = AllConfigs.client().mountedZoomMultiplier.getF();
                if (configured != mountedSetting) {
                    mountedSetting = configured;
                    zoomOut(configured);
                }
            }
            tick();
        });
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
    }

    /** Called after real native mount/removal, with the client tick covering level/player replacement. */
    public static void onMountChanged(net.minecraft.client.player.LocalPlayer player) {
        var client = net.minecraft.client.Minecraft.getInstance();
        if (player != client.player) return;
        if (observedPlayer != player || observedLevel != client.level) {
            clear();
            observedPlayer = player;
            observedLevel = client.level;
        }
        var vehicle = player.getVehicle();
        if (vehicle == observedVehicle) return;
        if (observedVehicle != null) reset();
        observedVehicle = vehicle;
        if (vehicle instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity) {
            mountedSetting = AllConfigs.client().mountedZoomMultiplier.getF();
            zoomOut(mountedSetting);
        }
    }

    private static void clear() {
        observedPlayer = null;
        observedVehicle = null;
        observedLevel = null;
        multiplier.startWithValue(1);
    }

	public static float getMultiplier() {
		return getMultiplier(net.minecraft.client.Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
	}

	public static float getMultiplier(float partialTicks) {
		return multiplier.getValue(partialTicks);
	}

	public static void tick() {
		multiplier.tickChaser();
	}

	public static void reset() {
		multiplier.chase(1, 0.1, LerpedFloat.Chaser.EXP);
	}

	public static void zoomOut() {
		zoomOut(AllConfigs.client().mountedZoomMultiplier.getF());
	}

	public static void zoomOut(float targetMultiplier) {
		multiplier.chase(targetMultiplier, 0.075, LerpedFloat.Chaser.EXP);
	}

}
