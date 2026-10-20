package com.simibubi.create.api.contraption.dispenser;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.mixin.accessor.ProjectileDispenseBehaviorAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.ProjectileDispenseBehavior;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Source .7 offset, registered power/uncertainty and contraption velocity. */
public abstract class MountedProjectileDispenseBehavior extends DefaultMountedDispenseBehavior {
    @Override protected ItemStack execute(ItemStack stack,MovementContext context,BlockPos pos,Vec3 facing) {
        var projectile=getProjectile(context.world,pos.getX()+facing.x*.7+.5,pos.getY()+facing.y*.7+.5,pos.getZ()+facing.z*.7+.5,stack.copy(),MountedDispenseBehavior.getClosestFacingDirection(facing));
        if(projectile==null)return stack;
        var velocity=facing.scale(getPower()).add(context.motion);
        projectile.shoot(velocity.x,velocity.y,velocity.z,(float)velocity.length(),getUncertainty());
        if(context.world.addFreshEntity(projectile)){
            projectile.applyOnProjectileSpawned((ServerLevel)context.world,stack);
            stack.shrink(1);
        }
        return stack;
    }
    @Override protected void playSound(LevelAccessor level,BlockPos pos) { level.levelEvent(getDispenseEvent(),pos,0); }
    protected int getDispenseEvent() { return LevelEvent.SOUND_DISPENSER_PROJECTILE_LAUNCH; }
    protected abstract @Nullable Projectile getProjectile(Level level,double x,double y,double z,ItemStack stack,Direction facing);
    protected float getUncertainty() { return 6; }
    protected float getPower() { return 1.1f; }
    public static MountedDispenseBehavior of(ProjectileDispenseBehavior original) {
        var accessor=(ProjectileDispenseBehaviorAccessor)original;
        return new MountedProjectileDispenseBehavior(){
            @Override protected Projectile getProjectile(Level level,double x,double y,double z,ItemStack stack,Direction facing) { return accessor.create$getProjectileItem().asProjectile(level,new Vec3(x,y,z),stack,facing); }
            @Override protected float getUncertainty() { return accessor.create$getDispenseConfig().uncertainty(); }
            @Override protected float getPower() { return accessor.create$getDispenseConfig().power(); }
            @Override protected int getDispenseEvent() { return accessor.create$getDispenseConfig().overrideDispenseEvent().orElse(super.getDispenseEvent()); }
        };
    }
}
