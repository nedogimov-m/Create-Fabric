package com.simibubi.create.content.redstone.displayLink;

import com.simibubi.create.content.trains.display.FlapDisplayBlockEntity;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;

/** Real target-owned source LineN records, attached to native BEs and saved by the accompanying mixin. */
public interface DisplayTargetReservations {
    Table create$displayReservations();
    final class Table {
        private final Map<Integer,BlockPos> owners=new TreeMap<>();
        public Map<Integer,BlockPos> snapshot(){return Map.copyOf(owners);}
        public void claim(int line,BlockPos owner,BlockEntity target){if(line<=0||line>=256)return;if(!owner.equals(owners.put(line,owner.immutable())))target.setChanged();}
        public void release(BlockPos owner,BlockEntity target){if(owners.values().removeIf(owner::equals))target.setChanged();}
        private void clear(int line,BlockEntity target){if(owners.remove(line)!=null)target.setChanged();}
        public boolean isReserved(int line,BlockEntity target,DisplayLinkContext context){
            var owner=owners.get(line);if(owner==null)return false;if(owner.equals(context.blockEntity().getBlockPos())){clear(line,target);return false;}var level=context.level();if(!LoadedLevelAccess.isLoaded(level,owner))return true;
            var state=LoadedLevelAccess.blockState(level,owner);if(!(state.getBlock()instanceof DisplayLinkBlock)){clear(line,target);return false;}var block=LoadedLevelAccess.blockEntity(level,owner);if(!(block instanceof DisplayLinkBlockEntity link))return true;
            var position=link.getTargetPosition();if(!LoadedLevelAccess.isLoaded(level,position))return true;var owned=LoadedLevelAccess.blockEntity(level,position);if(owned instanceof FlapDisplayBlockEntity flap)owned=flap.getController();if(owned==target&&link.targetLine==line&&link.activeSource!=null)return true;clear(line,target);return false;
        }
        public void write(ValueOutput out){if(owners.isEmpty())return;var child=out.child("DisplayLink");owners.forEach((line,pos)->child.putLong("Line"+line,pos.asLong()));}
        public void read(ValueInput in){
            owners.clear();var child=in.child("DisplayLink").orElseGet(()->in.childOrEmpty("NeoForgeData").child("DisplayLink").orElseGet(()->in.childOrEmpty("ForgeData").childOrEmpty("DisplayLink")));for(int line=1;line<256;line++){var owner=child.getLong("Line"+line);if(owner.isPresent())owners.put(line,BlockPos.of(owner.get()));}
        }
    }
}
