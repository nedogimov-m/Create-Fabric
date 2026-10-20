package com.simibubi.create.content.kinetics.base;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.crank.HandCrankBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.simpleRelays.CogWheelBlock;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlock;
import com.simibubi.create.content.kinetics.transmission.GearshiftBlock;
import com.simibubi.create.content.kinetics.millstone.MillstoneBlock;
import com.simibubi.create.content.kinetics.chainDrive.ChainDriveBlock;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlockEntity;
import com.simibubi.create.content.kinetics.press.MechanicalPressBlock;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlockEntity;
import com.simibubi.create.content.kinetics.waterwheel.WaterWheelBlock;
import com.simibubi.create.content.kinetics.waterwheel.LargeWaterWheelBlock;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.foundation.model.PartialModelRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Uses Minecraft's submission API, which selects the active RenderPearl backend. */
public final class KineticBlockEntityRenderer implements BlockEntityRenderer<KineticBlockEntity, KineticRenderState> {
    private final BlockModelResolver models;
    private final BlockDisplayContext displayContext = BlockDisplayContext.create();

    public KineticBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        models = context.blockModelResolver();
    }

    @Override
    public KineticRenderState createRenderState() {
        return new KineticRenderState();
    }

    @Override
    public void extractRenderState(KineticBlockEntity entity, KineticRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, breakProgress);
        state.attachedBracket.clear();
        var attached=entity.bracketBehaviour().getBracket();
        if(attached!=null)AllPartialModels.extract(attached,state.attachedBracket);
        BlockState blockState = entity.getBlockState();
        state.axis = ((IRotate) blockState.getBlock()).getRotationAxis(blockState);
        state.motorFacing = blockState.getBlock() instanceof CreativeMotorBlock
                ? blockState.getValue(CreativeMotorBlock.FACING) : null;
        state.crankFacing = blockState.getBlock() instanceof HandCrankBlock
                ? blockState.getValue(HandCrankBlock.FACING) : null;
        state.angle = entity.getAngle(partialTick) + rotationOffset(blockState, state.axis, entity.getBlockPos());
        state.handleAngle = entity.getIndependentAngle(partialTick);
        state.pressFacing = null;
        state.wheelFacing = null;
        state.pressHead.clear();
        state.mixerPole.clear(); state.mixerHead.clear(); state.mixer = false;
        state.fanPropeller.clear(); state.fanFacing = null;
        if (entity instanceof com.simibubi.create.content.kinetics.fan.EncasedFanBlockEntity) {
            state.fanFacing = blockState.getValue(com.simibubi.create.content.kinetics.fan.EncasedFanBlock.FACING).getOpposite();
            state.motorFacing = state.fanFacing;
            float rpm = entity.getSpeed();
            float speed = rpm == 0 ? 0 : Math.signum(rpm) * Math.clamp(Math.abs(rpm) * 5, 80, 1280);
            state.fanAngle = (float) ((entity.getLevel().getGameTime() + partialTick) * speed * 0.3 % 360);
            AllPartialModels.extract(AllPartialModels.FAN_PROPELLER, state.fanPropeller);
        }
        if (entity instanceof MechanicalMixerBlockEntity mixer) {
            state.mixer = true;
            state.mixerOffset = mixer.getHeadOffset(partialTick);
            state.mixerAngle = mixer.getHeadAngle(partialTick);
            AllPartialModels.extract(AllPartialModels.MIXER_POLE, state.mixerPole);
            AllPartialModels.extract(AllPartialModels.MIXER_HEAD, state.mixerHead);
        }
        if (entity instanceof MechanicalPressBlockEntity press) {
            state.pressFacing = blockState.getValue(MechanicalPressBlock.FACING);
            state.pressOffset = press.getHeadOffset(partialTick);
            AllPartialModels.extract(AllPartialModels.MECHANICAL_PRESS_HEAD, state.pressHead);
        }
        state.valvePointer.clear(); state.valveFacing = null;
        if(entity instanceof com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity valve){
            state.valveFacing = blockState.getValue(com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock.FACING);
            var pipeAxis=com.simibubi.create.content.fluids.pipes.valve.FluidValveBlock.getPipeAxis(blockState);
            state.valveAngle=(pipeAxis.isVertical()||pipeAxis.isHorizontal()&&state.axis==Direction.Axis.X?90:0)-90*valve.getPointer(partialTick);
            AllPartialModels.extract(AllPartialModels.FLUID_VALVE_POINTER,state.valvePointer);
        }
        state.ports.clear();
        state.gaugeHead.clear(); state.gaugeDial.clear(); state.gaugeFaces.clear();
        state.controllerBracket.clear();
        state.speedSetting=null;
        var client=net.minecraft.client.Minecraft.getInstance();
        if(com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.supports(blockState)&&client.player!=null&&client.player.mayBuild()&&!client.player.isSpectator()&&!client.player.isShiftKeyDown()&&!client.gui.hud.isHidden()&&client.gui.screen()==null&&client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit&&hit.getBlockPos().equals(entity.getBlockPos())&&com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.activeSide(blockState,hit.getDirection())){
            var text=net.minecraft.network.chat.Component.literal(String.valueOf(Math.abs(com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.value(entity))));int width=client.font.width(text);float scale=0.5f/64*3*client.font.lineHeight/Math.max(1,width);if(width<10)scale/=2;
            if(entity instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor){
                var scroll=motor.generatedSpeed;var slot=scroll.getSlotPositioning();
                if(slot instanceof com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided sided)sided.fromSide(hit.getDirection());
                var transform=new org.joml.Matrix4f();slot.transform(entity.getLevel(),entity.getBlockPos(),blockState,transform);
                text=net.minecraft.network.chat.Component.literal(scroll.formatValue());width=client.font.width(text);
                scale=slot.getFontScale()*3*client.font.lineHeight/Math.max(1,width);if(width<10)scale/=2;
                state.speedSetting=new KineticRenderState.SpeedSetting(slot.getLocalOffset(entity.getLevel(),entity.getBlockPos(),blockState),hit.getDirection(),text.getVisualOrderText(),scale,-width/2f,scroll.testHit(hit.getLocation()),new org.joml.Matrix4f(transform));
            }else{
            state.speedSetting=new KineticRenderState.SpeedSetting(com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.offset(blockState,hit.getDirection()),hit.getDirection(),text.getVisualOrderText(),scale,-width/2f,com.simibubi.create.content.kinetics.motor.KineticSpeedSettings.testHit(blockState,hit));
            }
        }
        if(entity instanceof com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity controller&&controller.isCogwheelPresent()){
            AllPartialModels.extract(AllPartialModels.SPEED_CONTROLLER_BRACKET,state.controllerBracket);state.controllerBracketLight=net.minecraft.util.LightCoordsUtil.getLightCoords(entity.getLevel(),entity.getBlockPos().above());
        }
        if (entity instanceof com.simibubi.create.content.kinetics.gauge.GaugeBlockEntity gauge) {
            var block = (com.simibubi.create.content.kinetics.gauge.GaugeBlock)blockState.getBlock();
            AllPartialModels.extract(block.type == com.simibubi.create.content.kinetics.gauge.GaugeBlock.Type.SPEED ? AllPartialModels.GAUGE_HEAD_SPEED : AllPartialModels.GAUGE_HEAD_STRESS, state.gaugeHead);
            AllPartialModels.extract(AllPartialModels.GAUGE_DIAL, state.gaugeDial); state.gaugeProgress = gauge.getDial(partialTick);
            for (var face : Direction.Plane.HORIZONTAL) if (block.shouldRenderHeadOnFace(entity.getLevel(), entity.getBlockPos(), blockState, face)) state.gaugeFaces.add(face);
        }
        if (blockState.getBlock() instanceof GearshiftBlock || blockState.getBlock() instanceof GearboxBlock
                || blockState.getBlock() instanceof com.simibubi.create.content.kinetics.transmission.sequencer.SequencedGearshiftBlock
                || blockState.getBlock() instanceof ChainDriveBlock) {
            state.model.clear();
            IRotate rotation = (IRotate) blockState.getBlock();
            for (Direction face : Direction.values()) {
                if (rotation.hasShaftTowards(entity.getLevel(), entity.getBlockPos(), blockState, face)) {
                    var partial = new PartialModelRenderState();
                    AllPartialModels.extract(AllPartialModels.SHAFT_HALF, partial);
                    double ticks = entity.getLevel().getGameTime() + partialTick;
                    float angle = (float) (ticks * entity.getPortSpeed(face) * 0.3 % 360)
                            + rotationOffset(blockState, face.getAxis(), entity.getBlockPos());
                    state.ports.add(new KineticRenderState.Port(partial, face, angle));
                }
            }
        } else if (blockState.getBlock() instanceof com.simibubi.create.content.decoration.girder.GirderEncasedShaftBlock) {
            AllPartialModels.extract(AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, state.axis), (PartialModelRenderState) state.model);
        } else if (state.valveFacing != null) {
            models.update(state.model, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS,state.axis), displayContext);
        } else if (entity instanceof com.simibubi.create.content.fluids.pump.PumpBlockEntity) {
            state.motorFacing = blockState.getValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING);
            AllPartialModels.extract(AllPartialModels.MECHANICAL_PUMP_COG, state.model);
        } else if (entity instanceof WaterWheelBlockEntity wheel) {
            boolean large = blockState.getBlock() instanceof LargeWaterWheelBlock;
            state.wheelFacing = large ? Direction.get(Direction.AxisDirection.POSITIVE, state.axis) : blockState.getValue(WaterWheelBlock.FACING);
            AllPartialModels.extract(AllPartialModels.waterWheel(wheel.getMaterial(), large, large && blockState.getValue(LargeWaterWheelBlock.EXTENSION)), state.model);
        } else if (entity instanceof com.simibubi.create.content.kinetics.crusher.CrushingWheelBlockEntity) {
            state.wheelFacing = Direction.get(Direction.AxisDirection.POSITIVE, state.axis);
            AllPartialModels.extract(AllPartialModels.CRUSHING_WHEEL, state.model);
        } else if (state.mixer) {
            AllPartialModels.extract(AllPartialModels.SHAFTLESS_COGWHEEL, state.model);
        } else if (state.pressFacing != null || entity instanceof com.simibubi.create.content.kinetics.gauge.GaugeBlockEntity || entity instanceof com.simibubi.create.content.kinetics.speedController.SpeedControllerBlockEntity) {
            models.update(state.model, AllBlocks.SHAFT.defaultBlockState().setValue(ShaftBlock.AXIS, state.axis), displayContext);
        } else if (blockState.getBlock() instanceof MillstoneBlock) {
            AllPartialModels.extract(AllPartialModels.MILLSTONE_COG, state.model);
        } else if (state.motorFacing != null) {
            AllPartialModels.extract(AllPartialModels.SHAFT_HALF, state.model);
        } else {
            models.update(state.model, blockState, displayContext);
        }
        state.crankHandle.clear();
        if (state.crankFacing != null) {
            AllPartialModels.extract(AllPartialModels.HAND_CRANK_HANDLE, state.crankHandle);
        }
    }

    @Override
    public void submit(KineticRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if(!state.attachedBracket.isEmpty())state.attachedBracket.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.rotate(switch (state.axis) {
            case X -> Axis.XP.rotationDegrees(state.angle);
            case Y -> Axis.YP.rotationDegrees(state.angle);
            case Z -> Axis.ZP.rotationDegrees(state.angle);
        });
        if (state.motorFacing != null) {
            faceSouthModel(pose, state.motorFacing);
        }
        if (state.wheelFacing != null) {
            Direction face = state.wheelFacing;
            pose.rotate(new Quaternionf().rotationTo(0, 1, 0, face.getStepX(), face.getStepY(), face.getStepZ()));
        }
        pose.translate(-0.5, -0.5, -0.5);
        if (!state.model.isEmpty()) {
            state.model.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
        pose.popPose();
        if(!state.controllerBracket.isEmpty()){
            pose.pushPose();pose.translate(0.5,1.5,0.5);pose.rotateDegrees(Axis.YP,state.axis==Direction.Axis.X?180:90);pose.translate(-0.5,-0.5,-0.5);
            state.controllerBracket.submit(pose,collector,state.controllerBracketLight,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }
        if(state.speedSetting!=null){
            var setting=state.speedSetting;pose.pushPose();
            if(setting.transform()!=null){
                pose.translate(setting.face().getStepX()*5d/128,setting.face().getStepY()*5d/128,setting.face().getStepZ()*5d/128);
                pose.mulPose(setting.transform());
            }else{
                pose.translate(setting.offset().x()+setting.face().getStepX()*5d/128,setting.offset().y()+setting.face().getStepY()*5d/128,setting.offset().z()+setting.face().getStepZ()*5d/128);
                pose.rotateDegrees(Axis.YP,switch(setting.face()){case NORTH->0;case SOUTH,UP,DOWN->180;case EAST->90;case WEST->270;});if(setting.face().getAxis().isVertical())pose.rotateDegrees(Axis.XP,setting.face()==Direction.UP?90:270);
            }
            double boxHalf=setting.transform()!=null?.5:.25;
            if(setting.highlighted())collector.submitShapeOutline(pose,net.minecraft.world.phys.shapes.Shapes.box(-boxHalf,-boxHalf,-0.001,boxHalf,boxHalf,0.001),net.minecraft.client.renderer.rendertype.RenderTypes.lines(),0xFFFFFFFF,1.5f,false);
            pose.scale(-setting.scale(),-setting.scale(),-setting.scale());
            collector.submitText(pose,setting.x(),-4,setting.text(),false,net.minecraft.client.gui.Font.DisplayMode.NORMAL,net.minecraft.util.LightCoordsUtil.FULL_BRIGHT,0xFFEDEDED,0,0xFF333333);pose.popPose();
        }
        for (var face : state.gaugeFaces) {
            pose.pushPose(); pose.translate(0.5, 0.5, 0.5); pose.rotateDegrees(Axis.YP, -face.toYRot() - 90); pose.translate(-0.5, -0.5, -0.5);
            state.gaugeHead.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            float pivot = 5.75f / 16; pose.translate(0, pivot, pivot); pose.rotateDegrees(Axis.XP, -90 * state.gaugeProgress); pose.translate(0, -pivot, -pivot);
            state.gaugeDial.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0); pose.popPose();
        }
        if(state.valveFacing!=null){
            pose.pushPose();pose.translate(0.5,0.5,0.5);
            float yaw=switch(state.valveFacing){case NORTH->180;case WEST->-90;case EAST->-270;default->0;};
            pose.rotateDegrees(Axis.YP,yaw);pose.rotateDegrees(Axis.XP,state.valveFacing==Direction.UP?0:state.valveFacing==Direction.DOWN?180:90);
            pose.rotateDegrees(Axis.YP,state.valveAngle);pose.translate(-0.5,-0.5,-0.5);
            state.valvePointer.submit(pose,collector,state.lightCoords,OverlayTexture.NO_OVERLAY,0);pose.popPose();
        }
        if (state.fanFacing != null) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.rotate(switch (state.axis) { case X -> Axis.XP.rotationDegrees(state.fanAngle); case Y -> Axis.YP.rotationDegrees(state.fanAngle); case Z -> Axis.ZP.rotationDegrees(state.fanAngle); });
            faceSouthModel(pose, state.fanFacing);
            pose.translate(-0.5, -0.5, -0.5);
            state.fanPropeller.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        for (KineticRenderState.Port port : state.ports) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.rotate(switch (port.facing().getAxis()) {
                case X -> Axis.XP.rotationDegrees(port.angle());
                case Y -> Axis.YP.rotationDegrees(port.angle());
                case Z -> Axis.ZP.rotationDegrees(port.angle());
            });
            faceSouthModel(pose, port.facing());
            pose.translate(-0.5, -0.5, -0.5);
            port.model().submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (state.mixer) {
            pose.pushPose();
            pose.translate(0, -state.mixerOffset, 0);
            state.mixerPole.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.translate(0.5, 0.5, 0.5);
            pose.rotate(Axis.YP.rotationDegrees(state.mixerAngle));
            pose.translate(-0.5, -0.5, -0.5);
            state.mixerHead.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (state.pressFacing != null) {
            pose.pushPose();
            pose.translate(0.5, 0.5 - state.pressOffset, 0.5);
            faceSouthModel(pose, state.pressFacing.getOpposite());
            pose.translate(-0.5, -0.5, -0.5);
            state.pressHead.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (state.crankFacing != null) {
            pose.pushPose();
            pose.translate(0.5, 0.5, 0.5);
            pose.rotate(switch (state.axis) {
                case X -> Axis.XP.rotationDegrees(state.handleAngle);
                case Y -> Axis.YP.rotationDegrees(state.handleAngle);
                case Z -> Axis.ZP.rotationDegrees(state.handleAngle);
            });
            faceSouthModel(pose, state.crankFacing.getOpposite());
            pose.translate(-0.5, -0.5, -0.5);
            state.crankHandle.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    private static void faceSouthModel(PoseStack pose, Direction facing) {
        pose.rotate(new Quaternionf().rotationTo(0, 0, 1, facing.getStepX(), facing.getStepY(), facing.getStepZ()));
    }

    static float rotationOffset(BlockState state, Direction.Axis axis, BlockPos pos) {
        int parity = (axis == Direction.Axis.X ? 0 : pos.getX())
                + (axis == Direction.Axis.Y ? 0 : pos.getY())
                + (axis == Direction.Axis.Z ? 0 : pos.getZ());
        return parity % 2 == 0 ? 22.5f
                : state.getBlock() instanceof CogWheelBlock cog && cog.isLargeCog() ? 11.25f : 0;
    }
}
