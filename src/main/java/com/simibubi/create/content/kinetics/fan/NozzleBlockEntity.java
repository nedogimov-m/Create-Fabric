package com.simibubi.create.content.kinetics.fan;

import java.util.ArrayList;
import java.util.List;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

public final class NozzleBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    private final List<Entity> pushingEntities = new ArrayList<>();
    private float range;
    private boolean pushing;
    public NozzleBlockEntity(BlockPos pos,BlockState state) { super(AllBlockEntityTypes.NOZZLE,pos,state);setLazyTickRate(5); }
    public float getRange() { return range; }
    public boolean isPushing() { return pushing; }
    public void setRange(float range) {
        boolean changed = this.range != range; this.range = range;
        if (range == 0) pushingEntities.clear();
        if (changed && level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
    }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() { return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this); }
    @Override public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {return super.getUpdateTag(registries);}
    @Override protected void read(net.minecraft.world.level.storage.ValueInput input, boolean clientPacket) {
        super.read(input, clientPacket); float value = input.getFloatOr("Range",0); range = Float.isFinite(value) ? Math.max(0,value) : 0;
        pushing = input.getBooleanOr("Pushing",false);
    }
    private float calcRange() {
        if (level == null) return 0;
        var fanPos = worldPosition.relative(getBlockState().getValue(NozzleBlock.FACING).getOpposite());
        if (!(LoadedLevelAccess.blockEntity(level,fanPos) instanceof IAirCurrentSource source)
                || source.isSourceRemoved() || source.getAirCurrent() == null || source.getSpeed() == 0) return 0;
        pushing = source.getAirFlowDirection() == source.getAirflowOriginSide();
        return source.getMaxDistance();
    }
    public static void tick(Level level,BlockPos pos,BlockState state,NozzleBlockEntity nozzle) {
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, nozzle)) {
            if (!createTick.shouldTick()) return;

        if (nozzle.isRemoved() || LoadedLevelAccess.blockEntity(level,pos) != nozzle) return;
        nozzle.setRange(nozzle.calcRange());
        var center = Vec3.atCenterOf(pos);
        if (level.isClientSide() && nozzle.range != 0 && level.getRandom().nextInt(Math.clamp(com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.fanPushDistance.get()-(int)nozzle.range,1,10)) == 0) {
            double radius = nozzle.pushing ? 1 : nozzle.range/2;
            var start = center.add((level.getRandom().nextFloat()-.5)*2*radius,(level.getRandom().nextFloat()-.5)*2*radius,(level.getRandom().nextFloat()-.5)*2*radius);
            var motion = center.subtract(start).normalize().scale(Math.clamp(nozzle.range*(nozzle.pushing?.025:1),0,.5)*(nozzle.pushing?-1:1));
            level.addParticle(ParticleTypes.POOF,start.x,start.y,start.z,motion.x,motion.y,motion.z);
        }
        
        for (var iterator = nozzle.pushingEntities.iterator(); iterator.hasNext();) {
            var entity = iterator.next();
            if (!(entity instanceof Player) && level.isClientSide()) continue;
            var diff = entity.position().subtract(center); double distance = diff.length();
            if (!entity.isAlive() || distance > nozzle.range || entity.isShiftKeyDown() || AirCurrent.isPlayerCreativeFlying(entity)) { iterator.remove(); continue; }
            if (!nozzle.pushing && distance < 1.5) continue;
            var force = diff.normalize().scale((nozzle.range-distance)*(nozzle.pushing?1:-1)*(entity instanceof ItemEntity?1d/128:1d/32));
            entity.setDeltaMovement(entity.getDeltaMovement().add(force)); entity.resetFallDistance(); entity.syncVelocity = true;
        }
    
        }
    }
    public void lazyTick() {
        if (range == 0 || level == null) return;
        var center = Vec3.atCenterOf(worldPosition);
        for (var entity : level.getEntitiesOfClass(Entity.class,new AABB(center,center).inflate(range/2))) {
            if (entity.position().distanceTo(center)>range || entity.isShiftKeyDown() || AirCurrent.isPlayerCreativeFlying(entity)) continue;
            if (!canSee(entity)) { pushingEntities.remove(entity); continue; }
            if (!pushingEntities.contains(entity)) pushingEntities.add(entity);
        }
        pushingEntities.removeIf(entity -> !entity.isAlive());
        // Retain the original nozzle overload gameplay: a non-block-damaging explosion and tracked entity disposal.
        if (!pushing && pushingEntities.size()>256 && !level.isClientSide()) {
            level.explode(null,center.x,center.y,center.z,2,Level.ExplosionInteraction.NONE);
            for (var entity : pushingEntities) entity.discard(); pushingEntities.clear();
        }
    }
    private boolean canSee(Entity entity) {
        // Native BlockGetter.clip, with unavailable chunks acting as an occluder instead of loading.
        BlockGetter view = new BlockGetter() {
            @Override public BlockState getBlockState(BlockPos pos) { return LoadedLevelAccess.isLoaded(level,pos) ? LoadedLevelAccess.blockState(level,pos) : Blocks.BEDROCK.defaultBlockState(); }
            @Override public BlockEntity getBlockEntity(BlockPos pos) { return LoadedLevelAccess.blockEntity(level,pos); }
            @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinY() { return level.getMinY(); }
        };
        return worldPosition.equals(view.clip(new ClipContext(entity.position(),Vec3.atCenterOf(worldPosition),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity)).getBlockPos());
    }
    @Override protected void write(net.minecraft.world.level.storage.ValueOutput output,boolean clientPacket){
        super.write(output,clientPacket);
        if(clientPacket){output.putFloat("Range",range);output.putBoolean("Pushing",pushing);}
    }
}
