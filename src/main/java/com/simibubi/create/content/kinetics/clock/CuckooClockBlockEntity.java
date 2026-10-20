package com.simibubi.create.content.kinetics.clock;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.damageTypes.CreateDamageSources;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.animation.LerpedFloat.Chaser;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.phys.Vec3;
/** Original two clock announcements, mechanical pause, transient animation and genuine surprise explosion. */
public class CuckooClockBlockEntity extends KineticBlockEntity {
 public LerpedFloat hourHand=LerpedFloat.angular(),minuteHand=LerpedFloat.angular(),animationProgress=LerpedFloat.linear();
 public enum Animation {PIG,CREEPER,SURPRISE,NONE}
 public Animation animationType=Animation.NONE;
 private boolean sendAnimationUpdate;
 private java.util.UUID advancementOwner;
 public java.util.UUID advancementOwner(){return advancementOwner;}
 public void setAdvancementOwner(net.minecraft.server.level.ServerPlayer player){advancementOwner=player.getUUID();setChanged();}
 public void clearAdvancementOwner(){if(com.simibubi.create.foundation.advancement.AdvancementBehaviour.hasPendingFor(this,advancementOwner))return;advancementOwner=null;setChanged();}

 public CuckooClockBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.CUCKOO_CLOCK,pos,state);}
 public static void tick(Level level,BlockPos pos,BlockState state,CuckooClockBlockEntity clock){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, clock)) {
            if (!createTick.shouldTick()) return;
clock.tick();
        }
    }
 public void tick(){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, worldPosition, getBlockState(), this)) {
            if (!createTick.shouldTick()) return;
KineticBlockEntity.tick(level,worldPosition,getBlockState(),this);tickClock();
        }
    }
 @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){var tag=super.getUpdateTag(registries);if(sendAnimationUpdate&&animationType!=null)tag.putString("Animation",animationType.name());sendAnimationUpdate=false;return tag;}
 @Override protected void read(ValueInput input, boolean clientPacket){super.read(input, clientPacket);if(level!=null&&level.isClientSide())input.getString("Animation").ifPresent(name->{try{animationType=Animation.valueOf(name);}catch(IllegalArgumentException invalid){animationType=Animation.NONE;}animationProgress.startWithValue(0);});}
 private Vec3 offsetRandomly(Vec3 vector,float radius){var random=level.getRandom();return new Vec3(vector.x+(random.nextFloat()-.5f)*2*radius,vector.y+(random.nextFloat()-.5f)*2*radius,vector.z+(random.nextFloat()-.5f)*2*radius);}
	private void tickClock() {
		if (getSpeed() == 0)
			return;


		boolean isNatural = !level.dimensionType().hasFixedTime();
		int dayTime = (int) ((level.getOverworldClockTime() * (isNatural ? 1 : 24)) % 24000);
		int hours = (dayTime / 1000 + 6) % 24;
		int minutes = (dayTime % 1000) * 60 / 1000;

		if (!isNatural) {
			if (level.isClientSide()) {
				moveHands(hours, minutes);

				if (level.getGameTime() % 6 == 0)
					playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1 / 16f, 2f);
				else if (level.getGameTime() % 3 == 0)
					playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1 / 16f, 1.5f);
			}
			return;
		}

		if (!level.isClientSide()) {
			if (animationType == Animation.NONE) {
				if (hours == 12 && minutes < 5)
					startAnimation(Animation.PIG);
				if (hours == 18 && minutes < 36 && minutes > 31)
					startAnimation(Animation.CREEPER);
			} else {
				float value = animationProgress.getValue();
				animationProgress.setValue(value + 1);
				if (value > 100)
					animationType = Animation.NONE;

				if (animationType == Animation.SURPRISE && Mth.equal(animationProgress.getValue(), 50)) {
					Vec3 center = Vec3.atCenterOf(worldPosition);
					level.destroyBlock(worldPosition, false);
					DamageSource damageSource = CreateDamageSources.cuckooSurprise(level);
					level.explode(null, damageSource, null, center.x, center.y, center.z, 3, false,
						ExplosionInteraction.BLOCK);
				}

			}
		}

		if (level.isClientSide()) {
			moveHands(hours, minutes);

			if (animationType == Animation.NONE) {
				if (level.getGameTime() % 32 == 0)
					playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1 / 16f, 2f);
				else if (level.getGameTime() % 16 == 0)
					playSound(SoundEvents.NOTE_BLOCK_HAT.value(), 1 / 16f, 1.5f);
			} else {

				boolean isSurprise = animationType == Animation.SURPRISE;
				float value = animationProgress.getValue();
				animationProgress.setValue(value + 1);
				if (value > 100)
					animationType = null;

				// sounds

				if (value == 1)
					playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2, .5f);
				if (value == 21)
					playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 2, 0.793701f);

				if (value > 30 && isSurprise) {
					Vec3 pos = offsetRandomly(Vec3.atCenterOf(this.worldPosition), .5f);
					level.addParticle(ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 0, 0, 0);
				}
				if (value == 40 && isSurprise)
					playSound(SoundEvents.TNT_PRIMED, 1f, 1f);

				int step = isSurprise ? 3 : 15;
				for (int phase = 30; phase <= 60; phase += step) {
					if (value == phase - step / 3)
						playSound(SoundEvents.CHEST_OPEN, 1 / 16f, 2f);
					if (value == phase) {
						if (animationType == Animation.PIG)
							playSound(SoundEvents.PIG_SOUNDS.get(net.minecraft.world.entity.animal.pig.PigSoundVariants.SoundSet.CLASSIC).adultSounds().ambientSound().value(), 1 / 4f, 1f);
						else
							playSound(SoundEvents.CREEPER_HURT, 1 / 4f, 3f);
					}
					if (value == phase + step / 3)
						playSound(SoundEvents.CHEST_CLOSE, 1 / 16f, 2f);

				}

			}

			return;
		}
	}
	public void startAnimation(Animation animation) {
		animationType = animation;
		if (animation != null && CuckooClockBlock.containsSurprise(getBlockState()))
			animationType = Animation.SURPRISE;
		animationProgress.startWithValue(0);
		sendAnimationUpdate = true;

		if (animation == Animation.CREEPER)
			CuckooClockAdvancements.awardNear(this);

		setChanged(); sync();
	}

	public void moveHands(int hours, int minutes) {
		float hourTarget = (float) (360 / 12 * (hours % 12));
		float minuteTarget = (float) (360 / 60 * minutes);

		hourHand.chase(hourTarget, .2f, Chaser.EXP);
		minuteHand.chase(minuteTarget, .2f, Chaser.EXP);

		hourHand.tickChaser();
		minuteHand.tickChaser();
	}

	private void playSound(SoundEvent sound, float volume, float pitch) {
		Vec3 vec = Vec3.atCenterOf(worldPosition);
		level.playLocalSound(vec.x, vec.y, vec.z, sound, SoundSource.BLOCKS, volume, pitch, false);
	}
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        super.addNativeAdvancementIds(ids);
        ids.add("cuckoo_clock");
    }
    private final com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage canonicalAdvancementOwner=new com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage() {
        @Override public java.util.UUID getPlayerId(){return advancementOwner;}
        @Override public void setPlayerId(java.util.UUID id){advancementOwner=id;}
    };
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return canonicalAdvancementOwner;}
}
