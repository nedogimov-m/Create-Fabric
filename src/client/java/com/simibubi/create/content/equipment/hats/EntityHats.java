package com.simibubi.create.content.equipment.hats;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import com.simibubi.create.content.trains.schedule.hat.*;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

public final class EntityHats {
    public record Hat(PartialModelRenderState model,TrainHatInfo info){}
    public static final RenderStateDataKey<Hat> HAT=RenderStateDataKey.create(()->"create:entity_hat");
    public static void extract(LivingEntity entity,LivingEntityRenderState state){
        Hat hat=null;if(entity.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty()&&shouldRenderTrainHat(entity)){var model=new PartialModelRenderState();AllPartialModels.extract(AllPartialModels.TRAIN_HAT,model);hat=new Hat(model,TrainHatInfoReloadListener.getHatInfoFor(entity));}else if(StockTickerInteractionHandler.hasLogisticsHat(entity)){var model=new PartialModelRenderState();AllPartialModels.extract(AllPartialModels.LOGISTICS_HAT,model);hat=new Hat(model,TrainHatInfoReloadListener.getHatInfoFor(entity));}
        var data=(FabricRenderState)state;if(hat!=null||data.getData(HAT)!=null)data.setData(HAT,hat);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) public static void register(){LivingEntityRenderLayerRegistrationCallback.EVENT.register((type,renderer,helper,context)->helper.register(new CreateHatArmorLayer(renderer)));TrainHatInfoReloadListener.register();}
	public static boolean shouldRenderTrainHat(LivingEntity entity) {
		if (ConductorHatState.has(entity))
			return true;
		if (!entity.isPassenger())
			return false;
		if (!(entity.getVehicle() instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce))
			return false;
		if (!cce.hasSchedule() && !(entity instanceof net.minecraft.world.entity.player.Player))
			return false;
		com.simibubi.create.content.contraptions.Contraption contraption = cce.getContraption();
		if (!(contraption instanceof com.simibubi.create.content.trains.entity.CarriageContraption cc))
			return false;
		net.minecraft.core.BlockPos seatOf = cc.getSeatOf(entity.getUUID());
		if (seatOf == null)
			return false;
		net.createmod.catnip.data.Couple<Boolean> validSides = cc.conductorSeats.get(seatOf);
		return validSides != null;
	}

    private EntityHats(){}
}
