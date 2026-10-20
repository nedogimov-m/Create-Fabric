package com.simibubi.create.content.kinetics;

import java.util.*;
import java.util.function.Predicate;
import com.simibubi.create.*;
import com.simibubi.create.content.decoration.encasing.*;
import com.simibubi.create.content.kinetics.simpleRelays.encased.*;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.sprite.SpriteFinder;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.*;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Exact source casing/cog contexts, emitted as real native chunk-model UV transforms. */
public final class NativeKineticsCTModel extends WrapperBlockStateModel {
    public static final CasingConnectivity CASING_CONNECTIVITY = new CasingConnectivity();
    private static final Map<Block,EncasedCTBehaviour> BEHAVIOURS = new IdentityHashMap<>();
    private record BakedShift(TextureAtlasSprite original,TextureAtlasSprite connected) {}
    public record FaceTile(CTSpriteShiftEntry shift,int tile) {}
    private final EncasedCTBehaviour behaviour;
    private final SpriteFinder finder;
    private final Map<CTSpriteShiftEntry,BakedShift> shifts;
    private NativeKineticsCTModel(BlockStateModel model,EncasedCTBehaviour behaviour,SpriteFinder finder,Map<CTSpriteShiftEntry,BakedShift> shifts) {
        super(model); this.behaviour = behaviour; this.finder = finder; this.shifts = Map.copyOf(shifts);
    }
    private static CTSpriteShiftEntry shift(String name,CTSpriteShiftEntry.Mode mode) {
        return new CTSpriteShiftEntry(Create.asResource("block/"+name),Create.asResource("block/"+name+"_connected"),mode);
    }
    public static void register() {
        var andesite = shift("andesite_casing",CTSpriteShiftEntry.Mode.OMNIDIRECTIONAL);
        var brass = shift("brass_casing",CTSpriteShiftEntry.Mode.OMNIDIRECTIONAL);
        for (var casing : List.of(AllBlocks.ANDESITE_CASING,AllBlocks.BRASS_CASING)) {
            var descriptor = casing == AllBlocks.ANDESITE_CASING ? andesite : brass;
            CASING_CONNECTIVITY.makeCasing(casing,descriptor); BEHAVIOURS.put(casing,new EncasedCTBehaviour(CASING_CONNECTIVITY,descriptor));
        }
        for(var casing : List.of(AllBlocks.SHADOW_STEEL_CASING,AllBlocks.REFINED_RADIANCE_CASING)){
            var descriptor=shift(casing==AllBlocks.SHADOW_STEEL_CASING?"shadow_steel_casing":"refined_radiance_casing",CTSpriteShiftEntry.Mode.OMNIDIRECTIONAL);
            CASING_CONNECTIVITY.makeCasing(casing,descriptor);BEHAVIOURS.put(casing,new EncasedCTBehaviour(CASING_CONNECTIVITY,descriptor));
        }
        CASING_CONNECTIVITY.make(AllBlocks.GEARBOX,andesite,(state,face) -> face.getAxis() == state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS));
        BEHAVIOURS.put(AllBlocks.GEARBOX,new EncasedCTBehaviour(CASING_CONNECTIVITY,andesite));
        for (var shaft : List.of(AllBlocks.ANDESITE_ENCASED_SHAFT,AllBlocks.BRASS_ENCASED_SHAFT)) {
            var descriptor = shaft.getCasing() == AllBlocks.ANDESITE_CASING ? andesite : brass;
            CASING_CONNECTIVITY.make(shaft,descriptor,(state,face) -> face.getAxis() != state.getValue(EncasedShaftBlock.AXIS));
            BEHAVIOURS.put(shaft,new EncasedCTBehaviour(CASING_CONNECTIVITY,descriptor));
        }
        for (var cog : List.of(AllBlocks.ANDESITE_ENCASED_COGWHEEL,AllBlocks.BRASS_ENCASED_COGWHEEL,AllBlocks.ANDESITE_ENCASED_LARGE_COGWHEEL,AllBlocks.BRASS_ENCASED_LARGE_COGWHEEL)) {
            var descriptor = cog.getCasing() == AllBlocks.ANDESITE_CASING ? andesite : brass;
            CASING_CONNECTIVITY.make(cog,descriptor,(state,face) -> face.getAxis() == state.getValue(EncasedCogwheelBlock.AXIS)
                    && !state.getValue(face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? EncasedCogwheelBlock.TOP_SHAFT : EncasedCogwheelBlock.BOTTOM_SHAFT));
            String side = (cog.getCasing() == AllBlocks.ANDESITE_CASING ? "andesite" : "brass")+"_encased_cogwheel_side";
            BEHAVIOURS.put(cog,new EncasedCogCTBehaviour(CASING_CONNECTIVITY,descriptor,cog.isLargeCog()?null:shift(side,CTSpriteShiftEntry.Mode.VERTICAL),cog.isLargeCog()?null:shift(side,CTSpriteShiftEntry.Mode.HORIZONTAL)));
            FluidRenderingRegistry.setBlockTransparency(cog,true);
        }
        FluidRenderingRegistry.setBlockTransparency(AllBlocks.NOZZLE,true);
        ModelLoadingPlugin.register(context -> context.modifyBlockModelAfterBake().register((model,bake) -> {
            var behaviour = BEHAVIOURS.get(bake.state().getBlock()); if (behaviour == null) return model;
            var materials = bake.baker().materials(); var shifts = new HashMap<CTSpriteShiftEntry,BakedShift>();
            for (var face : Direction.values()) {
                var descriptor = behaviour.getShift(bake.state(),face); if (descriptor == null || shifts.containsKey(descriptor)) continue;
                var original = materials.get(new Material(descriptor.original()),() -> "Create source encased casing/cog").sprite();
                var connected = materials.get(new Material(descriptor.connected()),() -> "Create source encased connected sheet").sprite();
                shifts.put(descriptor,new BakedShift(original,connected));
            }
            return new NativeKineticsCTModel(model,behaviour,materials.spriteFinder(net.minecraft.data.AtlasIds.BLOCKS),shifts);
        }));
    }
    public List<FaceTile> indices(BlockAndTintGetter level,BlockPos pos,BlockState state) {
        return Arrays.stream(Direction.values()).map(face -> {
            var descriptor = behaviour.getShift(state,face);
            return new FaceTile(descriptor,descriptor == null ? -1 : descriptor.textureIndex(behaviour.buildContext(level,pos,state,face)));
        }).toList();
    }
    private record Key(NativeKineticsCTModel model,Object original,List<FaceTile> faces) {}
    @Override public Object createGeometryKey(BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random) {
        var original = wrapped.createGeometryKey(level,pos,state,random); return original == null ? null : new Key(this,original,indices(level,pos,state));
    }
    @Override public void emitQuads(QuadEmitter out,BlockAndTintGetter level,BlockPos pos,BlockState state,RandomSource random,Predicate<Direction> cull) {
        var faces = indices(level,pos,state);
        out.pushTransform(quad -> {
            var face = faces.get(quad.lightFace().ordinal()); if (face.shift == null) return true;
            var selected = shifts.get(face.shift); var source = finder.find(quad); if (selected == null || source != selected.original) return true;
            int size = face.shift.sheetSize(); var target = selected.connected;
            for (int vertex=0;vertex<4;vertex++) {
                float u = (quad.u(vertex)-source.getU0())/(source.getU1()-source.getU0());
                float v = (quad.v(vertex)-source.getV0())/(source.getV1()-source.getV0());
                quad.uv(vertex,target.getU((u+face.tile%size)/size),target.getV((v+face.tile/size)/size));
            }
            return true;
        });
        try { wrapped.emitQuads(out,level,pos,state,random,cull); } finally { out.popTransform(); }
    }
}
