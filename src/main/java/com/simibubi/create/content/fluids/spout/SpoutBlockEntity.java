package com.simibubi.create.content.fluids.spout;

import com.simibubi.create.*;
import com.simibubi.create.api.behaviour.spouting.BlockSpoutingBehaviour;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltItemProcessor;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.foundation.fluid.*;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.fluid.*;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
import java.util.*;

public final class SpoutBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity implements BeltItemProcessor,com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"spout","foods");
    @Override public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip,boolean sneaking){return containedFluidTooltip(tooltip,sneaking,tank);}
    public static final int FILLING_TIME=20;
    public final SmartFluidTank tank=new SmartFluidTank(FluidConstants.BUCKET,fluid->changed());
    public int processingTicks=-1;
    private UUID target;
    private ItemVariant processingInput=ItemVariant.blank();
    private Pending pending;
    private boolean processingBlock;
    private boolean originalProcessing;
    private boolean sendSplash;
    private boolean createdSweetRoll,createdHoneyApple,createdChocolateBerries;
    private float previousLevel,fluidLevel;
    private record Pending(ItemVariant input,ItemStack output,FluidVariant fluid,long amount){}
    public SpoutBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.SPOUT,pos,state);}
    public float getFluidLevel(float partial){return previousLevel+(fluidLevel-previousLevel)*partial;}
    private void spawnProcessingParticles(){
        if(level==null||!level.isClientSide()||isVirtual()||tank.isEmpty())return;
        var at=net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(0,-8/16f,0);
        var particle=new com.simibubi.create.content.fluids.particle.FluidParticleData(AllParticleTypes.FLUID_PARTICLE,tank.getFluid());
        level.addAlwaysVisibleParticle(particle,at.x,at.y,at.z,0,-.1f,0);
    }
    private void consumeSplash(){
        if(!sendSplash||level==null||!level.isClientSide())return;
        sendSplash=false;
        if(isVirtual()||tank.isEmpty())return;
        var at=net.minecraft.world.phys.Vec3.atCenterOf(worldPosition).add(0,-2+5/16f,0);
        var particle=new com.simibubi.create.content.fluids.particle.FluidParticleData(AllParticleTypes.FLUID_PARTICLE,tank.getFluid());
        for(int i=0;i<20;i++){
            double x=(level.getRandom().nextDouble()-.5)*.25,y=Math.abs((level.getRandom().nextDouble()-.5)*.25),z=(level.getRandom().nextDouble()-.5)*.25;
            level.addAlwaysVisibleParticle(particle,at.x,at.y,at.z,x,y,z);
        }
    }
    private void changed(){
        setChanged();if(level!=null&&!level.isClientSide()){
            level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition,getBlockState().getBlock());
        }
    }
    private boolean eligible(ItemStack stack){
        if(!FillingBySpout.canItemBeFilled(level,stack))return false;
        return tank.isEmpty()||FillingBySpout.getRequiredAmountForItem(level,stack,tank.getFluid())!=-1;
    }
    @Override public boolean onBeltItemReceived(TransportedItemStack item){return eligible(item.stack);}
    @Override public boolean tryHoldBeltItem(BeltBlockEntity belt,TransportedItemStack item){return processingTicks>=0||eligible(item.stack);}
    private TransportedItemStack held(BeltBlockEntity belt){
        return belt.getInventory().getTransportedItems().stream().filter(item->worldPosition.equals(item.heldBy)&&(target==null||target.equals(item.id))).findFirst().orElse(null);
    }
    private void start(ItemStack stack,UUID id){
        if(!eligible(stack)||tank.isEmpty())return;
        long amount=FillingBySpout.getRequiredAmountForItem(level,stack,tank.getFluid());
        if(amount<=0||amount>tank.getAmount())return;
        target=id;processingInput=ItemVariant.of(stack);processingTicks=FILLING_TIME;pending=null;
        level.playSound(null,worldPosition,AllSoundEvents.SPOUTING,SoundSource.BLOCKS,0.75f,0.9f+level.getRandom().nextFloat()*0.2f);changed();
    }
    private void cancel(){originalProcessing=false;processingTicks=-1;target=null;processingInput=ItemVariant.blank();pending=null;processingBlock=false;changed();}
    private boolean prepare(ItemStack stack){
        if(!processingInput.matches(stack)){cancel();return false;}
        if(pending!=null){if(tank.isEmpty()||tank.getResource().equals(pending.fluid))return true;pending=null;}
        var fluid=tank.getFluid();long amount=FillingBySpout.getRequiredAmountForItem(level,stack,fluid);
        if(amount<=0||amount>fluid.getAmount()){cancel();return false;}
        var copy=stack.copyWithCount(1);var result=FillingBySpout.fillItem(level,amount,copy,fluid);
        if(!copy.isEmpty()){cancel();return false;}
        pending=new Pending(processingInput,result.copy(),tank.getResource(),amount);setChanged();return true;
    }
    private void produced(ItemStack output){
        sourceAdvancements.award("spout");if(sourceAdvancements.uuid()==null)return;
        createdChocolateBerries|=output.is(AllItems.CHOCOLATE_BERRIES);createdHoneyApple|=output.is(AllItems.HONEYED_APPLE);createdSweetRoll|=output.is(AllItems.SWEET_ROLL);setChanged();
        if(createdChocolateBerries&&createdHoneyApple&&createdSweetRoll)sourceAdvancements.award("foods");
    }
    private boolean processDepot(DepotBehaviour depot){
        var input=depot.inventory.inputs.getFirst();if(!prepare(input.stack()))return false;
        var output=pending.output.copy();try(var tx=Transaction.openOuter()){
            if(!depot.applyRecipeOutputs(1,List.of(pending.output),true,tx))return false;
            if(tank.extract(pending.fluid,pending.amount,tx)!=pending.amount)return false;
            tx.commit();
        }produced(output);pending=null;changed();return true;
    }
    private boolean processBelt(BeltBlockEntity belt,TransportedItemStack item){
        if(!prepare(item.stack))return false;
        var output=pending.output.copy();try(var tx=Transaction.openOuter()){
            if(!belt.getInventory().replace(item,1,List.of(pending.output),false,tx)
                ||tank.extract(pending.fluid,pending.amount,tx)!=pending.amount)return false;
            tx.commit();
        }produced(output);pending=null;changed();return true;
    }
    public static void tick(Level level,BlockPos pos,BlockState state,SpoutBlockEntity spout){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(level, pos, state, spout)) {
            if (!createTick.shouldTick()) return;

        spout.previousLevel=spout.fluidLevel;spout.fluidLevel+=(spout.tank.getAmount()/(float)FluidConstants.BUCKET-spout.fluidLevel)*0.25f;
        if(level.isClientSide()){spout.consumeSplash();if(spout.processingTicks>=0)spout.processingTicks--;if(spout.processingTicks>=8)spout.spawnProcessingParticles();return;}
        var below=LoadedLevelAccess.blockEntity(level,pos.below(2));
        var depot=BlockEntityBehaviour.get(below,DepotBehaviour.TYPE);
        // Original saves have a timer, but no target identity, input lock or paid output.
        // Adopt only actual presently held stock, preserving consumed animation progress.
        boolean originalBoundary=spout.originalProcessing&&spout.processingTicks==5;
        if(spout.originalProcessing){
            ItemStack actual=ItemStack.EMPTY;UUID actualTarget=null;
            if(depot!=null&&depot.readyForProcessing())actual=depot.getHeldItemStack();
            else if(below instanceof BeltBlockEntity segment){var belt=segment.getControllerBE();if(belt!=null&&belt.chainLoaded()){var held=spout.held(belt);if(held!=null){actual=held.stack;actualTarget=held.id;}}}
            if(!actual.isEmpty()){
                spout.processingInput=ItemVariant.of(actual);spout.target=actualTarget;spout.originalProcessing=false;spout.setChanged();
                if(originalBoundary)spout.processingTicks=6; // Genuine original processing boundary is the current5, not a new20-tick job.
            }
        }
        if(spout.processingTicks==-1){
            if(!spout.tank.isEmpty()){
                var behaviour=BlockSpoutingBehaviour.get(level,pos.below(2));
                if(behaviour!=null&&behaviour.fillBlock(level,pos.below(2),spout,spout.tank.getFluid(),true)>0){spout.processingTicks=FILLING_TIME;spout.processingBlock=true;spout.changed();return;}
            }
            if(depot!=null&&depot.readyForProcessing()&&!BeltProcessingBehaviour.isBlocked(level,pos.below(2)))spout.start(depot.getHeldItemStack(),null);
            else if(below instanceof BeltBlockEntity segment){var belt=segment.getControllerBE();if(belt!=null&&belt.chainLoaded()){var item=spout.held(belt);if(item!=null)spout.start(item.stack,item.id);}}
            return;
        }
        spout.processingTicks--;spout.setChanged();
        if(spout.processingTicks==5){
            boolean applied=false;
            if(spout.processingBlock){
                var behaviour=BlockSpoutingBehaviour.get(level,pos.below(2));
                if(behaviour!=null){
                    var fluid=spout.tank.getFluid();long required=behaviour.fillBlock(level,pos.below(2),spout,fluid.copy(),true);
                    if(required>0&&required<=fluid.getAmount()){
                        if(behaviour instanceof com.simibubi.create.compat.tconstruct.SpoutCasting casting)applied=casting.transferPaid(level,pos.below(2),spout)>0;
                        else{
                            long used=behaviour.fillBlock(level,pos.below(2),spout,fluid.copy(),false);
                            if(used>0&&used<=fluid.getAmount()){try(var tx=Transaction.openOuter()){spout.tank.extract(fluid.variant(),used,tx);tx.commit();}applied=true;}
                        }
                    }
                }
                spout.processingBlock=false;if(!applied)spout.cancel();
            }
            else if(depot!=null&&spout.target==null)applied=spout.processDepot(depot);
            else if(below instanceof BeltBlockEntity segment){var belt=segment.getControllerBE();if(belt!=null&&belt.chainLoaded()){var item=spout.held(belt);if(item!=null)applied=spout.processBelt(belt,item);else spout.cancel();}}
            else spout.cancel();
            if(applied){spout.sendSplash=true;spout.changed();}
            if(!applied&&spout.processingTicks==5){spout.processingTicks=6;spout.setChanged();}
        }
        if(spout.processingTicks<0)spout.cancel();
    
        }
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){
        super.write(out, clientPacket);if(sourceAdvancements.uuid()!=null){if(createdChocolateBerries)out.putBoolean("ChocolateBerries",true);if(createdHoneyApple)out.putBoolean("HoneyApple",true);if(createdSweetRoll)out.putBoolean("SweetRoll",true);}tank.save(out.child("Tank"));out.putInt("ProcessingTicks",processingTicks);out.putBoolean("ProcessingBlock",processingBlock);out.putBoolean("OriginalProcessing",originalProcessing);
        if(target!=null)out.store("ProcessingTarget",UUIDUtil.CODEC,target);
        if(!processingInput.isBlank())out.store("ProcessingInput",ItemVariant.CODEC,processingInput);
        if(pending!=null){var p=out.child("Pending");p.store("Input",ItemVariant.CODEC,pending.input);p.store("Output",ItemStack.OPTIONAL_CODEC,pending.output);p.store("Fluid",FluidVariant.CODEC,pending.fluid);p.putLong("Amount",pending.amount);}
        if(clientPacket&&sendSplash){out.putBoolean("Splash",true);sendSplash=false;}
    }
    @Override protected void read(ValueInput in, boolean clientPacket){
        super.read(in, clientPacket);createdChocolateBerries=in.getBooleanOr("ChocolateBerries",false);createdHoneyApple=in.getBooleanOr("HoneyApple",false);createdSweetRoll=in.getBooleanOr("SweetRoll",false);tank.load(in.childOrEmpty("Tank"));processingTicks=in.getIntOr("ProcessingTicks",-1);originalProcessing=in.getBooleanOr("OriginalProcessing",false);
        target=in.read("ProcessingTarget",UUIDUtil.CODEC).orElse(null);processingInput=in.read("ProcessingInput",ItemVariant.CODEC).orElse(ItemVariant.blank());pending=null;
        processingBlock=in.getBooleanOr("ProcessingBlock",false);
        if(in.child("Pending").isPresent()){
            var p=in.childOrEmpty("Pending");var input=p.read("Input",ItemVariant.CODEC).orElse(ItemVariant.blank());var fluid=p.read("Fluid",FluidVariant.CODEC).orElse(FluidVariant.blank());long amount=p.getLongOr("Amount",0);
            if(!input.isBlank()&&!fluid.isBlank()&&amount>0&&amount<=FluidConstants.BUCKET)pending=new Pending(input,p.read("Output",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY),fluid,amount);
        }
        sendSplash=clientPacket&&in.getBooleanOr("Splash",false);consumeSplash();
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    private final com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour beltProcessing = com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.createNative(this);
    @Override protected void addNativeBehaviours(java.util.List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        super.addNativeBehaviours(behaviours);
        if (beltProcessing != null) behaviours.add(beltProcessing);
    }
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("spout");
        ids.add("foods");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
