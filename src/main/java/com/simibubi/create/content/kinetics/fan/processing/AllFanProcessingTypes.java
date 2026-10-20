package com.simibubi.create.content.kinetics.fan.processing;

import com.simibubi.create.Create;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import java.util.List;
import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Genuine registered source types; the existing four recipe implementations remain the backend. */
public final class AllFanProcessingTypes {
    public static final BlastingType BLASTING=register("blasting",new BlastingType());
    public static final HauntingType HAUNTING=register("haunting",new HauntingType());
    public static final SmokingType SMOKING=register("smoking",new SmokingType());
    public static final SplashingType SPLASHING=register("splashing",new SplashingType());
    private static <T extends FanProcessingType>T register(String name,T type){return Registry.register(CreateBuiltInRegistries.FAN_PROCESSING_TYPE,Create.asResource(name),type);}
    public static void init(){FanProcessingTypeRegistry.ensureInitialized();}
    public static FanProcessingType ofLegacyName(String name){return switch(name){case "BLASTING","blasting"->BLASTING;case "HAUNTING","haunting"->HAUNTING;case "SMOKING","smoking"->SMOKING;case "SPLASHING","splashing"->SPLASHING;default->null;};}
    public static FanProcessingType parseLegacy(String name){var legacy=ofLegacyName(name);return legacy!=null?legacy:FanProcessingType.parse(name);}

