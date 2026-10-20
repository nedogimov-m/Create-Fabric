package com.simibubi.create.content.logistics.factoryBoard;

import com.simibubi.create.foundation.blockEntity.behaviour.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.*;

public final class FactoryPanelSupportBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<FactoryPanelSupportBehaviour> TYPE=new BehaviourType<>();
    private final List<FactoryPanelPosition> linkedPanels=new ArrayList<>();
    private final Supplier<Boolean> isOutput,outputPower;
    private final Runnable onNotify;
    private boolean dirty;
    public FactoryPanelSupportBehaviour(BlockEntity owner,Supplier<Boolean> isOutput,Supplier<Boolean> outputPower,Runnable onNotify) { super(owner);this.isOutput=isOutput;this.outputPower=outputPower;this.onNotify=onNotify; }
    public boolean isOutput() { return isOutput.get(); }
    public boolean shouldPanelBePowered() { return isOutput()&&outputPower.get(); }
    public void notifyLink() { onNotify.run(); }
    public List<FactoryPanelPosition> getLinkedPanels() { return List.copyOf(linkedPanels); }
    public void connect(FactoryPanelBehaviour panel) { if(!linkedPanels.contains(panel.getPanelPosition())){linkedPanels.add(panel.getPanelPosition());dirty=true;} }
    public void disconnect(FactoryPanelBehaviour panel) { if(linkedPanels.remove(panel.getPanelPosition()))dirty=true; }
    public void notifyPanels() { if(getWorld().isClientSide())return;var iterator=linkedPanels.iterator();while(iterator.hasNext()){var pos=iterator.next();if(!getWorld().isLoaded(pos.pos()))continue;var panel=FactoryPanelBehaviour.at(getWorld(),pos);if(panel==null){iterator.remove();dirty=true;}else panel.checkForRedstoneInput();} }
    public Boolean shouldBePoweredTristate() { var iterator=linkedPanels.iterator();while(iterator.hasNext()){var pos=iterator.next();if(!getWorld().isLoaded(pos.pos()))return null;var panel=FactoryPanelBehaviour.at(getWorld(),pos);if(panel==null){iterator.remove();dirty=true;continue;}if(panel.active&&panel.satisfied&&panel.count!=0)return true;}return false; }
    @Override public void tick() { super.tick();if(dirty){dirty=false;if(!isOutput())notifyLink();blockEntity.setChanged();} }
    @Override public void destroy() { for(var pos:List.copyOf(linkedPanels)){var panel=FactoryPanelBehaviour.at(getWorld(),pos);if(panel!=null){panel.targetedByLinks.remove(getPos());panel.panelBE().notifyUpdate();}}linkedPanels.clear(); }
    @Override public void write(ValueOutput out) { out.store("LinkedGauges",FactoryPanelPosition.CODEC.listOf(),linkedPanels); }
    @Override public void read(ValueInput in) { linkedPanels.clear();linkedPanels.addAll(in.read("LinkedGauges",FactoryPanelPosition.CODEC.listOf()).orElse(List.of())); }
    @Override public BehaviourType<?> getType() { return TYPE; }
}
