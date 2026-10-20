package com.simibubi.create.content.contraptions.glue;

import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class SuperGlueSelectionHelper {
    public static Set<BlockPos> searchGlueGroup(Level level,BlockPos from,BlockPos to,boolean includeOther){
        if(from==null||to==null)return null;
        var bounds=SuperGlueEntity.span(from,to);var frontier=new ArrayDeque<BlockPos>();var visited=new HashSet<BlockPos>();var attached=new HashSet<BlockPos>();var cache=new HashSet<SuperGlueEntity>();
        if(level.getChunkSource().getChunkNow(from.getX()>>4,from.getZ()>>4)==null)return null;
        frontier.add(from);visited.add(from);
        while(!frontier.isEmpty()){
            var current=frontier.removeFirst();attached.add(current);
            for(var direction:Direction.values()){
                var next=current.relative(direction);
                if(visited.contains(next))continue;
                if(level.getChunkSource().getChunkNow(next.getX()>>4,next.getZ()>>4)==null)continue;
                boolean glued=includeOther&&SuperGlueEntity.isGlued(level,current,direction,cache);
                boolean sticky=includeOther&&SuperGlueEntity.isSideSticky(level,current,direction)||SuperGlueEntity.isSideSticky(level,next,direction.getOpposite());
                if(!sticky&&!glued&&!bounds.contains(Vec3.atCenterOf(next)))continue;
                if(!BlockMovementChecks.isMovementNecessary(LoadedLevelAccess.blockState(level,next),level,next))continue;
                if(!SuperGlueEntity.isValidFace(level,current,direction)||!SuperGlueEntity.isValidFace(level,next,direction.getOpposite()))continue;
                visited.add(next);frontier.addLast(next);
            }
        }
        return attached.size()<2&&attached.contains(to)?null:attached;
    }
    public static boolean collectGlueFromInventory(Player player,int required,boolean simulate){
        if(required<0)return false;if(player.getAbilities().instabuild||required==0)return true;
        // Preview must not damage items; first prove the entire charge budget can be paid.
        int capacity=0,slots=player.getInventory().getNonEquipmentItems().size(),selected=player.getInventory().getSelectedSlot();
        var order=new ArrayList<Integer>();order.add(selected);for(int i=0;i<slots;i++)if(i!=selected)order.add(i);
        for(int slot:order){var stack=player.getInventory().getItem(slot);if(stack.getItem() instanceof SuperGlueItem)capacity+=Math.max(0,stack.getMaxDamage()-stack.getDamageValue());if(capacity>=required)break;}
        if(capacity<required)return false;if(simulate)return true;
        for(int slot:order){var stack=player.getInventory().getItem(slot);if(!(stack.getItem() instanceof SuperGlueItem))continue;int charge=Math.min(required,stack.getMaxDamage()-stack.getDamageValue());stack.hurtAndBreak(charge,player,EquipmentSlot.MAINHAND);required-=charge;if(required==0)return true;}
        return false;
    }
    private SuperGlueSelectionHelper(){}
}
