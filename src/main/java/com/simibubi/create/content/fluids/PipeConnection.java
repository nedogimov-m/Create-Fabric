package com.simibubi.create.content.fluids;
import java.util.*;
import java.util.function.Predicate;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.math.BlockFace;
import com.simibubi.create.foundation.fluid.FluidStack;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.*;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
public class PipeConnection {

	public Direction side;
    private OpenEndedPipe cachedOpenEnd;

	// Layer I
	Couple<Float> pressure; // [inbound, outward]
	Optional<FlowSource> source;
	Optional<FlowSource> previousSource;

	// Layer II
	Optional<Flow> flow;
	boolean particleSplashNextTick;

	// Layer III
	Optional<FluidNetwork> network; // not serialized

	public PipeConnection(Direction side) {
		this.side = side;
		pressure = Couple.create(() -> 0f);
		flow = Optional.empty();
		previousSource = Optional.empty();
		source = Optional.empty();
		network = Optional.empty();
		particleSplashNextTick = false;
	}

	public FluidStack getProvidedFluid() {
		FluidStack empty = FluidStack.EMPTY;
		if (!hasFlow())
			return empty;
		Flow flow = this.flow.get();
		if (!flow.inbound)
			return empty;
		if (!flow.complete)
			return empty;
		return flow.fluid;
	}

	public boolean flipFlowsIfPressureReversed() {
		if (!hasFlow())
			return false;
		boolean singlePressure = comparePressure() != 0 && (getInboundPressure() == 0 || getOutwardPressure() == 0);
		Flow flow = this.flow.get();
		if (!singlePressure || comparePressure() < 0 == flow.inbound)
			return false;
		flow.inbound = !flow.inbound;
		if (!flow.complete)
			this.flow = Optional.empty();
		return true;
	}

	public void manageSource(Level world, BlockPos pos, BlockEntity blockEntity) {
		if (!world.isLoaded(pos.relative(side))) { if (source.orElse(null) instanceof OpenEndedPipe) previousSource=source; source = Optional.empty(); return; }
        if (!source.isPresent() && !determineSource(world, pos))
			return;
		FlowSource flowSource = source.get();
		flowSource.manageSource(world, blockEntity);
	}

	public boolean manageFlows(Level world, BlockPos pos, FluidStack internalFluid,
		Predicate<FluidStack> extractionPredicate) {

		// Only keep network if still valid
		Optional<FluidNetwork> retainedNetwork = network;
		network = Optional.empty();

		// chunk border
		if (!source.isPresent() && !determineSource(world, pos))
			return false;
		FlowSource flowSource = source.get();

		if (!hasFlow()) {
			if (!hasPressure())
				return false;

			// Try starting a new flow
			boolean prioritizeInbound = comparePressure() < 0;
			for (boolean trueFalse : new boolean[]{true, false}) {
				boolean inbound = prioritizeInbound == trueFalse;
				if (pressure.get(inbound) == 0)
					continue;
				if (tryStartingNewFlow(inbound, inbound ? flowSource.provideFluid(extractionPredicate) : internalFluid))
					return true;
			}
			return false;
		}

		// Manage existing flow
		Flow flow = this.flow.get();
		FluidStack provided = flow.inbound ? flowSource.provideFluid(extractionPredicate) : internalFluid;
		if (!hasPressure() || provided.isEmpty() || !FluidStack.isSameFluidSameComponents(provided, flow.fluid)) {
			this.flow = Optional.empty();
			return true;
		}

		// Overwrite existing flow
		if (flow.inbound != comparePressure() < 0) {
			boolean inbound = !flow.inbound;
			if (inbound && !provided.isEmpty() || !inbound && !internalFluid.isEmpty()) {
				FluidPropagator.resetAffectedFluidNetworks(world, pos, side);
				tryStartingNewFlow(inbound, inbound ? flowSource.provideFluid(extractionPredicate) : internalFluid);
				return true;
			}
		}

		flowSource.whileFlowPresent(world, flow.inbound);

		if (!flowSource.isEndpoint())
			return false;
		if (!flow.inbound)
			return false;

		// Layer III
		network = retainedNetwork;
		if (!hasNetwork())
			network = Optional.of(new FluidNetwork(world, new BlockFace(pos, side), flowSource::provideHandler));
		network.get()
			.tick();

		return false;
	}

	private boolean tryStartingNewFlow(boolean inbound, FluidStack providedFluid) {
		if (providedFluid.isEmpty())
			return false;
		Flow flow = new Flow(inbound, providedFluid);
		this.flow = Optional.of(flow);
		return true;
	}

