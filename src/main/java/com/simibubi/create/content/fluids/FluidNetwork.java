package com.simibubi.create.content.fluids;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;
import net.createmod.catnip.math.BlockFace;
import net.createmod.catnip.data.Pair;
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.content.fluids.PipeConnection.Flow;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
public class FluidNetwork {

	private static final int CYCLES_PER_TICK = 16;

	Level world;
	BlockFace start;

	Supplier<Storage<FluidVariant>> sourceSupplier;
	Storage<FluidVariant> source = null;
	long transferSpeed;

	int pauseBeforePropagation;
	List<BlockFace> queued;
	Set<Pair<BlockFace, PipeConnection>> frontier;
	Set<BlockPos> visited;
	FluidStack fluid;
	List<Pair<BlockFace, FlowSource>> targets;
	Map<BlockPos, WeakReference<FluidTransportBehaviour>> cache;

	public FluidNetwork(Level world, BlockFace location, Supplier<Storage<FluidVariant>> sourceSupplier) {
		this.world = world;
		this.start = location;
		this.sourceSupplier = sourceSupplier;
		this.fluid = FluidStack.EMPTY;
		this.frontier = new HashSet<>();
		this.visited = new HashSet<>();
		this.targets = new ArrayList<>();
		this.cache = new HashMap<>();
		this.queued = new ArrayList<>();
		reset();
	}

	public void tick() {
		if (pauseBeforePropagation > 0) {
			pauseBeforePropagation--;
			return;
		}

		for (int cycle = 0; cycle < CYCLES_PER_TICK; cycle++) {
			boolean shouldContinue = false;
			for (Iterator<BlockFace> iterator = queued.iterator(); iterator.hasNext();) {
				BlockFace blockFace = iterator.next();
				if (!isPresent(blockFace))
					continue;
				PipeConnection pipeConnection = get(blockFace);
				if (pipeConnection != null) {
					if (blockFace.equals(start))
						transferSpeed = 81L * (int) Math.max(1, pipeConnection.pressure.get(true) / 2f);
					frontier.add(Pair.of(blockFace, pipeConnection));
				}
				iterator.remove();
			}

//			drawDebugOutlines();

			for (Iterator<Pair<BlockFace, PipeConnection>> iterator = frontier.iterator(); iterator.hasNext();) {
				Pair<BlockFace, PipeConnection> pair = iterator.next();
				BlockFace blockFace = pair.getFirst();
				PipeConnection pipeConnection = pair.getSecond();

				if (!pipeConnection.hasFlow())
					continue;

				Flow flow = pipeConnection.flow.get();
				if (!fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(flow.fluid, fluid)) {
					iterator.remove();
					continue;
				}
				if (!flow.inbound) {
					if (pipeConnection.comparePressure() >= 0)
						iterator.remove();
					continue;
				}
				if (!flow.complete)
					continue;

				if (fluid.isEmpty())
					fluid = flow.fluid;

				boolean canRemove = true;
				for (Direction side : Direction.values()) {
					if (side == blockFace.getFace())
						continue;
					BlockFace adjacentLocation = new BlockFace(blockFace.getPos(), side);
					PipeConnection adjacent = get(adjacentLocation);
					if (adjacent == null)
						continue;
					if (!adjacent.hasFlow()) {
						// Branch could potentially still appear
						if (adjacent.hasPressure() && adjacent.pressure.getSecond() > 0)
							canRemove = false;
						continue;
					}
					Flow outFlow = adjacent.flow.get();
					if (outFlow.inbound) {
						if (adjacent.comparePressure() > 0)
							canRemove = false;
						continue;
					}
					if (!outFlow.complete) {
						canRemove = false;
						continue;
					}

					// Give pipe end a chance to init connections
					if (!adjacent.source.isPresent() && !adjacent.determineSource(world, blockFace.getPos())) {
						canRemove = false;
						continue;
					}

					if (adjacent.source.isPresent() && adjacent.source.get()
						.isEndpoint()) {
						targets.add(Pair.of(adjacentLocation, adjacent.source.get()));
						continue;
					}

					if (visited.add(adjacentLocation.getConnectedPos())) {
						queued.add(adjacentLocation.getOpposite());
						shouldContinue = true;
					}
				}
				if (canRemove)
					iterator.remove();
			}
			if (!shouldContinue)
				break;
		}

//		drawDebugOutlines();

        source=sourceSupplier.get();if(source==null||targets.isEmpty()||fluid.isEmpty())return;
        for(var target:targets)target.getSecond().manageSource(world,null);
        var reachable=activeEndpoints();
        var validTargets=targets.stream().filter(target->reachable.contains(target.getFirst())).toList();
        if(validTargets.isEmpty())return;
        var variant=fluid.variant();long available;
        try(var probe=Transaction.openOuter()){available=source.extract(variant,transferSpeed,probe);}
        if(available==0)return;
        try(var tx=Transaction.openOuter()){
            long remaining=available;
            var outputs=new ArrayList<>(validTargets);
            while(!outputs.isEmpty()&&remaining>0){
                long divided=remaining/outputs.size(),remainder=remaining%outputs.size();
                for(var iterator=outputs.iterator();iterator.hasNext();){
                    var output=iterator.next();long requested=divided+(remainder-->0?1:0);var target=output.getSecond().provideHandler();
                    if(target==null){iterator.remove();continue;}
                    long moved=target.insert(variant,requested,tx);remaining-=moved;
                    if(moved<requested)iterator.remove();
                }


            }
            long moved=available-remaining;
            if(moved>0&&source.extract(variant,moved,tx)==moved)tx.commit();
        }

	}


