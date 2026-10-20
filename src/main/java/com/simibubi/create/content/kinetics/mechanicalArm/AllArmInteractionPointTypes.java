package com.simibubi.create.content.kinetics.mechanicalArm;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.Create;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerBlock;
import com.simibubi.create.content.kinetics.saw.SawBlock;
import com.simibubi.create.content.logistics.chute.AbstractChuteBlock;
import com.simibubi.create.content.logistics.funnel.*;
import com.simibubi.create.content.logistics.tunnel.BeltTunnelBlock;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.simibubi.create.foundation.item.LoadedItemStorage;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** All sixteen original target types, backed by their actual world blocks and native storage. */
public final class AllArmInteractionPointTypes {
    private static boolean initialized;
    public static void init(){
        if(initialized)return;initialized=true;
        register("basin",new BasinType());register("belt",new BeltType());register("blaze_burner",new BlazeBurnerType());
        register("chute",new ChuteType());register("crafter",new CrafterType());register("crushing_wheels",new CrushingWheelsType());
        register("deployer",new DeployerType());register("depot",new DepotType());register("funnel",new FunnelType());
        register("millstone",new MillstoneType());register("packager",new PackagerType());register("saw",new SawType());
        register("campfire",new CampfireType());register("composter",new ComposterType());register("jukebox",new JukeboxType());register("respawn_anchor",new RespawnAnchorType());
        ArmInteractionPointType.init();
    }
    private static void register(String id,ArmInteractionPointType type){Registry.register(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE,Create.asResource(id),type);}
    public static class BasinType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.getBlock() instanceof BasinBlock;}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new ArmInteractionPoint(this,l,p,s);}
    }
    public static class BeltType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return BeltBlock.canTransportObjects(s)&&!(LoadedLevelAccess.blockState(l,p.above()).getBlock() instanceof BeltTunnelBlock);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new BeltPoint(this,l,p,s);}
    }
    public static class BlazeBurnerType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(AllBlocks.BLAZE_BURNER);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new BlazeBurnerPoint(this,l,p,s);}
    }
    public static class ChuteType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.getBlock() instanceof AbstractChuteBlock;}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new TopFaceArmInteractionPoint(this,l,p,s);}
    }
    public static class CrafterType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(AllBlocks.MECHANICAL_CRAFTER);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new CrafterPoint(this,l,p,s);}
    }
    public static class CrushingWheelsType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(AllBlocks.CRUSHING_WHEEL_CONTROLLER);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new CrushingWheelPoint(this,l,p,s);}
    }
    public static class DeployerType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(AllBlocks.DEPLOYER);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new DeployerPoint(this,l,p,s);}
    }
    public static class DepotType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(AllBlocks.DEPOT)||s.is(AllBlocks.TRACK_STATION)||Create.asResource("weighted_ejector").equals(BuiltInRegistries.BLOCK.getKey(s.getBlock()));}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new DepotPoint(this,l,p,s);}
    }
    public static class FunnelType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.getBlock() instanceof AbstractFunnelBlock&&!s.getOptionalValue(FunnelBlock.EXTRACTING).orElse(false)&&s.getOptionalValue(BeltFunnelBlock.SHAPE).orElse(BeltFunnelBlock.Shape.RETRACTED)!=BeltFunnelBlock.Shape.PUSHING;}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new FunnelPoint(this,l,p,s);}
    }
    public static class MillstoneType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(AllBlocks.MILLSTONE);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new ArmInteractionPoint(this,l,p,s);}
    }
    public static class PackagerType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(AllBlocks.PACKAGER)||s.is(AllBlocks.REPACKAGER);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new ArmInteractionPoint(this,l,p,s);}
    }
    public static class SawType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(AllBlocks.MECHANICAL_SAW)&&s.getValue(SawBlock.FACING)==Direction.UP&&LoadedLevelAccess.blockEntity(l,p) instanceof KineticBlockEntity kinetic&&kinetic.getSpeed()!=0;}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new DepotPoint(this,l,p,s);}
    }
    public static class CampfireType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.getBlock() instanceof CampfireBlock;}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new CampfirePoint(this,l,p,s);}
    }
    public static class ComposterType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(Blocks.COMPOSTER);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new ComposterPoint(this,l,p,s);}
    }
    public static class JukeboxType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(Blocks.JUKEBOX);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new JukeboxPoint(this,l,p,s);}
    }
    public static class RespawnAnchorType extends ArmInteractionPointType {
        @Override public boolean canCreatePoint(Level l,BlockPos p,BlockState s){return s.is(Blocks.RESPAWN_ANCHOR);}
        @Override public ArmInteractionPoint createPoint(Level l,BlockPos p,BlockState s){return new RespawnAnchorPoint(this,l,p,s);}
    }
    public static class DepositOnlyArmInteractionPoint extends ArmInteractionPoint {
        public DepositOnlyArmInteractionPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override public boolean acceptsMode(Mode mode){return mode==Mode.DEPOSIT;}
        @Override public void cycleMode(){}
        @Override public ItemStack extract(ArmBlockEntity arm,int slot,int amount,TransactionContext tx){return ItemStack.EMPTY;}
        @Override public int getSlotCount(ArmBlockEntity arm){return 0;}
    }
    public static class TopFaceArmInteractionPoint extends ArmInteractionPoint {
        public TopFaceArmInteractionPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override protected Vec3 getInteractionPositionVector(){return Vec3.atLowerCornerOf(pos).add(.5,1,.5);}
    }
    public static class DepotPoint extends ArmInteractionPoint {
        public DepotPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override protected Vec3 getInteractionPositionVector(){return Vec3.atLowerCornerOf(pos).add(.5,14/16d,.5);}
    }
    public static class BeltPoint extends DepotPoint {
        public BeltPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override public void keepAlive(){
            if(!(LoadedLevelAccess.blockEntity(level,pos) instanceof BeltBlockEntity segment))return;
            var master=segment.getControllerBE();if(master==null||!master.chainLoaded())return;
            for(var item:master.getInventory().getTransportedItems())if(item.beltPosition>=segment.index&&item.beltPosition<segment.index+1){item.lockedExternally=true;break;}
        }
    }
    public static class BlazeBurnerPoint extends DepositOnlyArmInteractionPoint {
        public BlazeBurnerPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override public long insert(ArmBlockEntity arm,ItemVariant item,long amount,TransactionContext tx){return amount>0&&canInteract(arm)&&LoadedLevelAccess.blockEntity(level,pos) instanceof BlazeBurnerBlockEntity burner&&burner.tryFuel(item.toStack(),false,tx)?1:0;}
        @Override public ItemStack insertStack(ArmBlockEntity arm,ItemStack stack,TransactionContext tx){
            if(stack.isEmpty()||insert(arm,ItemVariant.of(stack),stack.getCount(),tx)==0)return stack.copy();
            var remainder=stack.getItem().getCraftingRemainder();
            if(stack.getCount()==1)return remainder==null?ItemStack.EMPTY:remainder.create();
            if(remainder!=null)tx.addOuterCloseCallback(result->{if(result.wasCommitted())Block.popResource(level,pos,remainder.create());});
            return stack.copyWithCount(stack.getCount()-1);
        }
    }
    public static class CrafterPoint extends ArmInteractionPoint {
        public CrafterPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override protected Direction getInteractionDirection(){return cachedState.getValue(MechanicalCrafterBlock.HORIZONTAL_FACING).getOpposite();}
        @Override protected Vec3 getInteractionPositionVector(){return Vec3.atCenterOf(pos).add(getInteractionDirection().getUnitVec3().scale(.5));}
        @Override public int getSlotCount(ArmBlockEntity arm){return canInteract(arm)?1:0;}
        @Override public ItemStack extract(ArmBlockEntity arm,int slot,int amount,TransactionContext tx){
            if(slot!=0||amount<1||!canInteract(arm)||!(LoadedLevelAccess.blockEntity(level,pos) instanceof MechanicalCrafterBlockEntity crafter))return ItemStack.EMPTY;
            var stack=crafter.inventory.stack();return stack.isEmpty()?ItemStack.EMPTY:stack.copyWithCount((int)crafter.inventory.extractForArm(ItemVariant.of(stack),amount,tx));
        }
    }
    public static class DeployerPoint extends ArmInteractionPoint {
        public DeployerPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override protected Direction getInteractionDirection(){return cachedState.getValue(DeployerBlock.FACING).getOpposite();}
        @Override protected Vec3 getInteractionPositionVector(){return Vec3.atCenterOf(pos).add(getInteractionDirection().getUnitVec3().scale(.65));}
    }
    public static class FunnelPoint extends DepositOnlyArmInteractionPoint {
        public FunnelPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override protected Direction getInteractionDirection(){var facing=AbstractFunnelBlock.getFunnelFacing(cachedState);return facing==null?Direction.UP:facing.getOpposite();}
        @Override protected Vec3 getInteractionPositionVector(){var facing=AbstractFunnelBlock.getFunnelFacing(cachedState);return Vec3.atCenterOf(pos).add(facing==null?Vec3.ZERO:facing.getUnitVec3().scale(-.15));}
        @Override public long insert(ArmBlockEntity arm,ItemVariant item,long amount,TransactionContext tx){return canInteract(arm)&&LoadedLevelAccess.blockEntity(level,pos) instanceof FunnelBlockEntity funnel?funnel.insert(item,amount,false,tx):0;}
    }
    public static class CampfirePoint extends DepositOnlyArmInteractionPoint {
        public CampfirePoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override public long insert(ArmBlockEntity arm,ItemVariant item,long amount,TransactionContext tx){return canInteract(arm)&&LoadedLevelAccess.blockEntity(level,pos) instanceof CampfireBlockEntity campfire?ArmVanillaTransactions.campfire(campfire,item,amount,tx):0;}
    }
    public static class ComposterPoint extends ArmInteractionPoint {
        public ComposterPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override protected Vec3 getInteractionPositionVector(){return Vec3.atLowerCornerOf(pos).add(.5,13/16d,.5);}
        @Override protected Storage<ItemVariant> getExtractionHandler(ArmBlockEntity arm){return LoadedItemStorage.find(level,pos,Direction.DOWN);}
    }
    public static class JukeboxPoint extends TopFaceArmInteractionPoint {
        public JukeboxPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
    }
    public static class RespawnAnchorPoint extends DepositOnlyArmInteractionPoint {
        public RespawnAnchorPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override protected Vec3 getInteractionPositionVector(){return Vec3.atLowerCornerOf(pos).add(.5,1,.5);}
        @Override public long insert(ArmBlockEntity arm,ItemVariant item,long amount,TransactionContext tx){return canInteract(arm)?ArmVanillaTransactions.anchor(level,pos,item,amount,tx):0;}
    }
    public static class CrushingWheelPoint extends DepositOnlyArmInteractionPoint {
        public CrushingWheelPoint(ArmInteractionPointType t,Level l,BlockPos p,BlockState s){super(t,l,p,s);}
        @Override protected Vec3 getInteractionPositionVector(){return Vec3.atLowerCornerOf(pos).add(.5,1,.5);}
    }
    private AllArmInteractionPointTypes(){}
}
