package com.simibubi.create.content.contraptions.glue;

import com.simibubi.create.content.contraptions.StructureTransform;
import net.minecraft.core.*;
import net.minecraft.core.particles.*;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SuperGlueItem extends Item {
    public SuperGlueItem(Properties properties){super(properties.durability(99));}
    @Override public boolean canDestroyBlock(ItemStack stack,BlockState state,Level level,BlockPos pos,net.minecraft.world.entity.LivingEntity player){return false;}
    public static void spawnParticles(Level level,BlockPos pos,Direction face,boolean full){
        var normal=face.getUnitVec3();var plane=new Vec3(1-Math.abs(normal.x),1-Math.abs(normal.y),1-Math.abs(normal.z));var center=Vec3.atCenterOf(pos).add(normal.scale(.5));
        float distance=full?1:.25f+.25f*(level.getRandom().nextFloat()-.5f);plane=plane.scale(distance);
        for(int i=full?40:15;i>0;i--){var offset=StructureTransform.rotate(plane,360*level.getRandom().nextFloat(),face.getAxis());var velocity=offset.normalize().scale(1/16d);if(full)offset=new Vec3(Mth.clamp(offset.x,-.5,.5),Mth.clamp(offset.y,-.5,.5),Mth.clamp(offset.z,-.5,.5));var point=center.add(offset);var particle=new ItemParticleOption(ParticleTypes.ITEM,Items.SLIME_BALL);if(level instanceof net.minecraft.server.level.ServerLevel server)server.sendParticles(particle,point.x,point.y,point.z,0,velocity.x,velocity.y,velocity.z,1);else level.addParticle(particle,point.x,point.y,point.z,velocity.x,velocity.y,velocity.z);}
    }
}
