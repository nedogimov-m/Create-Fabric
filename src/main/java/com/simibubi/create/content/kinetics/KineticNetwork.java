package com.simibubi.create.content.kinetics;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

public class KineticNetwork {

	public Long id;
	public boolean initialized;
	public Map<KineticBlockEntity, Float> sources;
	public Map<KineticBlockEntity, Float> members;

	private float currentCapacity;
	private float currentStress;
	private float unloadedCapacity;
	private float unloadedStress;
	private int unloadedMembers;

	public KineticNetwork() {
		sources = new HashMap<>();
		members = new HashMap<>();
	}

	public void initFromTE(float maxStress, float currentStress, int members) {
		unloadedCapacity = maxStress;
		unloadedStress = currentStress;
		unloadedMembers = members;
		initialized = true;
		updateStress();
		updateCapacity();
	}

	public void addSilently(KineticBlockEntity be, float lastCapacity, float lastStress) {
		enlist(be);
		if (members.containsKey(be))
			return;
		if (be.isSource()) {
			unloadedCapacity -= lastCapacity * getStressMultiplierForSpeed(be.getGeneratedSpeed());
			float addedStressCapacity = be.calculateAddedStressCapacity();
			sources.put(be, addedStressCapacity);
		}

		unloadedStress -= lastStress * getStressMultiplierForSpeed(be.getTheoreticalSpeed());
		float stressApplied = be.calculateStressApplied();
		members.put(be, stressApplied);

		unloadedMembers--;
		if (unloadedMembers < 0)
			unloadedMembers = 0;
		if (unloadedCapacity < 0)
			unloadedCapacity = 0;
		if (unloadedStress < 0)
			unloadedStress = 0;
	}

	public void add(KineticBlockEntity be) {
		enlist(be);
		if (members.containsKey(be))
			return;
		if (be.isSource())
			sources.put(be, be.calculateAddedStressCapacity());
		members.put(be, be.calculateStressApplied());
		updateFromNetwork(be);
		be.networkDirty = true;
	}

	public void updateCapacityFor(KineticBlockEntity be, float capacity) {
		enlist(be);
		sources.put(be, capacity);
		updateCapacity();
	}

	public void updateStressFor(KineticBlockEntity be, float stress) {
		enlist(be);
		members.put(be, stress);
		updateStress();
	}

	public void remove(KineticBlockEntity be) {
		if (!members.containsKey(be))
			return;
		sources.remove(be);
		members.remove(be);
		be.updateFromNetwork(0, 0, 0);

		if (members.isEmpty()) {
			TorquePropagator.removeNetwork(be.getLevel(), this);
			return;
		}

		members.keySet()
			.stream()
			.findFirst()
			.map(member -> member.networkDirty = true);
	}

	public void sync() {
		for (KineticBlockEntity be : java.util.List.copyOf(members.keySet()))
			if (members.containsKey(be) && isLoadedOwner(be)) updateFromNetwork(be);
	}

	private void updateFromNetwork(KineticBlockEntity be) {
		be.updateFromNetwork(currentCapacity, currentStress, getSize());
	}

	public void updateCapacity() {
		float newMaxStress = calculateCapacity();
		if (currentCapacity != newMaxStress) {
			currentCapacity = newMaxStress;
			sync();
		}
	}

	public void updateStress() {
		float newStress = calculateStress();
		if (currentStress != newStress) {
			currentStress = newStress;
			sync();
		}
	}

	public void updateNetwork() {
		float newStress = calculateStress();
		float newMaxStress = calculateCapacity();
		if (currentStress != newStress || currentCapacity != newMaxStress) {
			currentStress = newStress;
			currentCapacity = newMaxStress;
			sync();
		}
	}

	public float calculateCapacity() {
		float presentCapacity = 0;
		for (Iterator<KineticBlockEntity> iterator = sources.keySet()
			.iterator(); iterator.hasNext();) {
			KineticBlockEntity be = iterator.next();
			if (!isLoadedOwner(be)) {
				iterator.remove();
				continue;
			}
			enlist(be);
			presentCapacity += getActualCapacityOf(be);
		}
		float newMaxStress = presentCapacity + unloadedCapacity;
		return newMaxStress;
	}

	public float calculateStress() {
		float presentStress = 0;
		for (Iterator<KineticBlockEntity> iterator = members.keySet()
			.iterator(); iterator.hasNext();) {
			KineticBlockEntity be = iterator.next();
			if (!isLoadedOwner(be)) {
				iterator.remove();
				continue;
			}
			enlist(be);
			presentStress += getActualStressOf(be);
		}
		float newStress = presentStress + unloadedStress;
		return newStress;
	}

	public float getActualCapacityOf(KineticBlockEntity be) {
		return sources.get(be) * getStressMultiplierForSpeed(be.getGeneratedSpeed());
	}

	public float getActualStressOf(KineticBlockEntity be) {
		return members.get(be) * getStressMultiplierForSpeed(be.getTheoreticalSpeed());
	}

	private static void enlist(KineticBlockEntity be) {
		if (be.getLevel() instanceof net.minecraft.server.level.ServerLevel server && !be.isVirtual()
				&& KineticNetworks.isLive(server, be)) KineticNetworks.track(server, be);
	}

	/** Loaded identity check; addon maps cannot turn a stale owner into a chunk ticket. */
	private static boolean isLoadedOwner(KineticBlockEntity be) {
		return be.getLevel() != null && !be.isRemoved()
			&& com.simibubi.create.foundation.utility.LoadedLevelAccess.blockEntity(be.getLevel(), be.getBlockPos()) == be;
	}

	/** Remove an unloaded owner while retaining its saved source contributions exactly once. */
	void unload(KineticBlockEntity be) {
		Float oldImpact = members.remove(be);
		if (oldImpact != null) {
			unloadedMembers++;
			unloadedStress += oldImpact * getStressMultiplierForSpeed(be.getTheoreticalSpeed());
		}
		Float oldCapacity = sources.remove(be);
		if (oldCapacity != null)
			unloadedCapacity += oldCapacity * getStressMultiplierForSpeed(be.getGeneratedSpeed());
	}

	/** Silent canonical rebinding avoids publishing a transient stop inside one solved update. */
	void unlinkForRebind(KineticBlockEntity be) {
		members.remove(be);
		sources.remove(be);
		if (members.isEmpty() && unloadedMembers == 0)
			TorquePropagator.removeNetwork(be.getLevel(), this);
	}

	void linkForRebind(KineticBlockEntity be) {
		if (members.containsKey(be)) return;
		members.put(be, be.calculateStressApplied());
		if (be.isSource()) sources.put(be, be.calculateAddedStressCapacity());
	}

	private static float getStressMultiplierForSpeed(float speed) {
		return Math.abs(speed);
	}

	public int getSize() {
		return unloadedMembers + members.size();
	}

}
