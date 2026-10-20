package com.simibubi.create.content.kinetics.crafter;

import com.simibubi.create.*;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Predicate;

/** Uses immutable Fabric render data for joined inputs, including on chunk worker threads. */
public final class CrafterCTModel extends WrapperBlockStateModel {
    private final SpriteFinder finder;
    private final TextureAtlasSprite brass, side, connectedBrass, connectedSide;
    private CrafterCTModel(BlockStateModel model, SpriteFinder finder, TextureAtlasSprite brass, TextureAtlasSprite side, TextureAtlasSprite connectedBrass, TextureAtlasSprite connectedSide) {
        super(model); this.finder = finder; this.brass = brass; this.side = side; this.connectedBrass = connectedBrass; this.connectedSide = connectedSide;
    }
    public static void register() {
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model, bake) -> {
            if (!bake.state().is(AllBlocks.MECHANICAL_CRAFTER)) return model;
            var materials = bake.baker().materials();
            return new CrafterCTModel(model, materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),
                    materials.get(new Material(Create.asResource("block/brass_casing")), () -> "Crafter connected textures").sprite(),
                    materials.get(new Material(Create.asResource("block/crafter_side")), () -> "Crafter connected textures").sprite(),
                    materials.get(new Material(Create.asResource("block/brass_casing_connected")), () -> "Crafter connected textures").sprite(),
                    materials.get(new Material(Create.asResource("block/crafter_side_connected")), () -> "Crafter connected textures").sprite());
        }));
    }
    private boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState state, BlockPos other) {
        var next = level.getBlockState(other);
        if (!next.is(AllBlocks.MECHANICAL_CRAFTER) || next.getValue(MechanicalCrafterBlock.HORIZONTAL_FACING) != state.getValue(MechanicalCrafterBlock.HORIZONTAL_FACING)) return false;
        return level.getBlockEntityRenderData(pos) instanceof MechanicalCrafterBlockEntity.InputRenderData first
                && level.getBlockEntityRenderData(other) instanceof MechanicalCrafterBlockEntity.InputRenderData second
                && first.members().equals(second.members()) && first.members().contains(other);
    }
    private boolean connects(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction h, Direction v, int x, int y) { return connects(level, pos, state, pos.relative(h, x).relative(v, y)); }
    private int index(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction face) {
        Direction h = face.getAxis() == Direction.Axis.X ? Direction.SOUTH : Direction.WEST;
        Direction v = face.getAxis().isHorizontal() ? Direction.UP : Direction.NORTH;
        if (face.getAxisDirection() == Direction.AxisDirection.POSITIVE) h = h.getOpposite();
        if (face == Direction.DOWN) { v = v.getOpposite(); h = h.getOpposite(); }
        var facing = state.getValue(MechanicalCrafterBlock.HORIZONTAL_FACING);
        boolean reverse = face.getAxis().isVertical() && (face == Direction.DOWN && facing.getAxis() == Direction.Axis.Z
                ? facing.getAxisDirection() == Direction.AxisDirection.POSITIVE : facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE);
        int sign = reverse ? -1 : 1;
        boolean up = connects(level, pos, state, h, v, 0, sign), down = connects(level, pos, state, h, v, 0, -sign);
        boolean left = connects(level, pos, state, h, v, -sign, 0), right = connects(level, pos, state, h, v, sign, 0);
        if (face.getAxis() != facing.getAxis()) return face.getAxis().isVertical() && facing.getAxis() != Direction.Axis.X
                ? (right ? 1 : 0) + (left ? 2 : 0) : (up ? 1 : 0) + (down ? 2 : 0);
        boolean tl = up && left && connects(level, pos, state, h, v, -sign, sign), tr = up && right && connects(level, pos, state, h, v, sign, sign);
        boolean bl = down && left && connects(level, pos, state, h, v, -sign, -sign), br = down && right && connects(level, pos, state, h, v, sign, -sign);
        int x = (up ? 1 : 0) + (down ? 2 : 0), y = (left ? 1 : 0) + (right ? 2 : 0);
        int borders = (up ? 0 : 1) + (down ? 0 : 1) + (left ? 0 : 1) + (right ? 0 : 1);
        if (borders == 0) { x += (tr ? 1 : 0) + (tl ? 2 : 0); y += (br ? 2 : 0) + (bl ? 1 : 0); }
        if (borders == 1) {
            if (!right && (tl || bl)) { y = 4; x = -1 + (bl ? 1 : 0) + (tl ? 2 : 0); }
            if (!left && (tr || br)) { y = 5; x = -1 + (br ? 1 : 0) + (tr ? 2 : 0); }
            if (!down && (tl || tr)) { y = 6; x = -1 + (tl ? 1 : 0) + (tr ? 2 : 0); }
            if (!up && (bl || br)) { y = 7; x = -1 + (bl ? 1 : 0) + (br ? 2 : 0); }
        }
        if (borders == 2 && (up && left && tl || down && left && bl || up && right && tr || down && right && br)) x += 3;
        return x + y * 8;
    }
    private List<Integer> indices(BlockAndTintGetter level, BlockPos pos, BlockState state) { return Arrays.stream(Direction.values()).map(face -> index(level, pos, state, face)).toList(); }
    private record GeometryKey(CrafterCTModel model, Object wrappedKey, List<Integer> indices) {}
    @Override public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
        if (!state.is(AllBlocks.MECHANICAL_CRAFTER)) return wrapped.createGeometryKey(level, pos, state, random);
        var key = wrapped.createGeometryKey(level, pos, state, random); return key == null ? null : new GeometryKey(this, key, indices(level, pos, state));
    }
    @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, Predicate<Direction> cullTest) {
        if (!state.is(AllBlocks.MECHANICAL_CRAFTER)) { wrapped.emitQuads(emitter, level, pos, state, random, cullTest); return; }
        var indices = indices(level, pos, state);
        emitter.pushTransform(quad -> {
            var original = finder.find(quad); var face = quad.lightFace();
            boolean front = face.getAxis() == state.getValue(MechanicalCrafterBlock.HORIZONTAL_FACING).getAxis();
            if (original != (front ? brass : side)) return true;
            var target = front ? connectedBrass : connectedSide; int sheet = front ? 8 : 2, tile = indices.get(face.ordinal());
            for (int vertex = 0; vertex < 4; vertex++) {
                float u = (quad.u(vertex) - original.getU0()) / (original.getU1() - original.getU0()), v = (quad.v(vertex) - original.getV0()) / (original.getV1() - original.getV0());
                quad.uv(vertex, target.getU((u + tile % sheet) / sheet), target.getV((v + tile / sheet) / sheet));
            }
            return true;
        });
        wrapped.emitQuads(emitter, level, pos, state, random, cullTest); emitter.popTransform();
    }
}
