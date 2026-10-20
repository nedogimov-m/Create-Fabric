package com.simibubi.create.content.kinetics.fan;

import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import net.minecraft.core.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Actual native fan geometry, entity forces and processing engine, extracted without a second simulation. */
public final class AirCurrent {
    public float maxDistance;
    private boolean rebuild = true;
    private float previousSpeed;
    private Direction previousFacing;
    private FanProcessingType[] types = {};
    public AABB bounds = new AABB(0, 0, 0, 0, 0, 0);
    private final java.util.List<Entity> caughtEntities = new java.util.ArrayList<>();
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> TRANSPARENT = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, com.simibubi.create.Create.asResource("fan_transparent"));
    public final IAirCurrentSource source;
    public AirCurrent(IAirCurrentSource source) { this.source = source; }
    private float getSpeed() { return source.getSpeed(); }
    public boolean isPushing() { return source.getAirFlowDirection() == source.getAirflowOriginSide(); }
    public void rebuild() { rebuild = true; }
    public void findEntities() {
        var level = source.getAirCurrentWorld(); caughtEntities.clear();
        if (level != null && maxDistance > 0) caughtEntities.addAll(level.getEntities(null, bounds));
    }
    public FanProcessingType getTypeAt(float offset) { return types.length <= 1 ? null : types[Math.clamp((int)Math.ceil(offset),1,types.length-1)]; }
    public static boolean isPlayerCreativeFlying(Entity entity) {
        return entity instanceof Player player && (player.isSpectator() || player.isCreative() && player.getAbilities().flying);
    }
    public static float getFlowLimit(Level level, BlockPos start, float max, Direction facing) { return flowLimit(level,start,facing,max); }
    public static float flowLimit(Level level, BlockPos pos, Direction facing, float distance) {
        double[][] rays = {{0.25, 0.25}, {0.25, 0.75}, {0.5, 0.5}, {0.75, 0.25}, {0.75, 0.75}};
        for (int offset = 0; offset < distance; offset++) {
            var current = pos.relative(facing, offset + 1);
            if (!level.isLoaded(current)) return offset;
            var state = com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level,current);
            if (state.is(TRANSPARENT)) continue;
            var shape = state.getCollisionShape(level, current);
            if (shape.isEmpty()) continue;
            double maxDepth = 0;
            for (double[] ray : rays) {
                double depth = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? shape.min(facing.getAxis(), ray[0], ray[1]) : 1 - shape.max(facing.getAxis(), ray[0], ray[1]);
                maxDepth = Math.max(maxDepth, depth);
            }
            if (maxDepth == Double.POSITIVE_INFINITY) continue;
            return Math.min(distance, (float) (offset + maxDepth + 1.0 / 32));
        }
        return distance;
    }

    private void awardProcessing() {
        if (source instanceof EncasedFanBlockEntity fan) fan.award(AllAdvancements.FAN_PROCESSING);
    }

    public void tick() {
        Level level = source.getAirCurrentWorld();
        if (level == null || source.isSourceRemoved()) return;
        BlockPos pos = source.getAirCurrentPos();
        AirCurrent fan = this;
        if (fan.getSpeed() == 0) { fan.previousSpeed=0;fan.previousFacing=source.getAirflowOriginSide();fan.maxDistance = 0; fan.rebuild = true; fan.caughtEntities.clear(); fan.types=new FanProcessingType[0]; fan.bounds=new AABB(0,0,0,0,0,0); return; }
        Direction facing = source.getAirflowOriginSide();
        var nozzleState = com.simibubi.create.foundation.utility.LoadedLevelAccess.blockState(level, pos.relative(facing));
        if (nozzleState.getBlock() instanceof NozzleBlock && nozzleState.getValue(NozzleBlock.FACING) == facing) {
            maxDistance = 0; caughtEntities.clear(); types = new FanProcessingType[0]; bounds=new AABB(0,0,0,0,0,0); rebuild = true; return;
        }
        // Periodic local simulation must advance with the marked virtual owner, not host frame time.
        var localClock = source instanceof net.createmod.ponder.api.VirtualBlockEntity virtual && virtual.isVirtual()
                && level instanceof com.simibubi.create.foundation.utility.VirtualLevelClock clock ? clock : null;
        if (fan.rebuild || fan.previousSpeed != fan.getSpeed() || fan.previousFacing != facing || (localClock != null
                ? localClock.virtualGameTime() % com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.fanBlockCheckRate.get() == 0
                : level.getGameTime() % com.simibubi.create.infrastructure.config.AllConfigs.server().kinetics.fanBlockCheckRate.get() == 0)) {
            fan.rebuild = false; fan.previousSpeed = fan.getSpeed(); fan.previousFacing = facing;
            float expected = source.getMaxDistance();
            fan.maxDistance = flowLimit(level, pos, facing, expected);
            var direction = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
            float factor = fan.maxDistance - 1;
            var scale = direction.scale(factor);
            fan.bounds = factor >= 0 ? new AABB(pos.relative(facing)).expandTowards(scale)
                    : new AABB(pos.relative(facing)).contract(scale.x, scale.y, scale.z).move(scale);
            int limit = (int) Math.ceil(fan.maxDistance);
            fan.types = new FanProcessingType[limit + 1];
            FanProcessingType active = null;
            for (int i = fan.isPushing() ? 1 : limit; i >= 1 && i <= limit; i += fan.isPushing() ? 1 : -1) {
                var catalyst = FanProcessingType.getAt(level, pos.relative(facing, i));
                if (catalyst != null) active = catalyst;
                fan.types[i] = active;
            }
            fan.caughtEntities.clear(); fan.caughtEntities.addAll(level.getEntities(null, fan.bounds));
            if (!level.isClientSide() && fan.maxDistance > 0 && source instanceof EncasedFanBlockEntity owner)
                owner.award(AllAdvancements.ENCASED_FAN);
        }
        if (fan.maxDistance < 0.25) return;
        var direction = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        int limit = (int) Math.ceil(fan.maxDistance);
        var types = fan.types;
        if (localClock != null ? localClock.virtualGameTime() % 5 == 0 : level.getGameTime() % 5 == 0) {
            fan.caughtEntities.clear(); fan.caughtEntities.addAll(level.getEntities(null, fan.bounds));
        }
        if (level.isClientSide() && level.getRandom().nextFloat() < com.simibubi.create.infrastructure.config.AllConfigs.client().fanParticleDensity.get()) {
            var particlePos = Vec3.atCenterOf(pos).add(direction.scale(fan.isPushing() ? .5 : fan.maxDistance + .5));
            double sign = fan.isPushing() ? 1 : -1;
            level.addParticle(new AirFlowParticleData(source.getAirCurrentPos()), particlePos.x, particlePos.y, particlePos.z,
                    direction.x * sign * 0.12, direction.y * sign * 0.12, direction.z * sign * 0.12);
        }
        for (Entity entity : fan.caughtEntities) {
            if (!entity.isAlive() || !entity.getBoundingBox().intersects(fan.bounds)) continue;
            if (entity instanceof Player player && (player.isSpectator() || player.isCreative() && player.getAbilities().flying)) continue;
            double distance = Math.max(0.25, entity.position().distanceTo(Vec3.atCenterOf(pos)));
            double acceleration = Math.abs(fan.getSpeed()) / (entity.isShiftKeyDown() ? 4096f : 512f) / (distance / fan.maxDistance);
            var target = direction.scale(acceleration * (fan.isPushing() ? 1 : -1));
            var current = entity.getDeltaMovement();
            entity.setDeltaMovement(current.add(Math.clamp(target.x - current.x, -5, 5) / 8,
                    Math.clamp(target.y - current.y, -5, 5) / 8, Math.clamp(target.z - current.z, -5, 5) / 8));
            entity.resetFallDistance();
            if (entity instanceof net.minecraft.server.level.ServerPlayer player) player.connection.resetFlyingTicks();
            double offset=entity.position().subtract(Vec3.atCenterOf(pos)).dot(direction);
            var type=types[Math.clamp((int)Math.ceil(offset),1,limit)];if(type==null)continue;
            if(entity instanceof ItemEntity item){
                if(level.isClientSide())type.spawnProcessingParticles(level,entity.position());
                else FanProcessing.process(item,type,this::awardProcessing);
            }else type.affectEntity(entity,level);
        }
        for (int i = 1; i <= limit; i++) {
            var current = pos.relative(facing, i);
            for (int y = 0; y <= (facing.getAxis().isVertical() ? 0 : 1); y++) {
                var location = current.below(y);
                var depot=BlockEntityBehaviour.get(level,location,DepotBehaviour.TYPE);
                if (depot!=null) {
                    var catalyst = FanProcessingType.getAt(level, location);
                    var type = catalyst != null ? catalyst : types[i];
                    if(type!=null){
                        if(level.isClientSide()){var held=depot.getHeldItem();if(held!=null)type.spawnProcessingParticles(level,depot.transportedHandler.getWorldPositionOf(held));}
                        else FanProcessing.process(depot,type,this::awardProcessing);
                    }
                } else if (level.getBlockEntity(location) instanceof BeltBlockEntity belt && BeltBlock.canTransportObjects(belt.getBlockState())) {
                    var catalyst = FanProcessingType.getAt(level, location.above());
                    var type = catalyst != null ? catalyst : types[i];
                    if(type!=null){
                        if(level.isClientSide()){
                            var controller=belt.getControllerBE();var handler=BlockEntityBehaviour.get(belt,com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TYPE);
                            if(controller!=null&&handler!=null)for(var item:controller.getInventory().getTransportedItems())if(item.beltPosition>=belt.index&&item.beltPosition<belt.index+1&&!item.stack.isEmpty())type.spawnProcessingParticles(level,handler.getWorldPositionOf(item));
                        }else FanProcessing.process(belt,type,this::awardProcessing);
                    }
                }
            }
        }
    }
}
