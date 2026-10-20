package com.simibubi.create.content.trains.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import com.simibubi.create.content.trains.entity.Carriage.DimensionalCarriageEntity;
import com.simibubi.create.content.trains.entity.TravellingPoint.IEdgePointListener;
import com.simibubi.create.content.trains.entity.TravellingPoint.SteerDirection;
import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgeData;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackGraphLocation;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.observer.TrackObserver;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime.State;
import com.simibubi.create.content.trains.signal.SignalBlock.SignalType;
import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.TrainAdvancements;
import com.simibubi.create.content.trains.TrainManager;
import com.simibubi.create.content.trains.TrainConfig;
import com.simibubi.create.content.trains.RailwayGraphManager;
import com.simibubi.create.foundation.fluid.FluidStack;
import com.simibubi.create.foundation.utility.LoadedLevelAccess;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import com.simibubi.create.content.trains.graph.RailMath;

import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;


public class Train {
    public static final int MAX_CARRIAGES=200;
    /** Source public description: true bogey metadata and train identity, never cargo/body/passengers. */
    public static final StreamCodec<RegistryFriendlyByteBuf,Train> STREAM_CODEC=StreamCodec.of((buffer,train)->{
        if(train.isRestoring())throw new IllegalStateException("A restoring Train has no active public carriage description");
        buffer.writeUUID(train.id);buffer.writeBoolean(train.owner!=null);if(train.owner!=null)buffer.writeUUID(train.owner);
        buffer.writeVarInt(train.carriages.size());for(var carriage:train.carriages)Carriage.STREAM_CODEC.encode(buffer,carriage);
        buffer.writeVarInt(train.carriageSpacing.size());for(int spacing:train.carriageSpacing)buffer.writeVarInt(spacing);
        buffer.writeBoolean(train.doubleEnded);ComponentSerialization.STREAM_CODEC.encode(buffer,train.name);TrainIconType.STREAM_CODEC.encode(buffer,train.icon);buffer.writeInt(train.mapColorIndex);
    },buffer->{var id=buffer.readUUID();var owner=buffer.readBoolean()?buffer.readUUID():null;int count=descriptionCount(buffer,1,MAX_CARRIAGES);var carriages=new ArrayList<Carriage>();for(int i=0;i<count;i++)carriages.add(Carriage.STREAM_CODEC.decode(buffer));count=descriptionCount(buffer,0,MAX_CARRIAGES-1);var spacing=new ArrayList<Integer>();for(int i=0;i<count;i++)spacing.add(buffer.readVarInt());boolean doubleEnded=buffer.readBoolean();var name=ComponentSerialization.STREAM_CODEC.decode(buffer);var icon=TrainIconType.STREAM_CODEC.decode(buffer);int color=buffer.readInt();return new Train(id,owner,carriages,spacing,doubleEnded,name,icon,color);});
    private static int descriptionCount(RegistryFriendlyByteBuf buffer,int min,int max){int count=buffer.readVarInt();if(count<min||count>max)throw new io.netty.handler.codec.DecoderException("Invalid actual Train description size");return count;}
    private TrainManager manager;
    public TrainManager manager(){if(manager==null)throw new IllegalStateException("Public or unregistered Train has no native server traffic owner");return manager;}
    public @Nullable TrainManager trafficManager(){return manager;}
    public void bindManager(TrainManager owner){if(manager!=null&&owner!=null&&manager!=owner)throw new IllegalStateException("Train already belongs to another actual server");manager=owner;}
    private TrainConfig.Values config(){return manager==null?TrainConfig.client():TrainConfig.server();}

	public double speed = 0;
	public double targetSpeed = 0;
	public Double speedBeforeStall = null;
	public int carriageWaitingForChunks = -1;

	public double throttle = 1;
	public boolean honk = false;

	public UUID id;
	@Nullable
	public UUID owner;
	public TrackGraph graph;
	public Navigation navigation;
	public ScheduleRuntime runtime;
	public TrainIconType icon;
	public int mapColorIndex;
	public Component name;
	public TrainStatus status;

	public boolean invalid;

	public SteerDirection manualSteer;
	public boolean manualTick;

	public UUID currentStation;
	public boolean currentlyBackwards;

	public boolean doubleEnded;
	public List<Carriage> carriages;
	public List<Integer> carriageSpacing;

	public boolean updateSignalBlocks;
	public Map<UUID, UUID> occupiedSignalBlocks;
	public Set<UUID> reservedSignalBlocks;

	public Set<UUID> occupiedObservers;
	public Map<UUID, Pair<Integer, Boolean>> cachedObserverFiltering;

	List<TrainMigration> migratingPoints;
	public int migrationCooldown;
	public boolean derailed;

    private UUID disassemblyRequester;
    private net.minecraft.core.GlobalPos disassemblyReturnPosition;
    private ItemStack disassemblySchedule=ItemStack.EMPTY;
    private UUID disassemblyDropId;
    private boolean disassemblyDropAttempted;

	public int fuelTicks;
	public int honkTicks;

	public Boolean lowHonk;
	public int honkPitch;

	public float accumulatedSteamRelease;

	int tickOffset;
	int ticksSinceLastMailTransfer;
	double[] stress;

	// advancements
	public Player backwardsDriver;

	private Train(UUID id, UUID owner, List<Carriage> carriages, List<Integer> carriageSpacing, boolean doubleEnded, Component name, TrainIconType icon, int mapColorIndex) {
		this(id, owner, null, carriages, carriageSpacing, doubleEnded, name, icon, mapColorIndex);
	}

	public Train(UUID id, UUID owner, TrackGraph graph, List<Carriage> carriages, List<Integer> carriageSpacing, boolean doubleEnded, int mapColorIndex) {
		this(id, owner, graph, carriages, carriageSpacing, doubleEnded, Component.translatable("create." + "train.unnamed"), TrainIconType.getDefault(), mapColorIndex);
	}

	public Train(UUID id, UUID owner, TrackGraph graph, List<Carriage> carriages, List<Integer> carriageSpacing,
		boolean doubleEnded, Component name, TrainIconType icon, int mapColorIndex) {

        java.util.Objects.requireNonNull(carriages);
        java.util.Objects.requireNonNull(carriageSpacing);
        var checkedCarriages = List.copyOf(carriages);
        var checkedSpacing = List.copyOf(carriageSpacing);
        if(checkedCarriages.isEmpty()||checkedCarriages.size()>MAX_CARRIAGES||checkedSpacing.size()!=checkedCarriages.size()-1||checkedSpacing.stream().anyMatch(value->value<0||value>512))throw new IllegalArgumentException("Invalid actual Train carriage/spacing description");
        var distinct = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Carriage,Boolean>());
        for(var carriage:checkedCarriages)
            if(!distinct.add(carriage)||carriage.train!=null)
                throw new IllegalArgumentException("An actual Carriage may belong to exactly one Train");
        java.util.Objects.requireNonNull(name);
        java.util.Objects.requireNonNull(icon);
		this.id = java.util.Objects.requireNonNull(id);
		this.owner = owner;
		this.graph = graph;
		this.carriages = checkedCarriages;
		this.carriageSpacing = checkedSpacing;
		this.icon = icon;
		this.mapColorIndex = mapColorIndex;
		this.stress = new double[carriageSpacing.size()];
		this.name = name;
		this.status = new TrainStatus(this);
		this.doubleEnded = doubleEnded;

