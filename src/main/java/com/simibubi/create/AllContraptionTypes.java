package com.simibubi.create;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.bearing.BearingContraption;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.*;

public final class AllContraptionTypes {
  public static final Map<String, ContraptionType> BY_LEGACY_NAME = new HashMap<>();
  public static final Holder.Reference<ContraptionType> MOUNTED=register("mounted",com.simibubi.create.content.contraptions.mounted.MountedContraption::new);
  public static final Holder.Reference<ContraptionType> CARRIAGE =
      register("carriage", com.simibubi.create.content.trains.entity.CarriageContraption::new);
  public static final Holder.Reference<ContraptionType> PULLEY =
      register("pulley", com.simibubi.create.content.contraptions.pulley.PulleyContraption::new);
  public static final Holder.Reference<ContraptionType> ELEVATOR =
      register(
          "elevator", com.simibubi.create.content.contraptions.elevator.ElevatorContraption::new);
  public static final Holder.Reference<ContraptionType> GANTRY =
      register("gantry", com.simibubi.create.content.contraptions.gantry.GantryContraption::new);

  public static final Holder.Reference<ContraptionType> STABILIZED =
      register(
          "stabilized",
          com.simibubi.create.content.contraptions.bearing.StabilizedContraption::new);
  public static final Holder.Reference<ContraptionType> PISTON =
      register("piston", com.simibubi.create.content.contraptions.piston.PistonContraption::new);
  public static final Holder.Reference<ContraptionType> CLOCKWORK =
      register(
          "clockwork", com.simibubi.create.content.contraptions.bearing.ClockworkContraption::new);
  public static final Holder.Reference<ContraptionType> BEARING =
      register("bearing", BearingContraption::new);

  private static Holder.Reference<ContraptionType> register(
      String name, Supplier<? extends Contraption> factory) {
    var type = new ContraptionType(factory);
    BY_LEGACY_NAME.put(name, type);
    return Registry.registerForHolder(
        CreateBuiltInRegistries.CONTRAPTION_TYPE, Create.asResource(name), type);
  }

  public static void init() {}

  private AllContraptionTypes() {}
}
