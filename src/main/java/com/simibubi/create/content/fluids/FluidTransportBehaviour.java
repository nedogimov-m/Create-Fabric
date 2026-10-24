package com.simibubi.create.content.fluids;
import java.util.*;
import org.jetbrains.annotations.Nullable;
import java.util.function.Predicate;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.*;
public abstract class FluidTransportBehaviour extends com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour {
    public static final com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<FluidTransportBehaviour> TYPE = new com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<>();
    @Override public com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType<?> getType() { return TYPE; }

    public interface Provider { FluidTransportBehaviour transport(); }
    public Level getWorld() { return blockEntity.getLevel(); }
    public BlockPos getPos() { return blockEntity.getBlockPos(); }
    public void sync() { blockEntity.setChanged(); var world=getWorld();if(world!=null&&!world.isClientSide())world.sendBlockUpdated(getPos(),blockEntity.getBlockState(),blockEntity.getBlockState(),3); }


	public enum UpdatePhase {
		WAIT_FOR_PUMPS, // Do not run Layer II logic while pumps could still be distributing pressure
		FLIP_FLOWS, // Do not cut any flows until all pipes had a chance to reverse them
		IDLE; // Operate normally
	}

	public Map<Direction, PipeConnection> interfaces;
	public UpdatePhase phase;

	public FluidTransportBehaviour(net.minecraft.world.level.block.entity.BlockEntity be) {
		super(be);
		phase = UpdatePhase.WAIT_FOR_PUMPS;
	}

	public boolean canPullFluidFrom(FluidStack fluid, BlockState state, Direction direction) {
		return true;
	}

	public abstract boolean canHaveFlowToward(BlockState state, Direction direction);

	public void initialize() {
		createConnectionData();
	}

	public void tick() {
        super.tick();
		Level world = getWorld();
		BlockPos pos = getPos();
		boolean onServer = !world.isClientSide() || blockEntity.isVirtual();

		if (interfaces == null)
			return;
		Collection<PipeConnection> connections = interfaces.values();

		// Do not provide a lone pipe connection with its own flow input
		PipeConnection singleSource = null;

//		if (onClient) {
//			connections.forEach(connection -> {
//				connection.visualizeFlow(pos);
//				connection.visualizePressure(pos);
//			});
//		}

		if (phase == UpdatePhase.WAIT_FOR_PUMPS) {
			phase = UpdatePhase.FLIP_FLOWS;
			return;
		}

		if (onServer) {
			boolean sendUpdate = false;
			for (PipeConnection connection : connections) {
				sendUpdate |= connection.flipFlowsIfPressureReversed();
				connection.manageSource(world, pos, blockEntity);
			}
			if (sendUpdate)
				sync();
		}

		if (phase == UpdatePhase.FLIP_FLOWS) {
			phase = UpdatePhase.IDLE;
			return;
		}

		if (onServer) {
			FluidStack availableFlow = FluidStack.EMPTY;
			FluidStack collidingFlow = FluidStack.EMPTY;

			for (PipeConnection connection : connections) {
				FluidStack fluidInFlow = connection.getProvidedFluid();
				if (fluidInFlow.isEmpty())
					continue;
				if (availableFlow.isEmpty()) {
					singleSource = connection;
					availableFlow = fluidInFlow;
					continue;
				}
				if (FluidStack.isSameFluidSameComponents(availableFlow, fluidInFlow)) {
					singleSource = null;
					availableFlow = fluidInFlow;
					continue;
				}
				collidingFlow = fluidInFlow;
				break;
			}

			if (!collidingFlow.isEmpty()) {
				FluidReactions.handlePipeFlowCollision(world, pos, availableFlow, collidingFlow);
				return;
			}

			boolean sendUpdate = false;
			for (PipeConnection connection : connections) {
				FluidStack internalFluid = singleSource != connection ? availableFlow : FluidStack.EMPTY;
				Predicate<FluidStack> extractionPredicate =
					extracted -> canPullFluidFrom(extracted, blockEntity.getBlockState(), connection.side);
				sendUpdate |= connection.manageFlows(world, pos, internalFluid, extractionPredicate);
			}

			if (sendUpdate)
				sync();
		}

		for (PipeConnection connection : connections)
			connection.tickFlowProgress(world, pos);
	}

