package com.simibubi.create.content.kinetics.speedController;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.equipment.goggles.GoggleTooltip;
import com.simibubi.create.content.kinetics.*;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.List;

public final class SpeedControllerBlockEntity extends KineticBlockEntity {
    public static final int DEFAULT_SPEED=16;
    private int targetSpeed=DEFAULT_SPEED;
    public SpeedControllerBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.ROTATION_SPEED_CONTROLLER,pos,state);}
	public static float getConveyedSpeed(KineticBlockEntity cogWheel, KineticBlockEntity speedControllerIn,
		boolean targetingController) {
		if (!(speedControllerIn instanceof SpeedControllerBlockEntity))
			return 0;

		float speed = speedControllerIn.getTheoreticalSpeed();
		float wheelSpeed = cogWheel.getTheoreticalSpeed();
		float desiredOutputSpeed = getDesiredOutputSpeed(cogWheel, speedControllerIn, targetingController);

		float compareSpeed = targetingController ? speed : wheelSpeed;
		if (desiredOutputSpeed >= 0 && compareSpeed >= 0)
			return Math.max(desiredOutputSpeed, compareSpeed);
		if (desiredOutputSpeed < 0 && compareSpeed < 0)
			return Math.min(desiredOutputSpeed, compareSpeed);

		return desiredOutputSpeed;
	}

	public static float getDesiredOutputSpeed(KineticBlockEntity cogWheel, KineticBlockEntity speedControllerIn,
		boolean targetingController) {
		SpeedControllerBlockEntity speedController = (SpeedControllerBlockEntity) speedControllerIn;
		float targetSpeed = speedController.getTargetSpeed();
		float speed = speedControllerIn.getTheoreticalSpeed();
		float wheelSpeed = cogWheel.getTheoreticalSpeed();

		if (targetSpeed == 0)
			return 0;
		if (targetingController && wheelSpeed == 0)
			return 0;
		if (!speedController.hasSource()) {
			if (targetingController)
				return targetSpeed;
			return 0;
		}

		boolean wheelPowersController = speedController.source.equals(cogWheel.getBlockPos());

		if (wheelPowersController) {
			if (targetingController)
				return targetSpeed;
			return wheelSpeed;
		}

		if (targetingController)
			return speed;
		return targetSpeed;
	}


    public int getTargetSpeed(){return targetSpeed;}
    public void setTargetSpeed(int speed) {
        int value = Math.clamp(speed, -KineticNetworks.maxRotationSpeed(), KineticNetworks.maxRotationSpeed());
        if (targetSpeed == value) return;
        targetSpeed = value;
        setChanged();
        if (level instanceof ServerLevel server) {
            if (!isVirtual() && KineticNetworks.isLive(server, this)) updateTargetRotation();
            KineticNetworks.markDirty(server);
            sync();
        }
    }
    /** Exact source setting callback: remove the old dependent tree before applying the new ratio. */
    private void updateTargetRotation() {
        if (hasNetwork()) getOrCreateNetwork().remove(this);
        RotationPropagator.handleRemoved(level, worldPosition, this);
        removeSource();
        attachKinetics();
        if (isCogwheelPresent() && getSpeed() != 0)
            award(com.simibubi.create.foundation.advancement.AllAdvancements.SPEED_CONTROLLER);
    }
    public boolean isCogwheelPresent(){if(level==null)return false;var state=LoadedLevelAccess.blockState(level,worldPosition.above());return state.getBlock() instanceof com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock cog&&cog.isLargeCog()&&cog.getRotationAxis(state).isHorizontal();}
    @Override public boolean addToGoggleTooltip(List<Component> tooltip,boolean sneaking){tooltip.add(GoggleTooltip.translate("kinetics.speed_controller.rotation_speed"));GoggleTooltip.line(tooltip,GoggleTooltip.units(targetSpeed,"rpm"),1);return true;}
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("ScrollValue",targetSpeed);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);targetSpeed=in.getIntOr("ScrollValue",DEFAULT_SPEED);}
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("speed_controller");
    }
}
