package com.simibubi.create.content.kinetics.base;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.foundation.model.PartialModelRenderState;

public final class KineticRenderState extends BlockEntityRenderState {
    public final PartialModelRenderState attachedBracket=new PartialModelRenderState();
    public final BlockModelRenderState model = new PartialModelRenderState();
    public final BlockModelRenderState crankHandle = new PartialModelRenderState();
    public final BlockModelRenderState pressHead = new PartialModelRenderState();
    public final BlockModelRenderState mixerPole = new PartialModelRenderState();
    public final BlockModelRenderState mixerHead = new PartialModelRenderState();
    public final PartialModelRenderState valvePointer = new PartialModelRenderState();
    public Direction valveFacing;
    public float valveAngle;
    public boolean mixer;
    public float mixerOffset;
    public float mixerAngle;
    public Direction fanFacing;
    public float fanAngle;
    public final BlockModelRenderState fanPropeller = new PartialModelRenderState();
    public Direction pressFacing;
    public Direction wheelFacing;
    public float pressOffset;
    public Direction.Axis axis = Direction.Axis.Y;
    public Direction motorFacing;
    public Direction crankFacing;
    public float angle;
    public float handleAngle;
    public final List<Port> ports = new ArrayList<>();
    public final PartialModelRenderState gaugeHead = new PartialModelRenderState(), gaugeDial = new PartialModelRenderState();
    public final List<Direction> gaugeFaces = new ArrayList<>();
    public float gaugeProgress;
    public final PartialModelRenderState controllerBracket=new PartialModelRenderState();
    public int controllerBracketLight;
    public SpeedSetting speedSetting;
    public record SpeedSetting(net.minecraft.world.phys.Vec3 offset,Direction face,net.minecraft.util.FormattedCharSequence text,float scale,float x,boolean highlighted,org.joml.Matrix4fc transform){
        public SpeedSetting(net.minecraft.world.phys.Vec3 offset,Direction face,net.minecraft.util.FormattedCharSequence text,float scale,float x,boolean highlighted){this(offset,face,text,scale,x,highlighted,null);}
    }

    public record Port(BlockModelRenderState model, Direction facing, float angle) {}
}