	public FluidStack getProvidedOutwardFluid(Direction side) {
		createConnectionData();
		if (!interfaces.containsKey(side))
			return FluidStack.EMPTY;
		return interfaces.get(side)
			.provideOutboundFlow();
	}

	@Nullable
	public PipeConnection getConnection(Direction side) {
		createConnectionData();
		return interfaces.get(side);
	}

	public boolean hasAnyPressure() {
		createConnectionData();
		for (PipeConnection pipeConnection : interfaces.values())
			if (pipeConnection.hasPressure())
				return true;
		return false;
	}

	@Nullable
	public PipeConnection.Flow getFlow(Direction side) {
		createConnectionData();
		if (!interfaces.containsKey(side))
			return null;
		return interfaces.get(side).flow.orElse(null);
	}

	public void addPressure(Direction side, boolean inbound, float pressure) {
		createConnectionData();
		if (!interfaces.containsKey(side))
			return;
		interfaces.get(side)
			.addPressure(inbound, pressure);
		sync();
	}

	public void wipePressure() {
		if (interfaces != null)
			for (Direction d : Direction.values()) {
				if (!canHaveFlowToward(blockEntity.getBlockState(), d))
					interfaces.remove(d);
				else
					interfaces.computeIfAbsent(d, PipeConnection::new);
			}
		phase = UpdatePhase.WAIT_FOR_PUMPS;
		createConnectionData();
		interfaces.values()
			.forEach(PipeConnection::wipePressure);
		sync();
	}

	private void createConnectionData() {
		if (interfaces != null)
			return;
		interfaces = new IdentityHashMap<>();
		for (Direction d : Direction.values())
			if (canHaveFlowToward(blockEntity.getBlockState(), d))
				interfaces.put(d, new PipeConnection(d));
	}