		checkedCarriages.forEach(c -> c.setTrain(this));

		navigation = new Navigation(this);
		runtime = new ScheduleRuntime(this);
		migratingPoints = new ArrayList<>();
		currentStation = null;
		manualSteer = SteerDirection.NONE;
		occupiedSignalBlocks = new HashMap<>();
		reservedSignalBlocks = new HashSet<>();
		occupiedObservers = new HashSet<>();
		cachedObserverFiltering = new HashMap<>();
		tickOffset = java.util.concurrent.ThreadLocalRandom.current().nextInt(100);
	}

    public void validateMotion(){
        if(!Double.isFinite(speed)||!Double.isFinite(targetSpeed)||!Double.isFinite(throttle)||throttle<0||throttle>1
            ||speedBeforeStall!=null&&!Double.isFinite(speedBeforeStall)||fuelTicks<0)
            throw new IllegalArgumentException("Invalid actual native Train speed/throttle/fuel state");
    }

    public boolean isRestoring(){return carriages.stream().anyMatch(carriage->carriage.isRestoring()||carriage.isWorldRestored());}

	public void earlyTick(Level level) {
		status.tick(level);
        if(isRestoring()){
            if(carriages.stream().anyMatch(carriage->!carriage.isWorldRestored()))addToSignalGroups(occupiedSignalBlocks.keySet());
            return;
        }
		if (graph == null && !migratingPoints.isEmpty())
			reattachToTracks(level);
		if (graph == null) {
			addToSignalGroups(occupiedSignalBlocks.keySet());
			return;
		}

		if (updateSignalBlocks) {
			updateSignalBlocks = false;
			collectInitiallyOccupiedSignalBlocks();
		}

		addToSignalGroups(occupiedSignalBlocks.keySet());
		addToSignalGroups(reservedSignalBlocks);

		if (occupiedObservers.isEmpty())
			return;

		tickOccupiedObservers(level);
	}

	private void tickOccupiedObservers(Level level) {
		int storageVersion = 0;
		for (Carriage carriage : carriages)
			storageVersion += carriage.storage.getVersion();

		for (UUID uuid : occupiedObservers) {
			TrackObserver observer = graph.getPoint(com.simibubi.create.content.trains.observer.ObserverEdgePoints.OBSERVER, uuid);
			if (observer == null)
				continue;

			FilterItemStack filter = observer.getFilter();
			if (filter.isEmpty()) {
				observer.keepAlive(id);
				continue;
			}

			Pair<Integer, Boolean> cachedMatch = cachedObserverFiltering.computeIfAbsent(uuid, $ -> Pair.of(-1, false));
			boolean shouldActivate = cachedMatch.getSecond();

			if (cachedMatch.getFirst() == storageVersion) {
				if (shouldActivate)
					observer.keepAlive(id);
				continue;
			}

			shouldActivate = false;
			for (Carriage carriage : carriages) {
				if (shouldActivate)
					break;

                var inventory=carriage.storage.getAllItems();
                if(inventory!=null)for(var slot:inventory){
                    if(slot.isResourceBlank()||slot.getAmount()<1)continue;
                    try(var transaction=Transaction.openOuter()){
                        var item=slot.getResource();
                        if(slot.extract(item,1,transaction)==1&&filter.test(level,item.toStack(1))){
                            shouldActivate=true;
                            break;
                        }
                    }
                }
                if(!shouldActivate){
                    var tanks=carriage.storage.getFluids();
                    if(tanks!=null)for(var tank:tanks){
                        if(tank.isResourceBlank()||tank.getAmount()<1)continue;
                        try(var transaction=Transaction.openOuter()){
                            var fluid=tank.getResource();
                            if(tank.extract(fluid,1,transaction)==1&&filter.test(level,new FluidStack(fluid,1))){
                                shouldActivate=true;
                                break;
                            }
                        }
                    }
                }
			}

			cachedObserverFiltering.put(uuid, Pair.of(storageVersion, shouldActivate));

			if (shouldActivate)
				observer.keepAlive(id);
		}
	}

	private void addToSignalGroups(Collection<UUID> groups) {
		Map<UUID, SignalEdgeGroup> groupMap = manager().signalEdgeGroups();
		for (Iterator<UUID> iterator = groups.iterator(); iterator.hasNext();) {
			SignalEdgeGroup signalEdgeGroup = groupMap.get(iterator.next());
			if (signalEdgeGroup == null)
				iterator.remove();
			else
				signalEdgeGroup.trains.add(this);
		}
	}

	public void tick(Level level) {
        manager().markDirty();
        if(isRestoring()){
            speed=targetSpeed=0;
            reservedSignalBlocks.clear();
            manager().hideRestoring(this);
            if(CarriageDisassembly.resume(this))finishDisassembly();
            return;
        }

		if (graph == null) {
			carriages.forEach(c -> c.manageEntities(level));
			updateConductors();
			return;
		}

		GlobalStation currentStation = getCurrentStation();
		if (currentStation != null) {
			ticksSinceLastMailTransfer++;
			if (ticksSinceLastMailTransfer > 20) {
				currentStation.runMailTransfer();
				ticksSinceLastMailTransfer = 0;
			}
		}

		updateConductors();
		runtime.tick(level);
		navigation.tick(level);

		tickPassiveSlowdown();
		if (derailed)
			tickDerailedSlowdown();

		double distance = speed;
		Carriage previousCarriage = null;
		int carriageCount = carriages.size();
		boolean stalled = false;
		double maxStress = 0;

		if (carriageWaitingForChunks != -1)
			distance = 0;

		for (int i = 0; i < carriageCount; i++) {
			Carriage carriage = carriages.get(i);
			if (previousCarriage != null) {
				int target = carriageSpacing.get(i - 1);
				double actual = target;

				TravellingPoint leadingPoint = carriage.getLeadingPoint();
				TravellingPoint trailingPoint = previousCarriage.getTrailingPoint();

				int entries = 0;
				double total = 0;

				if (leadingPoint.node1 != null && trailingPoint.node1 != null) {
					ResourceKey<Level> d1 = leadingPoint.node1.getLocation().dimension;
					ResourceKey<Level> d2 = trailingPoint.node1.getLocation().dimension;
					for (boolean b : new boolean[]{true,false}) {
						ResourceKey<Level> d = b ? d1 : d2;
						if (!b && d1.equals(d2))
							continue;
						if (!d1.equals(d2))
							continue;

						DimensionalCarriageEntity dimensional = carriage.getDimensionalIfPresent(d);
						DimensionalCarriageEntity dimensional2 = previousCarriage.getDimensionalIfPresent(d);
						if (dimensional == null || dimensional2 == null)
							continue;

						Vec3 leadingAnchor = dimensional.leadingAnchor();
						Vec3 trailingAnchor = dimensional2.trailingAnchor();
						if (leadingAnchor == null || trailingAnchor == null)
							continue;

						double distanceTo = leadingAnchor.distanceToSqr(trailingAnchor);
						if (carriage.leadingBogey().isUpsideDown() != previousCarriage.trailingBogey().isUpsideDown()) {
							distanceTo = Math.sqrt(Math.max(0,distanceTo - 4));
						} else {
							distanceTo = Math.sqrt(distanceTo);
						}
						total += distanceTo;
						entries++;
					}
				}


				if (entries > 0)
					actual = total / entries;

				stress[i - 1] = target - actual;
				maxStress = Math.max(maxStress, Math.abs(target - actual));
			}

			previousCarriage = carriage;

			if (carriage.stalled) {
				if (speedBeforeStall == null)
					speedBeforeStall = speed;
				distance = 0;
				speed = 0;
				stalled = true;
			}
		}

		if (!stalled && speedBeforeStall != null) {
			speed = Mth.clamp(speedBeforeStall, -1, 1);
			speedBeforeStall = null;
		}

		// positive stress: carriages should move apart
		// negative stress: carriages should move closer

		boolean approachingStation = navigation.distanceToDestination < 5;
		double leadingModifier = approachingStation ? 0.75d : 0.5d;
		double trailingModifier = approachingStation ? 0d : 0.125d;

		boolean blocked = false;
		boolean iterateFromBack = speed < 0;

		for (int index = 0; index < carriageCount; index++) {
			int i = iterateFromBack ? carriageCount - 1 - index : index;
			double leadingStress = i == 0 ? 0 : stress[i - 1] * -(iterateFromBack ? trailingModifier : leadingModifier);
			double trailingStress =
				i == stress.length ? 0 : stress[i] * (iterateFromBack ? leadingModifier : trailingModifier);

			Carriage carriage = carriages.get(i);

			TravellingPoint toFollowForward = i == 0 ? null
				: carriages.get(i - 1)
					.getTrailingPoint();

			TravellingPoint toFollowBackward = i == carriageCount - 1 ? null
				: carriages.get(i + 1)
					.getLeadingPoint();

			double totalStress = derailed ? 0 : leadingStress + trailingStress;

			boolean first = i == 0;
			boolean last = i == carriageCount - 1;
			int carriageType = first ? last ? Carriage.BOTH : Carriage.FIRST : last ? Carriage.LAST : Carriage.MIDDLE;
			double actualDistance =
				carriage.travel(level, graph, distance + totalStress, toFollowForward, toFollowBackward, carriageType);
			blocked |= carriage.blocked || carriage.isOnIncompatibleTrack();

			boolean onTwoBogeys = carriage.isOnTwoBogeys();
			maxStress = Math.max(maxStress, onTwoBogeys ? carriage.bogeySpacing - carriage.getAnchorDiff() : 0);
			maxStress = Math.max(maxStress, carriage.leadingBogey()
				.getStress());
			if (onTwoBogeys)
				maxStress = Math.max(maxStress, carriage.trailingBogey()
					.getStress());

			if (index == 0) {
				distance = actualDistance;
				collideWithOtherTrains(level, carriage);
				backwardsDriver = null;
				if (graph == null)
					return;
			}
		}

		if (blocked) {
			speed = 0;
			navigation.cancelNavigation();
			runtime.tick(level);
			status.endOfTrack();

		} else if (maxStress > 4) {
			speed = 0;
			navigation.cancelNavigation();
			runtime.tick(level);
			derailed = true;
			status.highStress();

		} else if (speed != 0)
			status.trackOK();

		updateNavigationTarget(level, distance);
	}

	public IEdgePointListener frontSignalListener() {
		return (distance, couple) -> {

			if (couple.getFirst()instanceof GlobalStation station) {
				if (!station.canApproachFrom(couple.getSecond()
					.getSecond()) || navigation.destination != station)
					return false;
				speed = 0;
				navigation.distanceToDestination = 0;
				navigation.currentPath.clear();
				arriveAt(navigation.destination);
				navigation.destination = null;
				return true;
			}

			if (couple.getFirst()instanceof TrackObserver observer) {
				occupiedObservers.add(observer.getId());
				return false;
			}

			if (!(couple.getFirst()instanceof SignalBoundary signal))
				return false;
			if (navigation.waitingForSignal != null && navigation.waitingForSignal.getFirst()
				.equals(signal.getId())) {
				speed = 0;
				navigation.distanceToSignal = 0;
				return true;
			}

			UUID groupId = signal.groupFor(couple.getSecond()
				.getSecond());
			SignalEdgeGroup signalEdgeGroup = manager().signalEdgeGroups().get(groupId);
			if (signalEdgeGroup == null)
				return false;

			if ((runtime.getSchedule() == null || runtime.paused) && signalEdgeGroup.isOccupiedUnless(this))
				carriages.forEach(c -> c.forEachPresentEntity(cce -> cce.getControllingPlayer()
					.ifPresent(uuid -> TrainAdvancements.RED_SIGNAL.awardTo(cce.level().getPlayerByUUID(uuid)))));

			manager().reserve(this,signalEdgeGroup,signal);
			occupy(groupId, signal.id);
			return false;

		};
	}

	public void cancelStall() {
		speedBeforeStall = null;
		carriages.forEach(c -> {
			c.stalled = false;
			c.forEachPresentEntity(cce -> cce.getContraption()
				.getActors()
				.forEach(pair -> {
					MovementBehaviour behaviour = MovementBehaviour.REGISTRY.get(pair.getKey().state());
					if (behaviour != null)
						behaviour.cancelStall(pair.getValue());
				}));
		});
	}

	private boolean occupy(UUID groupId, @Nullable UUID boundaryId) {
		reservedSignalBlocks.remove(groupId);
		if (boundaryId != null && occupiedSignalBlocks.containsKey(groupId))
			if (boundaryId.equals(occupiedSignalBlocks.get(groupId)))
				return false;
		return occupiedSignalBlocks.put(groupId, boundaryId) == null;
	}

	public IEdgePointListener backSignalListener() {
		return (distance, couple) -> {
			if (couple.getFirst()instanceof TrackObserver observer) {
				occupiedObservers.remove(observer.getId());
				cachedObserverFiltering.remove(observer.getId());
				return false;
			}
			if (!(couple.getFirst()instanceof SignalBoundary signal))
				return false;
			UUID groupId = signal.groupFor(couple.getSecond()
				.getFirst());
			occupiedSignalBlocks.remove(groupId);
			return false;
		};
	}

	private void updateNavigationTarget(Level level, double distance) {
		if (navigation.destination == null)
			return;

		Pair<UUID, Boolean> blockingSignal = navigation.waitingForSignal;
		boolean fullRefresh = navigation.distanceToDestination > 100 && navigation.distanceToDestination % 100 > 20;
		boolean signalRefresh = blockingSignal != null && navigation.distanceToSignal % 50 > 5;
		boolean partialRefresh = navigation.distanceToDestination < 100 && navigation.distanceToDestination % 50 > 5;

		double toSubstract = navigation.destinationBehindTrain ? -distance : distance;
		boolean navigatingManually = runtime.paused;

		navigation.distanceToDestination -= toSubstract;
		if (blockingSignal != null) {
			navigation.distanceToSignal -= toSubstract;
			signalRefresh &= navigation.distanceToSignal % 50 < 5;
		}

		fullRefresh &= navigation.distanceToDestination % 100 <= 20;
		partialRefresh &= navigation.distanceToDestination % 50 <= 5;

		if (blockingSignal != null && navigation.ticksWaitingForSignal % 100 == 50) {
			SignalBoundary signal = graph.getPoint(com.simibubi.create.content.trains.signal.SignalEdgePoints.SIGNAL, blockingSignal.getFirst());
			fullRefresh |= signal != null && signal.type(blockingSignal.getSecond()) == SignalType.CROSS_SIGNAL;
		}

		if (signalRefresh)
			navigation.waitingForSignal = null;
		if (!fullRefresh && !partialRefresh)
			return;
		if (!reservedSignalBlocks.isEmpty())
			return;

		if (!navigatingManually && fullRefresh) {
			DiscoveredPath preferredPath = runtime.startCurrentInstruction(level);
			if (preferredPath != null){
				navigation.startNavigation(preferredPath);
			}
		}
	}

	private void tickDerailedSlowdown() {
		speed /= 3f;
		if (Mth.equal(speed, 0))
			speed = 0;
	}

	private void tickPassiveSlowdown() {
		if (!manualTick && navigation.destination == null && speed != 0) {
			double acceleration = acceleration();
			if (speed > 0) {
				speed = Math.max(speed - acceleration, 0);
			} else
				speed = Math.min(speed + acceleration, 0);
		}
		manualTick = false;
	}

	private void updateConductors() {
		for (Carriage carriage : carriages)
			carriage.updateConductors();
	}

	public boolean hasForwardConductor() {
		for (Carriage carriage : carriages)
			if (carriage.presentConductors.getFirst())
				return true;
		return false;
	}

	public boolean hasBackwardConductor() {
		for (Carriage carriage : carriages)
			if (carriage.presentConductors.getSecond())
				return true;
		return false;
	}

	private void collideWithOtherTrains(Level level, Carriage carriage) {
		if (derailed)
			return;

		TravellingPoint trailingPoint = carriage.getTrailingPoint();
		TravellingPoint leadingPoint = carriage.getLeadingPoint();

		if (leadingPoint.node1 == null || trailingPoint.node1 == null || leadingPoint.edge == null || trailingPoint.edge == null)
			return;
		ResourceKey<Level> dimension = leadingPoint.node1.getLocation().dimension;
		if (!dimension.equals(trailingPoint.node1.getLocation().dimension))
			return;

		Vec3 start = (speed < 0 ? trailingPoint : leadingPoint).getPosition(graph);
		Vec3 end = (speed < 0 ? leadingPoint : trailingPoint).getPosition(graph);

		Pair<Train, Vec3> collision = findCollidingTrain(level, start, end, dimension);
		if (collision == null)
			return;

		Train train = collision.getFirst();

		double combinedSpeed = Math.abs(speed) + Math.abs(train.speed);
		if (combinedSpeed > .2f) {
			Vec3 v = collision.getSecond();
			level.explode(null, v.x, v.y, v.z, (float) Math.min(3 * combinedSpeed, 5), ExplosionInteraction.NONE);
		}

		crash();
		train.crash();
	}

	public Pair<Train, Vec3> findCollidingTrain(Level level, Vec3 start, Vec3 end, ResourceKey<Level> dimension) {
		return findCollidingTrain(level,start,end,dimension,graph);
	}

	/** Relocation probes the actual target graph without changing this train's old graph. */
	public Pair<Train, Vec3> findCollidingTrain(Level level, Vec3 start, Vec3 end, ResourceKey<Level> dimension, TrackGraph collisionGraph) {
		Vec3 diff = end.subtract(start);
		double maxDistanceSqr = Math.pow(config().maxAssemblyLength(), 2.0);

		Trains: for (Train train : level.isClientSide()?TrainClientRegistry.trains().values():manager().trains().values()) {
			if (train == this)
				continue;
			if (train.graph != null && train.graph != collisionGraph)
				continue;

			Vec3 lastPoint = null;

			for (Carriage otherCarriage : train.carriages) {
				for (boolean betweenBits : new boolean[]{true,false}) {
					if (betweenBits && lastPoint == null)
						continue;

					TravellingPoint otherLeading = otherCarriage.getLeadingPoint();
					TravellingPoint otherTrailing = otherCarriage.getTrailingPoint();
					if (otherLeading.edge == null || otherTrailing.edge == null)
						continue;
					ResourceKey<Level> otherDimension = otherLeading.node1.getLocation().dimension;
					if (!otherDimension.equals(otherTrailing.node1.getLocation().dimension))
						continue;
					if (!otherDimension.equals(dimension))
						continue;

					Vec3 start2 = otherLeading.getPosition(train.graph);
					Vec3 end2 = otherTrailing.getPosition(train.graph);

					if (Math.min(start2.distanceToSqr(start), end2.distanceToSqr(start)) > maxDistanceSqr)
						continue Trains;

					if (betweenBits) {
						end2 = start2;
						start2 = lastPoint;
					}

					lastPoint = end2;

					if ((end.y < end2.y - 3 || end2.y < end.y - 3)
						&& (start.y < start2.y - 3 || start2.y < start.y - 3))
						continue;

					Vec3 diff2 = end2.subtract(start2);
					Vec3 normedDiff = diff.normalize();
					Vec3 normedDiff2 = diff2.normalize();
					double[] intersect = RailMath.intersect(start, start2, normedDiff, normedDiff2, Axis.Y);

					if (intersect == null) {
						Vec3 intersectSphere = TrainMath.intersectSphere(start2, normedDiff2, start, .125f);
						if (intersectSphere == null)
							continue;
						if (!Mth.equal(normedDiff2.dot(intersectSphere.subtract(start2)
							.normalize()), 1))
							continue;
						intersect = new double[2];
						intersect[0] = intersectSphere.distanceTo(start) - .125;
						intersect[1] = intersectSphere.distanceTo(start2) - .125;
					}

					if (intersect[0] > diff.length())
						continue;
					if (intersect[1] > diff2.length())
						continue;
					if (intersect[0] < 0)
						continue;
					if (intersect[1] < 0)
						continue;

					return Pair.of(train, start.add(normedDiff.scale(intersect[0])));
				}
			}
		}
		return null;
	}

	public void crash() {
		navigation.cancelNavigation();
		if (derailed)
			return;
		speed = -Mth.clamp(speed, -.5, .5);
		derailed = true;
		graph = null;
		status.crash();

		for (Carriage carriage : carriages)
			carriage.forEachPresentEntity(e -> e.getIndirectPassengers()
				.forEach(entity -> {
					if (!(entity instanceof Player p))
						return;
					Optional<UUID> controllingPlayer = e.getControllingPlayer();
					if (controllingPlayer.isPresent() && controllingPlayer.get()
						.equals(p.getUUID()))
						return;
					TrainAdvancements.TRAIN_CRASH.awardTo(p);
				}));

		if (backwardsDriver != null)
			TrainAdvancements.TRAIN_CRASH_BACKWARDS.awardTo(backwardsDriver);
	}

    public boolean disassemble(Direction direction,BlockPos position,Player requester){
        if(requester==null||requester.level().getServer()!=manager().server())throw new IllegalArgumentException("Actual native disassembly requester required");
        if(isRestoring()&&disassemblyRequester!=null&&!disassemblyRequester.equals(requester.getUUID()))return false;
        disassemblyRequester=requester.getUUID();
        boolean completed=disassemble(direction,position);
        if(!completed&&!isRestoring()){disassemblyRequester=null;disassemblyReturnPosition=null;}
        return completed;
    }

    public boolean disassemble(Direction assemblyDirection,BlockPos pos){
        boolean completed;
        if(isRestoring())completed=CarriageDisassembly.resume(this);
        else{
            if(!canDisassemble())return false;
            var station=getCurrentStation();
            if(station!=null)disassemblyReturnPosition=station.owner();
            else{
                var front=carriages.getFirst().anyAvailableEntity();
                if(front==null)return false;
                disassemblyReturnPosition=net.minecraft.core.GlobalPos.of(front.level().dimension(),pos);
            }
            completed=CarriageDisassembly.begin(this,assemblyDirection,pos);
        }
        if(isRestoring()){
            speed=targetSpeed=0;
            reservedSignalBlocks.clear();
            manager().hideRestoring(this);
            manager().markDirty();
        }
        if(completed)return finishDisassembly();
        return false;
    }

    /** Derived solely from actual consumed row receipts and absence of every private schedule owner. */
    public boolean hasCompletedDisassembly(){
        return isRestoring()&&carriages.stream().allMatch(Carriage::isWorldRestored)
            &&runtime.getSchedule()==null&&disassemblySchedule.isEmpty()&&disassemblyDropId==null&&!disassemblyDropAttempted;
    }

    private boolean finishDisassembly(){
        if(carriages.stream().anyMatch(carriage->!carriage.isWorldRestored()))
            throw new IllegalStateException("Native Train disassembly has unfinished actual carriage owners");
        if(!returnDisassembledSchedule())return false;
        var station=getCurrentStation();
        if(station!=null){
            var world=manager().server().getLevel(station.getBlockEntityDimension());
            if(world!=null&&LoadedLevelAccess.blockEntity(world,station.getBlockEntityPos()) instanceof StationBlockEntity owner&&station.ownedBy(owner)){
                // Native BE load clears its transient point binding. Real Train ticks may finish
                // consumed restoration before that BE's next tick; refresh the actual binding
                // and retain these receipts until the exact current graph owner is ready.
                if(owner.targeting().resolveTick()!=station)return false;
                owner.rememberDisassembledTrain(this);
            }
            station.cancelReservation(this);
        }
        manager().remove(id);
        return true;
    }

    private boolean returnDisassembledSchedule(){
        if(runtime.getSchedule()!=null){
            if(!disassemblySchedule.isEmpty())throw new IllegalStateException("Native disassembly schedule has two owners");
            disassemblySchedule=runtime.returnSchedule(manager().server().registryAccess());
            if(!disassemblySchedule.isEmpty())disassemblyDropId=UUID.randomUUID();
            manager().markDirty();
        }
        if(disassemblySchedule.isEmpty())return true;
        var player=disassemblyRequester==null?null:manager().server().getPlayerList().getPlayer(disassemblyRequester);
        if(disassemblyReturnPosition==null)throw new IllegalStateException("Actual native schedule return position is missing");
        if(disassemblyDropId==null)throw new IllegalStateException("Actual native schedule return identity is missing");
        var world=manager().server().getLevel(disassemblyReturnPosition.dimension());
        boolean ready=world!=null&&com.simibubi.create.foundation.utility.LoadedEntityAccess.isReady(world,disassemblyReturnPosition.pos());
        if(disassemblyDropAttempted){
            if(!ready)return false;
            var existing=world.getEntity(disassemblyDropId);
            if(existing!=null){
                if(!(existing instanceof net.minecraft.world.entity.item.ItemEntity item)||!ItemStack.matches(item.getItem(),disassemblySchedule))
                    throw new IllegalStateException("Another native entity owns the pending schedule return identity");
                disassemblySchedule=ItemStack.EMPTY;disassemblyDropId=null;disassemblyDropAttempted=false;manager().markDirty();return true;
            }
        }
        if(player!=null&&player.getMainHandItem().isEmpty()){
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,disassemblySchedule);
            disassemblySchedule=ItemStack.EMPTY;disassemblyDropId=null;disassemblyDropAttempted=false;manager().markDirty();return true;
        }
        if(!ready)return false;
        var at=Vec3.atCenterOf(disassemblyReturnPosition.pos());
        var item=new net.minecraft.world.entity.item.ItemEntity(world,at.x,at.y,at.z,disassemblySchedule.copy());
        item.setUUID(disassemblyDropId);item.setDeltaMovement(Vec3.ZERO);
        disassemblyDropAttempted=true;manager().markDirty();
        if(!world.addFreshEntity(item)||world.getEntity(disassemblyDropId)!=item)return false;
        disassemblySchedule=ItemStack.EMPTY;disassemblyDropId=null;disassemblyDropAttempted=false;manager().markDirty();return true;
    }

	public boolean canDisassemble() {
        if(isRestoring())return false;
		for (Carriage carriage : carriages) {
			if (carriage.presentInMultipleDimensions())
				return false;
			CarriageContraptionEntity entity = carriage.anyAvailableEntity();
			if (entity == null)
				return false;
			if (!Mth.equal(entity.pitch, 0))
				return false;
			if (!Mth.equal(((entity.yaw % 90) + 360) % 90, 0))
				return false;
		}
		return true;
	}

	public boolean isTravellingOn(TrackNode node) {
		MutableBoolean affected = new MutableBoolean(false);
		forEachTravellingPoint(tp -> {
			if (tp.node1 == node || tp.node2 == node)
				affected.setTrue();
		});
		return affected.booleanValue();
	}

	public void detachFromTracks() {
		migratingPoints.clear();
		navigation.cancelNavigation();
		forEachTravellingPoint(tp -> migratingPoints.add(new TrainMigration(tp)));
		graph = null;
	}

	public void forEachTravellingPoint(Consumer<TravellingPoint> callback) {
		for (Carriage c : carriages) {
			c.leadingBogey().points.forEach(callback::accept);
			if (c.isOnTwoBogeys())
				c.trailingBogey().points.forEach(callback::accept);
		}
	}

	public void forEachTravellingPointBackwards(BiConsumer<TravellingPoint, Double> callback) {
		double lastWheelOffset = 0;
		for (int i = 0; i < carriages.size(); i++) {
			int index = carriages.size() - i - 1;
			Carriage carriage = carriages.get(index);
			CarriageBogey trailingBogey = carriage.trailingBogey();
			double trailSpacing = trailingBogey.type.getWheelPointSpacing();

			// trailing point
			callback.accept(trailingBogey.trailing(),
				i == 0 ? 0 : carriageSpacing.get(index) - lastWheelOffset - trailSpacing / 2);

			// inside 1st bogey
			callback.accept(trailingBogey.leading(), trailSpacing);

			lastWheelOffset = trailSpacing / 2;

			if (!carriage.isOnTwoBogeys())
				continue;

			CarriageBogey leadingBogey = carriage.leadingBogey();
			double leadSpacing = carriage.leadingBogey().type.getWheelPointSpacing();

			// between bogeys
			callback.accept(leadingBogey.trailing(), carriage.bogeySpacing - lastWheelOffset - leadSpacing / 2);

			// inside 2nd bogey
			callback.accept(trailingBogey.leading(), leadSpacing);

			lastWheelOffset = leadSpacing / 2;
		}
	}

	public void reattachToTracks(Level level) {
		if (migrationCooldown > 0) {
			migrationCooldown--;
			return;
		}

		Set<Entry<UUID, TrackGraph>> entrySet = new HashSet<>(RailwayGraphManager.get(level).getTrackNetworks().entrySet());
		Map<UUID, List<TrackGraphLocation>> successfulMigrations = new HashMap<>();
		for (TrainMigration md : migratingPoints) {
			for (Iterator<Entry<UUID, TrackGraph>> iterator = entrySet.iterator(); iterator.hasNext();) {
				Entry<UUID, TrackGraph> entry = iterator.next();
				TrackGraphLocation gl = md.tryMigratingTo(entry.getValue());
				if (gl == null) {
					iterator.remove();
					continue;
				}
				successfulMigrations.computeIfAbsent(entry.getKey(), uuid -> new ArrayList<>())
					.add(gl);
			}
		}

		if (entrySet.isEmpty()) {
			migrationCooldown = 40;
			status.failedMigration();
			derailed = true;
			return;
		}

		for (Entry<UUID, TrackGraph> entry : entrySet) {
			graph = entry.getValue();
			List<TrackGraphLocation> locations = successfulMigrations.get(entry.getKey());
			forEachTravellingPoint(tp -> tp.migrateTo(locations));
			migratingPoints.clear();
			if (derailed)
				status.successfulMigration();
			derailed = false;
			if (runtime.getSchedule() != null && runtime.state == State.IN_TRANSIT)
				runtime.state = State.PRE_TRANSIT;
			GlobalStation currentStation = getCurrentStation();
			if (currentStation != null)
				currentStation.reserveFor(this);
			updateSignalBlocks = true;
			migrationCooldown = 0;
			return;
		}
	}

	public int getTotalLength() {
		int length = 0;
		for (int i = 0; i < carriages.size(); i++) {
			Carriage carriage = carriages.get(i);
			if (i == 0)
				length += carriage.leadingBogey().type.getWheelPointSpacing() / 2;
			if (i == carriages.size() - 1)
				length += carriage.trailingBogey().type.getWheelPointSpacing() / 2;

			length += carriage.bogeySpacing;
			if (i < carriageSpacing.size())
				length += carriageSpacing.get(i);
		}
		return length;
	}

	public void leaveStation() {
		GlobalStation currentStation = getCurrentStation();
		if (currentStation != null)
			currentStation.trainDeparted(this);
		this.currentStation = null;
	}

	public void arriveAt(GlobalStation station) {
		setCurrentStation(station);
		reservedSignalBlocks.clear();
		runtime.destinationReached();
		station.runMailTransfer();
		ticksSinceLastMailTransfer = 0;
	}

	public void setCurrentStation(GlobalStation station) {
		currentStation = station.id;
	}

	public GlobalStation getCurrentStation() {
		if (currentStation == null)
			return null;
		if (graph == null)
			return null;
		return graph.getPoint(com.simibubi.create.content.trains.station.StationEdgePoints.STATION, currentStation);
	}

	@Nullable
	public LivingEntity getOwner(Level level) {
		if (level.getServer() == null)
			return null;
		try {
			UUID uuid = owner;
			return uuid == null ? null
				: level.getServer()
					.getPlayerList()
					.getPlayer(uuid);
		} catch (IllegalArgumentException illegalargumentexception) {
			return null;
		}
	}

	public void approachTargetSpeed(float accelerationMod) {
		double actualTarget = targetSpeed;
		if (Mth.equal(actualTarget, speed))
			return;
		if (manualTick)
			leaveStation();
		double acceleration = acceleration();
		if (speed < actualTarget)
			speed = Math.min(speed + acceleration * accelerationMod, actualTarget);
		else if (speed > actualTarget)
			speed = Math.max(speed - acceleration * accelerationMod, actualTarget);
	}

	public void collectInitiallyOccupiedSignalBlocks() {
		TravellingPoint trailingPoint = carriages.get(carriages.size() - 1)
			.getTrailingPoint();
		TrackNode node1 = trailingPoint.node1;
		TrackNode node2 = trailingPoint.node2;
		TrackEdge edge = trailingPoint.edge;

        if(graph==null||!trailingPoint.resolve(graph)){updateSignalBlocks=true;return;}
        node1=trailingPoint.node1;node2=trailingPoint.node2;edge=trailingPoint.edge;

		double position = trailingPoint.position;
		EdgeData signalData = edge.getEdgeData();

		occupiedSignalBlocks.clear();
		reservedSignalBlocks.clear();
		occupiedObservers.clear();
		cachedObserverFiltering.clear();

		TravellingPoint signalScout = new TravellingPoint(node1, node2, edge, position, false);
		Map<UUID, SignalEdgeGroup> allGroups = manager().signalEdgeGroups();
		MutableObject<UUID> prevGroup = new MutableObject<>(null);

		if (signalData.hasSignalBoundaries()) {
			SignalBoundary nextBoundary = signalData.next(com.simibubi.create.content.trains.signal.SignalEdgePoints.SIGNAL, position);
			if (nextBoundary == null) {
				double d = 0;
				SignalBoundary prev = null;
				SignalBoundary current = signalData.next(com.simibubi.create.content.trains.signal.SignalEdgePoints.SIGNAL, 0);
				while (current != null) {
					prev = current;
					d = current.getLocationOn(edge);
					current = signalData.next(com.simibubi.create.content.trains.signal.SignalEdgePoints.SIGNAL, d);
				}
				if (prev != null) {
					UUID group = prev.groupFor(node2);
					if (manager().signalEdgeGroups().containsKey(group)) {
						occupy(group, null);
						prevGroup.setValue(group);
					}
				}

			} else {
				UUID group = nextBoundary.groupFor(node1);
				if (manager().signalEdgeGroups().containsKey(group)) {
					occupy(group, null);
					prevGroup.setValue(group);
				}
			}

		} else {
			UUID groupId = signalData.getEffectiveEdgeGroupId(graph);
			if (allGroups.containsKey(groupId)) {
				occupy(groupId, null);
				prevGroup.setValue(groupId);
			}
		}

		forEachTravellingPointBackwards((tp, d) -> {
			signalScout.travel(graph, d, signalScout.follow(tp), (distance, couple) -> {
				if (couple.getFirst()instanceof TrackObserver observer) {
					occupiedObservers.add(observer.getId());
					return false;
				}
				if (!(couple.getFirst()instanceof SignalBoundary signal))
					return false;
				couple.getSecond()
					.map(signal::groupFor)
					.forEach(id -> {
						if (!manager().signalEdgeGroups().containsKey(id))
							return;
						if (id.equals(prevGroup.getValue()))
							return;
						occupy(id, null);
						prevGroup.setValue(id);
					});
				return false;
			}, signalScout.ignoreTurns());
		});

	}

	public boolean shouldCarriageSyncThisTick(long gameTicks, int updateInterval) {
		return (gameTicks + tickOffset) % updateInterval == 0;
	}

	public Couple<Couple<TrackNode>> getEndpointEdges() {
		return Couple.create(carriages.get(0)
			.getLeadingPoint(),
			carriages.get(carriages.size() - 1)
				.getTrailingPoint())
			.map(tp -> Couple.create(tp.node1, tp.node2));
	}

	public static class Penalties {
		static final int STATION = 50, STATION_WITH_TRAIN = 300;
		static final int MANUAL_TRAIN = 200, IDLE_TRAIN = 700, ARRIVING_TRAIN = 50, WAITING_TRAIN = 50, ANY_TRAIN = 25,
			RED_SIGNAL = 25, REDSTONE_RED_SIGNAL = 400;
	}

	public int getNavigationPenalty() {
		if (manualTick)
			return Penalties.MANUAL_TRAIN;
		if (runtime.getSchedule() == null || runtime.paused)
			return Penalties.IDLE_TRAIN;
		if (navigation.waitingForSignal != null && navigation.ticksWaitingForSignal > 0)
			return Penalties.WAITING_TRAIN + Math.min(navigation.ticksWaitingForSignal / 20, 1000);
		if (navigation.destination != null && navigation.distanceToDestination < 50 || navigation.distanceToSignal < 20)
			return Penalties.ARRIVING_TRAIN;
		return Penalties.ANY_TRAIN;
	}

	public void burnFuel() {
		if (fuelTicks > 0) {
			fuelTicks--;
			return;
		}

		boolean iterateFromBack = speed < 0;
		int carriageCount = carriages.size();

		for (int index = 0; index < carriageCount; index++) {
			int i = iterateFromBack ? carriageCount - 1 - index : index;
			Carriage carriage = carriages.get(i);
            var fuelItems=carriage.storage.getFuelItems();
            if(fuelItems==null)continue;
            var front=carriage.anyAvailableEntity();
            net.minecraft.server.level.ServerLevel world;
            Vec3 origin;
            if(front!=null&&front.level() instanceof net.minecraft.server.level.ServerLevel actual&&!front.isRemoved()){
                world=actual;
                origin=front.position();
            }else{
                var point=carriage.getLeadingPoint();
                if(graph==null||!point.resolve(graph))continue;
                world=manager().server().getLevel(point.node1.getLocation().dimension);
                if(world==null)continue;
                origin=point.getPosition(graph);
                front=null;
            }
            int duration=TrainFuel.consume(fuelItems,world,origin,front);
            if(duration>0){fuelTicks=duration;return;}
		}
	}

	public float maxSpeed() {
		return (fuelTicks > 0 ? config().poweredTrainTopSpeed()
			: config().trainTopSpeed()) / 20;
	}

	public float maxTurnSpeed() {
		return (fuelTicks > 0 ? config().poweredTrainTurningTopSpeed()
			: config().trainTurningTopSpeed()) / 20;
	}

	public float acceleration() {
		return (fuelTicks > 0 ? config().poweredTrainAcceleration()
			: config().trainAcceleration()) / 400;
	}

	public CompoundTag write(DimensionPalette dimensions, HolderLookup.Provider registries) {
        validateMotion();
		CompoundTag tag = new CompoundTag();
		tag.store("Id",UUIDUtil.CODEC,id);
        if(disassemblyRequester!=null)tag.store("DisassemblyRequester",UUIDUtil.CODEC,disassemblyRequester);
        if(disassemblyReturnPosition!=null)tag.store("DisassemblyReturnPosition",net.minecraft.core.GlobalPos.CODEC,disassemblyReturnPosition);
        if(!disassemblySchedule.isEmpty())tag.store("DisassemblySchedule",ItemStack.CODEC,registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),disassemblySchedule);
        if(disassemblyDropId!=null)tag.store("DisassemblyDropId",UUIDUtil.CODEC,disassemblyDropId);
        if(disassemblyDropAttempted)tag.putBoolean("DisassemblyDropAttempted",true);
		if (owner != null)
			tag.store("Owner",UUIDUtil.CODEC,owner);
		if (graph != null)
			tag.store("Graph",UUIDUtil.CODEC,graph.id);
		tag.put("Carriages", TrainNbt.writeCompoundList(carriages, c -> c.write(dimensions, registries)));
		tag.putIntArray("CarriageSpacing",carriageSpacing.stream().mapToInt(Integer::intValue).toArray());
		tag.putBoolean("DoubleEnded", doubleEnded);
		tag.putDouble("Speed", speed);
		tag.putDouble("Throttle", throttle);
		if (speedBeforeStall != null)
			tag.putDouble("SpeedBeforeStall", speedBeforeStall);
		tag.putInt("Fuel", fuelTicks);
		tag.putDouble("TargetSpeed", targetSpeed);
		tag.putString("IconType", icon.getId().toString());
		tag.putInt("MapColorIndex", mapColorIndex);
		tag.store("Name",ComponentSerialization.CODEC,registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),name);
		if (currentStation != null)
			tag.store("Station",UUIDUtil.CODEC,currentStation);
		tag.putBoolean("Backwards", currentlyBackwards);
		tag.putBoolean("Derailed", derailed);
		tag.putBoolean("UpdateSignals", updateSignalBlocks);
		tag.put("SignalBlocks", TrainNbt.writeCompoundList(occupiedSignalBlocks.entrySet(), e -> {
			CompoundTag compoundTag = new CompoundTag();
			compoundTag.store("Id",UUIDUtil.CODEC,e.getKey());
			if (e.getValue() != null)
				compoundTag.store("Boundary",UUIDUtil.CODEC,e.getValue());
			return compoundTag;
		}));
		tag.put("ReservedSignalBlocks", TrainNbt.writeCompoundList(reservedSignalBlocks, uid -> {
			CompoundTag compoundTag = new CompoundTag();
			compoundTag.store("Id",UUIDUtil.CODEC,uid);
			return compoundTag;
		}));
		tag.put("OccupiedObservers", TrainNbt.writeCompoundList(occupiedObservers, uid -> {
			CompoundTag compoundTag = new CompoundTag();
			compoundTag.store("Id",UUIDUtil.CODEC,uid);
			return compoundTag;
		}));
		tag.put("MigratingPoints", TrainNbt.writeCompoundList(migratingPoints, tm -> tm.write(dimensions)));

		tag.put("Runtime", runtime.write(registries));
		tag.put("Navigation", navigation.write(dimensions));

		return tag;
	}

	public static Train read(CompoundTag tag, HolderLookup.Provider registries, Map<UUID, TrackGraph> trackNetworks, DimensionPalette dimensions) {
		UUID id = TrainNbt.uuid(tag,"Id");
		UUID owner = tag.contains("Owner") ? TrainNbt.uuid(tag,"Owner") : null;
		UUID graphId = tag.contains("Graph") ? TrainNbt.uuid(tag,"Graph") : null;
		TrackGraph graph = graphId == null ? null : trackNetworks.get(graphId);
		List<Carriage> carriages = new ArrayList<>();
		TrainNbt.iterateCompoundList(tag.getListOrEmpty("Carriages"),
			c -> carriages.add(Carriage.read(c, registries, graph, dimensions)));
		List<Integer> carriageSpacing = new ArrayList<>();
		for (int i : tag.getIntArray("CarriageSpacing").orElseThrow())
			carriageSpacing.add(i);
		boolean doubleEnded = tag.getBooleanOr("DoubleEnded",false);
		int mapColorIndex = tag.getIntOr("MapColorIndex",0);

		Train train = new Train(id, owner, graph, carriages, carriageSpacing, doubleEnded, mapColorIndex);

		train.speed = tag.getDoubleOr("Speed",0);
		train.throttle = tag.getDoubleOr("Throttle",1);
		if (tag.contains("SpeedBeforeStall"))
			train.speedBeforeStall = tag.getDoubleOr("SpeedBeforeStall",0);
		train.targetSpeed = tag.getDoubleOr("TargetSpeed",0);
		train.icon = TrainIconType.byId(Identifier.parse(tag.getStringOr("IconType",TrainIconType.getDefault().getId().toString())));
		train.name = TrainNbt.component(tag,"Name",registries);
		train.currentStation = tag.contains("Station") ? TrainNbt.uuid(tag,"Station") : null;
		train.currentlyBackwards = tag.getBooleanOr("Backwards",false);
		train.derailed = tag.getBooleanOr("Derailed",false);
		train.updateSignalBlocks = tag.getBooleanOr("UpdateSignals",false);
		train.fuelTicks = tag.getIntOr("Fuel",0);

		TrainNbt.iterateCompoundList(tag.getListOrEmpty("SignalBlocks"), c -> train.occupiedSignalBlocks
			.put(TrainNbt.uuid(c,"Id"), c.contains("Boundary") ? TrainNbt.uuid(c,"Boundary") : null));
		TrainNbt.iterateCompoundList(tag.getListOrEmpty("ReservedSignalBlocks"),
			c -> train.reservedSignalBlocks.add(TrainNbt.uuid(c,"Id")));
		TrainNbt.iterateCompoundList(tag.getListOrEmpty("OccupiedObservers"),
			c -> train.occupiedObservers.add(TrainNbt.uuid(c,"Id")));
		TrainNbt.iterateCompoundList(tag.getListOrEmpty("MigratingPoints"),
			c -> train.migratingPoints.add(TrainMigration.read(c, dimensions)));

        train.disassemblyRequester=tag.read("DisassemblyRequester",UUIDUtil.CODEC).orElse(null);
        train.disassemblyReturnPosition=tag.read("DisassemblyReturnPosition",net.minecraft.core.GlobalPos.CODEC).orElse(null);
        train.disassemblySchedule=tag.read("DisassemblySchedule",ItemStack.CODEC,registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE)).orElse(ItemStack.EMPTY);
        train.disassemblyDropId=tag.read("DisassemblyDropId",UUIDUtil.CODEC).orElse(null);
        train.disassemblyDropAttempted=tag.getBooleanOr("DisassemblyDropAttempted",false);
        if(train.disassemblyDropAttempted&&train.disassemblySchedule.isEmpty()
            ||!train.disassemblySchedule.isEmpty()&&(!train.isRestoring()||train.disassemblyReturnPosition==null||train.disassemblyDropId==null||train.disassemblySchedule.getCount()!=1||!train.disassemblySchedule.is(com.simibubi.create.AllItems.SCHEDULE)))
            throw new IllegalArgumentException("Invalid actual pending disassembly schedule custody");
        train.validateMotion();
		train.runtime.read(registries, tag.getCompoundOrEmpty("Runtime"));
        if(!train.disassemblySchedule.isEmpty()&&train.runtime.getSchedule()!=null)
            throw new IllegalArgumentException("Actual pending schedule cannot also remain owned by runtime");
		train.navigation.read(tag.getCompoundOrEmpty("Navigation"), graph, dimensions);


		return train;
	}

	public int countPlayerPassengers() {
		AtomicInteger count = new AtomicInteger();
		for (Carriage carriage : carriages)
			carriage.forEachPresentEntity(e -> e.getIndirectPassengers()
				.forEach(p -> {
					if (p instanceof Player)
						count.incrementAndGet();
				}));
		return count.intValue();
	}

	public void determineHonk(Level level) {
		if (lowHonk != null)
			return;
        for (Carriage carriage : carriages) {
            DimensionalCarriageEntity dimensional = carriage.getDimensionalIfPresent(level.dimension());
            if (dimensional == null)
                return;
            CarriageContraptionEntity entity = dimensional.entity.get();
            if (entity == null || !(entity.getContraption() instanceof CarriageContraption otherCC))
                break;
            Pair<Boolean, Integer> first = otherCC.soundQueue.getFirstWhistle(entity);
            if (first != null) {
                lowHonk = first.getFirst();
                honkPitch = first.getSecond();
            }
        }
	}

	public float distanceToLocationSqr(Level level, Vec3 location) {
		float distance = Float.MAX_VALUE;
		for (Carriage carriage : carriages) {
			DimensionalCarriageEntity dce = carriage.getDimensionalIfPresent(level.dimension());
			if (dce == null || dce.positionAnchor == null)
				continue;
			distance = Math.min(distance, (float) dce.positionAnchor.distanceToSqr(location));
		}
		return distance;
	}

	public List<ResourceKey<Level>> getPresentDimensions() {
		return carriages.stream()
				.flatMap((Carriage carriage) -> carriage.getPresentDimensions().stream())
				.distinct()
				.toList();
	}

	public Optional<BlockPos> getPositionInDimension(ResourceKey<Level> dimension) {
		return carriages.stream()
				.map(carriage -> carriage.getPositionInDimension(dimension))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.findFirst();
	}

}