    /** Revalidate routes against current loaded pipe flows, including after a chunk unload or a valve change. */
    private Set<BlockFace> activeEndpoints(){
        var endpoints=new HashSet<BlockFace>();var seen=new HashSet<BlockFace>();var queue=new ArrayDeque<BlockFace>();queue.add(start);
        while(!queue.isEmpty()){
            var incoming=queue.removeFirst();if(!seen.add(incoming))continue;var in=get(incoming);
            if(in==null||!in.hasFlow()||!in.flow.get().complete||!in.flow.get().inbound||!FluidStack.isSameFluidSameComponents(in.flow.get().fluid,fluid))continue;
            var transport=getFluidTransfer(incoming.getPos());if(transport==null||!transport.canPullFluidFrom(in.flow.get().fluid,transport.blockEntity.getBlockState(),incoming.getFace()))continue;
            for(var side:Direction.values()){
                if(side==incoming.getFace())continue;var face=new BlockFace(incoming.getPos(),side);var out=get(face);
                if(out==null||!out.hasFlow()||out.flow.get().inbound||!out.flow.get().complete||!FluidStack.isSameFluidSameComponents(out.flow.get().fluid,fluid))continue;
                if(out.source.isPresent()&&out.source.get().isEndpoint())endpoints.add(face);
                else queue.add(face.getOpposite());
            }
        }
        return endpoints;
    }

	public void reset() {
		frontier.clear();
		visited.clear();
		targets.clear();
		queued.clear();
		fluid = FluidStack.EMPTY;
		queued.add(start);
		pauseBeforePropagation = 2;
	}

	@Nullable
	private PipeConnection get(BlockFace location) {
		BlockPos pos = location.getPos();
		FluidTransportBehaviour fluidTransfer = getFluidTransfer(pos);
		if (fluidTransfer == null)
			return null;
		return fluidTransfer.getConnection(location.getFace());
	}

	private boolean isPresent(BlockFace location) {
		return world.isLoaded(location.getPos());
	}

	@Nullable
	private FluidTransportBehaviour getFluidTransfer(BlockPos pos) {
		if (!world.isLoaded(pos)) return null;
        WeakReference<FluidTransportBehaviour> weakReference = cache.get(pos);
		FluidTransportBehaviour behaviour = weakReference != null ? weakReference.get() : null;
		if (behaviour != null && behaviour.blockEntity.isRemoved())
			behaviour = null;
		if (behaviour == null) {
			behaviour = FluidPropagator.getPipe(world, pos);
			if (behaviour != null)
				cache.put(pos, new WeakReference<>(behaviour));
		}
		return behaviour;
	}

}
