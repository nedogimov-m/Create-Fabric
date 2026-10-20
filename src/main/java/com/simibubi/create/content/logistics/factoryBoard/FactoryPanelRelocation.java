package com.simibubi.create.content.logistics.factoryBoard;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import java.util.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.*;

public final class FactoryPanelRelocation {
    public static boolean move(FactoryPanelBehaviour source,FactoryPanelPosition target,Player player) {
        var level=source.getWorld();var state=source.panelBE().getBlockState();var existing=LoadedLevelAccess.blockState(level,target.pos());if(source.panelBE().restocker||FactoryPanelBehaviour.at(level,target)!=null||!target.pos().closerThan(source.getPos(),16)||!AllBlocks.FACTORY_GAUGE.canSurvive(state,level,target.pos())||!existing.isAir()&&!existing.is(AllBlocks.FACTORY_GAUGE))return false;
        if(existing.is(AllBlocks.FACTORY_GAUGE)&&existing!=state)return false;var diff=target.pos().subtract(source.getPos());if(FactoryPanelBlock.connectedDirection(state).getAxis().choose(diff.getX(),diff.getY(),diff.getZ())!=0)return false;
        for(var pos:source.targeting)if(!pos.pos().closerThan(target.pos(),24)||!level.isLoaded(pos.pos()))return false;for(var pos:source.targetedBy.keySet())if(!pos.pos().closerThan(target.pos(),24)||!level.isLoaded(pos.pos()))return false;for(var pos:source.targetedByLinks.keySet())if(!pos.closerThan(target.pos(),24)||!level.isLoaded(pos))return false;
        var old=source.getPanelPosition();var oldOwner=source.panelBE();var output=TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess());source.write(output);var tag=output.buildResult();var data=tag.getCompoundOrEmpty(source.slot.getSerializedName());var moved=new net.minecraft.nbt.CompoundTag();moved.put(target.slot().getSerializedName(),data);
        if(existing.isAir())level.setBlockAndUpdate(target.pos(),state);if(!(LoadedLevelAccess.blockEntity(level,target.pos()) instanceof FactoryPanelBlockEntity owner))return false;var replacement=owner.panels.get(target.slot());replacement.read(TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,level.registryAccess(),moved));
        for(var pos:source.targeting){var panel=FactoryPanelBehaviour.at(level,pos);if(panel!=null){var c=panel.targetedBy.remove(old);if(c!=null){c.from=target;panel.targetedBy.put(target,c);}panel.panelBE().notifyUpdate();}}
        for(var pos:source.targetedBy.keySet()){var panel=FactoryPanelBehaviour.at(level,pos);if(panel!=null){panel.targeting.remove(old);panel.targeting.add(target);panel.panelBE().notifyUpdate();}}
        for(var c:source.targetedByLinks.values()){var support=FactoryPanelBehaviour.linkAt(level,c);if(support!=null){support.disconnect(source);support.connect(replacement);}}
        source.targetedBy.clear();source.targeting.clear();source.targetedByLinks.clear();source.active=false;oldOwner.notifyUpdate();owner.notifyUpdate();if(oldOwner.activePanels()==0)level.setBlockAndUpdate(old.pos(),Blocks.AIR.defaultBlockState());return true;
    }
    private FactoryPanelRelocation() {}
}
