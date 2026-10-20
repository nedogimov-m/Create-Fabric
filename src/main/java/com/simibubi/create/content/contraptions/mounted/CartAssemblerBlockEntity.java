package com.simibubi.create.content.contraptions.mounted;

import com.simibubi.create.*;
import com.simibubi.create.content.contraptions.*;
import com.simibubi.create.content.contraptions.minecart.*;
import com.simibubi.create.content.contraptions.minecart.capability.*;
import com.simibubi.create.content.redstone.rail.ControllerRailBlock;
import com.simibubi.create.foundation.utility.*;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.minecart.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.*;
import net.minecraft.world.phys.Vec3;

/** Source eight-tick assembly action/cooldown and three modes, on genuine reserved native forest owners. */
public final class CartAssemblerBlockEntity extends com.simibubi.create.foundation.blockEntity.SmartBlockEntity {
    public enum CartMovementMode { ROTATE,ROTATE_PAUSED,ROTATION_LOCKED }
    public final com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner sourceAdvancements=new com.simibubi.create.foundation.advancement.PrerequisiteAdvancements.Owner(this,"contraption_actors");
    private CartMovementMode movementMode=CartMovementMode.ROTATE;
    private int ticksSinceMinecartUpdate=8;
    private WeakReference<AbstractMinecart> cartToAssemble=new WeakReference<>(null);
    private AssemblyException lastException;
    private CompoundTag savedError;
    public CartAssemblerBlockEntity(BlockPos pos,BlockState state){super(AllBlockEntityTypes.CART_ASSEMBLER,pos,state);}
    public CartMovementMode getMovementMode(){return movementMode;}
    public void setMovementMode(CartMovementMode mode){movementMode=Objects.requireNonNull(mode);changed();}
    public AssemblyException getLastAssemblyException(){return lastException;}
    public void assembleNextTick(AbstractMinecart cart){if(cartToAssemble.get()==null)cartToAssemble=new WeakReference<>(cart);}
    public boolean isMinecartUpdateValid(){return ticksSinceMinecartUpdate>=8;}
    public void resetTicksSinceMinecartUpdate(){ticksSinceMinecartUpdate=0;}
    private void changed(){setChanged();if(level!=null&&!level.isClientSide())level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    public static void tick(Level world,BlockPos pos,BlockState state,CartAssemblerBlockEntity assembler){
        try (var createTick = com.simibubi.create.foundation.blockEntity.SmartBlockEntity.enterNativeTick(world, pos, state, assembler)) {
            if (!createTick.shouldTick()) return;

        if(assembler.ticksSinceMinecartUpdate<8)assembler.ticksSinceMinecartUpdate++;
        var cart=assembler.cartToAssemble.get();assembler.cartToAssemble.clear();if(!world.isClientSide())assembler.tryAssemble(cart);
    
        }
    }
    public void tryAssemble(AbstractMinecart cart){
        if(!(level instanceof ServerLevel server)||cart==null||!isMinecartUpdateValid()||server.getEntity(cart.getUUID())!=cart||!LoadedEntityAccess.isReady(server,cart.blockPosition())||!getBlockState().is(AllBlocks.CART_ASSEMBLER))return;
        var controller=CapabilityMinecartController.getIfPresent(level,cart.getUUID());if(controller==null||controller.ownsPackedReceipt())return;
        resetTicksSinceMinecartUpdate();var state=getBlockState();var action=CartAssemblerBlock.getActionForCart(state,cart);
        if(action.shouldAssemble())assemble(server,worldPosition,cart);
        if(action.shouldDisassemble())disassemble(server,worldPosition,cart);
        double speed=MinecartSim2020.maxSpeed(cart);
        if(action==CartAssemblerBlock.CartAssemblerAction.ASSEMBLE_ACCELERATE&&cart.getDeltaMovement().length()>1/128f){
            var facing=cart.getMotionDirection();var axis=state.getValue(CartAssemblerBlock.RAIL_SHAPE)==RailShape.EAST_WEST?Direction.Axis.X:Direction.Axis.Z;
            for(var side:Direction.values())if(side.getAxis()==axis){var neighbor=worldPosition.relative(side);if(LoadedLevelAccess.isLoaded(level,neighbor)&&LoadedLevelAccess.blockState(level,neighbor).isRedstoneConductor(level,neighbor))facing=side.getOpposite();}
            cart.setDeltaMovement(facing.getUnitVec3().scale(speed));
        }
        if(action==CartAssemblerBlock.CartAssemblerAction.ASSEMBLE_ACCELERATE_DIRECTIONAL){var rail=AllBlocks.CONTROLLER_RAIL.defaultBlockState().setValue(ControllerRailBlock.SHAPE,state.getValue(CartAssemblerBlock.RAIL_SHAPE)).setValue(ControllerRailBlock.BACKWARDS,state.getValue(CartAssemblerBlock.BACKWARDS));cart.setDeltaMovement(Vec3.atLowerCornerOf(ControllerRailBlock.getAccelerationVector(rail)).scale(speed));}
        if(action==CartAssemblerBlock.CartAssemblerAction.DISASSEMBLE_BRAKE){var diff=Vec3.atCenterOf(worldPosition).subtract(cart.position());cart.setDeltaMovement(diff.x/16,0,diff.z/16);}
    }
    private void assemble(ServerLevel world,BlockPos pos,AbstractMinecart cart){
        var controller=CapabilityMinecartController.getIfPresent(world,cart.getUUID());if(controller==null||!cart.getPassengers().isEmpty()||controller.isCoupledThroughContraption()||controller.getMountedRoot()!=null)return;
        var structure=new MountedContraption(movementMode);OrientedContraptionEntity root=null;MinecartController secondary=null;boolean joined=false;
        try{
            if(!structure.assemble(world,pos))return;
            if(structure.connectedCart!=null){secondary=CapabilityMinecartController.getIfPresent(world,structure.connectedCart.getUUID());if(secondary==null||secondary.getMountedRoot()!=null)return;if(!CouplingHandler.tryToCoupleCarts(null,world,cart.getId(),structure.connectedCart.getId()))return;joined=true;}
            var orientation=CartAssemblerBlock.getHorizontalDirection(getBlockState());
            if(joined){var diff=structure.connectedCart.position().subtract(cart.position());orientation=Direction.fromYRot(Mth.atan2(diff.z,diff.x)*180/Math.PI);}
            root=new OrientedContraptionEntity(AllEntityTypes.ORIENTED_CONTRAPTION,world);root.setInitialOrientation(orientation);root.setMountedCartId(cart.getUUID());if(joined)root.setCouplingId(cart.getUUID());root.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            if(!world.addFreshEntity(root)||root.isRemoved()||world.getEntity(root.getUUID())!=root)throw AssemblyException.structureChanged(pos);
            var actual=root;var other=secondary;boolean structural=joined;
            structure.expandBoundsAroundAxis(Direction.Axis.Y);
            var result=ContraptionAssemblyGraph.assemble(world,List.of(new ContraptionAssemblyGraph.Root(actual,structure)),complete->{
                controller.setMountedRoot(actual.getUUID());if(other!=null)other.setMountedRoot(actual.getUUID());
                actual.setMountedCartId(cart.getUUID());if(structural)actual.setCouplingId(cart.getUUID());
                if(!actual.startRiding(cart,true,true))structure.getStabilizedSubContraptions().setRecovering(true);
                if(cart instanceof MinecartFurnace furnace)furnace.push=Vec3.ZERO;
                controller.sendData();if(other!=null)other.sendData();
            });
            if(!result.complete())throw result.failure()==null?AssemblyException.entitiesNotReady(pos):result.failure();if(structure.getActors().stream().anyMatch(actor->{var behavior=com.simibubi.create.api.behaviour.movement.MovementBehaviour.REGISTRY.get(actor.left.state());return behavior instanceof com.simibubi.create.content.kinetics.base.BlockBreakingMovementBehaviour||behavior instanceof com.simibubi.create.content.contraptions.actors.harvester.HarvesterMovementBehaviour;}))sourceAdvancements.award("contraption_actors");lastException=null;changed();
        }catch(AssemblyException refused){lastException=refused;changed();}
        finally{
            if(root!=null&&root.getContraption()==null)root.discard();
            if(joined&&(root==null||!root.hasOwnedStructure())){
                // No source blocks moved: the structural metadata can retire without another value owner.
                if(controller.getMountedRoot()==null&&secondary.getMountedRoot()==null&&CouplingHandler.detachRetiredStructuralPair(controller,secondary)){controller.sendData();secondary.sendData();}
            }
        }
    }
    private void disassemble(ServerLevel world,BlockPos pos,AbstractMinecart cart){
        var controller=CapabilityMinecartController.getIfPresent(world,cart.getUUID());if(controller==null||controller.getMountedRoot()==null)return;
        if(!(world.getEntity(controller.getMountedRoot()) instanceof OrientedContraptionEntity root)||!root.hasOwnedStructure())return;
        var coupled=root.getCoupledCartsIfPresent();var controllers=coupled==null?List.of(controller):List.of(coupled.getFirst(),coupled.getSecond());
        if(root.getCouplingId()!=null&&coupled==null)return;
        for(var other:controllers){var actual=other.cart();var at=actual.getCurrentBlockPosOrRailBelow();if(!LoadedEntityAccess.isReady(world,at))return;var state=LoadedLevelAccess.blockState(world,at);if(!state.is(AllBlocks.CART_ASSEMBLER)||!CartAssemblerBlock.getActionForCart(state,actual).shouldDisassemble())return;}
        if(coupled==null)root.setAngles(CartAssemblerBlock.getHorizontalDirection(getBlockState()).toYRot(),0);
        // The actual root's shared retirement endpoint owns link/controller cleanup, including
        // automatic recovery and callback/unload retries after the last world placement.
        root.disassemble();
    }
    @Override protected void write(ValueOutput out, boolean clientPacket){super.write(out, clientPacket);out.putInt("ScrollValue",movementMode.ordinal());var error=savedError==null?new CompoundTag():savedError.copy();if(level!=null)AssemblyException.write(error,level.registryAccess(),lastException);out.store("AssemblyError",CompoundTag.CODEC,error);}
    @Override protected void read(ValueInput in, boolean clientPacket){super.read(in, clientPacket);movementMode=CartMovementMode.values()[Math.clamp(in.getIntOr("ScrollValue",0),0,2)];savedError=in.read("AssemblyError",CompoundTag.CODEC).orElse(new CompoundTag()).copy();if(level!=null){lastException=AssemblyException.read(savedError,level.registryAccess());savedError=null;}}
    @Override public void setLevel(Level level){super.setLevel(level);if(savedError!=null){lastException=AssemblyException.read(savedError,level.registryAccess());savedError=null;}}
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries){return super.getUpdateTag(registries);}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    @Override protected void addNativeAdvancementIds(java.util.List<String> ids) {
        ids.clear();
        ids.add("contraption_actors");
    }
    @Override protected com.simibubi.create.foundation.advancement.AdvancementBehaviour.OwnerStorage nativeAdvancementOwnerStorage() {return sourceAdvancements;}
}
