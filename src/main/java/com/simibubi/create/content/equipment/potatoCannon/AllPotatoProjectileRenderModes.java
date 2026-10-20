package com.simibubi.create.content.equipment.potatoCannon;

import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.Create;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileRenderMode;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/** Original public polymorphic render modes, applied only during genuine native client extraction. */
public final class AllPotatoProjectileRenderModes {
    private static boolean registered;
    public static void init(){if(registered)return;registered=true;register("billboard",Billboard.CODEC);register("tumble",Tumble.CODEC);register("toward_motion",TowardMotion.CODEC);register("stuck_to_entity",StuckToEntity.CODEC);}
    private static void register(String id,MapCodec<? extends PotatoProjectileRenderMode> codec){Registry.register(CreateBuiltInRegistries.POTATO_PROJECTILE_RENDER_MODE,Create.asResource(id),codec);}
    public enum Billboard implements PotatoProjectileRenderMode {
        INSTANCE;
        public static final MapCodec<Billboard> CODEC=MapCodec.unit(INSTANCE);
        @Override public void transform(Matrix4f pose,PotatoProjectileEntity entity,float partial,@Nullable Vec3 cameraEye){
            if(cameraEye==null)return;
            var diff=entity.getBoundingBox().getCenter().subtract(cameraEye);
            pose.rotate(Axis.YP.rotationDegrees((float)Math.toDegrees(Math.atan2(diff.x,diff.z))+180));
            pose.rotate(Axis.XP.rotationDegrees((float)Math.toDegrees(Math.atan2(diff.y,Math.hypot(diff.x,diff.z)))));
        }
        @Override public MapCodec<? extends PotatoProjectileRenderMode> codec(){return CODEC;}
    }
    public enum Tumble implements PotatoProjectileRenderMode {
        INSTANCE;
        public static final MapCodec<Tumble> CODEC=MapCodec.unit(INSTANCE);
        @Override public void transform(Matrix4f pose,PotatoProjectileEntity entity,float partial,@Nullable Vec3 cameraEye){Billboard.INSTANCE.transform(pose,entity,partial,cameraEye);pose.rotate(Axis.ZP.rotationDegrees((entity.tickCount+partial)*2*entityRandom(entity,16)));pose.rotate(Axis.XP.rotationDegrees((entity.tickCount+partial)*entityRandom(entity,32)));}
        @Override public MapCodec<? extends PotatoProjectileRenderMode> codec(){return CODEC;}
    }
    public record TowardMotion(int spriteAngleOffset,float spin) implements PotatoProjectileRenderMode {
        public static final MapCodec<TowardMotion> CODEC=RecordCodecBuilder.mapCodec(i->i.group(Codec.INT.fieldOf("sprite_angle_offset").forGetter(TowardMotion::spriteAngleOffset),Codec.FLOAT.fieldOf("spin").forGetter(TowardMotion::spin)).apply(i,TowardMotion::new));
        @Override public void transform(Matrix4f pose,PotatoProjectileEntity entity,float partial,@Nullable Vec3 cameraEye){var diff=entity.getDeltaMovement();pose.rotate(Axis.YP.rotationDegrees((float)Math.toDegrees(Math.atan2(diff.x,diff.z))));pose.rotate(Axis.XP.rotationDegrees(270+(float)Math.toDegrees(Math.atan2(diff.y,-Math.hypot(diff.x,diff.z)))));pose.rotate(Axis.YP.rotationDegrees((entity.tickCount+partial)*20*spin+entityRandom(entity,360)));pose.rotate(Axis.ZP.rotationDegrees(-spriteAngleOffset));}
        @Override public MapCodec<? extends PotatoProjectileRenderMode> codec(){return CODEC;}
    }
    public record StuckToEntity(Vec3 offset) implements PotatoProjectileRenderMode {
        public static final MapCodec<StuckToEntity> CODEC=RecordCodecBuilder.mapCodec(i->i.group(Vec3.CODEC.fieldOf("offset").forGetter(StuckToEntity::offset)).apply(i,StuckToEntity::new));
        @Override public void transform(Matrix4f pose,PotatoProjectileEntity entity,float partial,@Nullable Vec3 cameraEye){pose.rotate(Axis.YP.rotationDegrees((float)Math.toDegrees(Math.atan2(offset.x,offset.z))));}
        @Override public MapCodec<? extends PotatoProjectileRenderMode> codec(){return CODEC;}
    }
    private static int entityRandom(PotatoProjectileEntity entity,int max){return System.identityHashCode(entity)*31%max;}
    private AllPotatoProjectileRenderModes(){}
}
