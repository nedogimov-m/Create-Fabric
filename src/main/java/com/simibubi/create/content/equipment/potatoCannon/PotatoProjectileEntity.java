package com.simibubi.create.content.equipment.potatoCannon;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllEnchantments;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.potatoCannon.PotatoCannonProjectileType;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileRenderMode;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.equipment.potatoCannon.AllPotatoProjectileRenderModes.StuckToEntity;
import com.simibubi.create.content.equipment.EquipmentAdvancements;
import com.simibubi.create.foundation.damageTypes.CreateDamageSources;
import com.simibubi.create.foundation.particle.AirParticleData;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.syncher.*;
import java.util.UUID;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class PotatoProjectileEntity extends AbstractHurtingProjectile {

	protected PotatoCannonProjectileType type;
	protected ItemStack stack = ItemStack.EMPTY;

	protected Entity stuckEntity;
	protected Vec3 stuckOffset;
	protected PotatoProjectileRenderMode stuckRenderer;
	protected double stuckFallSpeed;

	protected float additionalDamageMult = 1;
	protected float additionalKnockback = 0;
	protected float recoveryChance = 0;

	public PotatoProjectileEntity(EntityType<? extends AbstractHurtingProjectile> type, Level level) {
		super(type, level);
        resolveType();
	}

    private static final EntityDataAccessor<ItemStack> DATA_ITEM=SynchedEntityData.defineId(PotatoProjectileEntity.class,EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_STUCK=SynchedEntityData.defineId(PotatoProjectileEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3fc> DATA_OFFSET=SynchedEntityData.defineId(PotatoProjectileEntity.class,EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_FALL=SynchedEntityData.defineId(PotatoProjectileEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_DAMAGE=SynchedEntityData.defineId(PotatoProjectileEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_KNOCKBACK=SynchedEntityData.defineId(PotatoProjectileEntity.class,EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_RECOVERY=SynchedEntityData.defineId(PotatoProjectileEntity.class,EntityDataSerializers.FLOAT);
    private UUID savedStuckEntity;
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder){super.defineSynchedData(builder);builder.define(DATA_ITEM,ItemStack.EMPTY);builder.define(DATA_STUCK,-1);builder.define(DATA_OFFSET,new Vector3f());builder.define(DATA_FALL,0f);builder.define(DATA_DAMAGE,1f);builder.define(DATA_KNOCKBACK,0f);builder.define(DATA_RECOVERY,0f);}
    private void resolveType(){type=PotatoCannonProjectileType.getTypeForItem(registryAccess(),stack.getItem()).orElseGet(()->registryAccess().lookupOrThrow(CreateRegistries.POTATO_PROJECTILE_TYPE).getOrThrow(AllPotatoProjectileTypes.FALLBACK)).value();}
    public void setItem(ItemStack item){stack=item.copy();entityData.set(DATA_ITEM,stack.copy());resolveType();}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
        super.onSyncedDataUpdated(key);
        if(key==DATA_DAMAGE)additionalDamageMult=entityData.get(DATA_DAMAGE);
        if(key==DATA_KNOCKBACK)additionalKnockback=entityData.get(DATA_KNOCKBACK);
        if(key==DATA_RECOVERY)recoveryChance=entityData.get(DATA_RECOVERY);
        if(key==DATA_ITEM){stack=entityData.get(DATA_ITEM).copy();resolveType();}
        if(level().isClientSide()&&(key==DATA_STUCK||key==DATA_OFFSET||key==DATA_FALL)){
            var vector=entityData.get(DATA_OFFSET);stuckOffset=new Vec3(vector.x(),vector.y(),vector.z());stuckRenderer=new StuckToEntity(stuckOffset);stuckFallSpeed=entityData.get(DATA_FALL);
            stuckEntity=level().getEntity(entityData.get(DATA_STUCK));
        }
    }

	public void setEnchantmentEffectsFromCannon(ItemStack cannon) {
		var enchantmentRegistry = registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

		int recovery = EnchantmentHelper.getItemEnchantmentLevel(enchantmentRegistry.getOrThrow(AllEnchantments.POTATO_RECOVERY),cannon);

		if (recovery > 0)
			setRecoveryChance(.125f+recovery*.125f);
	}

    public void setRecoveryChance(float chance){recoveryChance=Math.clamp(finite(chance,0),0,1);entityData.set(DATA_RECOVERY,recoveryChance);}
    private void syncModifiers(){entityData.set(DATA_DAMAGE,additionalDamageMult);entityData.set(DATA_KNOCKBACK,additionalKnockback);entityData.set(DATA_RECOVERY,recoveryChance);}
    // These were inherited from the old AbstractHurtingProjectile; native26.3 makes pickability tag-driven.
    @Override public boolean isPickable(){return true;}
    @Override public float getPickRadius(){return 1;}
	public ItemStack getItem() {
		return stack.copy();
	}

	@Nullable
	public PotatoCannonProjectileType getProjectileType() {
		return type;
	}

    @Override protected void readAdditionalSaveData(ValueInput input){
        super.readAdditionalSaveData(input);setItem(input.read("Item",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
        additionalDamageMult=finite(input.getFloatOr("AdditionalDamage",1),1);additionalKnockback=finite(input.getFloatOr("AdditionalKnockback",0),0);recoveryChance=finite(input.getFloatOr("Recovery",0),0);
        savedStuckEntity=input.read("StuckEntity",net.minecraft.core.UUIDUtil.CODEC).orElse(null);stuckOffset=input.read("StuckOffset",Vec3.CODEC).orElse(Vec3.ZERO);stuckFallSpeed=input.getDoubleOr("StuckFallSpeed",0);if(!Double.isFinite(stuckFallSpeed))stuckFallSpeed=0;stuckRenderer=new StuckToEntity(stuckOffset);syncModifiers();
    }
    private static float finite(float value,float fallback){return Float.isFinite(value)?value:fallback;}
    @Override protected void addAdditionalSaveData(ValueOutput output){
        super.addAdditionalSaveData(output);output.store("Item",ItemStack.OPTIONAL_CODEC,stack);output.putFloat("AdditionalDamage",additionalDamageMult);output.putFloat("AdditionalKnockback",additionalKnockback);output.putFloat("Recovery",recoveryChance);
        if(stuckEntity!=null&&stuckEntity.isAlive()){output.store("StuckEntity",net.minecraft.core.UUIDUtil.CODEC,stuckEntity.getUUID());output.store("StuckOffset",Vec3.CODEC,stuckOffset);output.putDouble("StuckFallSpeed",stuckFallSpeed);}
        else if(savedStuckEntity!=null){output.store("StuckEntity",net.minecraft.core.UUIDUtil.CODEC,savedStuckEntity);output.store("StuckOffset",Vec3.CODEC,stuckOffset);output.putDouble("StuckFallSpeed",stuckFallSpeed);}
    }

	@Nullable
	public Entity getStuckEntity() {
        if(stuckEntity==null&&savedStuckEntity!=null&&level() instanceof ServerLevel server){stuckEntity=server.getEntity(savedStuckEntity);if(stuckEntity!=null){savedStuckEntity=null;entityData.set(DATA_STUCK,stuckEntity.getId());syncStuck();}}
        if(stuckEntity==null&&level().isClientSide()&&entityData.get(DATA_STUCK)>=0)stuckEntity=level().getEntity(entityData.get(DATA_STUCK));
        if (stuckEntity == null)return null;
		if (!stuckEntity.isAlive())
			return null;
		return stuckEntity;
	}

	public void setStuckEntity(Entity stuckEntity) {
		this.stuckEntity = stuckEntity;
		this.stuckOffset = position().subtract(stuckEntity.position());
		this.stuckRenderer = new StuckToEntity(stuckOffset);
		this.stuckFallSpeed = 0.0;
        setDeltaMovement(Vec3.ZERO);savedStuckEntity=null;entityData.set(DATA_STUCK,stuckEntity.getId());syncStuck();
    }
    private void syncStuck(){if(stuckOffset!=null){entityData.set(DATA_OFFSET,new Vector3f((float)stuckOffset.x,(float)stuckOffset.y,(float)stuckOffset.z));entityData.set(DATA_FALL,(float)stuckFallSpeed);}}

	public PotatoProjectileRenderMode getRenderMode() {
		if (getStuckEntity() != null)
			return stuckRenderer;

		return type.renderMode();
	}

	@Override
	public void tick() {
        if(type==null)resolveType();if(!level().isClientSide())syncModifiers();
        Entity stuckEntity = getStuckEntity();
		if (stuckEntity != null) {
			if (getY() < stuckEntity.getY() - 0.1) {
				pop(position());
				remove(Entity.RemovalReason.KILLED);
			} else {
				stuckFallSpeed += 0.007 * type.gravityMultiplier();
				stuckOffset = stuckOffset.add(0, -stuckFallSpeed, 0);
				Vec3 pos = stuckEntity.position()
					.add(stuckOffset);
                setPos(pos.x,pos.y,pos.z);if(!level().isClientSide())syncStuck();
			}
		} else {
			setDeltaMovement(getDeltaMovement().add(0, -0.05 * type.gravityMultiplier(), 0)
				.scale(type.drag()));
		}

		super.tick();
	}

	@Override
	protected float getInertia() {
		return 1;
	}

	@Override
	protected ParticleOptions getTrailParticle() {
		return new AirParticleData(1, 10);
	}

	@Override
	protected boolean shouldBurn() {
		return false;
	}

	@Override
	protected void onHitEntity(EntityHitResult ray) {
		super.onHitEntity(ray);

		if (getStuckEntity() != null)
			return;

		Vec3 hit = ray.getLocation();
		Entity target = ray.getEntity();
		float damage = type.damage() * additionalDamageMult;
		float knockback = type.knockback() + additionalKnockback;
		Entity owner = this.getOwner();

		if (!target.isAlive())
			return;
		if (owner instanceof LivingEntity livingEntity)
			livingEntity.setLastHurtMob(target);

		if (target instanceof PotatoProjectileEntity ppe) {
			if (tickCount < 10 && target.tickCount < 10)
				return;
			if (ppe.getProjectileType() != getProjectileType()) {
				if (owner instanceof Player p)
					EquipmentAdvancements.award(p,"potato_cannon_collide");
				if (ppe.getOwner() instanceof Player p)
					EquipmentAdvancements.award(p,"potato_cannon_collide");
			}
		}

		pop(hit);

		if (target instanceof WitherBoss && ((WitherBoss) target).isPowered())
			return;
		if (type.preEntityHit(stack, ray))
			return;

		boolean targetIsEnderman = target.getType() == net.minecraft.world.entity.EntityTypes.ENDERMAN;
		int k = target.getRemainingFireTicks();
		if (this.isOnFire() && !targetIsEnderman)
			target.igniteForSeconds(5);

		boolean onServer = !level().isClientSide();
		DamageSource damageSource = causePotatoDamage();
		if (onServer && !target.hurtServer((ServerLevel)level(),damageSource,damage)) {
			target.setRemainingFireTicks(k);
			remove(Entity.RemovalReason.KILLED);
			return;
		}

		if (targetIsEnderman)
			return;

		if (!type.onEntityHit(stack, ray) && onServer) {
			if (random.nextDouble() <= recoveryChance) {
				recoverItem();
			} else {
				spawnAtLocation((ServerLevel)level(),type.dropStack());
			}
		}

		if (!(target instanceof LivingEntity livingentity)) {
			playHitSound(level(), position());
			remove(Entity.RemovalReason.KILLED);
			return;
		}

		if (type.reloadTicks() < 10)
			livingentity.setInvulnerableTime(type.reloadTicks() + 10);

		if (onServer && knockback > 0) {
			Vec3 appliedMotion = this.getDeltaMovement()
				.multiply(1.0D, 0.0D, 1.0D)
				.normalize();
			if (appliedMotion.lengthSqr() > 0.0D)
				livingentity.knockback(knockback*.6,-appliedMotion.x,-appliedMotion.z,damageSource,damage);
		}

		if (onServer && owner instanceof LivingEntity) {
			EnchantmentHelper.doPostAttackEffects((ServerLevel) level(), livingentity, damageSource);
		}

		if (livingentity != owner && livingentity instanceof Player && owner instanceof ServerPlayer
			&& !this.isSilent()) {
			((ServerPlayer) owner).connection
				.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.PLAY_ARROW_HIT_SOUND, 0.0F));
		}

		if (onServer && owner instanceof ServerPlayer serverplayerentity) {
			if (!target.isAlive() && target.getType()
				.getCategory() == MobCategory.MONSTER || (target instanceof Player && target != owner))
				EquipmentAdvancements.award(serverplayerentity,"potato_cannon");
		}

		if (type.sticky() && target.isAlive()) {
			setStuckEntity(target);
		} else {
			remove(Entity.RemovalReason.KILLED);
		}

	}

	private void recoverItem() {
		if (!stack.isEmpty())
			spawnAtLocation((ServerLevel)level(),stack.copyWithCount(1));
	}

	public static void playHitSound(Level world, Vec3 location) {
		world.playSound(null,BlockPos.containing(location),AllSoundEvents.POTATO_HIT,net.minecraft.sounds.SoundSource.BLOCKS,1,1);
	}

	public static void playLaunchSound(Level world, Vec3 location, float pitch) {
		world.playLocalSound(location.x,location.y,location.z,AllSoundEvents.FWOOMP,net.minecraft.sounds.SoundSource.BLOCKS,1,pitch,true);
	}

	@Override
	protected void onHitBlock(BlockHitResult ray) {
		Vec3 hit = ray.getLocation();
		pop(hit);
		if (!type.onBlockHit(level(), stack, ray) && !level().isClientSide()) {
			if (random.nextDouble() <= recoveryChance) {
				recoverItem();
			} else {
				spawnAtLocation((ServerLevel)level(),getProjectileType().dropStack());
			}
		}

		super.onHitBlock(ray);
		remove(Entity.RemovalReason.KILLED);
	}

	@Override
	public boolean hurtServer(ServerLevel server,@NotNull DamageSource source,float amt) {
		if (source.is(DamageTypeTags.IS_FIRE))
			return false;
		if (this.isInvulnerableToBase(source))
			return false;
		pop(position());
		remove(Entity.RemovalReason.KILLED);
		return true;
	}

    @Override public boolean hurtClient(DamageSource source){if(source.is(DamageTypeTags.IS_FIRE)||isInvulnerableToBase(source))return false;pop(position());remove(Entity.RemovalReason.KILLED);return true;}
	private void pop(Vec3 hit) {
		if (!stack.isEmpty()) {
			for (int i = 0; i < 7; i++) {
				Vec3 m = new Vec3((random.nextFloat()-.5)*.5,(random.nextFloat()-.5)*.5,(random.nextFloat()-.5)*.5);
				level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack)), hit.x, hit.y, hit.z, m.x, m.y,
					m.z);
			}
		}
		if (!level().isClientSide())
			playHitSound(level(), position());
	}

	private DamageSource causePotatoDamage() {
		return CreateDamageSources.potatoCannon(level(), this, getOwner());
	}

	@SuppressWarnings("unchecked")
	public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
		EntityType.Builder<PotatoProjectileEntity> entityBuilder = (EntityType.Builder<PotatoProjectileEntity>) builder;
		return entityBuilder.sized(.25f, .25f);
	}

}