	public boolean determineSource(Level world, BlockPos pos) {
		BlockPos relative = pos.relative(side);
		// cannot use world.isLoaded because it always returns true on client
		if (world.getChunk(relative.getX() >> 4, relative.getZ() >> 4, ChunkStatus.FULL, false) == null)
			return false;

		BlockFace location = new BlockFace(pos, side);
		if (FluidPropagator.isOpenEnd(world, pos, side)) {
			if (cachedOpenEnd!=null)
                source=Optional.of(cachedOpenEnd);
            else if (previousSource.orElse(null) instanceof OpenEndedPipe)
				source = previousSource;
			else
				source = Optional.of(new OpenEndedPipe(location));
            cachedOpenEnd=(OpenEndedPipe)source.get();
			return true;
		}

		if (FluidPropagator.hasFluidCapability(world, location.getConnectedPos(), side.getOpposite())) {
			source = Optional.of(new FlowSource.FluidHandler(location));
			return true;
		}

		FluidTransportBehaviour behaviour =
			FluidPropagator.getPipe(world, relative);
		source = Optional.of(behaviour == null ? new FlowSource.Blocked(location) : new FlowSource.OtherPipe(location));
		return true;
	}

	public void tickFlowProgress(Level world, BlockPos pos) {
		if (!hasFlow())
			return;
		Flow flow = this.flow.get();
		if (flow.fluid.isEmpty())
			return;

		float flowSpeed = 1 / 32f + Mth.clamp(pressure.get(flow.inbound) / 128f, 0, 1) * 31 / 32f;
		flow.previousProgress = flow.progress; flow.progress = Math.min(flow.progress + flowSpeed, 1);
		if (flow.progress >= 1)
			flow.complete = true;
	}

	/**
	 * @return zero if outward == inbound <br>
	 *         positive if outward {@literal >} inbound <br>
	 *         negative if outward {@literal <} inbound
	 */
	public float comparePressure() {
		return getOutwardPressure() - getInboundPressure();
	}

	public void wipePressure() {
		this.pressure.replace(f -> 0f);
		if (this.source.isPresent())
			this.previousSource = this.source;
		this.source = Optional.empty();
		resetNetwork();
	}

	public FluidStack provideOutboundFlow() {
		if (!hasFlow())
			return FluidStack.EMPTY;
		Flow flow = this.flow.get();
		if (!flow.complete || flow.inbound)
			return FluidStack.EMPTY;
		return flow.fluid;
	}

	public void addPressure(boolean inbound, float pressure) {
		this.pressure = this.pressure.mapWithContext((f, in) -> in == inbound ? f + pressure : f);
	}

	public Couple<Float> getPressure() {
		return pressure;
	}

	public boolean hasPressure() {
		return getInboundPressure() != 0 || getOutwardPressure() != 0;
	}

	public float getOutwardPressure() {
		return pressure.getSecond();
	}

	public float getInboundPressure() {
		return pressure.getFirst();
	}

	public boolean hasFlow() {
		return flow.isPresent();
	}

	public boolean hasNetwork() {
		return network.isPresent();
	}

	public void resetNetwork() {
		network.ifPresent(FluidNetwork::reset);
	}

	public class Flow {

		public boolean complete;
		public boolean inbound;
		public float progress, previousProgress;
		public FluidStack fluid;

		public Flow(boolean inbound, FluidStack fluid) {
			this.inbound = inbound;
			this.fluid = fluid;
			this.progress = 0;
			this.complete = false;
		}

	}


    public void write(ValueOutput out) {
        out.putFloat("Inbound", getInboundPressure()); out.putFloat("Outward", getOutwardPressure());
        if (hasFlow()) {
            var f=flow.get(); var data=out.child("Flow"); data.store("Fluid",FluidVariant.CODEC,f.fluid.variant()); data.putBoolean("In",f.inbound); data.putFloat("Progress",f.progress);
        }
        var open=source.orElse(previousSource.orElse(cachedOpenEnd)); if(cachedOpenEnd!=null)cachedOpenEnd.write(out.child("OpenEnd"));else if(open instanceof OpenEndedPipe end)end.write(out.child("OpenEnd"));
    }
    public void read(ValueInput in, BlockPos pos) {
        pressure=Couple.create(finite(in.getFloatOr("Inbound",0)), finite(in.getFloatOr("Outward",0)));
        flow=in.child("Flow").flatMap(data->data.read("Fluid",FluidVariant.CODEC).filter(v->!v.isBlank()).map(v->{var f=new Flow(data.getBooleanOr("In",false),new FluidStack(v,81)); f.progress=f.previousProgress=Math.clamp(finite(data.getFloatOr("Progress",0)),0,1);f.complete=f.progress>=1;return f;}));
        source=in.child("OpenEnd").map(data->{var end=new OpenEndedPipe(new BlockFace(pos,side));end.read(data);cachedOpenEnd=end;return (FlowSource)end;});
    }
    private static float finite(float value) {return Float.isFinite(value)?Math.max(0,value):0;}
}