    public enum AttachmentTypes {
        NONE, CONNECTION, DETAILED_CONNECTION, RIM, PARTIAL_RIM, DRAIN, PARTIAL_DRAIN;
        public AttachmentTypes withoutConnector(){return this==RIM?PARTIAL_RIM:this==DRAIN?PARTIAL_DRAIN:this;}
        public java.util.List<String> parts(){return switch(this){case NONE->java.util.List.of();case CONNECTION->java.util.List.of("connection");case DETAILED_CONNECTION->java.util.List.of("rim_connector");case RIM->java.util.List.of("rim_connector","rim");case PARTIAL_RIM->java.util.List.of("rim");case DRAIN->java.util.List.of("rim_connector","drain");case PARTIAL_DRAIN->java.util.List.of("drain");};}
    }
    public record PipeRenderData(java.util.List<AttachmentTypes> attachments,boolean casing){}
    /** Source rim hook on common native BlockGetter; no client-only world type is required. */
    public AttachmentTypes getRenderedRimAttachment(net.minecraft.world.level.BlockGetter world,BlockPos pos,BlockState state,Direction direction){
        if(!canHaveFlowToward(state,direction))return AttachmentTypes.NONE;
        var offset=pos.relative(direction);var next=FluidPropagator.stateAt(world,offset);
        if(next.getBlock() instanceof com.simibubi.create.content.fluids.pump.PumpBlock
            &&next.getValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING)==direction.getOpposite())return AttachmentTypes.NONE;
        if(next.getBlock() instanceof com.simibubi.create.content.fluids.pipes.EncasedPipeBlock
            &&com.simibubi.create.content.fluids.pipes.FluidPipeBlock.isOpenAt(next,direction.getOpposite()))return AttachmentTypes.RIM;
        if(FluidPropagator.hasFluidCapability(world,offset,direction.getOpposite())
            &&!next.is(com.simibubi.create.AllBlocks.HOSE_PULLEY))return AttachmentTypes.DRAIN;
        return AttachmentTypes.RIM;
    }
    public PipeRenderData renderData(){
        var state=blockEntity.getBlockState();var level=getWorld();var attachments=new java.util.ArrayList<AttachmentTypes>();
        for(var side:Direction.values()){
            var type=AttachmentTypes.NONE;
            if(level!=null&&canHaveFlowToward(state,side)){
                var pos=getPos().relative(side);var next=level.isLoaded(pos)?FluidPropagator.stateAt(level,pos):net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
                boolean normal=state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock,glass=state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.IAxisPipe;
                var axis=FluidPropagator.getStraightPipeAxis(state);var otherAxis=FluidPropagator.getStraightPipeAxis(next);
                type=getRenderedRimAttachment(level,getPos(),state,side);
                if(next.getBlock() instanceof com.simibubi.create.content.fluids.pump.PumpBlock&&next.getValue(com.simibubi.create.content.fluids.pump.PumpBlock.FACING)==side.getOpposite())type=AttachmentTypes.NONE;
                if(normal&&!(state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.EncasedPipeBlock)){
                    if(next.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock&&!(next.getBlock() instanceof com.simibubi.create.content.fluids.pipes.EncasedPipeBlock))type=axis==side.getAxis()?AttachmentTypes.CONNECTION:AttachmentTypes.DETAILED_CONNECTION;
                    else if(next.getBlock() instanceof com.simibubi.create.content.fluids.pipes.AxisPipeBlock&&otherAxis==side.getAxis())type=axis==side.getAxis()?AttachmentTypes.CONNECTION:AttachmentTypes.DETAILED_CONNECTION;
                    else if(type==AttachmentTypes.RIM&&FluidPropagator.getPipe(level,pos)!=null&&!(next.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock))type=AttachmentTypes.DETAILED_CONNECTION;
                    // Pumps hide the rim, but an open normal pipe still needs its connecting segment.
                    if(type==AttachmentTypes.NONE)type=AttachmentTypes.DETAILED_CONNECTION;
                }
                if(state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.EncasedPipeBlock&&type!=AttachmentTypes.DRAIN)type=AttachmentTypes.NONE;
                if(glass){if(type==AttachmentTypes.RIM&&(next.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock||axis==otherAxis))type=AttachmentTypes.NONE;type=type.withoutConnector();}
                if((blockEntity instanceof com.simibubi.create.content.fluids.pump.PumpBlockEntity||blockEntity instanceof com.simibubi.create.content.fluids.pipes.valve.FluidValveBlockEntity)&&type==AttachmentTypes.RIM)type=AttachmentTypes.NONE;
            }
            attachments.add(type);
        }
        boolean casing=state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock&&!(state.getBlock() instanceof com.simibubi.create.content.fluids.pipes.EncasedPipeBlock)&&java.util.Arrays.stream(Direction.Axis.values()).anyMatch(axis->java.util.Arrays.stream(Direction.values()).filter(side->side.getAxis()!=axis&&canHaveFlowToward(state,side)).count()>2);
        return new PipeRenderData(java.util.List.copyOf(attachments),casing);
    }
    public PipeRenderData clientRenderData=new PipeRenderData(java.util.Collections.nCopies(6,AttachmentTypes.NONE),false);
    public Object getRenderData(){
        // Virtual scene edits have no server packet to refresh the attachment snapshot.
        return blockEntity.isVirtual()?renderData():clientRenderData;
    }
    @Override public void read(ValueInput input,boolean clientPacket) {
        var previous=clientRenderData;
        read(input);
        var world=getWorld();
        if(clientPacket&&!previous.equals(clientRenderData)&&world!=null&&world.isClientSide()
            &&com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(world,getPos())==blockEntity)
            world.sendBlockUpdated(getPos(),blockEntity.getBlockState(),blockEntity.getBlockState(),net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }
    public void read(ValueInput root) { var in=root.childOrEmpty("Transport"); clientRenderData=new PipeRenderData(in.read("Attachments",com.mojang.serialization.Codec.INT.listOf()).orElse(java.util.List.of()).stream().map(n->AttachmentTypes.values()[Math.clamp(n,0,AttachmentTypes.values().length-1)]).toList(),in.getBooleanOr("Casing",false)); if(clientRenderData.attachments().size()!=6)clientRenderData=new PipeRenderData(java.util.Collections.nCopies(6,AttachmentTypes.NONE),false); interfaces=new IdentityHashMap<>();for(var side:Direction.values())in.child(side.getName()).ifPresent(data->{var connection=new PipeConnection(side);connection.read(data,getPos());interfaces.put(side,connection);});if(interfaces.isEmpty())interfaces=null; }
    public void write(ValueOutput root) { var out=root.child("Transport"); var render=renderData();out.store("Attachments",com.mojang.serialization.Codec.INT.listOf(),render.attachments().stream().map(Enum::ordinal).toList());out.putBoolean("Casing",render.casing());createConnectionData();interfaces.forEach((side,c)->c.write(out.child(side.getName()))); }
}
