package com.simibubi.create.api.contraption.dispenser;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.foundation.item.LoadedItemStorage;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import com.simibubi.create.foundation.utility.LoadedEntityAccess;
import com.simibubi.create.foundation.item.MountedItemInventory;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Source mounted insertion/drop path, using live loaded-only Fabric storage. */
public class DefaultMountedDispenseBehavior implements MountedDispenseBehavior {
    public static final MountedDispenseBehavior INSTANCE=new DefaultMountedDispenseBehavior();

    @Override public ItemStack dispense(ItemStack stack,MovementContext context,BlockPos pos) {
        if(!(context.world instanceof ServerLevel)||stack.isEmpty())return stack;
        MountedItemInventory.requireNoTransaction();
        if(!context.contraption.getStorage().ownsContents())return stack;
        var normal=MountedDispenseBehavior.getDispenserNormal(context);
        var direction=MountedDispenseBehavior.getClosestFacingDirection(normal);
        var target=pos.relative(direction);
        if(!LoadedEntityAccess.isReady(context.world,pos)||!LoadedEntityAccess.isReady(context.world,target))return stack;
        // Native entity queries include a two-block section margin; overlapping carts may be centered
        // in a diagonal chunk even though all six target-cell neighbors have returned.
        for(int x=(target.getX()-2)>>4;x<=(target.getX()+3)>>4;x++)
            for(int z=(target.getZ()-2)>>4;z<=(target.getZ()+3)>>4;z++)
                if(!LoadedEntityAccess.isReady(context.world,x,z))return stack;
        var inventory=findInventory(context.world,target,direction.getOpposite());
        if(inventory!=null){
            try(var transaction=Transaction.openOuter()){
                if(inventory.insert(ItemVariant.of(stack),1,transaction)==1){transaction.commit();stack.shrink(1);}
            }
            return stack;
        }
        return executeWithEvents(stack,context,pos,normal,direction);
    }

    /** Native wrappers override this hook because vanilla already emits its own effects. */
    protected ItemStack executeWithEvents(ItemStack stack,MovementContext context,BlockPos pos,Vec3 facing,Direction direction) {
        var result=execute(stack,context,pos,facing);
        playSound(context.world,pos);playAnimation(context.world,pos,direction);
        return result;
    }

    /** Native hopper lookup also supports inventory minecarts in the target cell. */
    protected Storage<ItemVariant> findInventory(Level level,BlockPos pos,Direction side) {
        var block=LoadedItemStorage.find(level,pos,side);
        if(block!=null)return block;
        var entities=level.getEntities((Entity)null,new AABB(pos),entity->entity.isAlive()&&entity instanceof Container);
        return entities.isEmpty()?null:ContainerStorage.of((Container)entities.get(level.getRandom().nextInt(entities.size())),side);
    }

    protected ItemStack execute(ItemStack stack,MovementContext context,BlockPos pos,Vec3 facing) {
        var one=stack.copyWithCount(1);
        if(spawnItem(context.world,one,6,facing,pos,context))stack.shrink(1);
        return stack;
    }

    protected void playSound(LevelAccessor level,BlockPos pos) { level.levelEvent(LevelEvent.SOUND_DISPENSER_DISPENSE,pos,0); }
    protected void playAnimation(LevelAccessor level,BlockPos pos,Vec3 facing) { playAnimation(level,pos,MountedDispenseBehavior.getClosestFacingDirection(facing)); }
    protected void playAnimation(LevelAccessor level,BlockPos pos,Direction facing) { level.levelEvent(LevelEvent.PARTICLES_SHOOT_SMOKE,pos,facing.get3DDataValue()); }

    public static boolean spawnItem(Level level,ItemStack stack,int speed,Vec3 facing,BlockPos pos,MovementContext context) {
        if(stack.isEmpty()||!(level instanceof ServerLevel)||!LoadedLevelAccess.isLoaded(level,pos))return false;
        MountedItemInventory.requireNoTransaction();
        double x=pos.getX()+facing.x+.5,y=pos.getY()+facing.y+.5,z=pos.getZ()+facing.z+.5;
        y-=MountedDispenseBehavior.getClosestFacingDirection(facing).getAxis()==Direction.Axis.Y?.125:.15625;
        if(!LoadedEntityAccess.isReady(level,BlockPos.containing(x,y,z)))return false;
        var entity=new ItemEntity(level,x,y,z,stack.copy());var random=level.getRandom();double scale=random.nextDouble()*.1+.2;
        entity.setDeltaMovement(random.nextGaussian()*.0075*speed+facing.x*scale+context.motion.x,
                random.nextGaussian()*.0075*speed+facing.y*scale+context.motion.y,
                random.nextGaussian()*.0075*speed+facing.z*scale+context.motion.z);
        return level.addFreshEntity(entity);
    }
}
