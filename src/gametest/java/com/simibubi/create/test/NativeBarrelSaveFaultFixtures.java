package com.simibubi.create.test;

import java.util.Objects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

/** A scoped test-only action after the real original native barrel has serialized its data. */
public final class NativeBarrelSaveFaultFixtures {
  private static final ThreadLocal<Scope> ACTIVE = new ThreadLocal<>();

  public static final class Scope implements AutoCloseable {
    private final BarrelBlockEntity barrel;
    private final Runnable action;
    private boolean fired;

    private Scope(BarrelBlockEntity barrel, Runnable action) {
      this.barrel = barrel;
      this.action = action;
    }

    public boolean fired() {
      return fired;
    }

    @Override
    public void close() {
      if (ACTIVE.get() == this) ACTIVE.remove();
    }
  }

  public static Scope afterSave(BarrelBlockEntity barrel, Runnable action) {
    Objects.requireNonNull(barrel);
    Objects.requireNonNull(action);
    if (!(barrel.getLevel() instanceof ServerLevel level)
        || !level.getServer().isSameThread()
        || level.getBlockEntity(barrel.getBlockPos()) != barrel
        || barrel.isRemoved()
        || ACTIVE.get() != null)
      throw new IllegalStateException("Native barrel save fault requires the actual server owner");
    var scope = new Scope(barrel, action);
    ACTIVE.set(scope);
    return scope;
  }

  public static void afterNativeSave(BarrelBlockEntity barrel) {
    var scope = ACTIVE.get();
    if (scope == null || scope.barrel != barrel) return;
    ACTIVE.remove();
    scope.fired = true;
    scope.action.run();
  }

  private NativeBarrelSaveFaultFixtures() {}
}