    private abstract static class Builtin implements FanProcessingType {
        final FanProcessing.Type kind;
        private final int priority;
        Builtin(FanProcessing.Type kind,int priority){this.kind=kind;this.priority=priority;}
        @Override public boolean isValidAt(Level level,BlockPos pos){return kind.isValidBuiltinAt(level,pos);}
        @Override public int getPriority(){return priority;}
        @Override public boolean canProcess(ItemStack stack,Level level){return FanProcessing.builtinCanProcess(stack,level,kind);}
        @Override public List<ItemStack> process(ItemStack stack,Level level){return FanProcessing.builtinProcess(stack,level,kind);}
        @Override public void affectEntity(Entity entity,Level level){
            if(level.isClientSide()){
                if(kind==FanProcessing.Type.HAUNTING&&entity instanceof net.minecraft.world.entity.animal.equine.Horse){
                    var random=level.getRandom();var p=entity.getPosition(0);
                    var v=p.add(0,.5f,0).add(net.createmod.catnip.math.VecHelper.offsetRandomly(Vec3.ZERO,random,1).multiply(1,.2f,1).normalize().scale(1));
                    level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,v.x,v.y,v.z,0,.1f,0);
                    if(random.nextInt(3)==0)level.addParticle(ParticleTypes.LARGE_SMOKE,p.x,p.y+.5f,p.z,(random.nextFloat()-.5f)*.5f,.1f,(random.nextFloat()-.5f)*.5f);
                }
                return;
            }
            if(!(level instanceof net.minecraft.server.level.ServerLevel server))return;
            if(kind==FanProcessing.Type.SPLASHING){
                if(entity instanceof net.minecraft.world.entity.monster.Enderman||entity.getType()==net.minecraft.world.entity.EntityTypes.BLAZE||entity.getType()==net.minecraft.world.entity.EntityTypes.SNOW_GOLEM)entity.hurtServer(server,entity.damageSources().drown(),2);
                if(entity.isOnFire()){
                    entity.clearFire();var random=level.getRandom();
                    level.playSound(null,entity.blockPosition(),net.minecraft.sounds.SoundEvents.GENERIC_EXTINGUISH_FIRE,net.minecraft.sounds.SoundSource.NEUTRAL,.7f,1.6f+(random.nextFloat()-random.nextFloat())*.4f);
                }
            }else if((kind==FanProcessing.Type.SMOKING||kind==FanProcessing.Type.BLASTING)&&!entity.fireImmune()){
                entity.igniteForSeconds(kind==FanProcessing.Type.SMOKING?2:10);
                entity.hurtServer(server,kind==FanProcessing.Type.SMOKING?com.simibubi.create.foundation.damageTypes.CreateDamageSources.fanFire(level):com.simibubi.create.foundation.damageTypes.CreateDamageSources.fanLava(level),kind==FanProcessing.Type.SMOKING?2:4);
            }else if(kind==FanProcessing.Type.HAUNTING&&entity instanceof net.minecraft.world.entity.LivingEntity living){
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS,30,0,false,false));
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS,20,1,false,false));
                if(living instanceof net.minecraft.world.entity.animal.equine.Horse horse)HauntingHorseConversion.process(server,horse);
            }
        }
    }
    public static class BlastingType extends Builtin {
        public BlastingType(){super(FanProcessing.Type.BLASTING,100);}
        @Override public void spawnProcessingParticles(Level level,Vec3 pos){if(level.getRandom().nextInt(8)==0)level.addParticle(ParticleTypes.LARGE_SMOKE,pos.x,pos.y+.25,pos.z,0,1/16d,0);}
        @Override public void morphAirFlow(AirFlowParticleAccess access,RandomSource random){
            access.setColor(net.createmod.catnip.theme.Color.mixColors(0xFF4400,0xFF8855,random.nextFloat()));access.setAlpha(.5f);
            if(random.nextFloat()<1/32f)access.spawnExtraParticle(ParticleTypes.FLAME,.25f);
            if(random.nextFloat()<1/16f)access.spawnExtraParticle(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState()),.25f);
        }
    }
    public static class HauntingType extends Builtin {
        public HauntingType(){super(FanProcessing.Type.HAUNTING,300);}
        @Override public void spawnProcessingParticles(Level level,Vec3 pos){if(level.getRandom().nextInt(8)==0){pos=pos.add(net.createmod.catnip.math.VecHelper.offsetRandomly(Vec3.ZERO,level.getRandom(),1).multiply(1,.05f,1).normalize().scale(.15f));level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,pos.x,pos.y+.45f,pos.z,0,0,0);if(level.getRandom().nextInt(2)==0)level.addParticle(ParticleTypes.SMOKE,pos.x,pos.y+.25f,pos.z,0,0,0);}}
        @Override public void morphAirFlow(AirFlowParticleAccess access,RandomSource random){
            access.setColor(net.createmod.catnip.theme.Color.mixColors(0,0x126568,random.nextFloat()));access.setAlpha(1);
            if(random.nextFloat()<1/128f)access.spawnExtraParticle(ParticleTypes.SOUL_FIRE_FLAME,.125f);
            if(random.nextFloat()<1/32f)access.spawnExtraParticle(ParticleTypes.SMOKE,.125f);
        }
    }
    public static class SmokingType extends Builtin {
        public SmokingType(){super(FanProcessing.Type.SMOKING,200);}
        @Override public void spawnProcessingParticles(Level level,Vec3 pos){if(level.getRandom().nextInt(8)==0)level.addParticle(ParticleTypes.POOF,pos.x,pos.y+.25,pos.z,0,1/16d,0);}
        @Override public void morphAirFlow(AirFlowParticleAccess access,RandomSource random){
            access.setColor(net.createmod.catnip.theme.Color.mixColors(0,0x555555,random.nextFloat()));access.setAlpha(1);
            if(random.nextFloat()<1/32f)access.spawnExtraParticle(ParticleTypes.SMOKE,.125f);
            if(random.nextFloat()<1/32f)access.spawnExtraParticle(ParticleTypes.LARGE_SMOKE,.125f);
        }
    }
    public static class SplashingType extends Builtin {
        public SplashingType(){super(FanProcessing.Type.SPLASHING,400);}
        @Override public void spawnProcessingParticles(Level level,Vec3 pos){if(level.getRandom().nextInt(8)==0){var random=level.getRandom();level.addParticle(new net.minecraft.core.particles.DustParticleOptions(0x0055FF,1),pos.x+(random.nextFloat()-.5f)*.5f,pos.y+.5f,pos.z+(random.nextFloat()-.5f)*.5f,0,1/8f,0);level.addParticle(ParticleTypes.SPIT,pos.x+(random.nextFloat()-.5f)*.5f,pos.y+.5f,pos.z+(random.nextFloat()-.5f)*.5f,0,1/8f,0);}}
        @Override public void morphAirFlow(AirFlowParticleAccess access,RandomSource random){
            access.setColor(net.createmod.catnip.theme.Color.mixColors(0x4499FF,0x2277FF,random.nextFloat()));access.setAlpha(1);
            if(random.nextFloat()<1/32f)access.spawnExtraParticle(ParticleTypes.BUBBLE,.125f);
            if(random.nextFloat()<1/32f)access.spawnExtraParticle(ParticleTypes.BUBBLE_POP,.125f);
        }
    }
    private AllFanProcessingTypes(){}
}
