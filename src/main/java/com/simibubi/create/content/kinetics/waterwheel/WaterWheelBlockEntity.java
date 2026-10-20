package com.simibubi.create.content.kinetics.waterwheel;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.KineticNetworks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.simibubi.create.content.kinetics.base.IRotate;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class WaterWheelBlockEntity extends com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"water_wheel","lava_wheel_00000");
    private int flowScore;
    private BlockState material = Blocks.SPRUCE_PLANKS.defaultBlockState();

    public WaterWheelBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntityTypes.WATER_WHEEL, pos, state); }
    public WaterWheelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    protected int getSize() { return 1; }
    public BlockState getMaterial() { return material; }
    /** Source material interaction; virtual client owners update their actual native renderer material. */
    public net.minecraft.world.InteractionResult applyMaterialIfValid(net.minecraft.world.item.ItemStack stack) {
        if(!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem))
            return net.minecraft.world.InteractionResult.PASS;
        var material=blockItem.getBlock().defaultBlockState();
        if(material==this.material||!material.is(net.minecraft.tags.BlockTags.PLANKS))
            return net.minecraft.world.InteractionResult.PASS;
        if(level.isClientSide()&&!isVirtual())return net.minecraft.world.InteractionResult.SUCCESS;
        this.material=material;
        notifyUpdate();
        level.levelEvent(net.minecraft.world.level.block.LevelEvent.PARTICLES_DESTROY_BLOCK,
            worldPosition,net.minecraft.world.level.block.Block.getId(material));
        return net.minecraft.world.InteractionResult.SUCCESS;
    }
    public void setMaterial(BlockState material) {
        if (!material.is(net.minecraft.tags.BlockTags.PLANKS) || this.material == material) return;
        this.material = material; setChanged(); sync();
    }
    @Override public float getGeneratedSpeed() { return Math.clamp(flowScore, -1, 1) * 8f / getSize(); }

    public static void tick(Level level, BlockPos pos, BlockState state, WaterWheelBlockEntity wheel) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, wheel)) {
            if (!createTick.shouldTick()) return;

        KineticBlockEntity.tick(level, pos, state, wheel);
        if (!(level instanceof ServerLevel server)) return;
        var axis = ((IRotate) state.getBlock()).getRotationAxis(state);
        int score = 0;boolean lava=false;
        for (Direction face : Direction.values()) {
            if (face.getAxis() == axis) continue;
            var centralOffset = BlockPos.ZERO.relative(face, wheel.getSize());
            score += scoreAt(level, pos, centralOffset, axis);lava|=lavaAt(level,pos,centralOffset);
            if (wheel.getSize() == 2) for (Direction other : Direction.values()) {
                if (other.getAxis() != axis && other.getAxis() != face.getAxis()) {
                    score += scoreAt(level, pos, centralOffset.relative(other), axis);lava|=lavaAt(level,pos,centralOffset.relative(other));
                }
            }
        }
        if(score!=0)wheel.sourceAdvancements.award(lava?"lava_wheel_00000":"water_wheel");
        if (score != wheel.flowScore) {
            wheel.flowScore = score;
            wheel.updateGeneratedRotation();
        }
    
        }
    }

    private static boolean lavaAt(Level level,BlockPos pos,BlockPos offset){var target=pos.offset(offset);return level.isLoaded(target)&&level.getFluidState(target).getType().isSame(net.minecraft.world.level.material.Fluids.LAVA);}
    private static int scoreAt(Level level, BlockPos pos, BlockPos offset, Direction.Axis axis) {
        var target = pos.offset(offset);
        if (!level.isLoaded(target)) return 0;
        Vec3 flow = level.getFluidState(target).getFlow(level, target);
        var block = level.getBlockState(target);
        if (block.is(Blocks.BUBBLE_COLUMN)) flow = new Vec3(0, block.getValue(BubbleColumnBlock.DRAG_DOWN) ? -1 : 1, 0);
        flow = switch (axis) {
            case X -> flow.multiply(0, 1, 1);
            case Y -> flow.multiply(1, 0, 1);
            case Z -> flow.multiply(1, 1, 0);
        };
        if (flow.lengthSqr() == 0) return 0;
        Vec3 radial = Vec3.atLowerCornerOf(offset).normalize();
        Vec3 tangent = switch (axis) {
            case X -> radial.xRot((float) (Math.PI / 2));
            case Y -> radial.yRot((float) (Math.PI / 2));
            case Z -> radial.zRot((float) (Math.PI / 2));
        };
        double dot = flow.normalize().dot(tangent);
        return Math.abs(dot) > 0.5 ? (int) Math.signum(dot) : 0;
    }
    @Override protected void write(ValueOutput output, boolean clientPacket) {
        super.write(output, clientPacket); output.putInt("FlowScore", flowScore); output.store("Material", BlockState.CODEC, material);
    }
    @Override protected void read(ValueInput input, boolean clientPacket) {
        super.read(input, clientPacket); flowScore = input.getIntOr("FlowScore", 0);
        material = input.read("Material", BlockState.CODEC).orElse(Blocks.SPRUCE_PLANKS.defaultBlockState());
        if (material.isAir()) material = Blocks.SPRUCE_PLANKS.defaultBlockState();
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("lava_wheel_00000");
        ids.add("water_wheel");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
