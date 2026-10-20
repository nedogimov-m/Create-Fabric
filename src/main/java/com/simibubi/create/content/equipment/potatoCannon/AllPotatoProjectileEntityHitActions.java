package com.simibubi.create.content.equipment.potatoCannon;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileEntityHitAction;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;



import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.food.FoodProperties;

import net.minecraft.world.food.Foods;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.component.SuspiciousStewEffects.Entry;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.entity.FakePlayer;



public class AllPotatoProjectileEntityHitActions {

	private static boolean registered;
	public static void init() {
        if(registered)return;registered=true;
		register("set_on_fire", SetOnFire.CODEC);
		register("potion_effect", PotionEffect.CODEC);
		register("food_effects", FoodEffects.CODEC);
		register("chorus_teleport", ChorusTeleport.CODEC);
		register("cure_zombie_villager", CureZombieVillager.CODEC);
		register("suspicious_stew", SuspiciousStew.CODEC);
	}


	private static void register(String name, MapCodec<? extends PotatoProjectileEntityHitAction> codec) {
		Registry.register(CreateBuiltInRegistries.POTATO_PROJECTILE_ENTITY_HIT_ACTION, Create.asResource(name), codec);
	}

	public record SetOnFire(int ticks) implements PotatoProjectileEntityHitAction {
		public static final MapCodec<SetOnFire> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			ExtraCodecs.POSITIVE_INT.fieldOf("ticks").forGetter(SetOnFire::ticks)
		).apply(instance, SetOnFire::new));

		public static SetOnFire seconds(int seconds) {
			return new SetOnFire(seconds * 20);
		}

		@Override
		public boolean execute(ItemStack projectile, EntityHitResult ray, Type type) {
			ray.getEntity()
				.setRemainingFireTicks(ticks);
			return false;
		}

		@Override
		public MapCodec<? extends PotatoProjectileEntityHitAction> codec() {
			return CODEC;
		}
	}

	public record PotionEffect(Holder<MobEffect> effect, int level, int ticks,
							   boolean recoverable) implements PotatoProjectileEntityHitAction {
		public static final MapCodec<PotionEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("effect").forGetter(PotionEffect::effect),
			ExtraCodecs.POSITIVE_INT.fieldOf("level").forGetter(PotionEffect::level),
			ExtraCodecs.POSITIVE_INT.fieldOf("ticks").forGetter(PotionEffect::ticks),
			Codec.BOOL.fieldOf("recoverable").forGetter(PotionEffect::recoverable)
		).apply(instance, PotionEffect::new));

		@Override
		public boolean execute(ItemStack projectile, EntityHitResult ray, Type type) {
			Entity entity = ray.getEntity();
			if (entity.level().isClientSide())
				return true;
			if (entity instanceof LivingEntity livingEntity)
				applyEffect(livingEntity, new MobEffectInstance(effect, ticks, level - 1));
			return !recoverable;
		}

		@Override
		public MapCodec<? extends PotatoProjectileEntityHitAction> codec() {
			return CODEC;
		}
	}

    /** Original1.21 food JSON retains its actual effects after26.3 moved those fields to Consumable. */
    public record PossibleEffect(MobEffectInstance effect,float probability) {
        public static final Codec<PossibleEffect> CODEC=RecordCodecBuilder.create(i->i.group(
            MobEffectInstance.CODEC.fieldOf("effect").forGetter(PossibleEffect::effect),
            Codec.floatRange(0,1).optionalFieldOf("probability",1f).forGetter(PossibleEffect::probability)
        ).apply(i,PossibleEffect::new));
        public PossibleEffect{effect=new MobEffectInstance(effect);}
        @Override public MobEffectInstance effect(){return new MobEffectInstance(effect);}
    }
    private record FoodData(FoodProperties property,java.util.List<PossibleEffect> effects){
        static final Codec<FoodData> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.intRange(0,Integer.MAX_VALUE).fieldOf("nutrition").forGetter(value->value.property.nutrition()),
            Codec.floatRange(0,Float.MAX_VALUE).fieldOf("saturation").forGetter(value->value.property.saturation()),
            Codec.BOOL.optionalFieldOf("can_always_eat",false).forGetter(value->value.property.canAlwaysEat()),
            PossibleEffect.CODEC.listOf().optionalFieldOf("effects",java.util.List.of()).forGetter(FoodData::effects)
        ).apply(i,(nutrition,saturation,always,effects)->new FoodData(new FoodProperties(nutrition,saturation,always),effects)));
    }
    public record FoodEffects(FoodProperties foodProperty,boolean recoverable,java.util.List<PossibleEffect> effects) implements PotatoProjectileEntityHitAction {
        public static final MapCodec<FoodEffects> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
            FoodData.CODEC.fieldOf("food_property").forGetter(value->new FoodData(value.foodProperty,value.effects)),
            Codec.BOOL.fieldOf("recoverable").forGetter(FoodEffects::recoverable)
        ).apply(i,(food,recoverable)->new FoodEffects(food.property,recoverable,food.effects)));
        public FoodEffects{effects=java.util.List.copyOf(effects);}
        public FoodEffects(FoodProperties properties,boolean recoverable){this(properties,recoverable,nativeEffects(properties));}
        private static java.util.List<PossibleEffect> nativeEffects(FoodProperties properties){
            var result=new java.util.ArrayList<PossibleEffect>();
            for(var item:BuiltInRegistries.ITEM){var stack=item.getDefaultInstance();if(stack.get(DataComponents.FOOD)!=properties)continue;
                var consumable=stack.get(DataComponents.CONSUMABLE);if(consumable==null)continue;
                for(var action:consumable.onConsumeEffects())if(action instanceof net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect status)for(var effect:status.effects())result.add(new PossibleEffect(effect,status.probability()));
                break;
            }return result;
        }
        @Override public boolean execute(ItemStack projectile,EntityHitResult ray,Type type){
            var entity=ray.getEntity();if(entity.level().isClientSide())return true;
            if(entity instanceof LivingEntity living)for(var effect:effects)if(living.getRandom().nextFloat()<effect.probability())applyEffect(living,effect.effect());
            return !recoverable;
        }
        @Override public MapCodec<? extends PotatoProjectileEntityHitAction> codec(){return CODEC;}
    }

	public record ChorusTeleport(double teleportDiameter) implements PotatoProjectileEntityHitAction {
		public static final MapCodec<ChorusTeleport> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Codec.doubleRange(Double.MIN_NORMAL,Double.MAX_VALUE).fieldOf("teleport_diameter").forGetter(ChorusTeleport::teleportDiameter)
		).apply(instance, ChorusTeleport::new));

		@Override
		public boolean execute(ItemStack projectile, EntityHitResult ray, Type type) {
			Entity entity = ray.getEntity();
			Level level = entity.level();
			if (level.isClientSide())
				return true;
			if (!(entity instanceof LivingEntity livingEntity))
				return false;

			double entityX = livingEntity.getX();
			double entityY = livingEntity.getY();
			double entityZ = livingEntity.getZ();

			for (int teleportTry = 0; teleportTry < 16; ++teleportTry) {
				double teleportX = entityX + (livingEntity.getRandom()
					.nextDouble() - 0.5D) * teleportDiameter;
				double teleportY = Mth.clamp(entityY + (livingEntity.getRandom()
					.nextInt((int) teleportDiameter) - (int) (teleportDiameter / 2)), 0.0D, level.getHeight() - 1);
				double teleportZ = entityZ + (livingEntity.getRandom()
					.nextDouble() - 0.5D) * teleportDiameter;

                var candidate=BlockPos.containing(teleportX,teleportY,teleportZ);
                if(!com.simibubi.create.foundation.utility.LoadedLevelAccess.isLoaded(level,candidate))continue;
                if (livingEntity.randomTeleport(teleportX,teleportY,teleportZ,true,state->false)) {
					if (livingEntity.isPassenger())
						livingEntity.stopRiding();

					SoundEvent soundevent =
						livingEntity instanceof Fox ? SoundEvents.FOX_TELEPORT : SoundEvents.CHORUS_FRUIT_TELEPORT;
					level.playSound(null, entityX, entityY, entityZ, soundevent, SoundSource.PLAYERS, 1.0F, 1.0F);
					livingEntity.playSound(soundevent, 1.0F, 1.0F);
					livingEntity.setDeltaMovement(Vec3.ZERO);
					return true;
				}
			}

			return false;
		}

		@Override
		public MapCodec<? extends PotatoProjectileEntityHitAction> codec() {
			return CODEC;
		}
	}

	public enum CureZombieVillager implements PotatoProjectileEntityHitAction {
		INSTANCE;

		// Item components are not bound while action codecs are registered in 26.3.
		// Resolve the native golden-apple effects on the first actual fallback hit.
		private static final class Fallback {
			private static final FoodEffects EFFECT = new FoodEffects(Foods.GOLDEN_APPLE, false);
		}
		private static final GameProfile ZOMBIE_CONVERTER_NAME =
			new GameProfile(UUID.fromString("be12d3dc-27d3-4992-8c97-66be53fd49c5"), "Converter");


		public static final MapCodec<CureZombieVillager> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public boolean execute(ItemStack projectile, EntityHitResult ray, Type type) {
			Entity entity = ray.getEntity();
			Level world = entity.level();

			if (!(entity instanceof ZombieVillager zombieVillager) || !zombieVillager.hasEffect(MobEffects.WEAKNESS))
				return Fallback.EFFECT.execute(projectile, ray, type);
			if (world.isClientSide())
				return false;

			FakePlayer dummy = FakePlayer.get((ServerLevel)world,ZOMBIE_CONVERTER_NAME);
			dummy.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLDEN_APPLE, 1));
			zombieVillager.mobInteract(dummy, InteractionHand.MAIN_HAND);
			return true;
		}

		@Override
		public MapCodec<? extends PotatoProjectileEntityHitAction> codec() {
			return CODEC;
		}
	}

	public enum SuspiciousStew implements PotatoProjectileEntityHitAction {
		INSTANCE;

		public static final MapCodec<SuspiciousStew> CODEC = MapCodec.unit(INSTANCE);

		@Override
		public boolean execute(ItemStack projectile, EntityHitResult ray, Type type) {
			if (ray.getEntity() instanceof LivingEntity livingEntity) {
				SuspiciousStewEffects stew = projectile.getOrDefault(DataComponents.SUSPICIOUS_STEW_EFFECTS, SuspiciousStewEffects.EMPTY);
				for (Entry effect : stew.effects())
					livingEntity.addEffect(effect.createEffectInstance());
			}

			return false;
		}

		@Override
		public MapCodec<? extends PotatoProjectileEntityHitAction> codec() {
			return CODEC;
		}
	}

	private static void applyEffect(LivingEntity entity, MobEffectInstance effect) {
		if (effect.getEffect().value().isInstantaneous()) {
			effect.getEffect().value()
				.applyInstantaneousEffect((ServerLevel)entity.level(),null,null,entity,effect.getDuration(),1.0);
		} else {
			entity.addEffect(effect);
		}
	}
}
